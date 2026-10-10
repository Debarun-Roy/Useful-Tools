package backendsupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Stateless deterministic in-memory assembly; all sizes include manifest.json. */
public final class ArtifactBundle {

    public static final int MAX_BYTES=5*1024*1024, MAX_FILES=100, RESPONSE_BYTES=32*1024*1024;
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    public static final class Limit extends RuntimeException {
        public Limit(){
            super("OUTPUT_LIMIT");
        }
    }

    public static final class ResponseLimit extends RuntimeException {
        public ResponseLimit(){
            super("GENERATION_RESPONSE_LIMIT");
        }
    }

    public static byte[] previewBytes(Object body) {
        return previewBytes(body,RESPONSE_BYTES);
    }

    static byte[] previewBytes(Object body,int limit) {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        OutputStream bounded=new OutputStream() {
            int count;
            @Override public void write(int b){
                if(count==limit)
                    throw new ResponseLimit();
                bytes.write(b);
                count++;
            }
            @Override public void write(byte[] b,int off,int len){
                if(len>limit-count)
                    throw new ResponseLimit();
                bytes.write(b,off,len);
                count+=len;
            }
        };
        try(Writer writer=new OutputStreamWriter(bounded,StandardCharsets.UTF_8)) {
            new Gson().toJson(body,writer);
        }
        catch(IOException e){
            throw new IllegalStateException("SERIALIZATION_FAILED");
        }
        return bytes.toByteArray();
    }

    public static final class Text {
        private final StringBuilder value=new StringBuilder();
        private int bytes;
        public void add(String s){
            int n=s.getBytes(StandardCharsets.UTF_8).length;
            if(n>MAX_BYTES-bytes)
                throw new Limit();
            bytes+=n;
            value.append(s);
        }
        public String value(){
            return value.toString();
        }
    }

