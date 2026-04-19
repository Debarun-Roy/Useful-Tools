import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchActivityList, clearActivity } from '../../api/apiClient'
import styles from './RecentActivity.module.css'

/*
 * RecentActivity
 * ──────────────
 * Small widget rendered on the Dashboard for registered users. Shows the
 * N most recent entries from the unified activity timeline (Sprint 15's
 * user_activity table), each linking to the tool that produced it.
 *
 * ── Why it's Dashboard-only ──────────────────────────────────────────────
 * The Dashboard is the landing surface after login. A compact "here's what
 * you did last time" list turns the page into a continuation cue rather
 * than a blank grid. Keeping the widget small (5 rows) avoids competing
 * with the tool grid for attention.
 *
 * ── Refresh strategy ─────────────────────────────────────────────────────
 * We fetch on mount and on window 'focus' (so tab-back-and-forth updates
 * the list after a user completes an action elsewhere). We do NOT poll —
 * polling is overkill for this kind of soft-presence data.
 *
 * ── Privacy affordance ───────────────────────────────────────────────────
 * A "Clear all" action calls DELETE /api/activity/clear. The first click
 * arms a confirmation state; the second click (within 4 s) actually clears.
 * This avoids a destructive-button-one-click-miss while not requiring a
 * full confirm dialog for such a lightweight destructive action.
 *
 * ── Guests ───────────────────────────────────────────────────────────────
 * The parent (DashboardPage) is responsible for NOT rendering this widget
 * for guest users — guests don't have a backend activity log, so showing
 * an always-empty widget would be a worse experience than not showing one.
 */

// Maximum entries shown. Kept low to keep the widget compact — scrolling
// would turn it into a secondary surface, which it isn't.
const LIMIT = 5

// Metadata per tool name — drives the row icon, label, and click target.
// The tool_name values here MUST match ActivityDAO.VALID_TOOL_NAMES.
const TOOL_META = {
  'analyzer.classify':  { icon: '🔢', label: 'Number Analyser',     path: '/analyser'   },
  'converter.convert':  { icon: '🔄', label: 'Unit Converter',      path: '/converter'  },
  'text.transform':     { icon: '📝', label: 'Text Utilities',      path: '/text-utils' },
  'encoding.transform': { icon: '🔧', label: 'Encoding & Decoding', path: '/encoding'   },
  'code.format':        { icon: '💻', label: 'Code Utilities',      path: '/code-utils' },
  'webdev.generate':    { icon: '🛠️', label: 'Web Dev Helpers',     path: '/web-dev'    },
  'image.process':      { icon: '🖼️', label: 'Image Tools',         path: '/image-tools'},
  'hash.identify':      { icon: '#',  label: 'Hash Identifier',     path: '/dev-utils'  },
  'key.generate':       { icon: '🔑', label: 'API Key Generator',   path: '/dev-utils'  },
}

// ── Helpers ─────────────────────────────────────────────────────────────────

/**
 * Formats a server-provided ISO timestamp as a compact relative string
 * ("just now", "5 min ago", "2 hr ago", "3 days ago"). Past 6 days we fall
 * back to the date itself since "14 days ago" is less informative than
 * "12 Apr".
 */
