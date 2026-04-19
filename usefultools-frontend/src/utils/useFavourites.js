import { useCallback, useEffect, useRef, useState } from 'react'
import {
  fetchFavorites,
  toggleFavorite as apiToggleFavorite,
  removeFavorite as apiRemoveFavorite,
  reorderFavorites as apiReorderFavorites,
} from '../api/apiClient'

/*
 * useFavorites
 * ────────────
 * Hook that manages a user's favorited tool paths.
 *
 * ── Two storage backends ─────────────────────────────────────────────────
 * Registered users (`username !== 'Guest User'`)
 *   → favorites live on the server in user_favorites.
 *   → we fetch once on mount; subsequent mutations use optimistic local
 *     updates with rollback if the server rejects.
 *
 * Guest users (`username === 'Guest User'`)
 *   → no server session to tie favorites to, so we mirror the same API
 *     on top of localStorage under key 'usefultools.guestFavorites'.
 *   → this lets guests experiment with the feature without being forced to
 *     register, and their selections persist across browser restarts.
 *
 * The *shape* returned by the hook is identical for both backends, so the
 * Dashboard doesn't need to know or care which storage path is active.
 *
 * ── Optimistic updates ────────────────────────────────────────────────────
 * Toggle/reorder calls return a Promise resolving to { ok, error? }. The
 * local state is updated immediately so the UI feels responsive; on server
 * rejection we roll the state back to what it was before the call and
 * surface the error to the caller via the returned object. The Dashboard
 * uses this to show a small error banner and re-fetch if needed.
 *
 * ── Hard limits ───────────────────────────────────────────────────────────
 * The server returns `max` on list-fetch (currently 20). We expose it as
 * the `max` field so the UI can render a "you've pinned the max" hint and
 * avoid surfacing a confusing 409 FAVORITES_FULL response. For the guest
 * path we use DEFAULT_MAX to keep the behaviour identical.
 */

const GUEST_USERNAME = 'Guest User'
const GUEST_STORAGE_KEY = 'usefultools.guestFavorites'
const DEFAULT_MAX = 20

// Mirror of the server-side VALID_TOOL_PATHS — defensive. A malicious browser
// extension writing garbage into localStorage shouldn't propagate to the UI
// or (if the user later registers) to the server.
const VALID_PATHS = new Set([
  '/calculator',
  '/analyser',
  '/vault',
  '/converter',
  '/text-utils',
  '/encoding',
  '/code-utils',
  '/web-dev',
  '/image-tools',
  '/dev-utils',
])

// ─── localStorage helpers (guest path) ──────────────────────────────────────

