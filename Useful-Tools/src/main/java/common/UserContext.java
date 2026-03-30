package common;

/**
 * Holds the currently authenticated username for the duration of a single
 * HTTP request via a ThreadLocal. This allows DAOs and services deep in the
 * call chain to read the username without it being threaded through every
 * method signature.
 *
 * Lifecycle:
 *   - Set by AuthFilter after session validation, before chain.doFilter().
 *   - Cleared by AuthFilter in the finally block after chain.doFilter() returns.
 *   - Returns null for unauthenticated public paths (login, register) where
 *     it is never set — ComputeDAO handles null gracefully.
 */
public class UserContext {

    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();

    private UserContext() { }

    public static void set(String username) {
        CURRENT_USER.set(username);
    }

    public static String get() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}