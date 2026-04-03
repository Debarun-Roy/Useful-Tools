package numberanalyzer.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.LinkedHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Performs arithmetic on numbers expressed in any base from 2 to 62.
 *
 * Digit encoding (consistent with BaseNRepresentation):
 *   '0'–'9' → digits 0–9
 *   'A'–'Z' → digits 10–35
 *   'a'–'z' → digits 36–61
 *
 * For bases ≤ 36 the input is uppercased automatically so that users can type
 * standard lowercase hex (e.g. "ff") without an error. For bases > 36 the
 * case is preserved because lowercase letters carry distinct digit values.
 *
 * POST /api/analyzer/base-arithmetic
 * Content-Type: application/json
 *
 * Request:
 *   {
 *     "number1":  "FF",
 *     "number2":  "1A",
 *     "base":     16,
 *     "operation": "add"   // add | subtract | multiply | divide
 *   }
 *
 * Response 200:
 *   { "success": true, "data": {
 *       "number1": "FF", "number2": "1A", "base": 16, "operation": "add",
 *       "result": "119",
 *       "decimal1": "255", "decimal2": "26", "decimalResult": "281"
 *   }}
 *
 * Response 400: { "success": false, "errorCode": "...", "error": "..." }
 */
@WebServlet("/api/analyzer/base-arithmetic")
public class BaseArithmeticController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        try {
            // ── 1. Parse JSON body ──────────────────────────────────────────
            JsonObject body;
            try {
                body = gson.fromJson(request.getReader(), JsonObject.class);
            } catch (JsonSyntaxException jse) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body must be valid JSON.", "INVALID_JSON")));
                return;
            }

            if (body == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body is required.", "MISSING_BODY")));
                return;
            }

            // ── 2. Validate required fields ─────────────────────────────────
            if (!body.has("number1") || body.get("number1").isJsonNull() ||
                !body.has("number2") || body.get("number2").isJsonNull() ||
                !body.has("base")    || body.get("base").isJsonNull()    ||
                !body.has("operation") || body.get("operation").isJsonNull()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Fields 'number1', 'number2', 'base', and 'operation' are all required.",
                        "MISSING_FIELDS")));
                return;
            }

            // ── 3. Parse and validate base ──────────────────────────────────
            int base;
            try {
                base = body.get("base").getAsInt();
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "'base' must be a valid integer.", "INVALID_BASE")));
                return;
            }

            if (base < 2 || base > 62) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Base must be between 2 and 62 inclusive.", "INVALID_BASE")));
                return;
            }

            // ── 4. Normalise input strings ──────────────────────────────────
            // For bases ≤ 36, uppercase so 'ff' is accepted as valid hex.
            // For bases > 36, preserve case because 'a' and 'A' are different digits.
            String raw1 = body.get("number1").getAsString().trim();
            String raw2 = body.get("number2").getAsString().trim();
            String num1Str = base <= 36 ? raw1.toUpperCase() : raw1;
            String num2Str = base <= 36 ? raw2.toUpperCase() : raw2;

            if (num1Str.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "'number1' must not be empty.", "INVALID_NUMBER1")));
                return;
            }
            if (num2Str.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "'number2' must not be empty.", "INVALID_NUMBER2")));
                return;
            }

            // ── 5. Parse numbers from their base ────────────────────────────
            BigInteger decimal1, decimal2;
            try {
                decimal1 = parseInBase(num1Str, base);
            } catch (IllegalArgumentException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "'" + num1Str + "' is not a valid base-" + base + " number: " + e.getMessage(),
                        "INVALID_NUMBER1")));
                return;
            }
            try {
                decimal2 = parseInBase(num2Str, base);
            } catch (IllegalArgumentException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "'" + num2Str + "' is not a valid base-" + base + " number: " + e.getMessage(),
                        "INVALID_NUMBER2")));
                return;
            }

            // ── 6. Perform the requested operation ──────────────────────────
            String operation = body.get("operation").getAsString().trim().toLowerCase();
            BigInteger decimalResult;

            switch (operation) {
                case "add":
                    decimalResult = decimal1.add(decimal2);
                    break;
                case "subtract":
                    decimalResult = decimal1.subtract(decimal2);
                    break;
                case "multiply":
                    decimalResult = decimal1.multiply(decimal2);
                    break;
                case "divide":
                    if (decimal2.equals(BigInteger.ZERO)) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        out.print(gson.toJson(ApiResponse.fail(
                                "Division by zero.", "DIVISION_BY_ZERO")));
                        return;
                    }
                    decimalResult = decimal1.divide(decimal2);
                    break;
                default:
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print(gson.toJson(ApiResponse.fail(
                            "Unknown operation '" + operation
                            + "'. Valid values: add, subtract, multiply, divide.",
                            "UNKNOWN_OPERATION")));
                    return;
            }

            // ── 7. Convert result back to the requested base ────────────────
            boolean negative = decimalResult.signum() < 0;
            String resultStr = toBase(decimalResult.abs(), base);
            if (negative) resultStr = "-" + resultStr;

            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("number1",       num1Str);
            data.put("number2",       num2Str);
            data.put("base",          base);
            data.put("operation",     operation);
            data.put("result",        resultStr);
            data.put("decimal1",      decimal1.toString());
            data.put("decimal2",      decimal2.toString());
            data.put("decimalResult", decimalResult.toString());

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(gson.toJson(ApiResponse.fail(
                    "Arithmetic calculation failed. Please check your inputs.",
                    "INTERNAL_ERROR")));
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Parses a string representing a non-negative integer in the given base.
     *
     * Digit encoding:
     *   '0'–'9' → 0–9
     *   'A'–'Z' → 10–35
     *   'a'–'z' → 36–61
     *
     * @throws IllegalArgumentException if any character is invalid or exceeds the base.
     */
    private static BigInteger parseInBase(String str, int base) {
        BigInteger result = BigInteger.ZERO;
        BigInteger bigBase = BigInteger.valueOf(base);
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            int digit;
            if      (c >= '0' && c <= '9') digit = c - '0';
            else if (c >= 'A' && c <= 'Z') digit = c - 'A' + 10;
            else if (c >= 'a' && c <= 'z') digit = c - 'a' + 36;
            else throw new IllegalArgumentException("Unrecognised character: '" + c + "'");

            if (digit >= base) {
                throw new IllegalArgumentException(
                        "Digit '" + c + "' (value " + digit + ") is out of range for base " + base);
            }
            result = result.multiply(bigBase).add(BigInteger.valueOf(digit));
        }
        return result;
    }

    /**
     * Converts a non-negative long to its string representation in the given base.
     *
     * Digit encoding:
     *   0–9  → '0'–'9'
     *   10–35 → 'A'–'Z'
     *   36–61 → 'a'–'z'
     */
    private static String toBase(BigInteger num, int base) {
        if (num.equals(BigInteger.ZERO)) return "0";
        BigInteger bigBase = BigInteger.valueOf(base);
        StringBuilder sb = new StringBuilder();
        while (num.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divRem = num.divideAndRemainder(bigBase);
            int rem = divRem[1].intValue();
            char c;
            if      (rem < 10) c = (char)('0' + rem);
            else if (rem < 36) c = (char)('A' + rem - 10);
            else               c = (char)('a' + rem - 36);
            sb.append(c);
            num = divRem[0];
        }
        return sb.reverse().toString();
    }
}