function readGuestFavorites() {
  try {
    const raw = localStorage.getItem(GUEST_STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    // Filter anything stale or malformed from disk. Keep the first occurrence
    // of each unique path (dedupe preserving order).
    const seen = new Set()
    const out = []
    for (const p of parsed) {
      if (typeof p !== 'string') continue
      if (!VALID_PATHS.has(p))  continue
      if (seen.has(p))          continue
      seen.add(p)
      out.push(p)
      if (out.length >= DEFAULT_MAX) break
    }
    return out
  } catch {
    // Quota exceeded, JSON parse error, or storage blocked — treat as empty.
    return []
  }
}

function writeGuestFavorites(paths) {
  try {
    localStorage.setItem(GUEST_STORAGE_KEY, JSON.stringify(paths))
  } catch {
    // Storage full or blocked — silent. The in-memory state is still correct;
    // only persistence across tabs/reloads is affected.
  }
}

// ─── Hook ──────────────────────────────────────────────────────────────────

/**
 * @param {string|null} username  The active username from AuthContext.
 * @returns {{
 *   favorites: string[],      // tool paths in display order
 *   loading:   boolean,
 *   error:     string | null,
 *   max:       number,
 *   isGuestBacked: boolean,
 *   isFavorited: (toolPath: string) => boolean,
 *   toggleFavorite: (toolPath: string) => Promise<{ok: boolean, error?: string}>,
 *   reorderFavorites: (orderedPaths: string[]) => Promise<{ok: boolean, error?: string}>,
 * }}
 */
export default function useFavorites(username) {
  const isGuest = username === GUEST_USERNAME

  const [favorites, setFavorites] = useState([])
  const [loading,   setLoading]   = useState(!isGuest)  // guest: sync read, no loading
  const [error,     setError]     = useState(null)
  const [max,       setMax]       = useState(DEFAULT_MAX)

  // Tracks whether the component is still mounted — prevents setState calls
  // from landing after unmount (React 18 StrictMode double-mount safeguard).
  const mountedRef = useRef(true)
  useEffect(() => {
    mountedRef.current = true
    return () => { mountedRef.current = false }
  }, [])

  // ── Initial load ─────────────────────────────────────────────────────────
  useEffect(() => {
    // Not logged in → nothing to load.
    if (!username) {
      setFavorites([])
      setLoading(false)
      return
    }

    if (isGuest) {
      setFavorites(readGuestFavorites())
      setMax(DEFAULT_MAX)
      setLoading(false)
      setError(null)
      return
    }

    // Registered user → hit the server.
    setLoading(true)
    fetchFavorites()
      .then(({ data }) => {
        if (!mountedRef.current) return
        if (data?.success) {
          const paths = Array.isArray(data.data?.favorites)
            ? data.data.favorites
                .map(f => f?.toolPath)
                .filter(p => typeof p === 'string' && VALID_PATHS.has(p))
            : []
          setFavorites(paths)
          if (typeof data.data?.max === 'number') setMax(data.data.max)
          setError(null)
        } else {
          setError(data?.error || 'Failed to load favorites.')
        }
      })
      .catch(() => {
        if (!mountedRef.current) return
        setError('Could not reach the server.')
      })
      .finally(() => {
        if (!mountedRef.current) return
        setLoading(false)
      })
  }, [username, isGuest])

  // ── Lookup helper ────────────────────────────────────────────────────────
  const isFavorited = useCallback(
    (toolPath) => favorites.includes(toolPath),
    [favorites]
  )

  // ── Toggle ───────────────────────────────────────────────────────────────
  //
  // Returns { ok, error? }. Updates happen optimistically — on failure, the
  // previous state is restored and the error is bubbled up. The Dashboard
  // renders a small error banner using the returned error.
  const toggleFavorite = useCallback(async (toolPath) => {
    if (!VALID_PATHS.has(toolPath)) {
      return { ok: false, error: 'Unknown tool.' }
    }

    const wasFavorited = favorites.includes(toolPath)
    const previous = favorites

    // Compute the new list first so both branches (add/remove) stay consistent.
    const next = wasFavorited
      ? favorites.filter(p => p !== toolPath)
      : (favorites.length >= max
          ? null  // at cap, refuse
          : [...favorites, toolPath])

    if (next === null) {
      return {
        ok: false,
        error: `You've pinned the maximum number of tools (${max}).`,
      }
    }

    // Optimistic local update.
    setFavorites(next)
    setError(null)

    // Guest path — persist to localStorage and we're done.
    if (isGuest) {
      writeGuestFavorites(next)
      return { ok: true }
    }

    // Registered user — hit the server.
    try {
      const { status, data } = wasFavorited
        ? await apiRemoveFavorite(toolPath)
        : await apiToggleFavorite(toolPath)

      const okResponse = data?.success && status >= 200 && status < 300

      if (!okResponse) {
        // Server rejected — rollback the local state.
        if (mountedRef.current) setFavorites(previous)
        // Surface server-provided message if useful.
        const message = data?.errorCode === 'FAVORITES_FULL'
          ? `You've pinned the maximum number of tools (${max}).`
          : (data?.error || 'Could not update favorites.')
        return { ok: false, error: message }
      }

      return { ok: true }
    } catch {
      if (mountedRef.current) setFavorites(previous)
      return { ok: false, error: 'Could not reach the server.' }
    }
  }, [favorites, isGuest, max])

  // ── Reorder ──────────────────────────────────────────────────────────────
  //
  // The caller passes the new ordered list of paths. We validate it locally
  // (only known paths, no dupes, stays at or below current length), apply
  // optimistically, then send to the server. On failure we roll back.
  const reorderFavorites = useCallback(async (orderedPaths) => {
    if (!Array.isArray(orderedPaths)) {
      return { ok: false, error: 'Invalid reorder request.' }
    }

    // Normalise: strip unknowns, dedupe.
    const seen = new Set()
    const clean = []
    for (const p of orderedPaths) {
      if (typeof p !== 'string') continue
      if (!VALID_PATHS.has(p))  continue
      if (seen.has(p))          continue
      seen.add(p)
      clean.push(p)
    }

    // If the caller dropped any currently-favorited paths from the order,
    // preserve them at the tail to avoid silently deleting favorites during
    // a reorder. (The toggle path is the only intended way to remove.)
    for (const p of favorites) {
      if (!seen.has(p)) {
        clean.push(p)
        seen.add(p)
      }
    }

    // No-op short-circuit — identical order means nothing to do.
    const sameOrder =
      clean.length === favorites.length &&
      clean.every((p, i) => p === favorites[i])
    if (sameOrder) return { ok: true }

    const previous = favorites
    setFavorites(clean)
    setError(null)

    if (isGuest) {
      writeGuestFavorites(clean)
      return { ok: true }
    }

    try {
      const { status, data } = await apiReorderFavorites(clean)
      const okResponse = data?.success && status >= 200 && status < 300
      if (!okResponse) {
        if (mountedRef.current) setFavorites(previous)
        return { ok: false, error: data?.error || 'Could not save new order.' }
      }
      return { ok: true }
    } catch {
      if (mountedRef.current) setFavorites(previous)
      return { ok: false, error: 'Could not reach the server.' }
    }
  }, [favorites, isGuest])

  return {
    favorites,
    loading,
    error,
    max,
    isGuestBacked: isGuest,
    isFavorited,
    toggleFavorite,
    reorderFavorites,
  }
}
