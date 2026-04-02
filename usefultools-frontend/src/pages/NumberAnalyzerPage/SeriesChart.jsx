import { useState } from 'react'
import styles from './SeriesChart.module.css'

// ── Constants ─────────────────────────────────────────────────────────────────

const PALETTE = [
  '#4a90d9', '#34d399', '#f59e0b', '#e879f9',
  '#fb7185', '#a78bfa', '#38bdf8', '#f97316',
]

// SVG viewport and margin constants
const W  = 560   // total SVG width
const H  = 200   // total SVG height
const ML = 58    // left margin (y-axis labels)
const MR = 14    // right margin
const MT = 14    // top margin
const MB = 32    // bottom margin (x-axis labels)
const PW = W - ML - MR   // plot area width
const PH = H - MT - MB   // plot area height

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Returns true if the string can be parsed as a finite number. */
function isNumeric(val) {
  const n = parseFloat(val)
  return !isNaN(n) && isFinite(n)
}

/**
 * Extracts plottable series from a category's seriesMap.
 * A series is plottable if it has ≥ 2 numeric values.
 * Returns array of { name: string, points: [{x, y}] }.
 */
function extractSeries(seriesMap) {
  const out = []
  for (const [name, series] of Object.entries(seriesMap || {})) {
    const values = series?.values || {}
    const points = []
    for (const [k, v] of Object.entries(values)) {
      if (isNumeric(v)) {
        points.push({ x: Number(k), y: parseFloat(v) })
      }
    }
    if (points.length >= 2) {
      points.sort((a, b) => a.x - b.x)
      out.push({ name, points })
    }
  }
  return out
}

/**
 * Formats a tick value concisely for the y-axis label.
 * Uses SI suffixes for large numbers to avoid overflow.
 */
function formatTick(v) {
  const abs = Math.abs(v)
  if (abs >= 1e15) return (v / 1e15).toPrecision(3) + 'P'
  if (abs >= 1e12) return (v / 1e12).toPrecision(3) + 'T'
  if (abs >= 1e9)  return (v / 1e9).toPrecision(3)  + 'B'
  if (abs >= 1e6)  return (v / 1e6).toPrecision(3)  + 'M'
  if (abs >= 1e3)  return (v / 1e3).toPrecision(3)  + 'k'
  if (!Number.isInteger(v)) return parseFloat(v.toPrecision(4)).toString()
  return String(v)
}

// ── CategoryChart ─────────────────────────────────────────────────────────────

/**
 * SVG line chart for all plottable series in one category.
 * Supports a linear/log-scale toggle for sequences with exponential growth.
 */
