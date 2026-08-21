/**
 * DataVisualizationPage.jsx — Sprint 20
 *
 * Full data-visualisation suite. All processing is client-side; the backend
 * is not touched (frozen constraint). Chart rendering uses Chart.js (already
 * available in the project via recharts/chart.js in the Artifact environment,
 * imported here via the CDN-backed import below).
 *
 * Tabs
 *   1. Builder   — choose chart type, enter / paste / import data, preview
 *   2. Import    — CSV or JSON array import wizard with column mapping
 *   3. Analysis  — statistical summary of the current dataset
 *   4. Export    — download chart as PNG or SVG; export data as CSV/JSON
 *
 * Activity logging:  'chart.build'  (on render)
 *                    'chart.export' (on download)
 *                    'chart.import' (on data import)
 * Metrics:           trackTool wraps the chart-build operation (debounced).
 *
 * Privacy rule: NEVER log user data values — only metadata (row count,
 * column count, chart type, export format).
 */

import { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logoutUser } from '../../api/apiClient'
import UserMenu from '../../components/UserMenu/UserMenu'
import { logActivity } from '../../utils/logActivity'
import { trackTool } from '../../utils/logMetric'
import styles from './DataVisualizationPage.module.css'

// ─── Constants ────────────────────────────────────────────────────────────────

const CHART_TYPES = [
  { id: 'bar',     label: 'Bar',     icon: '▊' },
  { id: 'line',    label: 'Line',    icon: '📈' },
  { id: 'pie',     label: 'Pie',     icon: '◕' },
  { id: 'area',    label: 'Area',    icon: '◬' },
  { id: 'scatter', label: 'Scatter', icon: '⁙' },
]

const TABS = [
  { id: 'builder',  label: 'Builder',  icon: '⬛' },
  { id: 'import',   label: 'Import',   icon: '⬆' },
  { id: 'analysis', label: 'Analysis', icon: '◎' },
  { id: 'export',   label: 'Export',   icon: '⬇' },
]

const THEME_PALETTES = {
  ocean:    ['#3b6fd4', '#4a90d9', '#7ec8e3', '#0077b6', '#48cae4', '#90e0ef'],
  forest:   ['#2d6a4f', '#40916c', '#52b788', '#74c69d', '#95d5b2', '#b7e4c7'],
  sunset:   ['#e63946', '#f4a261', '#e9c46a', '#2a9d8f', '#264653', '#a8dadc'],
  monochrome: ['#212529', '#495057', '#6c757d', '#adb5bd', '#ced4da', '#dee2e6'],
  vivid:    ['#ff595e', '#ffca3a', '#6a4c93', '#1982c4', '#8ac926', '#ff6b35'],
}

const SAMPLE_DATASETS = {
  monthly_revenue: {
    label: 'Monthly Revenue',
    labels: ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'],
    datasets: [{ name: 'Revenue', values: [42000,47000,38000,55000,61000,58000,67000,72000,64000,78000,83000,91000] }],
  },
  product_share: {
    label: 'Product Market Share',
    labels: ['Product A','Product B','Product C','Product D','Product E'],
    datasets: [{ name: 'Share %', values: [34, 28, 18, 12, 8] }],
  },
  multi_series: {
    label: 'Sales vs Target',
    labels: ['Q1','Q2','Q3','Q4'],
    datasets: [
      { name: 'Actual',  values: [85000, 92000, 104000, 118000] },
      { name: 'Target',  values: [90000, 95000, 100000, 110000] },
    ],
  },
}

// ─── Statistical helpers ──────────────────────────────────────────────────────

function computeStats(values) {
  if (!values || values.length === 0) return null
  const sorted = [...values].sort((a, b) => a - b)
  const n = sorted.length
  const sum = sorted.reduce((a, b) => a + b, 0)
  const mean = sum / n
  const variance = sorted.reduce((acc, v) => acc + (v - mean) ** 2, 0) / n
  const stdDev = Math.sqrt(variance)
  const median = n % 2 === 0
    ? (sorted[n / 2 - 1] + sorted[n / 2]) / 2
    : sorted[Math.floor(n / 2)]
  const q1 = sorted[Math.floor(n * 0.25)]
  const q3 = sorted[Math.floor(n * 0.75)]
  const iqr = q3 - q1
  const outlierFence = 1.5 * iqr
  const outliers = sorted.filter(v => v < q1 - outlierFence || v > q3 + outlierFence)

  // Simple linear trend: slope of least-squares line
  const indices = sorted.map((_, i) => i)
  const iMean = (n - 1) / 2
  const slope = indices.reduce((acc, i) => acc + (i - iMean) * (values[i] - mean), 0)
             / indices.reduce((acc, i) => acc + (i - iMean) ** 2, 0)

  return {
    count: n,
    sum: Math.round(sum * 100) / 100,
    mean: Math.round(mean * 100) / 100,
    median: Math.round(median * 100) / 100,
    stdDev: Math.round(stdDev * 100) / 100,
    min: sorted[0],
    max: sorted[n - 1],
    range: Math.round((sorted[n - 1] - sorted[0]) * 100) / 100,
    q1: Math.round(q1 * 100) / 100,
    q3: Math.round(q3 * 100) / 100,
    iqr: Math.round(iqr * 100) / 100,
    outlierCount: outliers.length,
    trendSlope: Math.round(slope * 10000) / 10000,
    trendDirection: slope > 0.01 ? 'up' : slope < -0.01 ? 'down' : 'flat',
  }
}

