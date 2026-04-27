import { useCallback, useEffect, useMemo, useState } from 'react'
import { fetchAdminAnalytics } from '../../api/apiClient'
import styles from './AnalyticsTab.module.css'

/*
 * AnalyticsTab — Sprint 18.
 *
 * Third tab on AdminPage (after Users, Tool Toggles). Shows an admin the
 * tool_metrics roll-up within a selectable time window.
 *
 * Layout:
 *   1. Window selector pill-row (24h / 7d / 30d / all)
 *   2. Four summary cards: total invocations, avg exec time, avg latency,
 *      success rate. Plus a secondary memory card.
 *   3. Three ranked panels side-by-side: Top slow, Most failing, Most popular
 *   4. Per-tool breakdown table (every tool with at least one invocation)
 *
 * All visual language matches the existing AdminPage (red/rose accent on
 * Observatory surface). No new chart library — everything is semantic HTML
 * + CSS bars so the component has zero dependencies.
 */

// ── Tool display metadata ───────────────────────────────────────────────────
//
// Mirrors the label-and-icon data used elsewhere in the app, so every
// tool_name row in the dashboard is readable without the admin having to
// decode "webdev.headers" etc. Unknown tool names fall back to raw id.

const TOOL_DISPLAY = {
  'analyzer.classify':     { icon: '🔢', label: 'Number Analyser — Classify'  },
  'analyzer.base':         { icon: '🔢', label: 'Number Analyser — Base'      },
  'analyzer.series':       { icon: '🔢', label: 'Number Analyser — Series'    },
  'converter.convert':     { icon: '🔄', label: 'Unit Converter'              },
  'text.transform':        { icon: '📝', label: 'Text Utilities'              },
  'encoding.transform':    { icon: '🔧', label: 'Encoding & Decoding'         },
  'code.format':           { icon: '💻', label: 'Code Utilities'              },
  'webdev.generate':       { icon: '🛠️', label: 'Web Dev Helpers'            },
  'webdev.headers':        { icon: '📋', label: 'HTTP Header Viewer'          },
  'image.process':         { icon: '🖼️', label: 'Image Tools'                },
  'hash.identify':         { icon: '#️⃣', label: 'Hash Identifier'             },
  'key.generate':          { icon: '🔑', label: 'API Key Generator'           },
  'qrcode.generate':       { icon: '▪️', label: 'QR Code Generator'           },
  'cron.build':            { icon: '⏲', label: 'Cron Builder'                 },
  'time.convert':          { icon: '🌍', label: 'Timezone Converter'          },
  'time.timestamp':        { icon: '🔢', label: 'Timestamp ↔ Date'            },
  'password.generate':     { icon: '🔐', label: 'Password Generator'          },
  'password.save':         { icon: '🔐', label: 'Password Vault — Save'       },
  'password.fetch':        { icon: '🔐', label: 'Password Vault — Fetch'      },
  'calculator.standard':   { icon: '🧮', label: 'Calculator'                  },
  'calculator.financial':  { icon: '💰', label: 'Financial Calculator'        },
  'calculator.probability':{ icon: '🎲', label: 'Probability Calculator'      },
}

function toolMeta(toolName) {
  return TOOL_DISPLAY[toolName] || { icon: '•', label: toolName }
}

// ── Formatting helpers ──────────────────────────────────────────────────────

function fmtInt(n) {
  if (n == null) return '—'
  return Math.round(n).toLocaleString()
}

function fmtMs(n) {
  if (n == null || !isFinite(n)) return '—'
  if (n < 1)   return '<1 ms'
  if (n < 10)  return n.toFixed(1) + ' ms'
  return Math.round(n).toLocaleString() + ' ms'
}