function CategoryChart({ name, seriesMap }) {
  const [logScale, setLogScale] = useState(false)

  const series = extractSeries(seriesMap)
  if (series.length === 0) return null

  const allPts  = series.flatMap(s => s.points)
  const xMin    = Math.min(...allPts.map(p => p.x))
  const xMax    = Math.max(...allPts.map(p => p.x))
  const rawYMin = Math.min(...allPts.map(p => p.y))
  const rawYMax = Math.max(...allPts.map(p => p.y))

  // Log scale is only offered when values span more than 2 orders of magnitude
  // and are all non-negative.
  const canLog  = rawYMin >= 0 && rawYMax > 0 && rawYMax / Math.max(rawYMin, 1) > 50
  const useLog  = logScale && canLog

  const toDisplay = (y) => useLog ? Math.log1p(Math.max(0, y)) : y
  const yMin    = toDisplay(rawYMin)
  const yMax    = toDisplay(rawYMax)
  const yRange  = yMax - yMin || 1
  const xRange  = xMax - xMin || 1

  // Coordinate transform functions
  const sx = (x) => ML + ((x - xMin) / xRange) * PW
  const sy = (y) => MT + PH - ((toDisplay(y) - yMin) / yRange) * PH

  // Y-axis ticks: 5 evenly spaced in display space, labelled in real values
  const yTicks = Array.from({ length: 5 }, (_, i) => {
    const frac    = i / 4
    const dispVal = yMin + frac * yRange
    const realVal = useLog ? Math.expm1(dispVal) : dispVal
    return { screenY: MT + PH - frac * PH, label: formatTick(realVal) }
  })

  // X-axis ticks: up to 6, evenly sampled from the first series' x values
  const xPts  = series[0].points
  const step  = Math.max(1, Math.floor(xPts.length / 5))
  const xTicks = xPts
    .filter((_, i) => i % step === 0 || i === xPts.length - 1)
    .map(p => ({ screenX: sx(p.x), label: String(p.x) }))

  // Don't render individual dot markers when a series has many points —
  // they become too dense and obscure the line.
  const avgLen   = allPts.length / series.length
  const showDots = avgLen <= 16

  return (
    <div className={styles.chartBlock}>
      <div className={styles.chartHeader}>
        <span className={styles.chartCatName}>{name}</span>
        {canLog && (
          <button
            type="button"
            className={styles.scaleToggle}
            onClick={() => setLogScale(v => !v)}
          >
            {logScale ? '≡ linear' : 'log scale'}
          </button>
        )}
      </div>

      <svg
        viewBox={`0 0 ${W} ${H}`}
        className={styles.svg}
        aria-label={`Line chart for ${name} series`}
      >
        {/* ── Y-axis grid lines and tick labels ──────────────────────── */}
        {yTicks.map((t, i) => (
          <g key={`yt-${i}`}>
            {/* Horizontal grid line (dashed for all but the bottom axis) */}
            <line
              x1={ML} y1={t.screenY}
              x2={ML + PW} y2={t.screenY}
              stroke="var(--clr-border)"
              strokeWidth={i === 0 ? 1 : 0.5}
              strokeDasharray={i === 0 ? 'none' : '4 4'}
            />
            {/* Tick mark */}
            <line
              x1={ML - 4} y1={t.screenY}
              x2={ML}     y2={t.screenY}
              stroke="var(--clr-border)" strokeWidth="1"
            />
            {/* Label */}
            <text
              x={ML - 7} y={t.screenY + 4}
              textAnchor="end"
              fontSize="9"
              fontFamily="var(--font-mono, monospace)"
              fill="var(--clr-text-muted)"
            >
              {t.label}
            </text>
          </g>
        ))}

        {/* ── X-axis tick labels ──────────────────────────────────────── */}
        {xTicks.map((t, i) => (
          <g key={`xt-${i}`}>
            <line
              x1={t.screenX} y1={MT + PH}
              x2={t.screenX} y2={MT + PH + 4}
              stroke="var(--clr-border)" strokeWidth="1"
            />
            <text
              x={t.screenX} y={MT + PH + 16}
              textAnchor="middle"
              fontSize="9"
              fontFamily="var(--font-mono, monospace)"
              fill="var(--clr-text-muted)"
            >
              {t.label}
            </text>
          </g>
        ))}

        {/* ── Axes ───────────────────────────────────────────────────── */}
        {/* Y axis */}
        <line
          x1={ML} y1={MT}
          x2={ML} y2={MT + PH}
          stroke="var(--clr-border)" strokeWidth="1"
        />
        {/* X axis */}
        <line
          x1={ML}      y1={MT + PH}
          x2={ML + PW} y2={MT + PH}
          stroke="var(--clr-border)" strokeWidth="1"
        />

        {/* ── Series lines and dots ───────────────────────────────────── */}
        {series.map((s, si) => {
          const color = PALETTE[si % PALETTE.length]
          const pts   = s.points
            .map(p => `${sx(p.x).toFixed(1)},${sy(p.y).toFixed(1)}`)
            .join(' ')

          return (
            <g key={s.name}>
              <polyline
                points={pts}
                fill="none"
                stroke={color}
                strokeWidth="2"
                strokeLinejoin="round"
                strokeLinecap="round"
              />
              {showDots && s.points.map((p, pi) => (
                <circle
                  key={pi}
                  cx={sx(p.x).toFixed(1)}
                  cy={sy(p.y).toFixed(1)}
                  r="3"
                  fill={color}
                />
              ))}
            </g>
          )
        })}
      </svg>

      {/* ── Legend ─────────────────────────────────────────────────────── */}
      <div className={styles.legend}>
        {series.map((s, si) => (
          <div key={s.name} className={styles.legendItem}>
            <span
              className={styles.legendSwatch}
              style={{ background: PALETTE[si % PALETTE.length] }}
            />
            <span className={styles.legendName}>{s.name}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

// ── SeriesChart (public) ──────────────────────────────────────────────────────

/**
 * Renders SVG line charts for all categories in the series result that
 * contain at least one numeric sequence.
 *
 * Props:
 *   categories — the `categories` field from the normalized series result.
 *                Pass `normalizeSeriesResult(rawResult)?.categories`.
 *
 * Non-numeric series (e.g. Twin Prime pairs like "(3, 5)") are silently
 * skipped. Categories with no plottable series are not rendered.
 */
export default function SeriesChart({ categories }) {
  if (!categories) return null

  // Filter to categories that have at least one plottable series
  const chartable = Object.entries(categories).filter(([, sm]) =>
    Object.values(sm).some(series =>
      Object.values(series?.values || {}).some(isNumeric)
    )
  )

  if (chartable.length === 0) return null

  return (
    <div className={styles.section}>
      <div className={styles.sectionHeader}>
        <h3 className={styles.sectionTitle}>Visualisation</h3>
        <p className={styles.sectionHint}>
          Numeric sequences only — pair-type series (e.g. Twin Primes) are excluded.
          Use the log scale toggle for fast-growing sequences.
        </p>
      </div>
      {chartable.map(([catName, seriesMap]) => (
        <CategoryChart key={catName} name={catName} seriesMap={seriesMap} />
      ))}
    </div>
  )
}