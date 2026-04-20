package common;

/**
 * Holds the currently authenticated username AND role for the duration of a
 * single HTTP request via ThreadLocals.
 *
 * Sprint 17 addition: role field mirrors the username field so AdminFilter
 * and any other downstream code can read the role without touching the session.
 *
 * Lifecycle (unchanged):
 *   - Set by AuthFilter after session validation, before chain.doFilter().
 *   - Cleared by AuthFilter in the finally block after chain.doFilter() returns.
 *   - Returns null for unauthenticated public paths (login, register).
 */
public class UserContext {

    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();

    /** Roles recognized by the system. */
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_USER  = "user";
    public static final String ROLE_GUEST = "guest";

    private UserContext() { }

    // ── Username ─────────────────────────────────────────────────────────────

    public static void set(String username) {
        CURRENT_USER.set(username);
    }

    public static String get() {
        return CURRENT_USER.get();
    }

    // ── Role ─────────────────────────────────────────────────────────────────

    public static void setRole(String role) {
        CURRENT_ROLE.set(role);
    }

    public static String getRole() {
        return CURRENT_ROLE.get();
    }

    public static boolean isAdmin() {
        return ROLE_ADMIN.equals(CURRENT_ROLE.get());
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public static void clear() {
        CURRENT_USER.remove();
        CURRENT_ROLE.remove();
    }
}