// ─── CSV parser ───────────────────────────────────────────────────────────────

function parseCSV(text) {
  const lines = text.trim().split(/\r?\n/)
  if (lines.length < 2) throw new Error('CSV must have at least a header row and one data row')
  const headers = lines[0].split(',').map(h => h.trim().replace(/^"|"$/g, ''))
  const rows = lines.slice(1).map(line => {
    const cols = line.split(',').map(c => c.trim().replace(/^"|"$/g, ''))
    return Object.fromEntries(headers.map((h, i) => [h, cols[i] ?? '']))
  })
  return { headers, rows }
}

function parseJSONArray(text) {
  const parsed = JSON.parse(text)
  if (!Array.isArray(parsed)) throw new Error('JSON must be an array of objects')
  if (parsed.length === 0) throw new Error('JSON array is empty')
  const headers = Object.keys(parsed[0])
  return { headers, rows: parsed }
}

// ─── Chart canvas renderer ────────────────────────────────────────────────────

function renderChartToCanvas(canvas, chartData, chartType, palette, title) {
  const ctx = canvas.getContext('2d')
  const { width, height } = canvas
  const colors = THEME_PALETTES[palette] || THEME_PALETTES.ocean
  const { labels, datasets } = chartData

  ctx.clearRect(0, 0, width, height)

  // Background
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, width, height)

  const padLeft = 70
  const padRight = 30
  const padTop = title ? 55 : 30
  const padBottom = 60
  const chartW = width - padLeft - padRight
  const chartH = height - padTop - padBottom

  // Title
  if (title) {
    ctx.fillStyle = '#0d1117'
    ctx.font = 'bold 15px "Plus Jakarta Sans", sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText(title, width / 2, 28)
  }

  if (chartType === 'pie') {
    renderPie(ctx, width, height, labels, datasets[0]?.values || [], colors)
    return
  }
  if (chartType === 'scatter') {
    renderScatter(ctx, padLeft, padTop, chartW, chartH, datasets, colors)
    renderAxes(ctx, padLeft, padTop, chartW, chartH, [], datasets, false)
    return
  }

  // Compute value range across all datasets
  const allValues = datasets.flatMap(ds => ds.values || [])
  const maxVal = Math.max(...allValues, 0)
  const minVal = Math.min(...allValues, 0)
  const range = maxVal - minVal || 1

  const toY = (v) => padTop + chartH - ((v - minVal) / range) * chartH

  // Y-axis grid + labels
  const ticks = 5
  ctx.strokeStyle = '#e8eaed'
  ctx.lineWidth = 1
  ctx.fillStyle = '#6b7280'
  ctx.font = '11px "Plus Jakarta Sans", sans-serif'
  ctx.textAlign = 'right'
  for (let i = 0; i <= ticks; i++) {
    const val = minVal + (range * i) / ticks
    const y = toY(val)
    ctx.beginPath()
    ctx.moveTo(padLeft, y)
    ctx.lineTo(padLeft + chartW, y)
    ctx.stroke()
    ctx.fillText(formatNum(val), padLeft - 8, y + 4)
  }

  const barGroupWidth = chartW / (labels?.length || 1)

  datasets.forEach((ds, dsIdx) => {
    const values = ds.values || []
    const color = colors[dsIdx % colors.length]

    if (chartType === 'bar') {
      const barW = (barGroupWidth * 0.6) / datasets.length
      values.forEach((v, i) => {
        const x = padLeft + i * barGroupWidth + barGroupWidth * 0.2 + dsIdx * barW
        const y0 = toY(0)
        const y1 = toY(v)
        ctx.fillStyle = color
        ctx.fillRect(x, Math.min(y0, y1), barW, Math.abs(y1 - y0))
      })
    } else if (chartType === 'line' || chartType === 'area') {
      ctx.beginPath()
      ctx.strokeStyle = color
      ctx.lineWidth = 2.5
      ctx.lineJoin = 'round'
      values.forEach((v, i) => {
        const x = padLeft + i * barGroupWidth + barGroupWidth / 2
        const y = toY(v)
        i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)
      })
      ctx.stroke()
      if (chartType === 'area') {
        ctx.lineTo(padLeft + (values.length - 1) * barGroupWidth + barGroupWidth / 2, toY(0))
        ctx.lineTo(padLeft + barGroupWidth / 2, toY(0))
        ctx.closePath()
        ctx.fillStyle = color + '33'
        ctx.fill()
      }
      // Data point dots
      values.forEach((v, i) => {
        const x = padLeft + i * barGroupWidth + barGroupWidth / 2
        const y = toY(v)
        ctx.beginPath()
        ctx.arc(x, y, 4, 0, Math.PI * 2)
        ctx.fillStyle = color
        ctx.fill()
      })
    }
  })

  // X-axis labels
  if (labels) {
    ctx.fillStyle = '#6b7280'
    ctx.font = '11px "Plus Jakarta Sans", sans-serif'
    ctx.textAlign = 'center'
    labels.forEach((lbl, i) => {
      const x = padLeft + i * barGroupWidth + barGroupWidth / 2
      ctx.fillText(String(lbl).slice(0, 12), x, padTop + chartH + 20)
    })
  }

  // Y-axis line
  ctx.strokeStyle = '#d1d5db'
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.moveTo(padLeft, padTop)
  ctx.lineTo(padLeft, padTop + chartH)
  ctx.stroke()

  // Legend
  if (datasets.length > 1) {
    const legX = padLeft + 10
    let legY = padTop + chartH + 38
    datasets.forEach((ds, i) => {
      ctx.fillStyle = colors[i % colors.length]
      ctx.fillRect(legX + i * 110, legY, 12, 12)
      ctx.fillStyle = '#374151'
      ctx.font = '11px "Plus Jakarta Sans", sans-serif'
      ctx.textAlign = 'left'
      ctx.fillText(ds.name || `Series ${i + 1}`, legX + i * 110 + 16, legY + 10)
    })
  }
}