    public record Bundle(JsonObject manifest,String digest,SortedMap<String,String> files,boolean projectPaths) {
        public Bundle(JsonObject manifest,String digest,SortedMap<String,String> files) {
            this(manifest,digest,files,false);
        }
        public Map<String,Object> preview(List<Contracts.Finding> findings,boolean adminPreview) {
            return Map.of("manifest",manifest,"digest",digest,"findings",findings,"adminPreview",adminPreview,
                "files",files.entrySet().stream().map(e->Map.of("path",e.getKey(),"content",e.getValue())).toList());
        }
        public byte[] zip() {
            try {
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(ZipOutputStream zip=new ZipOutputStream(bytes,StandardCharsets.UTF_8)) {
                    int total=0;
                    Set<String> paths=new HashSet<>();
                    for(var f:files.entrySet()) {
                        if(projectPaths)safeProjectPath(f.getKey(),paths);else safePath(f.getKey(),paths);
                        byte[] data=f.getValue().getBytes(StandardCharsets.UTF_8);
                        if(paths.size()>MAX_FILES||data.length>MAX_BYTES-total)
                            throw new Limit();
                        total+=data.length;
                        ZipEntry entry=new ZipEntry(f.getKey());
                        entry.setMethod(ZipEntry.STORED);
                        // Jan 1 at midnight is ZipEntry's pre-DOS sentinel and adds a zone-dependent
                        // extended timestamp. Jan 2 is representable as DOS time without that extra.
                        entry.setTimeLocal(LocalDateTime.of(1980,1,2,0,0));
                        entry.setSize(data.length);
                        entry.setCompressedSize(data.length);
                        CRC32 crc=new CRC32();
                        crc.update(data);
                        entry.setCrc(crc.getValue());
                        zip.putNextEntry(entry);
                        zip.write(data);
                        zip.closeEntry();
                    }
                }
                return bytes.toByteArray();
            }
            catch(IOException e){
                throw new IllegalStateException("ASSEMBLY_FAILED");
            }
        }
    }
    public static void safePath(String path,Set<String> seen) {
        // Only flat fixed ASCII artifact names are accepted; no directories or symlinks can be emitted.
        if(!path.matches("[A-Za-z0-9][A-Za-z0-9_-]*(?:\\.[A-Za-z0-9][A-Za-z0-9_-]*)+")||!seen.add(path.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("UNSAFE_ARTIFACT_PATH");
    }
    /** Opt-in regular-file paths for fixed project templates; never accepts archive links. */
    public static void safeProjectPath(String path,Set<String> seen) {
        if(path==null||path.length()>240||!path.matches("[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*"))
            throw new IllegalArgumentException("UNSAFE_ARTIFACT_PATH");
        for(String segment:path.split("/")) {
            String stem=segment.split("\\.",2)[0].toLowerCase(Locale.ROOT);
            if(segment.equals(".")||segment.equals("..")||segment.endsWith(".")||segment.length()>100
                ||stem.matches("con|prn|aux|nul|com[0-9]|lpt[0-9]"))
                throw new IllegalArgumentException("UNSAFE_ARTIFACT_PATH");
        }
        String normalized=path.toLowerCase(Locale.ROOT);
        for(String prior:seen)if(prior.equals(normalized)||prior.startsWith(normalized+"/")||normalized.startsWith(prior+"/"))
            throw new IllegalArgumentException("UNSAFE_ARTIFACT_PATH");
        seen.add(normalized);
    }
    public static String canonical(JsonElement value) {
        if(value.isJsonObject()) {
            JsonObject sorted=new JsonObject();
            new TreeSet<>(value.getAsJsonObject().keySet()).forEach(k->sorted.add(k,JsonParser.parseString(canonical(value.getAsJsonObject().get(k)))));
            return JSON.toJson(sorted);
        }
        if(value.isJsonArray())
            return "["+String.join(",",value.getAsJsonArray().asList().stream().map(ArtifactBundle::canonical).toList())+"]";
        if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isNumber())
            return value.getAsBigDecimal().stripTrailingZeros().toPlainString();
        return JSON.toJson(value);
    }
    public static String sha(String s){
        return sha(s.getBytes(StandardCharsets.UTF_8));
    }
    public static String sha(byte[] bytes){
        try{
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch(NoSuchAlgorithmException e){
            throw new IllegalStateException(e);
        }
    }
    private final SortedMap<String,String> files=new TreeMap<>();
    private final Set<String> paths=new HashSet<>();
    private int bytes;
    private final boolean projectPaths;
    public ArtifactBundle(){this(false);}
    private ArtifactBundle(boolean projectPaths){this.projectPaths=projectPaths;}
    public static ArtifactBundle project(){return new ArtifactBundle(true);}
    public void add(String path,String content) {
        if(projectPaths)safeProjectPath(path,paths);else safePath(path,paths);
        int n=content.getBytes(StandardCharsets.UTF_8).length;
        if(paths.size()>MAX_FILES||n>MAX_BYTES-bytes)
            throw new Limit();
        if(content.indexOf('\r')>=0)
            throw new IllegalArgumentException("NON_LF_ARTIFACT");
        bytes+=n;
        files.put(path,content);
    }
    /** B3 module assembly; leaves the released B2 assembler and its bytes unchanged. */
    public Bundle finish(JsonObject metadata) {
        JsonObject core=metadata.deepCopy();
        JsonArray inventory=new JsonArray();
        for(var f:files.entrySet()) {
            JsonObject file=new JsonObject();
            file.addProperty("path",f.getKey());
            file.addProperty("bytes",f.getValue().getBytes(StandardCharsets.UTF_8).length);
            file.addProperty("sha256",sha(f.getValue()));
            inventory.add(file);
        }
        core.add("files",inventory);
        String digest=sha(canonical(core));
        JsonObject manifest=new JsonObject();
        manifest.add("core",core);
        manifest.addProperty("digest",digest);
        add("manifest.json",canonical(manifest)+"\n");
        return new Bundle(manifest,digest,Collections.unmodifiableSortedMap(new TreeMap<>(files)),projectPaths);
    }
    public static Bundle assemble(SchemaGeneration.Model model) {
        String target=SchemaGeneration.str(model.request(),"target");
        boolean sqlite=target.equals("sqlite");
        ArtifactBundle out=new ArtifactBundle();
        out.add("schema.sql",(sqlite?new SchemaDialect.SQLite():new SchemaDialect.PostgreSQL()).render(model));
        String normalized=canonical(model.request());out.add("schema.json",normalized+"\n");
        out.add("verify.py",resource("schema-verify.py"));
        out.add("README.md","# UsefulTools initial schema bundle\n\nTemplate: "+SchemaGeneration.TEMPLATE+"; contract: 0.2.0; target: "+target+".\n\n"
            +"Use an EMPTY database you own. No migrations, DROP, reconciliation, identity sequences or deferred constraints are generated. Back up real data separately.\n\n"
            +(sqlite?"SQLite compatibility: 3.45.1 or newer with JSON functions. On EVERY connection, before any transaction, execute PRAGMA foreign_keys=ON and confirm PRAGMA foreign_keys returns 1. Then apply schema.sql. The SQL pragma alone cannot enable enforcement inside an existing transaction.\n\n":"PostgreSQL compatibility: 17.11. Use psql 17.11 -X -v ON_ERROR_STOP=1 -d YOUR_DATABASE -f schema.sql. Apply only to an empty trusted schema with an appropriate search_path. standard_conforming_strings is enabled; timezone is UTC.\n\n")
            +"Verify before application: python verify.py"+(sqlite?"":" --database YOUR_DISPOSABLE_DATABASE --psql /path/to/psql")+". The procedure verifies payload hashes and creates/probes all tables in an in-memory SQLite database or a rolled-back PostgreSQL transaction. It needs Python 3.14; PostgreSQL uses psql 17.11 (no Python driver). It is a schema smoke check, not a proof of arbitrary business invariants.\n\n"
            +"SQLite: integer/bigint affinity does not enforce integer ranges/types; decimal uses NUMERIC affinity and may lose precision. Boolean 0/1, varchar character length and JSON validity have explicit CHECK constraints. Date and timestamp TEXT are conventions, not format-enforced on inserted values. Timestamp defaults use UTC ISO text; PostgreSQL uses timestamptz and JSONB. PostgreSQL NUMERIC rounds inserted fractions to the declared scale. Text ordering and collation are database-dependent. NULL can satisfy CHECK/UNIQUE where SQL permits it.\n\n"
            +"Stable IDs are references, not database names. All identifiers are quoted; conservative case-insensitive collision checks and 63 UTF-8-byte limits apply. Self-references are supported; multi-table cycles rejected. Checks are column/literal comparisons only, ANDed by separate constraints. No raw expressions/functions or SET DEFAULT actions.\n\n"
            +"Digest: SHA-256 of UTF-8 canonical normalized request (without trailing LF). Payload SHA-256 covers exact file bytes including LF. Bundle digest is SHA-256 of canonical manifest.core (without LF); manifest.json is excluded from its own payload inventory. Canonical JSON sorts object keys lexicographically, preserves arrays, emits compact JSON and plain minimal numbers. ZIP entries use sorted paths, STORED method and 1980-01-02 metadata. Limit: 100 files and 5 MiB including manifest.json; no symlinks.\n");
        JsonObject core=new JsonObject();
        core.addProperty("manifestVersion","1.0.0");
        core.addProperty("schemaVersion",SchemaGeneration.VERSION);
        core.addProperty("templateVersion",SchemaGeneration.TEMPLATE);
        core.addProperty("target",target);
        core.addProperty("specificationDigest",sha(normalized));
        core.addProperty("runtimeCompatibility",sqlite?"SQLite >= 3.45.1 with JSON":"PostgreSQL 17.11");
        core.addProperty("verificationRuntime",sqlite?"SQLite 3.50.4":"PostgreSQL 17.11");
        core.add("verificationDependencies",JSON.toJsonTree(List.of("Python 3.14",sqlite?"stdlib sqlite3":"psql 17.11")));
        core.add("limitations",JSON.toJsonTree(List.of("Initial schema only; no multi-table cycles",sqlite?"SQLite affinity and approximate decimal; date/timestamp format conventions":"PostgreSQL NUMERIC insertion rounding; database collation semantics")));
        JsonArray inventory=new JsonArray();
        for(var f:out.files.entrySet()){
            JsonObject file=new JsonObject();
            file.addProperty("path",f.getKey());
            file.addProperty("bytes",f.getValue().getBytes(StandardCharsets.UTF_8).length);
            file.addProperty("sha256",sha(f.getValue()));inventory.add(file);
        }
        core.add("files",inventory);
        String digest=sha(canonical(core));
        JsonObject manifest=new JsonObject();
        manifest.add("core",core);
        manifest.addProperty("digest",digest);
        out.add("manifest.json",canonical(manifest)+"\n");
        return new Bundle(manifest,digest,Collections.unmodifiableSortedMap(out.files));
    }
    private static String resource(String name) {
        try(InputStream in=ArtifactBundle.class.getResourceAsStream("/backendsupport/"+name)) {
            if(in==null)
                throw new IllegalStateException("MISSING_TEMPLATE");
            return new String(in.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n");
        }
        catch(IOException e){
            throw new IllegalStateException("MISSING_TEMPLATE");
        }
    }
}
