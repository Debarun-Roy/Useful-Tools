package formatter.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * FormatterService — Handles formatting and minification of JSON and XML.
 *
 * ── Responsibilities ─────────────────────────────────────────────────────
 * - Format JSON with pretty-printing
 * - Minify JSON (remove all whitespace)
 * - Parse and validate JSON syntax
 * - Format XML with indentation (XXE-safe)
 * - Minify XML (strip whitespace text nodes)
 * - Validate XML syntax
 * - Provide statistics on formatted output
 *
 * ── Notes ────────────────────────────────────────────────────────────────
 * - Uses Google Gson for JSON; javax.xml.parsers for XML
 * - All methods are stateless and thread-safe
 * - XML parsing uses XXE protection via disallow-doctype-decl feature
 */
public class FormatterService {

    private static final Gson prettyGson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final Gson compactGson = new Gson();

    // ── JSON ─────────────────────────────────────────────────────────────────

    public String formatJson(String jsonString) throws JsonSyntaxException {
        if (jsonString == null || jsonString.isBlank()) {
            throw new JsonSyntaxException("JSON input cannot be empty");
        }
        try {
            JsonElement element = JsonParser.parseString(jsonString);
            return prettyGson.toJson(element);
        } catch (JsonSyntaxException e) {
            throw new JsonSyntaxException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    public String minifyJson(String jsonString) throws JsonSyntaxException {
        if (jsonString == null || jsonString.isBlank()) {
            throw new JsonSyntaxException("JSON input cannot be empty");
        }
        try {
            JsonElement element = JsonParser.parseString(jsonString);
            return compactGson.toJson(element);
        } catch (JsonSyntaxException e) {
            throw new JsonSyntaxException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    public boolean validateJson(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) return false;
        try {
            JsonParser.parseString(jsonString);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    public ValidationResult validateJsonDetailed(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) {
            return new ValidationResult(false, "JSON input cannot be empty");
        }
        try {
            JsonParser.parseString(jsonString);
            return new ValidationResult(true, null);
        } catch (JsonSyntaxException e) {
            return new ValidationResult(false, "Invalid JSON: " + e.getMessage());
        }
    }

    public FormattingStats getFormattingStats(String jsonString) throws JsonSyntaxException {
        if (jsonString == null || jsonString.isBlank()) {
            throw new JsonSyntaxException("JSON input cannot be empty");
        }
        JsonElement element = JsonParser.parseString(jsonString);
        int originalSize = jsonString.length();
        String minified = compactGson.toJson(element);
        int minifiedSize = minified.length();
        double compressionRatio = originalSize > 0
                ? ((double) (originalSize - minifiedSize) / originalSize) * 100
                : 0;
        return new FormattingStats(originalSize, minifiedSize, compressionRatio);
    }

    // ── XML ──────────────────────────────────────────────────────────────────

    public String formatXml(String xmlString) throws Exception {
        if (xmlString == null || xmlString.isBlank()) {
            throw new IllegalArgumentException("XML input cannot be empty");
        }
        Document doc = parseXml(xmlString);
        return serializeXml(doc, true);
    }

    public String minifyXml(String xmlString) throws Exception {
        if (xmlString == null || xmlString.isBlank()) {
            throw new IllegalArgumentException("XML input cannot be empty");
        }
        Document doc = parseXml(xmlString);
        removeWhitespaceNodes(doc.getDocumentElement());
        return serializeXml(doc, false);
    }

    public ValidationResult validateXmlDetailed(String xmlString) {
        if (xmlString == null || xmlString.isBlank()) {
            return new ValidationResult(false, "XML input cannot be empty");
        }
        try {
            parseXml(xmlString);
            return new ValidationResult(true, null);
        } catch (Exception e) {
            return new ValidationResult(false, "Invalid XML: " + e.getMessage());
        }
    }

    public FormattingStats getXmlFormattingStats(String xmlString) throws Exception {
        if (xmlString == null || xmlString.isBlank()) {
            throw new IllegalArgumentException("XML input cannot be empty");
        }
        Document doc = parseXml(xmlString);
        removeWhitespaceNodes(doc.getDocumentElement());
        String minified = serializeXml(doc, false);
        int originalSize = xmlString.length();
        int minifiedSize = minified.length();
        double compressionRatio = originalSize > 0
                ? ((double) (originalSize - minifiedSize) / originalSize) * 100
                : 0;
        return new FormattingStats(originalSize, minifiedSize, compressionRatio);
    }

    // ── XML helpers ──────────────────────────────────────────────────────────

    private Document parseXml(String xmlString) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // XXE protection: disallow DOCTYPE declarations entirely
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xmlString)));
    }

    private String serializeXml(Document doc, boolean indent) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        if (indent) {
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        } else {
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
        }
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString().trim();
    }

    private void removeWhitespaceNodes(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE && child.getNodeValue().isBlank()) {
                node.removeChild(child);
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                removeWhitespaceNodes(child);
            }
        }
    }

    // ── Helper classes ───────────────────────────────────────────────────────

    public static class ValidationResult {
        public final boolean isValid;
        public final String errorMessage;

        public ValidationResult(boolean isValid, String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }
    }

    public static class FormattingStats {
        public final int originalSize;
        public final int minifiedSize;
        public final double compressionRatio;

        public FormattingStats(int originalSize, int minifiedSize, double compressionRatio) {
            this.originalSize = originalSize;
            this.minifiedSize = minifiedSize;
            this.compressionRatio = compressionRatio;
        }
    }
}
