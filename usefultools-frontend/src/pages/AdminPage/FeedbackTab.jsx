import { useCallback, useEffect, useMemo, useState } from 'react'
import { fetchAdminFeedback } from '../../api/apiClient'
import styles from './AnalyticsTab.module.css'

/*
 * FeedbackTab — admin view of submitted user feedback.
 *
 * Reuses AnalyticsTab.module.css so the visual language matches the
 * existing admin surface (no new styles needed for a read-only list).
 *
 *   1. Summary strip — total count, average overall rating, 1–5 distribution.
 *   2. Filter row — minimum rating + free-text query (username/comment).
 *   3. List of feedback cards, expandable to show per-feature breakdown.
 *   4. Pagination footer.
 */

const PAGE_SIZE = 25

function fmtDate(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  return d.toLocaleString('en-GB', {
    day: 'numeric', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

function Stars({ rating }) {
  if (!rating) return <span className={styles.starsMuted}>—</span>
  const full = Math.max(0, Math.min(5, Math.round(rating)))
  return (
    <span className={styles.starsRow} aria-label={`${full} out of 5 stars`}>
      {'★'.repeat(full)}
      <span className={styles.starsMuted}>{'☆'.repeat(5 - full)}</span>
    </span>
  )
}

function DistributionBar({ distribution, total }) {
  // Render five stacked rows (5★ → 1★) with proportional bars.
  if (total === 0) {
    return <p className={styles.rankedEmpty}>No feedback yet.</p>
  }
  const rows = [5, 4, 3, 2, 1].map(n => {
    const c = distribution?.[String(n)] || 0
    const pct = total > 0 ? (c / total) * 100 : 0
    return { rating: n, count: c, pct }
  })
  return (
    <div className={styles.feedbackDistribution}>
      {rows.map(r => (
        <div key={r.rating} className={styles.distRow}>
          <span className={styles.distLabel}>{r.rating}★</span>
          <div className={styles.rankBarTrack}>
            <div
              className={styles.rankBar}
              style={{ width: Math.max(2, r.pct) + '%' }}
            />
          </div>
          <span className={styles.distCount}>{r.count}</span>
        </div>
      ))}
    </div>
  )
}

function FeedbackCard({ entry, expanded, onToggle }) {
  const features = Array.isArray(entry.features) ? entry.features : []
  return (
    <article className={styles.feedbackCard}>
      <header className={styles.feedbackCardHead}>
        <div className={styles.feedbackCardWho}>
          <span className={styles.feedbackUser}>{entry.username}</span>
          <span className={styles.feedbackDate}>{fmtDate(entry.submittedAt)}</span>
        </div>
        <Stars rating={entry.overallRating} />
      </header>

      {entry.generalComment && (
        <p className={styles.feedbackBody}>{entry.generalComment}</p>
      )}

      {features.length > 0 && (
        <>
          <button
            type="button"
            className={styles.feedbackExpandBtn}
            onClick={onToggle}
            aria-expanded={expanded}
          >
            {expanded ? '▾' : '▸'} {features.length} feature{features.length !== 1 ? 's' : ''} rated
          </button>

          {expanded && (
            <ul className={styles.feedbackFeatures}>
              {features.map((f, i) => (
                <li key={i} className={styles.feedbackFeatureItem}>
                  <span className={styles.feedbackFeatureName}>{f.featureName}</span>
                  <Stars rating={f.rating} />
                  {f.comment && (
                    <span className={styles.feedbackFeatureComment}>{f.comment}</span>
                  )}
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </article>
  )
}

export default function FeedbackTab() {
  const [data,    setData]    = useState(null)
  const [page,    setPage]    = useState(0)
  const [loading, setLoading] = useState(true)
  const [error,   setError]   = useState(null)
  const [expanded,setExpanded]= useState({})    // { [feedbackId]: true }
  const [minStar, setMinStar] = useState(0)
  const [query,   setQuery]   = useState('')

  const load = useCallback(async (offset) => {
    setLoading(true)
    setError(null)
    try {
      const { data: resp } = await fetchAdminFeedback({
        limit:  PAGE_SIZE,
        offset,
      })
      if (resp?.success) {
        setData(resp.data)
      } else {
        setError(resp?.error || 'Failed to load feedback.')
      }
    } catch {
      setError('Network error — could not reach the feedback endpoint.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load(page * PAGE_SIZE)
  }, [load, page])

  const summary = data?.summary || { total: 0, avgOverallRating: 0, distribution: {} }
  const total   = data?.total   || 0
  const offset  = data?.offset  || 0
  const entries = data?.entries || []

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return entries.filter(e => {
      if (minStar > 0 && (e.overallRating || 0) < minStar) return false
      if (!q) return true
      if (e.username && e.username.toLowerCase().includes(q)) return true
      if (e.generalComment && e.generalComment.toLowerCase().includes(q)) return true
      if (Array.isArray(e.features)) {
        for (const f of e.features) {
          if (f.featureName && f.featureName.toLowerCase().includes(q)) return true
          if (f.comment && f.comment.toLowerCase().includes(q)) return true
        }
      }
      return false
    })
  }, [entries, query, minStar])

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))
  const currentPage = Math.floor(offset / PAGE_SIZE) + 1

  return (
    <div className={styles.tabPanel}>

      <div className={styles.controlsRow}>
        <div>
          <h2 className={styles.panelHeading}>User Feedback</h2>
          <p className={styles.panelSub}>
            Submissions from the in-app Feedback modal. Newest first; expand
            a card to see per-feature ratings and comments.
          </p>
        </div>
      </div>

      {/* ── Summary strip ──────────────────────────────────────────── */}
      <div className={styles.summaryGrid}>
        <div className={styles.summaryCard}>
          <span className={styles.summaryLabel}>Total submissions</span>
          <span className={styles.summaryValue}>
            {(summary.total || 0).toLocaleString()}
          </span>
          <span className={styles.summarySub}>
            {total > entries.length ? `${entries.length} on this page` : 'all loaded'}
          </span>
        </div>
        <div className={styles.summaryCard}>
          <span className={styles.summaryLabel}>Avg overall rating</span>
          <span className={styles.summaryValue}>
            {summary.total > 0 ? summary.avgOverallRating.toFixed(2) : '—'}
            {summary.total > 0 && <span className={styles.summarySub} style={{display:'inline'}}> / 5</span>}
          </span>
          <span className={styles.summarySub}>
            <Stars rating={summary.avgOverallRating} />
          </span>
        </div>
        <div className={styles.feedbackDistCard}>
          <span className={styles.summaryLabel}>Rating distribution</span>
          <DistributionBar
            distribution={summary.distribution}
            total={summary.total || 0}
          />
        </div>
      </div>

      {/* ── Filter row ─────────────────────────────────────────────── */}
      <div className={styles.feedbackFilterBar}>
        <div className={styles.feedbackFilterGroup}>
          <label className={styles.feedbackFilterLabel} htmlFor="fb-min-star">
            Minimum rating
          </label>
          <select
            id="fb-min-star"
            className={styles.feedbackFilterSelect}
            value={minStar}
            onChange={e => setMinStar(Number(e.target.value))}
          >
            <option value={0}>Any</option>
            <option value={5}>5★ only</option>
            <option value={4}>4★ and above</option>
            <option value={3}>3★ and above</option>
            <option value={2}>2★ and above</option>
            <option value={1}>1★ and above</option>
          </select>
        </div>
        <div className={styles.feedbackFilterGroup} style={{ flex: 1 }}>
          <label className={styles.feedbackFilterLabel} htmlFor="fb-query">
            Search
          </label>
          <input
            id="fb-query"
            type="search"
            className={styles.feedbackFilterInput}
            placeholder="Filter by username, comment, or feature…"
            value={query}
            onChange={e => setQuery(e.target.value)}
          />
        </div>
      </div>

      {/* ── List ───────────────────────────────────────────────────── */}
      {error && <div className={styles.errorBanner}>{error}</div>}

      {loading && !data && (
        <div className={styles.loadingRow}>Loading feedback…</div>
      )}

      {!loading && !error && data && filtered.length === 0 && (
        <p className={styles.rankedEmpty}>
          {entries.length === 0
            ? 'No feedback has been submitted yet.'
            : 'No feedback matches the current filter.'}
        </p>
      )}

      {!loading && !error && filtered.length > 0 && (
        <div className={styles.feedbackList}>
          {filtered.map(entry => (
            <FeedbackCard
              key={entry.id}
              entry={entry}
              expanded={!!expanded[entry.id]}
              onToggle={() => setExpanded(s => ({ ...s, [entry.id]: !s[entry.id] }))}
            />
          ))}
        </div>
      )}

      {/* ── Pagination ─────────────────────────────────────────────── */}
      {total > PAGE_SIZE && (
        <div className={styles.feedbackPager}>
          <button
            type="button"
            className={styles.feedbackPagerBtn}
            onClick={() => setPage(p => Math.max(0, p - 1))}
            disabled={page === 0 || loading}
          >
            ← Newer
          </button>
          <span className={styles.feedbackPagerInfo}>
            Page {currentPage} of {totalPages}
          </span>
          <button
            type="button"
            className={styles.feedbackPagerBtn}
            onClick={() => setPage(p => p + 1)}
            disabled={(page + 1) >= totalPages || loading}
          >
            Older →
          </button>
        </div>
      )}
    </div>
  )
}
