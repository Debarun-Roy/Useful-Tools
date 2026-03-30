import { useState, useEffect, useCallback } from 'react'
import { fetchCalculationHistory } from '../../../api/apiClient'
import styles from './HistoryTab.module.css'

const PAGE_SIZE = 20

export default function HistoryTab() {
  const [entries,  setEntries]  = useState([])
  const [total,    setTotal]    = useState(0)
  const [page,     setPage]     = useState(0)
  const [loading,  setLoading]  = useState(false)
  const [error,    setError]    = useState('')

  const totalPages = Math.ceil(total / PAGE_SIZE)

  const load = useCallback(async (p) => {
    setLoading(true)
    setError('')
    try {
      const { data } = await fetchCalculationHistory(p, PAGE_SIZE)
      if (data.success) {
        setEntries(data.data.entries)
        setTotal(data.data.total)
        setPage(p)
      } else {
        setError(data.error || 'Failed to load history.')
      }
    } catch {
      setError('Could not reach the server.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load(0) }, [load])

  function formatTimestamp(iso) {
    try {
      return new Date(iso).toLocaleString(undefined, {
        dateStyle: 'medium', timeStyle: 'short',
      })
    } catch { return iso }
  }

  return (
    <div className={styles.historyTab}>

      <div className={styles.toolbar}>
        <h2 className={styles.title}>Calculation History</h2>
        <button
          className={styles.refreshBtn}
          onClick={() => load(page)}
          disabled={loading}
        >
          {loading ? '…' : '↺ Refresh'}
        </button>
      </div>

      {error && (
        <div className={styles.errorBanner} role="alert">{error}</div>
      )}

      {!loading && entries.length === 0 && !error && (
        <div className={styles.empty}>
          No calculations recorded yet. Evaluations from the Simple, Intermediate,
          Boolean, Trig, Combined, and Complex tabs are all logged here.
        </div>
      )}

      {entries.length > 0 && (
        <>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th className={styles.thExpr}>Expression</th>
                  <th className={styles.thResult}>Result</th>
                  <th className={styles.thTime}>When</th>
                </tr>
              </thead>
              <tbody>
                {entries.map(entry => (
                  <tr key={entry.id} className={styles.row}>
                    <td className={styles.tdExpr}>
                      <code className={styles.mono}>{entry.expression}</code>
                    </td>
                    <td className={styles.tdResult}>
                      <code className={`${styles.mono} ${styles.result}`}>
                        {entry.result}
                      </code>
                    </td>
                    <td className={styles.tdTime}>
                      <span className={styles.timestamp}>
                        {formatTimestamp(entry.calculatedAt)}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className={styles.pagination}>
            <button
              className={styles.pageBtn}
              onClick={() => load(page - 1)}
              disabled={page === 0 || loading}
            >
              ← Previous
            </button>
            <span className={styles.pageInfo}>
              Page {page + 1} of {totalPages || 1}
              <span className={styles.totalCount}> · {total} total</span>
            </span>
            <button
              className={styles.pageBtn}
              onClick={() => load(page + 1)}
              disabled={page >= totalPages - 1 || loading}
            >
              Next →
            </button>
          </div>
        </>
      )}
    </div>
  )
}