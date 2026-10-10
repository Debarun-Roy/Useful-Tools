package backendsupport;

import com.google.gson.*;
import java.util.*;
import backendsupport.Contracts.Findings;

/** B1 consistency checks only. Never generates or executes user content. */
public final class SpecificationValidator {
    private SpecificationValidator() {}
    public static Findings validate(JsonElement input) {
        Findings f = Contracts.structural(input);
        if (!f.valid()) return f;
        JsonObject request = input.getAsJsonObject(), spec = request.getAsJsonObject("spec");
        String dialect = request.getAsJsonObject("target").get("dialect").getAsString();
        switch (request.get("module").getAsString()) {
            case "schema" -> schema(spec, dialect, "/spec", f);
            case "evaluator" -> schema(spec.getAsJsonObject("schema"), dialect, "/spec/schema", f);
            case "migration" -> migration(spec, dialect, f);
            case "view" -> view(spec, dialect, f);
            case "etl" -> etl(spec, dialect, f);
            case "rest" -> rest(spec, f);
            default -> throw new IllegalStateException("Unvalidated module");
        }
        return f;
    }
    private static String str(JsonObject o, String key) { return o.get(key).getAsString(); }
    private static Set<String> unique(JsonArray a, String property, String path, Findings f) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < a.size(); i++) {
            String value = property == null ? a.get(i).getAsString() : str(a.get(i).getAsJsonObject(), property);
            // SQL identifiers are compared case-insensitively for portable targets.
            if (!seen.add(value.toLowerCase(Locale.ROOT))) f.add("DUPLICATE_IDENTIFIER", path + "/" + i);
        }
        return seen;
    }
    private static Map<String, JsonObject> indexed(JsonArray a) {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        for (JsonElement e : a) result.put(str(e.getAsJsonObject(), "id"), e.getAsJsonObject());
        return result;
    }
    private static void references(JsonArray refs, Map<String, JsonObject> columns, String path, Findings f) {
        unique(refs, null, path, f);
        for (int i = 0; i < refs.size(); i++) if (!columns.containsKey(refs.get(i).getAsString()))
            f.add("UNKNOWN_REFERENCE", path + "/" + i);
    }
    private static Map<String, JsonObject> schema(JsonObject spec, String dialect, String path, Findings f) {
        if (!dialect.equals(str(spec, "dialect"))) f.add("DIALECT_MISMATCH", path + "/dialect");
        JsonArray tables = spec.getAsJsonArray("tables");
        unique(tables, "id", path + "/tables", f); unique(tables, "name", path + "/tables", f);
        Map<String, JsonObject> tableMap = indexed(tables); int total = 0;
        for (int i = 0; i < tables.size(); i++) {
            JsonObject t = tables.get(i).getAsJsonObject(); String p = path + "/tables/" + i;
            JsonArray cols = t.getAsJsonArray("columns"); total += cols.size();
            unique(cols, "id", p + "/columns", f); unique(cols, "name", p + "/columns", f);
            Map<String, JsonObject> columns = indexed(cols);
            for (int j = 0; j < cols.size(); j++) {
                JsonObject c = cols.get(j).getAsJsonObject();
                if (c.has("scale") && (!c.has("precision") || c.get("scale").getAsInt() > c.get("precision").getAsInt())
                    || (c.has("scale") || c.has("precision")) && !str(c,"type").equals("decimal"))
                    f.add("INVALID_DECIMAL_OPTIONS", p + "/columns/" + j);
            }
            references(t.getAsJsonArray("primaryKey"), columns, p + "/primaryKey", f);
            for (JsonElement key : t.getAsJsonArray("primaryKey")) {
                JsonObject c = columns.get(key.getAsString());
                if (c != null && c.get("nullable").getAsBoolean()) f.add("NULLABLE_PRIMARY_KEY", p + "/primaryKey");
            }
            JsonArray indexes = t.getAsJsonArray("indexes"); unique(indexes, "id", p + "/indexes", f);
            for (int j = 0; j < indexes.size(); j++) references(indexes.get(j).getAsJsonObject().getAsJsonArray("columns"), columns, p + "/indexes/" + j + "/columns", f);
            for (int j = 0; j < t.getAsJsonArray("unique").size(); j++) references(t.getAsJsonArray("unique").get(j).getAsJsonArray(), columns, p + "/unique/" + j, f);
            JsonArray keys = t.getAsJsonArray("foreignKeys");
            for (int j = 0; j < keys.size(); j++) {
                JsonObject key = keys.get(j).getAsJsonObject(); String kp = p + "/foreignKeys/" + j;
                JsonArray local = key.getAsJsonArray("columns"), remote = key.getAsJsonArray("targetColumns");
                references(local, columns, kp + "/columns", f);
                JsonObject target = tableMap.get(str(key, "tableId"));
                if (target == null) { f.add("UNKNOWN_REFERENCE", kp + "/tableId"); continue; }
                Map<String, JsonObject> targetCols = indexed(target.getAsJsonArray("columns"));
                references(remote, targetCols, kp + "/targetColumns", f);
                if (local.size() != remote.size()) f.add("KEY_ARITY_MISMATCH", kp);
                else for (int k = 0; k < local.size(); k++) {
                    JsonObject lc = columns.get(local.get(k).getAsString()), rc = targetCols.get(remote.get(k).getAsString());
                    if (lc != null && rc != null && !lc.get("type").equals(rc.get("type"))) f.add("KEY_TYPE_MISMATCH", kp);
                }
                boolean isUnique = remote.equals(target.get("primaryKey"));
                for (JsonElement u : target.getAsJsonArray("unique")) isUnique |= remote.equals(u);
                for (JsonElement idx : target.getAsJsonArray("indexes")) isUnique |= idx.getAsJsonObject().get("unique").getAsBoolean() && remote.equals(idx.getAsJsonObject().get("columns"));
                if (!isUnique) f.add("FOREIGN_KEY_NOT_UNIQUE", kp + "/targetColumns");
            }
        }
        if (total > 1000) f.add("TOTAL_COLUMN_LIMIT", path + "/tables");
        return tableMap;
    }
    private static void migration(JsonObject s, String dialect, Findings f) {
        Map<String, JsonObject> before = schema(s.getAsJsonObject("before"), dialect, "/spec/before", f);
        Map<String, JsonObject> after = schema(s.getAsJsonObject("after"), dialect, "/spec/after", f);
        boolean destructive = false;
        for (var entry : before.entrySet()) {
            JsonObject next = after.get(entry.getKey());
            if (next == null) { destructive = true; continue; }
            Map<String, JsonObject> nextCols = indexed(next.getAsJsonArray("columns"));
            for (var col : indexed(entry.getValue().getAsJsonArray("columns")).entrySet()) {
                JsonObject nc = nextCols.get(col.getKey());
                destructive |= nc == null || !nc.get("type").equals(col.getValue().get("type"))
                    || col.getValue().get("nullable").getAsBoolean() && !nc.get("nullable").getAsBoolean();
            }
        }
        JsonArray renames = s.getAsJsonArray("renames"); unique(renames, "id", "/spec/renames", f);
        for (int i = 0; i < renames.size(); i++) {
            JsonObject r = renames.get(i).getAsJsonObject(); String id = str(r,"id"); boolean found = false;
            if (before.containsKey(id) && after.containsKey(id)) found = str(before.get(id),"name").equals(str(r,"from")) && str(after.get(id),"name").equals(str(r,"to"));
            for (String table : before.keySet()) if (after.containsKey(table)) {
                JsonObject a = indexed(before.get(table).getAsJsonArray("columns")).get(id), b = indexed(after.get(table).getAsJsonArray("columns")).get(id);
                if (a != null && b != null) found |= str(a,"name").equals(str(r,"from")) && str(b,"name").equals(str(r,"to"));
            }
            if (!found) f.add("INVALID_RENAME_REFERENCE", "/spec/renames/" + i);
        }
        if (destructive) f.add("DESTRUCTIVE_MIGRATION", "/spec/destructiveAcknowledged", !s.get("destructiveAcknowledged").getAsBoolean());
    }
    private static void field(JsonObject r, Set<String> sources, Map<String, JsonObject> tables, String p, Findings f) {
        JsonObject t = tables.get(str(r,"tableId"));
        if (!sources.contains(str(r,"tableId").toLowerCase(Locale.ROOT)) || t == null
                || !indexed(t.getAsJsonArray("columns")).containsKey(str(r,"columnId"))) f.add("UNKNOWN_REFERENCE", p);
    }
    private static void view(JsonObject s, String dialect, Findings f) {
        Map<String, JsonObject> tables = schema(s.getAsJsonObject("schema"), dialect, "/spec/schema", f);
        Set<String> sources = unique(s.getAsJsonArray("sources"), null, "/spec/sources", f);
        for (JsonElement source : s.getAsJsonArray("sources")) if (!tables.containsKey(source.getAsString())) f.add("UNKNOWN_REFERENCE", "/spec/sources");
        unique(s.getAsJsonArray("projection"), "alias", "/spec/projection", f);
        for (String kind : List.of("projection","predicates","groupBy","joins")) {
            JsonArray rows = s.getAsJsonArray(kind);
            for (int i=0; i<rows.size(); i++) {
                JsonObject row=rows.get(i).getAsJsonObject(); String p="/spec/"+kind+"/"+i;
                if (kind.equals("joins")) { field(row.getAsJsonObject("left"),sources,tables,p+"/left",f); field(row.getAsJsonObject("right"),sources,tables,p+"/right",f); }
                else field(kind.equals("groupBy")?row:row.getAsJsonObject("field"),sources,tables,p,f);
            }
        }
    }
    private static void etl(JsonObject s, String dialect, Findings f) {
        if (!dialect.equals(str(s.getAsJsonObject("destination"),"dialect"))) f.add("DIALECT_MISMATCH","/spec/destination/dialect");
        JsonObject src=s.getAsJsonObject("source");
        if (str(src,"format").equals("csv") != src.has("delimiter")) f.add("INVALID_DELIMITER_OPTION","/spec/source/delimiter");
        Set<String> targets=unique(s.getAsJsonArray("mappings"),"target","/spec/mappings",f);
        unique(s.getAsJsonArray("uniqueKey"),null,"/spec/uniqueKey",f);
        if (str(s,"mode").equals("upsert") && s.getAsJsonArray("uniqueKey").isEmpty()) f.add("UPSERT_KEY_REQUIRED","/spec/uniqueKey");
        for (JsonElement key:s.getAsJsonArray("uniqueKey")) if (!targets.contains(key.getAsString().toLowerCase(Locale.ROOT))) f.add("UNKNOWN_REFERENCE","/spec/uniqueKey");
    }
    private static void rest(JsonObject s, Findings f) {
        Set<String> modules=unique(s.getAsJsonArray("modules"),null,"/spec/modules",f);
        unique(s.getAsJsonArray("profileFields"),null,"/spec/profileFields",f);
        unique(s.getAsJsonArray("allowedOrigins"),null,"/spec/allowedOrigins",f);
        if ((modules.contains("login") || modules.contains("logout") || modules.contains("profile")) && !modules.contains("session")) f.add("SESSION_REQUIRED","/spec/modules");
        if ((modules.contains("register") || modules.contains("login") || modules.contains("logout") || modules.contains("profile")) && !modules.contains("csrf")) f.add("CSRF_REQUIRED","/spec/modules");
        if (!modules.contains("profile") && !s.getAsJsonArray("profileFields").isEmpty()) f.add("PROFILE_MODULE_REQUIRED","/spec/profileFields");
        JsonObject captcha=s.getAsJsonObject("captcha"); boolean on=captcha.get("enabled").getAsBoolean();
        if (on != modules.contains("captcha") || on != str(captcha,"provider").equals("recaptcha-v3") || on != captcha.has("secretEnv")) f.add("CAPTCHA_OPTIONS_MISMATCH","/spec/captcha");
        for (JsonElement origin : s.getAsJsonArray("allowedOrigins")) try {
            java.net.URI uri=java.net.URI.create(origin.getAsString());
            if (uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null) f.add("INVALID_ORIGIN","/spec/allowedOrigins");
        } catch (IllegalArgumentException e) { f.add("INVALID_ORIGIN","/spec/allowedOrigins"); }
    }
}
