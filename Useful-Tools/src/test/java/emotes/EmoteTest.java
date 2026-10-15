package emotes;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import com.google.gson.*;
import static org.junit.jupiter.api.Assertions.*;

class EmoteTest {
    @TempDir Path dir;
    Connection open(String name) throws Exception {Connection c=DriverManager.getConnection("jdbc:sqlite:"+dir.resolve(name));EmoteDb.configure(c);return c;}
    void sql(Connection c,String sql) throws Exception {try(Statement s=c.createStatement()){s.execute(sql);}}
    long number(Connection c,String sql) throws Exception {try(Statement s=c.createStatement();ResultSet rs=s.executeQuery(sql)){rs.next();return rs.getLong(1);}}
    void setup(Connection c) throws Exception {EmoteMigration.migrate(c);CatalogImport.install(c);sql(c,"INSERT INTO user_table(username,hashed_password) VALUES('user','synthetic')");sql(c,"UPDATE tool_toggles SET enabled=1 WHERE tool_path='/emote-utils'");}
    EmoteService service(String name){return new EmoteService(()->DriverManager.getConnection("jdbc:sqlite:"+dir.resolve(name)));}
    JsonObject read(String name,String query){return new Gson().toJsonTree(service(name).read("user","user",query,false)).getAsJsonObject();}
    void failure(int status,String code,Runnable fn){var e=assertThrows(EmoteService.Failure.class,fn::run);assertEquals(status,e.status);assertEquals(code,e.code);}
    CatalogSeed priorFixture() throws Exception {
        // Test-only synthetic prior content. Production only exposes load() with pinned provenance.
        var entries=new ArrayList<>(CatalogSeed.load().entries());entries.remove(0);
        var constructor=CatalogSeed.class.getDeclaredConstructor(List.class);constructor.setAccessible(true);
        return constructor.newInstance(entries);
    }