function fmtBytes(n) {
  if (n == null || !isFinite(n) || n === 0) return '—'
  const abs = Math.abs(n)
  const sign = n < 0 ? '−' : ''
  if (abs < 1024)                    return sign + Math.round(abs) + ' B'
  if (abs < 1024 * 1024)             return sign + (abs / 1024).toFixed(1) + ' KB'
  if (abs < 1024 * 1024 * 1024)      return sign + (abs / (1024 * 1024)).toFixed(1) + ' MB'
  return sign + (abs / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

function fmtPct(n) {
  if (n == null || !isFinite(n)) return '—'
  return n.toFixed(1) + '%'
}

// ── Sub-components ──────────────────────────────────────────────────────────

function WindowPicker({ value, onChange, disabled }) {
  const WINDOWS = [
    { id: '24h', label: '24 hours' },
    { id: '7d',  label: '7 days'   },
    { id: '30d', label: '30 days'  },
    { id: 'all', label: 'All time' },
  ]
  return (
    <div className={styles.windowPicker} role="tablist" aria-label="Time window">
      {WINDOWS.map(w => (
        <button
          key={w.id}
          className={value === w.id ? styles.windowPillActive : styles.windowPill}
          onClick={() => onChange(w.id)}
          disabled={disabled}
          role="tab"
          aria-selected={value === w.id}
        >
          {w.label}
        </button>
      ))}
    </div>
  )
}

function SummaryCard({ label, value, sub, tone = 'default' }) {
  const toneClass =
    tone === 'good'    ? styles.summaryCardGood
  : tone === 'warn'    ? styles.summaryCardWarn
  : tone === 'bad'     ? styles.summaryCardBad
  : styles.summaryCard
  return (
    <div className={toneClass}>
      <span className={styles.summaryLabel}>{label}</span>
      <span className={styles.summaryValue}>{value}</span>
      {sub && <span className={styles.summarySub}>{sub}</span>}
    </div>
  )
}

/**
 * Ranked list with a proportional bar for the primary metric.
 *
 * @param rows  Array of { toolName, primary, secondary } rows
 * @param max   Maximum primary value for bar scaling
 * @param primaryFmt, secondaryFmt  Formatter functions
 */
function RankedList({ title, icon, emptyHint, rows, max, primaryLabel, primaryFmt, secondaryLabel, secondaryFmt, barTone = 'default' }) {
  const barClass =
    barTone === 'bad'  ? styles.rankBarBad
  : barTone === 'good' ? styles.rankBarGood
  : styles.rankBar

  return (
    <section className={styles.rankedPanel}>
      <header className={styles.rankedHeader}>
        <span className={styles.rankedIcon} aria-hidden="true">{icon}</span>
        <h3 className={styles.rankedTitle}>{title}</h3>
      </header>

      {(!rows || rows.length === 0) && (
        <p className={styles.rankedEmpty}>{emptyHint}</p>
      )}

      {rows && rows.length > 0 && (
        <ol className={styles.rankedList}>
          {rows.map((row, i) => {
            const meta = toolMeta(row.toolName)
            const pct = max > 0 ? Math.max(2, (row.primary / max) * 100) : 0
            return (
              <li key={row.toolName + i} className={styles.rankedItem}>
                <div className={styles.rankedItemHead}>
                  <span className={styles.rankedRank}>{i + 1}</span>
                  <span className={styles.rankedToolIcon} aria-hidden="true">{meta.icon}</span>
                  <span className={styles.rankedToolLabel}>{meta.label}</span>
                  <span className={styles.rankedPrimary}>{primaryFmt(row.primary)}</span>
                </div>
                <div className={styles.rankBarTrack}>
                  <div className={barClass} style={{ width: pct + '%' }} />
                </div>
                <div className={styles.rankedSecondary}>
                  <span>{primaryLabel}: <strong>{primaryFmt(row.primary)}</strong></span>
                  <span>{secondaryLabel}: <strong>{secondaryFmt(row.secondary)}</strong></span>
                </div>
              </li>
            )
          })}
        </ol>
      )}
    </section>
  )
}

function PerToolTable({ rows }) {
  // Sort control — default by invocations DESC
  const [sortKey,  setSortKey]  = useState('invocations')
  const [sortDir,  setSortDir]  = useState('desc')

  const sorted = useMemo(() => {
    const copy = rows ? rows.slice() : []
    copy.sort((a, b) => {
      const av = a[sortKey] ?? 0
      const bv = b[sortKey] ?? 0
      if (av === bv) return 0
      const dir = sortDir === 'asc' ? 1 : -1
      return av < bv ? -1 * dir : 1 * dir
    })
    return copy
  }, [rows, sortKey, sortDir])

  function clickHeader(key) {
    if (sortKey === key) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc')
    } else {
      setSortKey(key)
      setSortDir(key === 'toolName' ? 'asc' : 'desc')
    }
  }

  function arrow(key) {
    if (sortKey !== key) return ''
    return sortDir === 'asc' ? ' ↑' : ' ↓'
  }

  if (!rows || rows.length === 0) {
    return (
      <section className={styles.breakdownPanel}>
        <h3 className={styles.sectionTitle}>Per-tool breakdown</h3>
        <p className={styles.rankedEmpty}>
          No metrics recorded in this window yet. Run any tool to start
          populating the table — server-side tools are auto-tracked, client-side
          tools report via fire-and-forget after each operation.
        </p>
      </section>
    )
  }

  return (
    <section className={styles.breakdownPanel}>
      <h3 className={styles.sectionTitle}>Per-tool breakdown</h3>
      <div className={styles.tableWrap}>
        <table className={styles.breakdownTable}>
          <thead>
            <tr>
              <th className={styles.thSortable} onClick={() => clickHeader('toolName')}>Tool{arrow('toolName')}</th>
              <th className={styles.thSortable} onClick={() => clickHeader('invocations')}>Invocations{arrow('invocations')}</th>
              <th className={styles.thSortable} onClick={() => clickHeader('avgExecutionMs')}>Avg exec{arrow('avgExecutionMs')}</th>
              <th className={styles.thSortable} onClick={() => clickHeader('maxExecutionMs')}>Max exec{arrow('maxExecutionMs')}</th>
              <th className={styles.thSortable} onClick={() => clickHeader('avgLatencyMs')}>Avg latency{arrow('avgLatencyMs')}</th>
              <th className={styles.thSortable} onClick={() => clickHeader('avgMemoryBytes')}>Avg memory{arrow('avgMemoryBytes')}</th>
              <th className={styles.thSortable} onClick={() => clickHeader('successRatePct')}>Success{arrow('successRatePct')}</th>
              <th className={styles.thSortable} onClick={() => clickHeader('failures')}>Failures{arrow('failures')}</th>
            </tr>
          </thead>
          <tbody>
            {sorted.map(row => {
              const meta = toolMeta(row.toolName)
              const successTone =
                row.successRatePct >= 99 ? styles.pillGood
              : row.successRatePct >= 90 ? styles.pillWarn
              :                            styles.pillBad
              return (
                <tr key={row.toolName} className={styles.breakdownRow}>
                  <td className={styles.tdTool}>
                    <span className={styles.tdToolIcon} aria-hidden="true">{meta.icon}</span>
                    <span>
                      <span className={styles.tdToolLabel}>{meta.label}</span>
                      <code className={styles.tdToolId}>{row.toolName}</code>
                    </span>
                  </td>
                  <td className={styles.tdNum}><strong>{fmtInt(row.invocations)}</strong></td>
                  <td className={styles.tdNum}>{fmtMs(row.avgExecutionMs)}</td>
                  <td className={styles.tdNum}>{fmtMs(row.maxExecutionMs)}</td>
                  <td className={styles.tdNum}>{fmtMs(row.avgLatencyMs)}</td>
                  <td className={styles.tdNum}>{fmtBytes(row.avgMemoryBytes)}</td>
                  <td className={styles.tdNum}>
                    <span className={successTone}>{fmtPct(row.successRatePct)}</span>
                  </td>
                  <td className={styles.tdNum}>
                    {row.failures > 0
                      ? <span className={styles.failCount}>{fmtInt(row.failures)}</span>
                      : <span className={styles.zeroCount}>0</span>}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      <p className={styles.breakdownNote}>
        Memory figures are coarse proxies — per-request JVM / JS-heap deltas
        are influenced by other activity, so they're only meaningful in
        aggregate across many samples.
      </p>
    </section>
  )
}

// ── Main component ──────────────────────────────────────────────────────────

export default function AnalyticsTab() {
  const [window,    setWindow]    = useState('7d')
  const [data,      setData]      = useState(null)
  const [loading,   setLoading]   = useState(true)
  const [error,     setError]     = useState(null)

  const load = useCallback(async (w) => {
    setLoading(true)
    setError(null)
    try {
      const { data: resp } = await fetchAdminAnalytics(w)
      if (resp?.success) {
        setData(resp.data)
      } else {
        setError(resp?.error || 'Failed to load analytics.')
      }
    } catch {
      setError('Network error — could not reach the analytics endpoint.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load(window) }, [load, window])

  // ── Render ───────────────────────────────────────────────────────────────

  const overall = data?.overall || {}
  const maxSlow     = Math.max(1, ...(data?.topSlow     || []).map(r => r.avgExecutionMs || 0))
  const maxFail     = Math.max(1, ...(data?.mostFailing || []).map(r => r.failureRatePct || 0))
  const maxPop      = Math.max(1, ...(data?.mostPopular || []).map(r => r.invocations    || 0))

  const successTone =
    overall.successRatePct >= 99 ? 'good'
  : overall.successRatePct >= 90 ? 'warn'
  :                                'bad'

  return (
    <div className={styles.tabPanel}>

      {/* ── Controls row ────────────────────────────────────────────────── */}
      <div className={styles.controlsRow}>
        <div>
          <h2 className={styles.panelHeading}>Tool Analytics</h2>
          <p className={styles.panelSub}>
            Aggregated from every tool invocation — server-side tools are
            auto-instrumented; client-side tools report via{' '}
            <code>/api/metrics/log</code>. Rankings require at least 5 samples
            per tool to avoid outlier noise.
          </p>
        </div>
        <WindowPicker value={window} onChange={setWindow} disabled={loading} />
      </div>

      {/* ── Error / loading ─────────────────────────────────────────────── */}
      {error && <div className={styles.errorBanner}>{error}</div>}

      {loading && !data && (
        <div className={styles.loadingRow}>Loading analytics…</div>
      )}

      {!loading && !error && data && (
        <>
          {/* ── Summary cards ─────────────────────────────────────────── */}
          <div className={styles.summaryGrid}>
            <SummaryCard
              label="Total invocations"
              value={fmtInt(overall.totalInvocations)}
              sub={`in last ${data.window === 'all' ? 'all-time' : data.window}`}
            />
            <SummaryCard
              label="Avg execution"
              value={fmtMs(overall.avgExecutionMs)}
              sub="across all tools"
            />
            <SummaryCard
              label="Avg latency"
              value={fmtMs(overall.avgLatencyMs)}
              sub="round-trip (server-side only)"
            />
            <SummaryCard
              label="Success rate"
              value={fmtPct(overall.successRatePct)}
              sub={`${(overall.totalInvocations || 0).toLocaleString()} samples`}
              tone={overall.totalInvocations > 0 ? successTone : 'default'}
            />
            <SummaryCard
              label="Avg memory delta"
              value={fmtBytes(overall.avgMemoryBytes)}
              sub="coarse proxy — aggregate only"
            />
          </div>

          {/* ── Ranked panels (3-across on wide, stacked on narrow) ──── */}
          <div className={styles.rankedGrid}>
            <RankedList
              title="Top slow tools"
              icon="🐢"
              emptyHint="No tools have reached the 5-sample ranking threshold yet."
              rows={(data.topSlow || []).map(r => ({
                toolName:  r.toolName,
                primary:   r.avgExecutionMs,
                secondary: r.invocations,
              }))}
              max={maxSlow}
              primaryLabel="Avg exec"
              primaryFmt={fmtMs}
              secondaryLabel="Invocations"
              secondaryFmt={fmtInt}
              barTone="warn"
            />

            <RankedList
              title="Most failing tools"
              icon="⚠"
              emptyHint="No tool in this window has both ≥5 samples and any failures. Healthy!"
              rows={(data.mostFailing || []).map(r => ({
                toolName:  r.toolName,
                primary:   r.failureRatePct,
                secondary: r.failures,
              }))}
              max={maxFail}
              primaryLabel="Failure rate"
              primaryFmt={fmtPct}
              secondaryLabel="Failures"
              secondaryFmt={fmtInt}
              barTone="bad"
            />

            <RankedList
              title="Most popular tools"
              icon="★"
              emptyHint="No invocations recorded yet."
              rows={(data.mostPopular || []).map(r => ({
                toolName:  r.toolName,
                primary:   r.invocations,
                secondary: r.successRatePct,
              }))}
              max={maxPop}
              primaryLabel="Invocations"
              primaryFmt={fmtInt}
              secondaryLabel="Success rate"
              secondaryFmt={fmtPct}
              barTone="good"
            />
          </div>

          {/* ── Breakdown table ───────────────────────────────────────── */}
          <PerToolTable rows={data.perTool} />
        </>
      )}
    </div>
  )
}
