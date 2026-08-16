/**
 * ToolRecommendations.jsx — Sprint 21
 *
 * Dashboard widget that shows two sections:
 *   1. "For you" — personalized recommendations based on the user's recent
 *      tool usage (GET /api/search/recommendations).
 *   2. "Most Popular This Week" — site-wide trending tools
 *      (GET /api/search/trending).
 *
 * Both sections collapse gracefully when empty, so the widget only renders
 * if there is something meaningful to display. Guest users see only trending.
 *
 * Props
 *   username     {string}    Current username (null/undefined = guest mode)
 *   onSelectTool {Function}  Called with the tool path string when a card is clicked
 */

import { useState, useEffect } from 'react'
import { request } from '../../api/apiClient'
import styles from './ToolRecommendations.module.css'

function ToolChip({ tool, onClick }) {
  return (
    <button
      type="button"
      className={styles.chip}
      onClick={() => onClick(tool.path || tool.toolPath)}
      title={tool.description || tool.toolName || tool.name}
    >
      <span className={styles.chipIcon}>{tool.icon || '🔧'}</span>
      <span className={styles.chipName}>{tool.name || tool.toolName || tool.toolPath}</span>
      {tool.usageCount != null && (
        <span className={styles.chipBadge}>{tool.usageCount}×</span>
      )}
      {tool.trend != null && (
        <span className={styles.chipBadge}>{tool.trend} uses</span>
      )}
    </button>
  )
}

export default function ToolRecommendations({ username, onSelectTool }) {
  const [recs,     setRecs]     = useState([])
  const [trending, setTrending] = useState([])
  const [loading,  setLoading]  = useState(true)

  const isGuest = !username || username === 'Guest User'

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      try {
        // Fetch trending for everyone
        const trendResp = await request('/search/trending?limit=6')
        if (!cancelled && trendResp.data?.success) {
          setTrending(trendResp.data.data?.trending || [])
        }

        // Fetch personal recs for logged-in users only
        if (!isGuest && username) {
          const params = new URLSearchParams({ username, limit: 5 })
          const recResp = await request(`/search/recommendations?${params}`)
          if (!cancelled && recResp.data?.success) {
            setRecs(recResp.data.data?.recommendations || [])
          }
        }
      } catch {
        // Fail silently — recommendations are a secondary concern
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => { cancelled = true }
  }, [username, isGuest])

  // Nothing to show yet
  if (loading) return null
  if (recs.length === 0 && trending.length === 0) return null

  function handleClick(pathOrTool) {
    if (onSelectTool) onSelectTool(pathOrTool)
  }

  return (
    <section className={styles.widget} aria-label="Tool recommendations">
      {/* Personal recommendations */}
      {!isGuest && recs.length > 0 && (
        <div className={styles.section}>
          <div className={styles.sectionHeader}>
            <span className={styles.sectionIcon}>⚡</span>
            <p className={styles.sectionTitle}>For you</p>
            <span className={styles.sectionHint}>Based on your recent activity</span>
          </div>
          <div className={styles.chipRow}>
            {recs.map((tool, i) => (
              <ToolChip key={i} tool={tool} onClick={handleClick} />
            ))}
          </div>
        </div>
      )}

      {/* Trending */}
      {trending.length > 0 && (
        <div className={styles.section}>
          <div className={styles.sectionHeader}>
            <span className={styles.sectionIcon}>📈</span>
            <p className={styles.sectionTitle}>Trending this week</p>
            <span className={styles.sectionHint}>Most popular across all users</span>
          </div>
          <div className={styles.chipRow}>
            {trending.map((tool, i) => (
              <ToolChip key={i} tool={tool} onClick={handleClick} />
            ))}
          </div>
        </div>
      )}
    </section>
  )
}
