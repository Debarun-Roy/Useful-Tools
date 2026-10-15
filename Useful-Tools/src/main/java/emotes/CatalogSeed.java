package emotes;

import com.google.gson.*;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Validates retained byte provenance and independently reconstructs ordered Unicode rows. */
public final class CatalogSeed {
    public static final String VERSION="emote-1-unicode-17.0";
    static final String MANIFEST="e88ec5396124d15663c7314b78802b99f8b832aed6df0a9e11b11b420faa3661";
    public record Entry(String id,String value,String name,String kind,String categoryId,String subgroup,
                        String qualification,String emojiVersion,List<String> codepoints,int sortOrder,List<String> tags) { }
    @FunctionalInterface interface Resources { byte[] read(String name) throws SQLException; }
    private final List<Entry> entries;
    private CatalogSeed(List<Entry> entries) { this.entries=List.copyOf(entries); }
    public List<Entry> entries() { return entries; }
    public static CatalogSeed load() throws SQLException { return load(n -> resource("/emotes/catalog/"+n)); }
    static CatalogSeed load(Resources resources) throws SQLException {
        try {
            byte[] manifest=resources.read("manifest.json"); require(sha(manifest).equals(MANIFEST));
            JsonObject m=JsonParser.parseString(new String(manifest,StandardCharsets.UTF_8)).getAsJsonObject();
            require(VERSION.equals(m.get("catalogVersion").getAsString()));
            Map<String,byte[]> bytes=new HashMap<>();
            for(var d:m.getAsJsonObject("sha256").entrySet()) {
                byte[] b=resources.read(d.getKey());require(sha(b).equals(d.getValue().getAsString()));bytes.put(d.getKey(),b);
            }
            require(bytes.size()==4 && bytes.get("sources/UNICODE-LICENSE.txt").length>0);
            List<Entry> unicode=parse(bytes.get("emoji-17.0.json"));
            List<Entry> expected=new ArrayList<>();String group=null,subgroup=null;
            Pattern row=Pattern.compile("^([0-9A-F ]+)\\s*;\\s*([a-z-]+)\\s*#\\s*(\\S+) E([0-9.]+) (.+)$");
            for(String line:new String(bytes.get("sources/emoji-test-17.0.txt"),StandardCharsets.UTF_8).split("\\R")) {
                if(line.startsWith("# group: ")) {group=line.substring(9);subgroup=null;}
                else if(line.startsWith("# subgroup: ")) subgroup=line.substring(12);
                else if(!line.isBlank() && !line.startsWith("#")) {
                    Matcher match=row.matcher(line);require(match.matches() && group!=null && subgroup!=null);
                    List<String> points=List.of(match.group(1).trim().split(" +"));String value=decode(points);
                    require(value.equals(match.group(3)));
                    expected.add(new Entry("emoji-"+String.join("-",points).toLowerCase(Locale.ROOT),value,match.group(5),"emoji",group,subgroup,match.group(2),match.group(4),points,expected.size(),List.of()));
                }
            }
            require(unicode.equals(expected) && unicode.size()==5225);
            List<Entry> curated=parse(bytes.get("text-and-symbols.json"));require(curated.size()==144);
            List<Entry> all=new ArrayList<>(unicode);all.addAll(curated);Set<String> ids=new HashSet<>();Set<String> curatedValues=new HashSet<>();
            Map<String,Integer> counts=new HashMap<>();
            for(Entry e:all) {
                require(ids.add(e.id) && !e.name.isBlank() && !e.categoryId.isBlank() && e.sortOrder>=0 && decode(e.codepoints).equals(e.value));
                require(Set.of("emoji","ascii","kaomoji","symbol").contains(e.kind));
                if(e.kind.equals("emoji")) require(Set.of("fully-qualified","component","minimally-qualified","unqualified").contains(e.qualification));
                else require(e.subgroup==null && e.qualification==null && e.emojiVersion==null && curatedValues.add(e.value));
                if(e.kind.equals("ascii")) require(e.value.codePoints().allMatch(cp->cp<=127));
                counts.merge(e.kind.equals("emoji")?e.qualification:e.kind,1,Integer::sum);
            }
            require(counts.equals(Map.of("fully-qualified",3944,"component",9,"minimally-qualified",1029,"unqualified",243,"ascii",38,"kaomoji",53,"symbol",53)));
            return new CatalogSeed(all);
        } catch(RuntimeException e) { throw new SQLException("Pinned catalog validation failed"); }
    }
    private static List<Entry> parse(byte[] bytes) {
        List<Entry> result=new ArrayList<>();
        for(JsonElement element:JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonArray()) {
            JsonObject o=element.getAsJsonObject(); List<String> cps=new ArrayList<>(), tags=new ArrayList<>();
            o.getAsJsonArray("codepoints").forEach(v->cps.add(v.getAsString()));
            if(o.has("tags")) o.getAsJsonArray("tags").forEach(v->tags.add(v.getAsString()));
            result.add(new Entry(str(o,"id"),str(o,"value"),str(o,"name"),str(o,"kind"),str(o,"category"),str(o,"subgroup"),str(o,"qualification"),str(o,"emojiVersion"),List.copyOf(cps),o.get("sortOrder").getAsInt(),List.copyOf(tags)));
        }
        return result;
    }
    private static String str(JsonObject o,String k) { return o.has(k)?o.get(k).getAsString():null; }
    private static String decode(List<String> points) {
        StringBuilder b=new StringBuilder();for(String hex:points) {
            require(hex.matches("[0-9A-F]{4,6}"));int cp=Integer.parseInt(hex,16);
            require(cp<=0x10ffff && !(cp>=0xd800 && cp<=0xdfff));b.appendCodePoint(cp);
        }return b.toString();
    }
    private static void require(boolean value) { if(!value) throw new IllegalArgumentException("Invalid pinned catalog"); }
    static byte[] resource(String name) throws SQLException {
        try(var in=CatalogSeed.class.getResourceAsStream(name)) {
            if(in==null) throw new SQLException("Required packaged resource missing");return in.readAllBytes();
        } catch(java.io.IOException e) { throw new SQLException("Packaged resource unreadable"); }
    }
    static String sha(byte[] b) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
