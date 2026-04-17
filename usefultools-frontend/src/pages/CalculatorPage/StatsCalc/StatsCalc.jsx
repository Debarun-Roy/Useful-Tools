import { useState } from 'react'
import { calculateStats } from '../../../api/apiClient'
import styles from './StatsCalc.module.css'

const STAT_DEFINITIONS = [
  { key: 'count',    label: 'Count',        icon: 'n',  desc: 'Number of values' },
  { key: 'sum',      label: 'Sum',          icon: 'Σ',  desc: 'Total of all values' },
  { key: 'mean',     label: 'Mean',         icon: 'μ',  desc: 'Arithmetic average' },
  { key: 'median',   label: 'Median',       icon: 'M',  desc: 'Middle value (sorted)' },
  { key: 'mode',     label: 'Mode',         icon: 'Mo', desc: 'Most frequent value(s)' },
  { key: 'min',      label: 'Minimum',      icon: '↓',  desc: 'Smallest value' },
  { key: 'max',      label: 'Maximum',      icon: '↑',  desc: 'Largest value' },
  { key: 'range',    label: 'Range',        icon: 'R',  desc: 'Max − Min' },
  { key: 'variance', label: 'Variance',     icon: 'σ²', desc: 'Population variance' },
  { key: 'stdDev',   label: 'Std Dev',      icon: 'σ',  desc: 'Population std deviation' },
  { key: 'skewness', label: 'Skewness',     icon: 'γ₁', desc: "Pearson's 2nd coefficient" },
  { key: 'kurtosis', label: 'Kurtosis',     icon: 'γ₂', desc: 'Excess (Fisher) kurtosis' },
]

function formatValue(key, value) {
  if (key === 'count') return String(value)
  if (key === 'mode')  return String(value)
  if (typeof value === 'number') {
    if (Number.isInteger(value)) return String(value)
    return parseFloat(value.toPrecision(10)).toString()
  }
  return String(value)
}

export default function StatsCalc({ isGuest }) {
  const [input,   setInput]   = useState('')
  const [result,  setResult]  = useState(null)
  const [error,   setError]   = useState('')
  const [loading, setLoading] = useState(false)

  async function handleCalculate() {
    const trimmed = input.trim()
    if (!trimmed) { setError('Please enter at least one number.'); return }

    // Parse locally first to give instant feedback before hitting the network.
    const parts = trimmed.split(/[,\s\n]+/).filter(Boolean)
    for (const part of parts) {
      if (isNaN(Number(part))) {
        setError(`"${part}" is not a valid number.`)
        return
      }
    }

    setLoading(true)
    setError('')
    setResult(null)

    try {
      const { data } = await calculateStats(trimmed)
      if (data.success) {
        setResult(data.data)
      } else {
        setError(data.error || 'Calculation failed. Please check your input.')
      }
    } catch {
      setError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setLoading(false)
    }
  }

  function handleClear() {
    setInput('')
    setResult(null)
    setError('')
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) handleCalculate()
  }

  return (
    <div className={styles.calculator}>

      {!isGuest && (
        <div className={styles.note}>
          Results from this calculator are automatically saved to calculation history.
        </div>
      )}

      {/* ── Input ──────────────────────────────────────────────────── */}
      <div className={styles.inputSection}>
        <label className={styles.inputLabel} htmlFor="stats-input">
          Dataset
          <span className={styles.inputHint}>
            — separate values with commas, spaces, or newlines
          </span>
        </label>
        <textarea
          id="stats-input"
          className={styles.textarea}
          value={input}
          onChange={e => { setInput(e.target.value); setError(''); setResult(null) }}
          onKeyDown={handleKeyDown}
          placeholder={'Examples:\n1, 2, 3, 4, 5\n2.5 4.1 6.8 3.2\n10\n20\n30'}
          rows={5}
          disabled={loading}
        />
        <div className={styles.inputFooter}>
          <span className={styles.inputHint}>Ctrl+Enter to calculate</span>
          <div className={styles.actionRow}>
            <button className={styles.clearBtn} onClick={handleClear} disabled={loading}>
              Clear
            </button>
            <button className={styles.calcBtn} onClick={handleCalculate} disabled={loading}>
              {loading ? 'Computing…' : 'Calculate'}
            </button>
          </div>
        </div>
      </div>

      {/* ── Error ─────────────────────────────────────────────────── */}
      {error && (
        <div className={styles.errorRow} role="alert">{error}</div>
      )}

      {/* ── Results ───────────────────────────────────────────────── */}
      {result && (
        <div className={styles.resultsSection}>
          <div className={styles.summaryStrip}>
            <div className={styles.summaryItem}>
              <span className={styles.summaryValue}>{result.count}</span>
              <span className={styles.summaryLabel}>values</span>
            </div>
            <div className={styles.summaryItem}>
              <span className={styles.summaryValue}>{formatValue('mean', result.mean)}</span>
              <span className={styles.summaryLabel}>mean</span>
            </div>
            <div className={styles.summaryItem}>
              <span className={styles.summaryValue}>{formatValue('median', result.median)}</span>
              <span className={styles.summaryLabel}>median</span>
            </div>
            <div className={styles.summaryItem}>
              <span className={styles.summaryValue}>{formatValue('stdDev', result.stdDev)}</span>
              <span className={styles.summaryLabel}>std dev</span>
            </div>
          </div>

          <div className={styles.statsGrid}>
            {STAT_DEFINITIONS.map(({ key, label, icon, desc }) => (
              <div key={key} className={styles.statCard}>
                <div className={styles.statCardHeader}>
                  <span className={styles.statIcon}>{icon}</span>
                  <span className={styles.statLabel}>{label}</span>
                </div>
                <span className={styles.statValue}>{formatValue(key, result[key])}</span>
                <span className={styles.statDesc}>{desc}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}