function relativeTime(iso) {
  if (!iso) return ''
  const then = new Date(iso)
  if (Number.isNaN(then.getTime())) return ''

  const seconds = Math.max(0, Math.floor((Date.now() - then.getTime()) / 1000))
  if (seconds < 60)      return 'just now'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60)      return `${minutes} min ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24)        return `${hours} hr ago`
  const days = Math.floor(hours / 24)
  if (days < 7)          return `${days} day${days !== 1 ? 's' : ''} ago`
  return then.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
}

// ── Component ───────────────────────────────────────────────────────────────

export default function RecentActivity() {
  const navigate = useNavigate()

  const [entries, setEntries] = useState([])
  const [loading, setLoading] = useState(true)
  const [error,   setError]   = useState(null)
  const [armed,   setArmed]   = useState(false)

  const mountedRef = useRef(true)
  const armTimerRef = useRef(null)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      if (armTimerRef.current) clearTimeout(armTimerRef.current)
    }
  }, [])

  // ── Load ────────────────────────────────────────────────────────────────
  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data } = await fetchActivityList({ limit: LIMIT })
      if (!mountedRef.current) return
      if (data?.success) {
        const list = Array.isArray(data.data?.entries) ? data.data.entries : []
        setEntries(list)
        setError(null)
      } else {
        setError(data?.error || 'Could not load recent activity.')
      }
    } catch {
      if (!mountedRef.current) return
      setError('Could not reach the server.')
    } finally {
      if (mountedRef.current) setLoading(false)
    }
  }, [])

  // Initial fetch + refetch on window focus (tab-back-and-forth case).
  useEffect(() => {
    load()
    function onFocus() { load() }
    window.addEventListener('focus', onFocus)
    return () => window.removeEventListener('focus', onFocus)
  }, [load])

  // ── Clear with two-click confirmation ───────────────────────────────────
  function handleClearClick() {
    if (!armed) {
      setArmed(true)
      // Auto-disarm after 4 s to avoid a stale armed state.
      if (armTimerRef.current) clearTimeout(armTimerRef.current)
      armTimerRef.current = setTimeout(() => {
        if (mountedRef.current) setArmed(false)
      }, 4000)
      return
    }

    // Armed — actually clear.
    clearActivity()
      .then(({ data }) => {
        if (!mountedRef.current) return
        if (data?.success) {
          setEntries([])
          setError(null)
        } else {
          setError(data?.error || 'Could not clear activity.')
        }
      })
      .catch(() => {
        if (!mountedRef.current) return
        setError('Could not reach the server.')
      })
      .finally(() => {
        if (!mountedRef.current) return
        setArmed(false)
        if (armTimerRef.current) {
          clearTimeout(armTimerRef.current)
          armTimerRef.current = null
        }
      })
  }

  // ── Render ──────────────────────────────────────────────────────────────
  return (
    <section className={styles.widget} aria-label="Recent activity">

      <header className={styles.header}>
        <h2 className={styles.title}>
          <span className={styles.titleIcon} aria-hidden="true">⚡</span>
          Recent activity
        </h2>
        <div className={styles.actions}>
          <button
            type="button"
            className={styles.refreshBtn}
            onClick={load}
            disabled={loading}
            aria-label="Refresh"
            title="Refresh"
          >
            {loading ? '…' : '↻'}
          </button>
          {entries.length > 0 && (
            <button
              type="button"
              className={armed ? styles.clearBtnArmed : styles.clearBtn}
              onClick={handleClearClick}
              title={armed ? 'Click again to confirm' : 'Clear all'}
            >
              {armed ? 'Click again to clear' : 'Clear'}
            </button>
          )}
        </div>
      </header>

      {error && <div className={styles.errorBanner} role="alert">{error}</div>}

      {!loading && !error && entries.length === 0 && (
        <div className={styles.emptyState}>
          <div className={styles.emptyIcon} aria-hidden="true">✨</div>
          <p className={styles.emptyTitle}>Your timeline is empty.</p>
          <p className={styles.emptyBody}>
            Use any tool and your recent actions will appear here. Nothing
            sensitive is recorded — just shape and counts.
          </p>
        </div>
      )}

      {entries.length > 0 && (
        <ul className={styles.list}>
          {entries.map(entry => {
            const meta = TOOL_META[entry.toolName] || {
              icon: '•', label: entry.toolName, path: null,
            }
            return (
              <li key={entry.id} className={styles.item}>
                <button
                  type="button"
                  className={styles.itemButton}
                  onClick={() => { if (meta.path) navigate(meta.path) }}
                  disabled={!meta.path}
                >
                  <span className={styles.itemIcon} aria-hidden="true">
                    {meta.icon}
                  </span>
                  <div className={styles.itemBody}>
                    <span className={styles.itemSummary}>{entry.summary}</span>
                    <span className={styles.itemMeta}>
                      <span className={styles.itemToolLabel}>{meta.label}</span>
                      <span className={styles.metaDot} aria-hidden="true">·</span>
                      <span className={styles.itemTime}>{relativeTime(entry.createdAt)}</span>
                    </span>
                  </div>
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