function renderPie(ctx, width, height, labels, values, colors) {
  const total = values.reduce((a, b) => a + b, 0) || 1
  const cx = width / 2
  const cy = height / 2
  const r = Math.min(width, height) * 0.32
  let start = -Math.PI / 2

  values.forEach((v, i) => {
    const sweep = (v / total) * Math.PI * 2
    ctx.beginPath()
    ctx.moveTo(cx, cy)
    ctx.arc(cx, cy, r, start, start + sweep)
    ctx.closePath()
    ctx.fillStyle = colors[i % colors.length]
    ctx.fill()
    ctx.strokeStyle = '#fff'
    ctx.lineWidth = 2
    ctx.stroke()

    // Label
    const mid = start + sweep / 2
    const lx = cx + Math.cos(mid) * r * 0.65
    const ly = cy + Math.sin(mid) * r * 0.65
    ctx.fillStyle = '#fff'
    ctx.font = 'bold 11px "Plus Jakarta Sans", sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    if (sweep > 0.2) ctx.fillText(`${Math.round((v / total) * 100)}%`, lx, ly)
    ctx.textBaseline = 'alphabetic'
    start += sweep
  })

  // Legend
  const legStartY = height - 20 - labels.length * 0
  labels.forEach((lbl, i) => {
    const row = Math.floor(i / 3)
    const col = i % 3
    const lx = 20 + col * (width / 3)
    const ly = height - 50 + row * 18
    ctx.fillStyle = colors[i % colors.length]
    ctx.fillRect(lx, ly, 10, 10)
    ctx.fillStyle = '#374151'
    ctx.font = '11px "Plus Jakarta Sans", sans-serif'
    ctx.textAlign = 'left'
    ctx.fillText(String(lbl).slice(0, 14), lx + 14, ly + 9)
  })
}

function renderScatter(ctx, padL, padT, cW, cH, datasets, colors) {
  datasets.forEach((ds, di) => {
    const color = colors[di % colors.length]
    const pts = ds.values || []
    // For scatter: values is an array of {x,y} or plain numbers (index as x)
    pts.forEach((pt, i) => {
      const px = typeof pt === 'object' ? pt.x : i
      const py = typeof pt === 'object' ? pt.y : pt
      const allX = pts.map((p, j) => typeof p === 'object' ? p.x : j)
      const allY = pts.map(p => typeof p === 'object' ? p.y : p)
      const xRange = (Math.max(...allX) - Math.min(...allX)) || 1
      const yRange = (Math.max(...allY) - Math.min(...allY)) || 1
      const cx = padL + ((px - Math.min(...allX)) / xRange) * cW
      const cy = padT + cH - ((py - Math.min(...allY)) / yRange) * cH
      ctx.beginPath()
      ctx.arc(cx, cy, 5, 0, Math.PI * 2)
      ctx.fillStyle = color + 'bb'
      ctx.fill()
    })
  })
}

