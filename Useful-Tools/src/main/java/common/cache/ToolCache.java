package common.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * ToolCache — Lightweight in-memory TTL cache for the formatter and search layers.
 *
 * ── Design ───────────────────────────────────────────────────────────────────
 * Singleton backed by a ConcurrentHashMap. Each entry carries an absolute
 * expiry timestamp; expired entries are evicted lazily on the next read.
 *
 * ── TTL conventions ──────────────────────────────────────────────────────────
 * Pass ttlSeconds < 0 to cache indefinitely (static data that never changes
 * at runtime, e.g. the common patterns library). Pass a positive value for
 * data that may change (DB-backed lists, computed search results).
 *
 * ── Thread safety ─────────────────────────────────────────────────────────────
 * ConcurrentHashMap provides safe concurrent reads and writes. The
 * check-then-evict pattern in get() has a benign TOCTOU: if two threads
 * simultaneously detect the same expired entry, both remove it and one
 * re-fetches — that is acceptable for a cache.
 */
public final class ToolCache {

    private static final ToolCache INSTANCE = new ToolCache();

    private final ConcurrentHashMap<String, CacheEntry<?>> store = new ConcurrentHashMap<>();

    private ToolCache() {}

    public static ToolCache getInstance() { return INSTANCE; }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Stores value under key with the given TTL.
     *
     * @param key        Cache key — must be unique across all callers
     * @param value      Value to cache (any object)
     * @param ttlSeconds Seconds until expiry; negative means never expires
     */
    public <T> void put(String key, T value, long ttlSeconds) {
        long expiresAt = (ttlSeconds < 0)
                ? Long.MAX_VALUE
                : System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ttlSeconds);
        store.put(key, new CacheEntry<>(value, expiresAt));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns the cached value, or null if absent or expired.
     * Expired entries are evicted on read (lazy eviction).
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        CacheEntry<?> entry = store.get(key);
        if (entry == null) return null;
        if (entry.isExpired()) {
            store.remove(key);
            return null;
        }
        return (T) entry.value;
    }

    // ── Eviction ──────────────────────────────────────────────────────────────

    public void invalidate(String key) { store.remove(key); }
    public void invalidateAll()        { store.clear(); }

    // ── Entry ─────────────────────────────────────────────────────────────────

    private static final class CacheEntry<T> {
        final T    value;
        final long expiresAt;

        CacheEntry(T value, long expiresAt) {
            this.value     = value;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
