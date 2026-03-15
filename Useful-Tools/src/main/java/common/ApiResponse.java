package common;

/**
 * Standardised JSON response envelope for every API endpoint.
 *
 * ── Why this exists ──────────────────────────────────────────────────────────
 * Before this class, servlets returned raw values: a plain string, a raw number,
 * a Map, or an error string — with no consistent shape. A React client had to
 * guess whether a given response was a success value or an error message, and
 * every component needed its own ad-hoc parsing logic.
 *
 * With ApiResponse, every endpoint returns the same outer envelope:
 *
 *   Success:  { "success": true,  "data": <T>,   "error": null, "errorCode": null }
 *   Failure:  { "success": false, "data": null,  "error": "...", "errorCode": "..." }
 *
 * React can check response.success once, then read response.data or
 * response.error — identical logic for every API call.
 *
 * ── errorCode ────────────────────────────────────────────────────────────────
 * The errorCode is a machine-readable constant (e.g. "USER_NOT_FOUND",
 * "INVALID_CREDENTIALS"). The React client uses this to decide which message to
 * show or which navigation action to take, independent of the human-readable
 * error string which may change over time.
 *
 * ── Usage in servlets ────────────────────────────────────────────────────────
 *   // Success
 *   response.setStatus(HttpServletResponse.SC_OK);
 *   out.print(gson.toJson(ApiResponse.ok(someData)));
 *
 *   // Failure
 *   response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
 *   out.print(gson.toJson(ApiResponse.fail("Incorrect password.", "INVALID_CREDENTIALS")));
 *
 * ── Type parameter T ─────────────────────────────────────────────────────────
 * T is the type of the success payload. It can be any Gson-serialisable type:
 * a String, a primitive wrapper, a Map, a List, or a custom POJO.
 * When there is no meaningful payload (e.g. a simple "Password updated"), use
 * ApiResponse<String> and pass a human-readable confirmation message as data.
 *
 * ── Eclipse setup ────────────────────────────────────────────────────────────
 * Place in: src/common/ApiResponse.java
 */
public class ApiResponse<T> {

    private final boolean success;
    private final T       data;
    private final String  error;
    private final String  errorCode;

    // Private constructor — use the static factory methods below.
    private ApiResponse(boolean success, T data, String error, String errorCode) {
        this.success   = success;
        this.data      = data;
        this.error     = error;
        this.errorCode = errorCode;
    }

    // ── Factory methods ──────────────────────────────────────────────────────

    /**
     * Creates a success response carrying a data payload.
     *
     * @param data The payload to return to the client. Must be Gson-serialisable.
     * @param <T>  The type of the payload.
     * @return A success ApiResponse wrapping the given data.
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    /**
     * Creates a failure response carrying a human-readable message and a
     * machine-readable error code.
     *
     * @param error     A human-readable description of what went wrong.
     *                  This may be shown directly in the UI.
     * @param errorCode A constant string the React client can switch on.
     *                  Convention: SCREAMING_SNAKE_CASE, e.g. "USER_NOT_FOUND".
     * @param <T>       Type parameter (inferred as Void/null for failure responses).
     * @return A failure ApiResponse.
     */
    public static <T> ApiResponse<T> fail(String error, String errorCode) {
        return new ApiResponse<>(false, null, error, errorCode);
    }

    // ── Getters (required by Gson for serialisation) ─────────────────────────

    public boolean isSuccess()    { return success;   }
    public T       getData()      { return data;      }
    public String  getError()     { return error;     }
    public String  getErrorCode() { return errorCode; }
}