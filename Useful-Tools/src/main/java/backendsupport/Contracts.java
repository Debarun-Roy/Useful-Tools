package backendsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Offline interpreter for the pinned B0 vocabulary, not a general schema service. */
public final class Contracts {
    public static final JsonObject SCHEMA = resource("contracts/v0.1.0/models.schema.json");
    private static final JsonObject CATALOG = resource("catalog.json");
    private static final JsonObject SAMPLES = resource("samples.json");
    public static JsonObject catalog() { return CATALOG.deepCopy(); }
    public static JsonElement sample(String id) { return SAMPLES.has(id) ? SAMPLES.get(id).deepCopy() : null; }
    public static Set<String> sampleIds() { return Collections.unmodifiableSet(SAMPLES.keySet()); }
    private Contracts() {}
    public static JsonObject resource(String name) {
        try (InputStream in = Contracts.class.getResourceAsStream("/backendsupport/" + name)) {
            if (in == null) throw new IllegalStateException("Missing bundled contract");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) { throw new IllegalStateException("Unavailable bundled contract"); }
    }
    public record Finding(String ruleId, String severity, String path, String message,
                          String suggestion, boolean blocking) {}
    public static final class Findings {
        public final List<Finding> items = new ArrayList<>();
        public boolean truncated;
        private boolean errors;
        public void add(String code, String path) { add(code, path, true); }
        public void add(String code, String path, boolean blocking) {
            errors |= blocking;
            if (items.size() >= 100) { truncated = true; return; }
            items.add(new Finding(code, blocking ? "error" : "warning", path.isEmpty() ? "/" : path,
                    code.replace('_', ' ').toLowerCase(Locale.ROOT) + ".",
                    "Review the indicated specification field.", blocking));
        }
        public boolean valid() { return !errors; }
        public Map<String, Object> result() {
            return Map.of("schemaVersion", "0.1.0", "valid", valid(), "findings", items);
        }
    }
    public static Findings structural(JsonElement request) {
        Findings out = new Findings();
        return structural(request, SCHEMA);
    }
    public static Findings structural(JsonElement request, JsonObject root) {
        Findings out = new Findings();
        check(request, root.getAsJsonObject("$defs").getAsJsonObject("GenerationRequest"), "", out, root);
        return out;
    }
    private static boolean matches(JsonElement value, JsonObject schema, JsonObject root) {
        Findings test = new Findings(); check(value, schema, "", test, root); return test.valid();
    }
    private static boolean type(JsonElement v, String type) {
        return switch (type) {
            case "null" -> v.isJsonNull();
            case "object" -> v.isJsonObject();
            case "array" -> v.isJsonArray();
            case "string" -> v.isJsonPrimitive() && v.getAsJsonPrimitive().isString();
            case "boolean" -> v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean();
            case "number" -> v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber();
            case "integer" -> v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()
                    && v.getAsBigDecimal().stripTrailingZeros().scale() <= 0;
            default -> throw new IllegalStateException("Unsupported pinned schema type");
        };
    }
    private static void check(JsonElement value, JsonObject schema, String path, Findings out, JsonObject root) {
        if (schema.has("$ref")) {
            String ref = schema.get("$ref").getAsString();
            if (!ref.startsWith("#/$defs/")) throw new IllegalStateException("Nonlocal schema reference");
            check(value, root.getAsJsonObject("$defs").getAsJsonObject(ref.substring(8)), path, out, root);
        }
        if (schema.has("type")) {
            JsonElement t = schema.get("type"); boolean ok = false;
            if (t.isJsonArray()) { for (JsonElement item : t.getAsJsonArray()) ok |= type(value, item.getAsString()); }
            else ok = type(value, t.getAsString());
            if (!ok) { out.add("INVALID_TYPE", path); return; }
        }
        if (schema.has("const") && !schema.get("const").equals(value)) out.add("UNSUPPORTED_VALUE", path);
        if (schema.has("enum") && !schema.getAsJsonArray("enum").contains(value)) out.add("UNSUPPORTED_VALUE", path);
        if (value.isJsonObject()) {
            JsonObject obj = value.getAsJsonObject();
            JsonObject props = schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();
            if (schema.has("required")) for (JsonElement field : schema.getAsJsonArray("required"))
                if (!obj.has(field.getAsString())) out.add("REQUIRED_FIELD", path + "/" + field.getAsString());
            for (var property : props.entrySet()) if (obj.has(property.getKey()))
                check(obj.get(property.getKey()), property.getValue().getAsJsonObject(), path + "/" + property.getKey(), out, root);
            if (schema.has("additionalProperties") && !schema.get("additionalProperties").getAsBoolean())
                for (String field : obj.keySet()) if (!props.has(field)) out.add("UNKNOWN_FIELD", path);
        }
        if (value.isJsonArray()) {
            JsonArray a = value.getAsJsonArray(); bounds(a.size(), schema, "minItems", "maxItems", path, out);
            if (schema.has("items")) for (int i = 0; i < a.size(); i++)
                check(a.get(i), schema.getAsJsonObject("items"), path + "/" + i, out, root);
        }
        if (type(value, "string")) {
            String s = value.getAsString(); bounds(s.codePointCount(0,s.length()), schema, "minLength", "maxLength", path, out);
            if (schema.has("pattern") && !Pattern.compile(schema.get("pattern").getAsString()).matcher(s).find())
                out.add("INVALID_FORMAT", path);
        }
        if (type(value, "number")) {
            if (schema.has("minimum") && value.getAsBigDecimal().compareTo(schema.get("minimum").getAsBigDecimal()) < 0
                    || schema.has("maximum") && value.getAsBigDecimal().compareTo(schema.get("maximum").getAsBigDecimal()) > 0)
                out.add("VALUE_LIMIT", path);
        }
        if (schema.has("allOf")) for (JsonElement sub : schema.getAsJsonArray("allOf")) check(value, sub.getAsJsonObject(), path, out, root);
        if (schema.has("if") && matches(value, schema.getAsJsonObject("if"), root)) check(value, schema.getAsJsonObject("then"), path, out, root);
        if (schema.has("oneOf")) {
            int count = 0; for (JsonElement sub : schema.getAsJsonArray("oneOf")) if (matches(value, sub.getAsJsonObject(), root)) count++;
            if (count != 1) out.add("INVALID_COMBINATION", path);
        }
    }
    private static void bounds(int count, JsonObject schema, String min, String max, String path, Findings out) {
        if (schema.has(min) && count < schema.get(min).getAsInt() || schema.has(max) && count > schema.get(max).getAsInt())
            out.add("COLLECTION_OR_LENGTH_LIMIT", path);
    }
}