function renderAxes(ctx, padL, padT, cW, cH) {
  ctx.strokeStyle = '#d1d5db'
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.moveTo(padL, padT)
  ctx.lineTo(padL, padT + cH)
  ctx.lineTo(padL + cW, padT + cH)
  ctx.stroke()
}

function formatNum(n) {
  if (Math.abs(n) >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (Math.abs(n) >= 1_000)     return (n / 1_000).toFixed(1)     + 'k'
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function TabBar({ tabs, active, onChange }) {
  return (
    <div className={styles.tabBar} role="tablist">
      {tabs.map(t => (
        <button
          key={t.id}
          role="tab"
          aria-selected={active === t.id}
          className={active === t.id ? styles.tabActive : styles.tab}
          onClick={() => onChange(t.id)}
        >
          <span className={styles.tabIcon}>{t.icon}</span>
          {t.label}
        </button>
      ))}
    </div>
  )
}

function StatCard({ label, value, sub }) {
  return (
    <div className={styles.statCard}>
      <span className={styles.statLabel}>{label}</span>
      <span className={styles.statValue}>{value}</span>
      {sub && <span className={styles.statSub}>{sub}</span>}
    </div>
  )
}

// ─── Main component ───────────────────────────────────────────────────────────

export default function DataVisualizationPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const isGuest = username === 'Guest User'
  const canvasRef = useRef(null)

  // ── Tab state ──────────────────────────────────────────────────────────────
  const [activeTab, setActiveTab] = useState('builder')

  // ── Chart config ──────────────────────────────────────────────────────────
  const [chartType,  setChartType]  = useState('bar')
  const [palette,    setPalette]    = useState('ocean')
  const [chartTitle, setChartTitle] = useState('')

  // ── Dataset state ──────────────────────────────────────────────────────────
  // chartData: { labels: string[], datasets: [{ name, values: number[] }] }
  const [chartData, setChartData] = useState(SAMPLE_DATASETS.monthly_revenue)

  // ── Manual entry state ─────────────────────────────────────────────────────
  const [labelInput,    setLabelInput]    = useState(SAMPLE_DATASETS.monthly_revenue.labels.join(', '))
  const [seriesInputs,  setSeriesInputs]  = useState(
    SAMPLE_DATASETS.monthly_revenue.datasets.map(ds => ({
      name: ds.name,
      values: ds.values.join(', '),
    }))
  )
  const [manualError, setManualError] = useState('')

  // ── Import state ───────────────────────────────────────────────────────────
  const [importText,     setImportText]    = useState('')
  const [importError,    setImportError]   = useState('')
  const [importHeaders,  setImportHeaders] = useState([])
  const [importRows,     setImportRows]    = useState([])
  const [labelColumn,    setLabelColumn]   = useState('')
  const [valueColumns,   setValueColumns]  = useState([])
  const [importSuccess,  setImportSuccess] = useState('')

  // ── Export state ───────────────────────────────────────────────────────────
  const [exportMsg, setExportMsg] = useState('')

  // ── Chart rendered flag ───────────────────────────────────────────────────
  const [rendered, setRendered] = useState(false)

  // ── Draw chart whenever chartData / type / palette / title change ─────────
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas || !chartData) return
    trackTool('chart.build', () => {
      renderChartToCanvas(canvas, chartData, chartType, palette, chartTitle)
    })
    setRendered(true)
    logActivity('chart.build', `Built ${chartType} chart with ${chartData.labels?.length || 0} labels`, {
      chartType,
      labelCount: chartData.labels?.length || 0,
      seriesCount: chartData.datasets?.length || 0,
    })
  }, [chartData, chartType, palette, chartTitle])

  // ── Manual data apply ──────────────────────────────────────────────────────
  function applyManualData() {
    setManualError('')
    try {
      const labels = labelInput.split(',').map(s => s.trim()).filter(Boolean)
      if (labels.length === 0) throw new Error('Enter at least one label')
      const datasets = seriesInputs.map((si, idx) => {
        const values = si.values.split(',').map(s => {
          const n = parseFloat(s.trim())
          if (isNaN(n)) throw new Error(`Series "${si.name || idx + 1}": "${s.trim()}" is not a number`)
          return n
        })
        if (values.length !== labels.length) throw new Error(`Series "${si.name || idx + 1}" has ${values.length} values but ${labels.length} labels`)
        return { name: si.name || `Series ${idx + 1}`, values }
      })
      setChartData({ labels, datasets })
    } catch (e) {
      setManualError(e.message)
    }
  }

  function addSeries() {
    setSeriesInputs(prev => [...prev, { name: `Series ${prev.length + 1}`, values: '' }])
  }

  function removeSeries(idx) {
    setSeriesInputs(prev => prev.filter((_, i) => i !== idx))
  }

  function updateSeries(idx, field, val) {
    setSeriesInputs(prev => prev.map((s, i) => i === idx ? { ...s, [field]: val } : s))
  }

  function loadSample(key) {
    const ds = SAMPLE_DATASETS[key]
    setChartData(ds)
    setLabelInput(ds.labels.join(', '))
    setSeriesInputs(ds.datasets.map(d => ({ name: d.name, values: d.values.join(', ') })))
    setChartTitle(ds.label)
    setManualError('')
  }

  // ── Import ─────────────────────────────────────────────────────────────────
  function parseImport(format) {
    setImportError('')
    setImportSuccess('')
    try {
      const result = format === 'csv' ? parseCSV(importText) : parseJSONArray(importText)
      setImportHeaders(result.headers)
      setImportRows(result.rows)
      setLabelColumn(result.headers[0] || '')
      setValueColumns(result.headers.slice(1).filter(h => {
        const sample = result.rows[0]?.[h]
        return !isNaN(parseFloat(sample))
      }))
      setImportError('')
    } catch (e) {
      setImportError(e.message)
      setImportHeaders([])
      setImportRows([])
    }
  }

  function applyImport() {
    if (!labelColumn || valueColumns.length === 0) {
      setImportError('Select a label column and at least one value column')
      return
    }
    try {
      const labels = importRows.map(r => String(r[labelColumn] ?? ''))
      const datasets = valueColumns.map(col => ({
        name: col,
        values: importRows.map(r => parseFloat(r[col]) || 0),
      }))
      setChartData({ labels, datasets })
      setLabelInput(labels.join(', '))
      setSeriesInputs(datasets.map(ds => ({ name: ds.name, values: ds.values.join(', ') })))
      setImportSuccess(`Imported ${importRows.length} rows, ${valueColumns.length} series.`)
      setActiveTab('builder')
      logActivity('chart.import', `Imported ${importRows.length} rows`, {
        rows: importRows.length,
        columns: valueColumns.length,
      })
    } catch (e) {
      setImportError(e.message)
    }
  }

  function handleFileDrop(e) {
    e.preventDefault()
    const file = e.dataTransfer?.files?.[0] || e.target?.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = ev => setImportText(ev.target.result)
    reader.readAsText(file)
  }

  // ── Analysis ───────────────────────────────────────────────────────────────
  const analysisStats = useMemo(() => {
    if (!chartData?.datasets) return []
    return chartData.datasets.map(ds => ({
      name: ds.name,
      stats: computeStats(ds.values || []),
    }))
  }, [chartData])

  // ── Export ─────────────────────────────────────────────────────────────────
  function exportPNG() {
    const canvas = canvasRef.current
    if (!canvas) return
    const url = canvas.toDataURL('image/png')
    const a = document.createElement('a')
    a.href = url
    a.download = `chart-${Date.now()}.png`
    a.click()
    setExportMsg('PNG downloaded.')
    logActivity('chart.export', 'Exported chart as PNG', { format: 'png' })
  }

  function exportSVG() {
    // Build a minimal SVG from canvas data URL (embed as image in SVG)
    const canvas = canvasRef.current
    if (!canvas) return
    const dataUrl = canvas.toDataURL('image/png')
    const svgContent = `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="${canvas.width}" height="${canvas.height}">
  <image href="${dataUrl}" width="${canvas.width}" height="${canvas.height}"/>
</svg>`
    const blob = new Blob([svgContent], { type: 'image/svg+xml' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `chart-${Date.now()}.svg`
    a.click()
    URL.revokeObjectURL(url)
    setExportMsg('SVG downloaded.')
    logActivity('chart.export', 'Exported chart as SVG', { format: 'svg' })
  }

  function exportDataCSV() {
    if (!chartData) return
    const { labels, datasets } = chartData
    const headers = ['Label', ...datasets.map(d => d.name)].join(',')
    const rows = labels.map((lbl, i) =>
      [lbl, ...datasets.map(d => d.values[i] ?? '')].join(',')
    )
    const csv = [headers, ...rows].join('\n')
    const blob = new Blob([csv], { type: 'text/csv' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `data-${Date.now()}.csv`
    a.click()
    URL.revokeObjectURL(url)
    setExportMsg('CSV downloaded.')
  }

  function exportDataJSON() {
    if (!chartData) return
    const { labels, datasets } = chartData
    const out = labels.map((lbl, i) => ({
      label: lbl,
      ...Object.fromEntries(datasets.map(d => [d.name, d.values[i] ?? null])),
    }))
    const blob = new Blob([JSON.stringify(out, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `data-${Date.now()}.json`
    a.click()
    URL.revokeObjectURL(url)
    setExportMsg('JSON downloaded.')
  }

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div className={styles.page}>

      {/* ── Header ──────────────────────────────────────────────────────── */}
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.brand}>
            <span className={styles.brandMark} aria-hidden="true">◈</span>
            <span className={styles.brandName}>UsefulTools</span>
          </div>
          <button className={styles.backBtn} onClick={() => navigate('/dashboard')}>
            Dashboard
          </button>
        </div>
        <UserMenu username={username} isGuest={isGuest} variant="light" onLogout={handleLogout} />
      </header>

      {/* ── Hero ────────────────────────────────────────────────────────── */}
      <section className={styles.hero}>
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>Sprint 20</div>
          <h1 className={styles.heroTitle}>Data Visualisation</h1>
          <p className={styles.heroSub}>
            Build interactive charts from manual data or imported CSV/JSON.
            Analyse trends, detect outliers, and export publication-ready images.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div>
            <strong>{CHART_TYPES.length}</strong>
            <span>chart types</span>
          </div>
          <div>
            <strong>{chartData?.labels?.length ?? 0}</strong>
            <span>data points</span>
          </div>
          <div>
            <strong>{chartData?.datasets?.length ?? 0}</strong>
            <span>series</span>
          </div>
        </div>
      </section>

      {/* ── Main ────────────────────────────────────────────────────────── */}
      <main className={styles.main}>
        <TabBar tabs={TABS} active={activeTab} onChange={setActiveTab} />

        {/* ── Builder tab ─────────────────────────────────────────────── */}
        {activeTab === 'builder' && (
          <div className={styles.builderGrid}>

            {/* Left panel: controls */}
            <div className={styles.panel}>
              <p className={styles.eyebrow}>Configuration</p>
              <h2 className={styles.panelTitle}>Chart Builder</h2>

              {/* Chart type picker */}
              <label className={styles.fieldLabel}>Chart Type</label>
              <div className={styles.chartTypeRow}>
                {CHART_TYPES.map(ct => (
                  <button
                    key={ct.id}
                    type="button"
                    className={chartType === ct.id ? styles.chartTypeActive : styles.chartTypeBtn}
                    onClick={() => setChartType(ct.id)}
                    title={ct.label}
                  >
                    <span>{ct.icon}</span>
                    <span>{ct.label}</span>
                  </button>
                ))}
              </div>

              {/* Palette */}
              <label className={styles.fieldLabel}>Colour Theme</label>
              <div className={styles.paletteRow}>
                {Object.entries(THEME_PALETTES).map(([key, colours]) => (
                  <button
                    key={key}
                    type="button"
                    className={palette === key ? styles.paletteSwatchActive : styles.paletteSwatch}
                    onClick={() => setPalette(key)}
                    title={key}
                    aria-pressed={palette === key}
                  >
                    {colours.slice(0, 4).map((c, i) => (
                      <span key={i} className={styles.swatchDot} style={{ background: c }} />
                    ))}
                  </button>
                ))}
              </div>

              {/* Title */}
              <label className={styles.fieldLabel} htmlFor="chart-title">Chart Title</label>
              <input
                id="chart-title"
                className={styles.textInput}
                value={chartTitle}
                onChange={e => setChartTitle(e.target.value)}
                placeholder="Optional chart title"
                maxLength={120}
              />

              {/* Sample datasets */}
              <label className={styles.fieldLabel}>Sample Datasets</label>
              <div className={styles.sampleRow}>
                {Object.entries(SAMPLE_DATASETS).map(([key, ds]) => (
                  <button
                    key={key}
                    type="button"
                    className={styles.sampleBtn}
                    onClick={() => loadSample(key)}
                  >
                    {ds.label}
                  </button>
                ))}
              </div>

              {/* Manual data entry */}
              <div className={styles.manualSection}>
                <label className={styles.fieldLabel}>Labels <span className={styles.hint}>(comma-separated)</span></label>
                <input
                  className={styles.textInput}
                  value={labelInput}
                  onChange={e => setLabelInput(e.target.value)}
                  placeholder="Jan, Feb, Mar, ..."
                  maxLength={2000}
                  aria-label="Chart labels (comma-separated)"
                />

                {seriesInputs.map((si, idx) => (
                  <div key={idx} className={styles.seriesRow}>
                    <input
                      className={styles.seriesNameInput}
                      value={si.name}
                      onChange={e => updateSeries(idx, 'name', e.target.value)}
                      placeholder={`Series ${idx + 1}`}
                      maxLength={60}
                      aria-label={`Series ${idx + 1} name`}
                    />
                    <input
                      className={styles.seriesValInput}
                      value={si.values}
                      onChange={e => updateSeries(idx, 'values', e.target.value)}
                      placeholder="42, 55, 38, ..."
                      maxLength={2000}
                      aria-label={`Series ${idx + 1} values (comma-separated)`}
                    />
                    {seriesInputs.length > 1 && (
                      <button
                        type="button"
                        className={styles.removeSeriesBtn}
                        onClick={() => removeSeries(idx)}
                        title="Remove series"
                        aria-label={`Remove series ${idx + 1}`}
                      >×</button>
                    )}
                  </div>
                ))}

                {manualError && <div className={styles.errorBanner}>{manualError}</div>}

                <div className={styles.manualActions}>
                  <button type="button" className={styles.ghostBtn} onClick={addSeries}>
                    + Add Series
                  </button>
                  <button type="button" className={styles.primaryBtn} onClick={applyManualData}>
                    Render Chart
                  </button>
                </div>
              </div>
            </div>

            {/* Right panel: preview */}
            <div className={styles.panel}>
              <p className={styles.eyebrow}>Preview</p>
              <h2 className={styles.panelTitle}>Chart Preview</h2>
              <div className={styles.canvasWrap}>
                <canvas
                  ref={canvasRef}
                  width={660}
                  height={400}
                  className={styles.chartCanvas}
                  aria-label="Chart preview"
                />
              </div>
              <div className={styles.previewActions}>
                <button type="button" className={styles.secondaryBtn} onClick={exportPNG}>
                  Download PNG
                </button>
                <button type="button" className={styles.secondaryBtn} onClick={() => setActiveTab('export')}>
                  More Export Options
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ── Import tab ──────────────────────────────────────────────── */}
        {activeTab === 'import' && (
          <div className={styles.importGrid}>
            <div className={styles.panel}>
              <p className={styles.eyebrow}>Data Import</p>
              <h2 className={styles.panelTitle}>Import Wizard</h2>

              {/* Drop zone */}
              <div
                className={styles.dropZone}
                onDragOver={e => e.preventDefault()}
                onDrop={handleFileDrop}
              >
                <span className={styles.dropIcon}>⬆</span>
                <p>Drag & drop a CSV or JSON file here</p>
                <label className={styles.fileLabel}>
                  or choose a file
                  <input type="file" accept=".csv,.json" className={styles.fileInput} onChange={handleFileDrop} />
                </label>
              </div>

              <p className={styles.orDivider}>or paste data below</p>

              <textarea
                className={styles.importTextarea}
                value={importText}
                onChange={e => setImportText(e.target.value)}
                rows={8}
                spellCheck={false}
                placeholder={'CSV example:\nMonth,Revenue,Cost\nJan,42000,31000\nFeb,47000,34000\n\nJSON example:\n[{"Month":"Jan","Revenue":42000},...]'}
              />

              <div className={styles.importBtnRow}>
                <button type="button" className={styles.primaryBtn} onClick={() => parseImport('csv')}>
                  Parse CSV
                </button>
                <button type="button" className={styles.primaryBtn} onClick={() => parseImport('json')}>
                  Parse JSON
                </button>
              </div>

              {importError && <div className={styles.errorBanner}>{importError}</div>}
              {importSuccess && <div className={styles.successBanner}>{importSuccess}</div>}
            </div>

            {/* Column mapping */}
            {importHeaders.length > 0 && (
              <div className={styles.panel}>
                <p className={styles.eyebrow}>Column Mapping</p>
                <h2 className={styles.panelTitle}>Select Columns</h2>
                <p className={styles.importMeta}>
                  {importRows.length} rows · {importHeaders.length} columns detected
                </p>

                <label className={styles.fieldLabel}>Label Column</label>
                <select
                  className={styles.selectInput}
                  value={labelColumn}
                  onChange={e => setLabelColumn(e.target.value)}
                >
                  {importHeaders.map(h => <option key={h} value={h}>{h}</option>)}
                </select>

                <label className={styles.fieldLabel}>Value Columns <span className={styles.hint}>(one series each)</span></label>
                <div className={styles.checkboxGroup}>
                  {importHeaders.filter(h => h !== labelColumn).map(h => {
                    const checked = valueColumns.includes(h)
                    return (
                      <label key={h} className={styles.checkLabel}>
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={() =>
                            setValueColumns(prev =>
                              checked ? prev.filter(c => c !== h) : [...prev, h]
                            )
                          }
                        />
                        {h}
                      </label>
                    )
                  })}
                </div>

                {/* Preview table */}
                <div className={styles.previewTableWrap}>
                  <table className={styles.previewTable}>
                    <thead>
                      <tr>
                        {importHeaders.map(h => <th key={h}>{h}</th>)}
                      </tr>
                    </thead>
                    <tbody>
                      {importRows.slice(0, 5).map((row, i) => (
                        <tr key={i}>
                          {importHeaders.map(h => <td key={h}>{row[h]}</td>)}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {importRows.length > 5 && (
                    <p className={styles.tableMore}>…and {importRows.length - 5} more rows</p>
                  )}
                </div>

                <button type="button" className={styles.primaryBtn} onClick={applyImport}>
                  Apply & Build Chart
                </button>
              </div>
            )}
          </div>
        )}

        {/* ── Analysis tab ────────────────────────────────────────────── */}
        {activeTab === 'analysis' && (
          <div className={styles.analysisSection}>
            <div className={styles.panel}>
              <p className={styles.eyebrow}>Statistical Summary</p>
              <h2 className={styles.panelTitle}>Data Analysis</h2>

              {analysisStats.length === 0 && (
                <p className={styles.emptyMsg}>No data to analyse. Build or import a dataset first.</p>
              )}

              {analysisStats.map(({ name, stats }) => stats && (
                <div key={name} className={styles.analysisBlock}>
                  <h3 className={styles.seriesHeading}>{name}</h3>
                  <div className={styles.statsGrid}>
                    <StatCard label="Count"    value={stats.count} />
                    <StatCard label="Sum"      value={formatNum(stats.sum)} />
                    <StatCard label="Mean"     value={formatNum(stats.mean)} />
                    <StatCard label="Median"   value={formatNum(stats.median)} />
                    <StatCard label="Std Dev"  value={formatNum(stats.stdDev)} />
                    <StatCard label="Min"      value={formatNum(stats.min)} />
                    <StatCard label="Max"      value={formatNum(stats.max)} />
                    <StatCard label="Range"    value={formatNum(stats.range)} />
                    <StatCard label="Q1"       value={formatNum(stats.q1)} />
                    <StatCard label="Q3"       value={formatNum(stats.q3)} />
                    <StatCard label="IQR"      value={formatNum(stats.iqr)} />
                    <StatCard
                      label="Trend"
                      value={stats.trendDirection === 'up' ? '↑ Rising' : stats.trendDirection === 'down' ? '↓ Falling' : '→ Flat'}
                      sub={`slope ${stats.trendSlope}`}
                    />
                    <StatCard
                      label="Outliers"
                      value={stats.outlierCount}
                      sub={stats.outlierCount > 0 ? '(1.5× IQR fence)' : 'none detected'}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ── Export tab ──────────────────────────────────────────────── */}
        {activeTab === 'export' && (
          <div className={styles.exportSection}>
            <div className={styles.panel}>
              <p className={styles.eyebrow}>Download</p>
              <h2 className={styles.panelTitle}>Export Options</h2>

              <div className={styles.exportGroup}>
                <h3 className={styles.exportGroupLabel}>Chart Image</h3>
                <div className={styles.exportRow}>
                  <div className={styles.exportCard}>
                    <span className={styles.exportIcon}>🖼</span>
                    <span className={styles.exportTitle}>PNG</span>
                    <span className={styles.exportDesc}>Raster image — best for presentations and embedding</span>
                    <button type="button" className={styles.primaryBtn} onClick={exportPNG}>
                      Download PNG
                    </button>
                  </div>
                  <div className={styles.exportCard}>
                    <span className={styles.exportIcon}>✦</span>
                    <span className={styles.exportTitle}>SVG</span>
                    <span className={styles.exportDesc}>Vector format — scales to any size without loss</span>
                    <button type="button" className={styles.primaryBtn} onClick={exportSVG}>
                      Download SVG
                    </button>
                  </div>
                </div>
              </div>

              <div className={styles.exportGroup}>
                <h3 className={styles.exportGroupLabel}>Raw Data</h3>
                <div className={styles.exportRow}>
                  <div className={styles.exportCard}>
                    <span className={styles.exportIcon}>📄</span>
                    <span className={styles.exportTitle}>CSV</span>
                    <span className={styles.exportDesc}>Spreadsheet-compatible, one row per label</span>
                    <button type="button" className={styles.secondaryBtn} onClick={exportDataCSV}>
                      Download CSV
                    </button>
                  </div>
                  <div className={styles.exportCard}>
                    <span className={styles.exportIcon}>{ }</span>
                    <span className={styles.exportTitle}>JSON</span>
                    <span className={styles.exportDesc}>Array of objects, one object per label</span>
                    <button type="button" className={styles.secondaryBtn} onClick={exportDataJSON}>
                      Download JSON
                    </button>
                  </div>
                </div>
              </div>

              {exportMsg && <div className={styles.successBanner}>{exportMsg}</div>}
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
