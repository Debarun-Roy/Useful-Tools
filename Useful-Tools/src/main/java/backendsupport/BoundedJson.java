package backendsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

/** Strict, bounded decoding before any specification is inspected. */
public final class BoundedJson {
    public static final int MAX_BYTES = 1_048_576;
    public static final class Rejected extends IOException {
        public final int status;
        public final String code;
        public Rejected(int status, String code) { super(code); this.status = status; this.code = code; }
    }
    private int nodes;
    public static JsonElement read(InputStream stream) throws IOException {
        byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new Rejected(413, "BODY_TOO_LARGE");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            try (JsonReader reader = new JsonReader(new StringReader(text))) {
                reader.setLenient(false);
                JsonElement result = new BoundedJson().value(reader, 0);
                if (reader.peek() != JsonToken.END_DOCUMENT) throw new Rejected(400, "MALFORMED_JSON");
                return result;
            }
        } catch (Rejected e) { throw e;
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            throw new Rejected(400, "MALFORMED_JSON");
        }
    }
    private String string(JsonReader reader, boolean name) throws IOException {
        String value = name ? reader.nextName() : reader.nextString();
        if (value.length() > 4096) throw new Rejected(413, "STRING_LIMIT");
        return value;
    }
    private JsonElement value(JsonReader reader, int depth) throws IOException {
        if (depth > 32 || ++nodes > 20000) throw new Rejected(413, "COMPLEXITY_LIMIT");
        switch (reader.peek()) {
            case BEGIN_OBJECT: {
                reader.beginObject(); JsonObject object = new JsonObject(); int count = 0;
                while (reader.hasNext()) {
                    if (++count > 1000) throw new Rejected(413, "COLLECTION_LIMIT");
                    String key = string(reader, true);
                    if (object.has(key)) throw new Rejected(400, "DUPLICATE_KEY");
                    object.add(key, value(reader, depth + 1));
                }
                reader.endObject(); return object;
            }
            case BEGIN_ARRAY: {
                reader.beginArray(); JsonArray array = new JsonArray();
                while (reader.hasNext()) {
                    if (array.size() >= 1000) throw new Rejected(413, "COLLECTION_LIMIT");
                    array.add(value(reader, depth + 1));
                }
                reader.endArray(); return array;
            }
            case STRING: return new JsonPrimitive(string(reader, false));
            case NUMBER: return new JsonPrimitive(new BigDecimal(string(reader, false)));
            case BOOLEAN: return new JsonPrimitive(reader.nextBoolean());
            case NULL: reader.nextNull(); return JsonNull.INSTANCE;
            default: throw new Rejected(400, "MALFORMED_JSON");
        }
    }
}