    @Test void pinnedSourceAndCompleteSqliteGsonRoundTrip() throws Exception {
        var seed=CatalogSeed.load();assertEquals(5369,seed.entries().size());
        try(var c=open("roundtrip")){setup(c);CatalogImport.validate(c,seed,CatalogSeed.VERSION);
            Set<String> seen=new HashSet<>();for(int offset=0;offset<5369;offset+=100){var page=read("roundtrip","type=all&includeAlternates=true&limit=100&offset="+offset);assertEquals(5369,page.get("total").getAsInt());for(var item:page.getAsJsonArray("items")){String id=item.getAsJsonObject().get("id").getAsString();assertTrue(seen.add(id));var expected=seed.entries().stream().filter(e->e.id().equals(id)).findFirst().orElseThrow();assertEquals(new Gson().toJsonTree(expected),item);}}
            assertEquals(5369,seen.size());assertEquals(3953,read("roundtrip","").get("total").getAsInt());
            assertEquals(4097,read("roundtrip","type=all").get("total").getAsInt());
        }
    }
    @Test void resourcesRejectEveryAlterationAndMissingLicense() throws Exception {
        for(String file:List.of("manifest.json","emoji-17.0.json","text-and-symbols.json","sources/emoji-test-17.0.txt","sources/UNICODE-LICENSE.txt")) {
            assertThrows(SQLException.class,()->CatalogSeed.load(n->{byte[] b=CatalogSeed.resource("/emotes/catalog/"+n);if(n.equals(file))b[0]^=1;return b;}));
        }
        assertThrows(SQLException.class,()->CatalogSeed.load(n->{if(n.endsWith("LICENSE.txt"))throw new SQLException("missing");return CatalogSeed.resource("/emotes/catalog/"+n);}));
    }
    @Test void upgradePreservesBinaryCaseIdentitiesAndEnforcesParent() throws Exception {
        try(var c=open("upgrade")){sql(c,"CREATE TABLE user_table(username VARCHAR(500) NOT NULL,hashed_password TEXT NOT NULL,role TEXT,locked_until TEXT)");sql(c,"INSERT INTO user_table VALUES('Case','hash','admin','later'),('case','other','user',NULL)");EmoteMigration.migrate(c);EmoteMigration.migrate(c);
            assertEquals(2,number(c,"SELECT count(*) FROM user_table"));assertTrue(AccountKey.compatible(c));
            sql(c,"CREATE TABLE child(owner TEXT REFERENCES user_table(username))");sql(c,"INSERT INTO child VALUES('Case')");assertThrows(SQLException.class,()->sql(c,"INSERT INTO child VALUES('missing')"));assertThrows(SQLException.class,()->sql(c,"INSERT INTO user_table VALUES('Case','x',NULL,NULL)"));
            assertEquals(1,number(c,"SELECT count(*) FROM user_table WHERE username='Case' AND role='admin' AND locked_until='later'"));
        }
    }
    @Test void badAccountsAbortWithoutRepairOrLedger() throws Exception {
        int i=0;for(String rows:List.of("('same'),('same')","(NULL)","('')","('Guest User')","(char(10))","(zeroblob(5))","(CAST(X'80' AS TEXT))")){
            try(var c=open("invalid"+i++)){sql(c,"CREATE TABLE user_table(username)");sql(c,"INSERT INTO user_table VALUES"+rows);long before=number(c,"SELECT count(*) FROM user_table");assertThrows(SQLException.class,()->EmoteMigration.migrate(c));assertEquals(before,number(c,"SELECT count(*) FROM user_table"));assertEquals(0,number(c,"SELECT count(*) FROM sqlite_master WHERE name='emote_schema_migration'"));}
        }
    }
    @Test void compatibleAndMisleadingIndexes() throws Exception {
        try(var c=open("compatible")){sql(c,"CREATE TABLE user_table(username TEXT COLLATE BINARY,hashed_password TEXT)");sql(c,"CREATE UNIQUE INDEX other_name ON user_table(username COLLATE BINARY)");EmoteMigration.migrate(c);assertEquals(0,number(c,"SELECT count(*) FROM sqlite_master WHERE name='emote_account_username_binary'"));}
        for(String definition:List.of("ON user_table(username COLLATE NOCASE)","ON user_table(username) WHERE username<>''","ON user_table(hashed_password)")) {
            try(var c=open(UUID.randomUUID().toString())){sql(c,"CREATE TABLE user_table(username TEXT,hashed_password TEXT)");sql(c,"CREATE UNIQUE INDEX emote_account_username_binary "+definition);assertThrows(SQLException.class,()->EmoteMigration.migrate(c));}
        }
        try(var c=open("parentnocase")){sql(c,"CREATE TABLE user_table(username TEXT COLLATE NOCASE,hashed_password TEXT)");assertThrows(SQLException.class,()->EmoteMigration.migrate(c));}
    }
    @Test void checksumAndFailureRollback() throws Exception {
        for(String point:List.of("account-key","ddl","before-ledger","before-commit"))try(var c=open(point)){
            assertThrows(SQLException.class,()->EmoteMigration.migrate(c,p->{if(p.equals(point))throw new SQLException("injected");}));assertEquals(0,number(c,"SELECT count(*) FROM sqlite_master WHERE name LIKE 'emote_%'"));EmoteMigration.migrate(c);
        }
        try(var c=open("checksum")){EmoteMigration.migrate(c);sql(c,"UPDATE emote_schema_migration SET checksum='"+"a".repeat(64)+"'");assertThrows(SQLException.class,()->EmoteMigration.migrate(c));assertEquals(2,number(c,"SELECT count(*) FROM emote_schema_migration WHERE checksum='"+"a".repeat(64)+"'"));}
    }
    @Test void concurrentMigratorsAndImportersActuallyExecute() throws Exception {
        CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
        try {List<Future<?>> tasks=new ArrayList<>();for(int i=0;i<2;i++)tasks.add(pool.submit(()->{try{ready.countDown();assertTrue(go.await(5,TimeUnit.SECONDS));try(var c=open("concurrent")){EmoteMigration.migrate(c);CatalogImport.install(c);}}catch(Exception e){throw new RuntimeException(e);}}));assertTrue(ready.await(5,TimeUnit.SECONDS));go.countDown();for(var t:tasks)t.get(20,TimeUnit.SECONDS);}
        finally{pool.shutdownNow();}
        try(var c=open("concurrent")){assertEquals(5369,number(c,"SELECT count(*) FROM emote_catalog_entry"));assertEquals(2,number(c,"SELECT count(*) FROM emote_schema_migration"));}
    }
    @Test void idempotenceAndInterruptedStageRecoveryPreservePriorVersionAndPrivateFixture() throws Exception {
        try(var c=open("retry")){setup(c);sql(c,"CREATE TABLE legacy_private(owner TEXT,value TEXT)");sql(c,"INSERT INTO legacy_private VALUES('user','exact  ')");
            long before=number(c,"SELECT total_changes()");CatalogImport.install(c);assertEquals(before,number(c,"SELECT total_changes()"));
            for(String point:List.of("before-rows","row","validation","activation","after-pointer","staged-commit")){
                String v="fixture-"+point;assertThrows(SQLException.class,()->CatalogImport.install(c,CatalogSeed.load(),v,p->{if(p.equals(point))throw new SQLException("injected");}));
                assertEquals(CatalogSeed.VERSION,read("retry","").get("catalogVersion").getAsString());
                CatalogImport.install(c,CatalogSeed.load(),v,EmoteMigration.NONE);assertEquals(v,read("retry","").get("catalogVersion").getAsString());CatalogImport.activate(c,CatalogSeed.VERSION);
            }
            assertEquals(1,number(c,"SELECT count(*) FROM legacy_private WHERE value='exact  '"));assertEquals(1,number(c,"SELECT count(*) FROM user_table"));
            sql(c,"UPDATE emote_catalog_entry SET value='corrupt' WHERE version='fixture-row' AND entry_id='text-001'");assertThrows(SQLException.class,()->CatalogImport.install(c,CatalogSeed.load(),"fixture-row",EmoteMigration.NONE));assertEquals(CatalogSeed.VERSION,read("retry","").get("catalogVersion").getAsString());
        }
    }
    @Test void activePointerAndSchemaConstraints() throws Exception {
        try(var c=open("constraints")){setup(c);
            for(String invalid:List.of("INSERT INTO emote_catalog_active VALUES(2,'"+CatalogSeed.VERSION+"')","UPDATE emote_catalog_active SET version='absent'","UPDATE emote_catalog_version SET state='retired'","UPDATE emote_catalog_entry SET qualification='unknown' WHERE kind='emoji'","UPDATE emote_catalog_entry SET kind='wrong'","UPDATE emote_catalog_entry SET category_id='absent'","UPDATE emote_catalog_tag SET ordinal=-1"))assertThrows(SQLException.class,()->sql(c,invalid));
            sql(c,"INSERT INTO emote_catalog_version VALUES('staged','"+"a".repeat(64)+"','now','staged')");assertThrows(SQLException.class,()->sql(c,"UPDATE emote_catalog_active SET version='staged'"));
            assertEquals(2,number(c,"SELECT count(*) FROM sqlite_master WHERE type='index' AND name IN ('emote_catalog_page','emote_catalog_qualification')"));
        }
    }
    @Test void literalSearchCountsTaxonomyAndBoundaries() throws Exception {
        try(var c=open("search")){setup(c);var seed=CatalogSeed.load();
            for(String q:List.of(":)","%","_","\\","' OR 1=1 --","😀","🇮🇳","SMILE","e\u0301"," ")){
                String lower=q.chars().collect(StringBuilder::new,(b,ch)->b.append((char)(ch>='A'&&ch<='Z'?ch+32:ch)),StringBuilder::append).toString();
                long expected=seed.entries().stream().filter(e->e.name().toLowerCase(Locale.ROOT).contains(lower)||e.value().contains(q)).count();
                assertEquals(expected,read("search","type=all&includeAlternates=true&q="+URLEncoder.encode(q,StandardCharsets.UTF_8)).get("total").getAsLong());
            }
            assertEquals(0,read("search","offset=5369").getAsJsonArray("items").size());assertFalse(read("search","q=not-present-zzzz").get("hasMore").getAsBoolean());
            JsonObject categories=new Gson().toJsonTree(service("search").read("user","user",null,true)).getAsJsonObject();long sum=0;
            for(var cat:categories.getAsJsonArray("categories")){var x=cat.getAsJsonObject();sum+=x.get("count").getAsLong();assertEquals(x.get("count").getAsLong(),read("search","type="+x.get("type").getAsString()+"&category="+URLEncoder.encode(x.get("id").getAsString(),StandardCharsets.UTF_8)).get("total").getAsLong());}assertEquals(4097,sum);
        }
    }
    @Test void queryErrorsAndAccessPrecedence() throws Exception {
        try(var c=open("query")){setup(c);
            for(String q:List.of("limit=0","limit=101","limit=-1","limit=2147483648","offset=5370","offset=1.0","type=mine","includeAlternates=1","type=all&type=emoji","extra=1","q=%ED%A0%80","q=%FF","q=%","q="+"x".repeat(101),"subgroup=face-smiling","category=not-present","version=","limit=","q=one&&q=two"))failure(400,"INVALID_QUERY",()->read("query",q));
            failure(409,"CATALOG_VERSION_CHANGED",()->read("query","version=old"));
            failure(401,"UNAUTHENTICATED",()->service("query").read("missing","user",null,false));
            sql(c,"UPDATE tool_toggles SET enabled=0");for(String role:List.of("user","admin","guest"))failure(403,"TOOL_DISABLED",()->service("query").read(role.equals("guest")?"Guest User":"user",role,"limit=bad",false));
        }
        failure(503,"DB_UNAVAILABLE",()->new EmoteService(()->null).read("user","user",null,false));
        try(var c=open("empty")){EmoteMigration.migrate(c);sql(c,"UPDATE tool_toggles SET enabled=1");failure(503,"CATALOG_UNAVAILABLE",()->service("empty").read("Guest User","guest",null,false));}
    }
    @Test void boundedLockedDatabase() throws Exception {
        try(var c=open("locked")){setup(c);sql(c,"BEGIN EXCLUSIVE");long start=System.nanoTime();failure(503,"DB_UNAVAILABLE",()->read("locked",""));long ms=(System.nanoTime()-start)/1_000_000;assertTrue(ms>=2000 && ms<7000,"bounded SQLite busy timeout");sql(c,"ROLLBACK");}
    }
    @Test void conflictingHistoryAndSchemaPreventImportAndActivation() throws Exception {
        try(var c=open("history")){setup(c);sql(c,"UPDATE emote_schema_migration SET checksum='"+"0".repeat(64)+"'");
            assertThrows(SQLException.class,()->CatalogImport.install(c));assertThrows(SQLException.class,()->CatalogImport.activate(c,CatalogSeed.VERSION));
        }
        try(var c=open("shape")){setup(c);sql(c,"DROP INDEX emote_catalog_page");sql(c,"CREATE INDEX emote_catalog_page ON emote_catalog_entry(name)");
            assertThrows(SQLException.class,()->EmoteMigration.migrate(c));assertThrows(SQLException.class,()->CatalogImport.install(c));
        }
    }
    @Test void realReadTransactionRemainsCoherentAcrossActivation() throws Exception {
        try(var reader=open("snapshot");var writer=open("snapshot")){
            var prior=priorFixture();setup(reader);CatalogImport.install(writer,prior,"prior-fixture",EmoteMigration.NONE);CatalogImport.activate(writer,CatalogSeed.VERSION);
            sql(reader,"BEGIN");assertEquals(1,number(reader,"SELECT count(*) FROM emote_catalog_active WHERE version='"+CatalogSeed.VERSION+"'"));
            CountDownLatch pointerChanged=new CountDownLatch(1),release=new CountDownLatch(1);ExecutorService pool=Executors.newSingleThreadExecutor();
            try{Future<?> f=pool.submit(()->{try{CatalogImport.activate(writer,prior,"prior-fixture",p->{if(p.equals("after-pointer")){pointerChanged.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new SQLException("timeout");}catch(InterruptedException e){throw new SQLException("interrupted");}}});}catch(Exception e){throw new RuntimeException(e);}});
                assertTrue(pointerChanged.await(5,TimeUnit.SECONDS));assertEquals(5369,number(reader,"SELECT count(*) FROM emote_catalog_entry WHERE version=(SELECT version FROM emote_catalog_active)"));assertEquals(1,number(reader,"SELECT count(*) FROM emote_catalog_active WHERE version='"+CatalogSeed.VERSION+"'"));sql(reader,"COMMIT");release.countDown();f.get(10,TimeUnit.SECONDS);assertEquals("prior-fixture",read("snapshot","").get("catalogVersion").getAsString());
                assertEquals(5368,read("snapshot","type=all&includeAlternates=true").get("total").getAsInt());
                failure(409,"CATALOG_VERSION_CHANGED",()->read("snapshot","version="+CatalogSeed.VERSION));
            }finally{release.countDown();pool.shutdownNow();}
        }
    }
    @Test void actualFreshBootstrapAndExistingMetadataReconciliation() throws Exception {
        var method=passwordgenerator.dao.UserDAO.class.getDeclaredMethod("ensureUserProfileSchema",Connection.class);method.setAccessible(true);
        try(var c=open("bootstrap")){method.invoke(null,c);assertTrue(AccountKey.compatible(c));
            sql(c,"INSERT INTO user_table(username,hashed_password) VALUES('fresh','hash')");
            assertThrows(SQLException.class,()->sql(c,"INSERT INTO user_table(username,hashed_password) VALUES('fresh','different')"));
            method.invoke(null,c);EmoteMigration.migrate(c);assertEquals(1,number(c,"SELECT count(*) FROM user_table WHERE hashed_password='hash'"));
        }
    }
    @Test void legacyTextIdentitiesAreNotReinterpretedOrLengthLimited() throws Exception {
        try(var c=open("identities")){sql(c,"CREATE TABLE user_table(username TEXT NOT NULL,hashed_password TEXT)");
            try(var p=c.prepareStatement("INSERT INTO user_table VALUES(?,'synthetic')")) {
                for(String name:List.of(" space ","x".repeat(501),"\ud83d\ude00".repeat(300),"Case","case")){p.setString(1,name);p.executeUpdate();}
            }
            EmoteMigration.migrate(c);assertEquals(5,number(c,"SELECT count(*) FROM user_table"));
            assertEquals(1,number(c,"SELECT count(*) FROM user_table WHERE username=' space '"));
        }
        try(var c=open("utf16-identities")){sql(c,"PRAGMA encoding='UTF-16le'");sql(c,"CREATE TABLE user_table(username TEXT NOT NULL,hashed_password TEXT)");
            sql(c,"INSERT INTO user_table VALUES('exact space ','synthetic')");EmoteMigration.migrate(c);
            assertEquals(1,number(c,"SELECT count(*) FROM user_table WHERE username='exact space '"));
        }
    }
    @Test void existingProbeTableIsNeverDropped() throws Exception {
        try(var c=open("collision")){sql(c,"CREATE TABLE emote_parent_probe(value TEXT)");sql(c,"INSERT INTO emote_parent_probe VALUES('retained')");
            assertThrows(SQLException.class,()->EmoteMigration.migrate(c));assertEquals(1,number(c,"SELECT count(*) FROM emote_parent_probe WHERE value='retained'"));
            assertEquals(0,number(c,"SELECT count(*) FROM sqlite_master WHERE name='emote_schema_migration'"));
        }
    }
    @Test void midImportRollbackAndIncompleteStageFailClosed() throws Exception {
        try(var c=open("partial")){setup(c);AtomicInteger rows=new AtomicInteger();
            assertThrows(SQLException.class,()->CatalogImport.install(c,CatalogSeed.load(),"middle",p->{if(p.equals("row") && rows.incrementAndGet()==2700)throw new SQLException("injected mid-import");}));
            assertEquals(2700,rows.get());assertEquals(0,number(c,"SELECT count(*) FROM emote_catalog_entry WHERE version='middle'"));
            sql(c,"INSERT INTO emote_catalog_version VALUES('incomplete','"+CatalogSeed.MANIFEST+"','now','staged')");
            assertThrows(SQLException.class,()->CatalogImport.install(c,CatalogSeed.load(),"incomplete",EmoteMigration.NONE));
            assertEquals(CatalogSeed.VERSION,read("partial","").get("catalogVersion").getAsString());
        }
        // Fresh connection after interruption: successful retry from the rolled-back version.
        try(var c=open("partial")){CatalogImport.install(c,CatalogSeed.load(),"middle",EmoteMigration.NONE);assertEquals(5369,number(c,"SELECT count(*) FROM emote_catalog_entry WHERE version='middle'"));}
    }
    @Test void corruptedPinnedMetadataAndRowSetsAreRejected() throws Exception {
        for(String mutation:List.of("qualification","id","missing","extra","name","subgroup","value")) {
            var array=JsonParser.parseString(new String(CatalogSeed.resource("/emotes/catalog/emoji-17.0.json"),StandardCharsets.UTF_8)).getAsJsonArray();
            switch(mutation){
                case "missing" -> array.remove(0);
                case "extra" -> array.add(array.get(0).deepCopy());
                case "id" -> array.get(0).getAsJsonObject().addProperty("id",array.get(1).getAsJsonObject().get("id").getAsString());
                default -> array.get(0).getAsJsonObject().addProperty(mutation,"corrupted");
            }
            byte[] altered=new Gson().toJson(array).getBytes(StandardCharsets.UTF_8);
            assertThrows(SQLException.class,()->CatalogSeed.load(n->n.equals("emoji-17.0.json")?altered:CatalogSeed.resource("/emotes/catalog/"+n)));
        }
    }
    @Test void unexpectedStorageFailureIsSanitized() {
        var service=new EmoteService(()->{throw new IllegalStateException("internal path must not escape");});
        failure(503,"DB_UNAVAILABLE",()->service.read("user","user",null,false));
        failure(503,"DB_UNAVAILABLE",()->service.access("user","user"));
    }
}
