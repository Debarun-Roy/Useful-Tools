/**
 * BulkConverterPage.jsx — Sprint 23
 *
 * Bulk file conversion suite. All processing is client-side (backend frozen).
 *
 * Tabs
 *   1. Data     — CSV ↔ JSON ↔ XML conversion with preview
 *   2. Image    — Batch image format conversion (PNG/JPEG/WebP) via Canvas API
 *   3. Download — Staged output files ready to download
 *
 * Activity logging : 'bulk.data', 'bulk.image'
 * Metrics          : trackTool wraps each conversion batch
 *
 * Privacy rule: log only file counts, sizes, and format names — never
 * log file contents or filenames.
 */

import { useState, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logoutUser } from '../../api/apiClient'
import UserMenu from '../../components/UserMenu/UserMenu'
import { logActivity } from '../../utils/logActivity'
import { trackTool } from '../../utils/logMetric'
import styles from './BulkConverterPage.module.css'

// ─── Data conversion helpers ──────────────────────────────────────────────────

function parseCSV(text) {
  const lines = text.trim().split(/\r?\n/)
  if (lines.length < 1) throw new Error('Empty CSV')
  const headers = splitCSVLine(lines[0])
  const rows = lines.slice(1).filter(l => l.trim()).map(line => {
    const cols = splitCSVLine(line)
    return Object.fromEntries(headers.map((h, i) => [h, cols[i] ?? '']))
  })
  return { headers, rows }
}

function splitCSVLine(line) {
  const result = []
  let cur = '', inQuote = false
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]
    if (ch === '"') {
      if (inQuote && line[i+1] === '"') { cur += '"'; i++ }
      else inQuote = !inQuote
    } else if (ch === ',' && !inQuote) {
      result.push(cur); cur = ''
    } else {
      cur += ch
    }
  }
  result.push(cur)
  return result.map(s => s.trim())
}

function rowsToCSV(rows, headers) {
  const escape = v => {
    const s = String(v ?? '')
    return s.includes(',') || s.includes('"') || s.includes('\n')
      ? `"${s.replace(/"/g, '""')}"` : s
  }
  const head = headers.map(escape).join(',')
  const body = rows.map(r => headers.map(h => escape(r[h] ?? '')).join(',')).join('\n')
  return head + '\n' + body
}

function rowsToJSON(rows) {
  return JSON.stringify(rows, null, 2)
}

function rowsToXML(rows, headers, rootTag = 'data', rowTag = 'row') {
  const escXml = v => String(v ?? '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&apos;')
  const items = rows.map(r => {
    const fields = headers.map(h => `    <${h}>${escXml(r[h])}</${h}>`).join('\n')
    return `  <${rowTag}>\n${fields}\n  </${rowTag}>`
  }).join('\n')
  return `<?xml version="1.0" encoding="UTF-8"?>\n<${rootTag}>\n${items}\n</${rootTag}>`
}

function parseJSON(text) {
  const parsed = JSON.parse(text)
  if (!Array.isArray(parsed)) throw new Error('JSON must be an array of objects')
  if (parsed.length === 0) throw new Error('JSON array is empty')
  const headers = [...new Set(parsed.flatMap(obj => Object.keys(obj)))]
  return { headers, rows: parsed }
}

function parseXML(text) {
  const parser = new DOMParser()
  const doc = parser.parseFromString(text, 'text/xml')
  const errEl = doc.querySelector('parsererror')
  if (errEl) throw new Error('Invalid XML: ' + errEl.textContent.slice(0, 80))
  const items = [...doc.documentElement.children]
  if (items.length === 0) throw new Error('No child elements found in XML root')
  const headers = [...new Set(items.flatMap(el => [...el.children].map(c => c.tagName)))]
  const rows = items.map(el => {
    const obj = {}
    for (const child of el.children) obj[child.tagName] = child.textContent
    return obj
  })
  return { headers, rows }
}

function detectFormat(text) {
  const trimmed = text.trim()
  if (trimmed.startsWith('[') || trimmed.startsWith('{')) return 'json'
  if (trimmed.startsWith('<?xml') || trimmed.startsWith('<')) return 'xml'
  return 'csv'
}

// ─── Image conversion helpers ─────────────────────────────────────────────────

function convertImageToFormat(file, format, quality) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = ev => {
      const img = new Image()
      img.onload = () => {
        const canvas = document.createElement('canvas')
        canvas.width  = img.naturalWidth
        canvas.height = img.naturalHeight
        const ctx = canvas.getContext('2d')
        // For JPEG, fill transparent areas with white
        if (format === 'image/jpeg') {
          ctx.fillStyle = '#ffffff'
          ctx.fillRect(0, 0, canvas.width, canvas.height)
        }
        ctx.drawImage(img, 0, 0)
        canvas.toBlob(blob => {
          if (!blob) { reject(new Error(`Conversion to ${format} failed`)); return }
          resolve({ blob, width: img.naturalWidth, height: img.naturalHeight })
        }, format, quality / 100)
      }
      img.onerror = () => reject(new Error(`Cannot decode image: ${file.name}`))
      img.src = ev.target.result
    }
    reader.onerror = () => reject(new Error(`Cannot read file: ${file.name}`))
    reader.readAsDataURL(file)
  })
}

const FORMAT_EXT = { 'image/png': 'png', 'image/jpeg': 'jpg', 'image/webp': 'webp' }

// ─── Tabs ─────────────────────────────────────────────────────────────────────

const TABS = [
  { id: 'data',  label: 'Data Converter', icon: '⇄' },
  { id: 'image', label: 'Image Converter', icon: '🖼' },
]

// ─── Shared sub-components ────────────────────────────────────────────────────

function TabBar({ tabs, active, onChange }) {
  return (
    <div className={styles.tabBar} role="tablist">
      {tabs.map(t => (
        <button key={t.id} role="tab" aria-selected={active === t.id}
          className={active === t.id ? styles.tabActive : styles.tab}
          onClick={() => onChange(t.id)}>
          <span className={styles.tabIcon}>{t.icon}</span>
          {t.label}
        </button>
      ))}
    </div>
  )
}

function DropZone({ onFiles, accept, multiple = true, children }) {
  const [dragging, setDragging] = useState(false)
  const inputRef = useRef(null)

  function handleDrop(e) {
    e.preventDefault()
    setDragging(false)
    const files = [...(e.dataTransfer?.files || [])]
    if (files.length > 0) onFiles(files)
  }

  function handleChange(e) {
    const files = [...(e.target.files || [])]
    if (files.length > 0) onFiles(files)
    e.target.value = ''
  }

  return (
    <div
      className={`${styles.dropZone} ${dragging ? styles.dropZoneDragging : ''}`}
      onDragOver={e => { e.preventDefault(); setDragging(true) }}
      onDragLeave={() => setDragging(false)}
      onDrop={handleDrop}
      onClick={() => inputRef.current?.click()}
      role="button"
      tabIndex={0}
      onKeyDown={e => e.key === 'Enter' && inputRef.current?.click()}
      aria-label="Drop files here or click to browse"
    >
      <input
        ref={inputRef} type="file" accept={accept}
        multiple={multiple} className={styles.fileInput}
        onChange={handleChange}
      />
      {children}
    </div>
  )
}

// ─── Data Converter Tab ────────────────────────────────────────────────────────

function DataConverterTab() {
  const [inputText,   setInputText]   = useState('')
  const [inputFormat, setInputFormat] = useState('csv')
  const [outputFormat,setOutputFormat]= useState('json')
  const [rootTag,     setRootTag]     = useState('data')
  const [rowTag,      setRowTag]      = useState('row')
  const [output,      setOutput]      = useState('')
  const [preview,     setPreview]     = useState(null)   // { headers, rows }
  const [error,       setError]       = useState('')
  const [converting,  setConverting]  = useState(false)
  const [copiedOut,   setCopiedOut]   = useState(false)

  function handleDetect() {
    if (inputText.trim()) setInputFormat(detectFormat(inputText))
  }

  function handleConvert() {
    setError('')
    setOutput('')
    setPreview(null)
    setConverting(true)
    try {
      const result = trackTool('bulk.data', () => {
        let parsed
        if (inputFormat === 'csv')  parsed = parseCSV(inputText)
        else if (inputFormat === 'json') parsed = parseJSON(inputText)
        else if (inputFormat === 'xml')  parsed = parseXML(inputText)
        else throw new Error('Unknown input format')

        let out
        if (outputFormat === 'csv')  out = rowsToCSV(parsed.rows, parsed.headers)
        else if (outputFormat === 'json') out = rowsToJSON(parsed.rows)
        else if (outputFormat === 'xml')  out = rowsToXML(parsed.rows, parsed.headers, rootTag, rowTag)
        else throw new Error('Unknown output format')

        return { parsed, out }
      })

      setOutput(result.out)
      setPreview(result.parsed)
      logActivity('bulk.data', 'Converted data format', {
        from: inputFormat,
        to: outputFormat,
        rows: result.parsed.rows.length,
        cols: result.parsed.headers.length,
      })
    } catch (e) {
      setError(e.message)
    } finally {
      setConverting(false)
    }
  }

  function handleFileUpload(files) {
    const file = files[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = ev => {
      const text = ev.target.result
      setInputText(text)
      setInputFormat(detectFormat(text))
      setError('')
      setOutput('')
    }
    reader.readAsText(file)
  }

  function handleDownload() {
    if (!output) return
    const ext = outputFormat === 'json' ? 'json' : outputFormat === 'xml' ? 'xml' : 'csv'
    const mime = outputFormat === 'json' ? 'application/json'
               : outputFormat === 'xml'  ? 'application/xml'
               : 'text/csv'
    const blob = new Blob([output], { type: mime })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `converted-${Date.now()}.${ext}`
    a.click()
    URL.revokeObjectURL(url)
  }

  function handleCopyOutput() {
    navigator.clipboard.writeText(output).then(() => {
      setCopiedOut(true); setTimeout(() => setCopiedOut(false), 1400)
    })
  }

  const FORMATS = ['csv', 'json', 'xml']

  return (
    <div className={styles.dataLayout}>
      {/* Left: input */}
      <div className={styles.panel}>
        <p className={styles.eyebrow}>Input</p>
        <h2 className={styles.panelTitle}>Source Data</h2>

        <DropZone
          accept=".csv,.json,.xml,.txt"
          multiple={false}
          onFiles={handleFileUpload}
        >
          <span className={styles.dropIcon}>📂</span>
          <p className={styles.dropText}>Drop a CSV, JSON, or XML file here</p>
          <p className={styles.dropHint}>or click to browse</p>
        </DropZone>

        <p className={styles.orDivider}>or paste below</p>

        <textarea
          className={styles.dataTextarea}
          value={inputText}
          onChange={e => setInputText(e.target.value)}
          spellCheck={false}
          rows={10}
          placeholder={'CSV:\nname,age\nAlice,30\n\nJSON:\n[{"name":"Alice"}]\n\nXML:\n<data><row><name>Alice</name></row></data>'}
        />

        <div className={styles.formatRow}>
          <div>
            <label className={styles.fieldLabel}>Input Format</label>
            <div className={styles.chipRow}>
              {FORMATS.map(f => (
                <button key={f} type="button"
                  className={inputFormat === f ? styles.chipActive : styles.chip}
                  onClick={() => setInputFormat(f)}>
                  {f.toUpperCase()}
                </button>
              ))}
            </div>
          </div>
          <button type="button" className={styles.ghostBtn} onClick={handleDetect}>
            Auto-detect
          </button>
        </div>

        <label className={styles.fieldLabel}>Output Format</label>
        <div className={styles.chipRow}>
          {FORMATS.filter(f => f !== inputFormat).map(f => (
            <button key={f} type="button"
              className={outputFormat === f ? styles.chipActive : styles.chip}
              onClick={() => setOutputFormat(f)}>
              {f.toUpperCase()}
            </button>
          ))}
        </div>

        {outputFormat === 'xml' && (
          <div className={styles.xmlTagRow}>
            <div>
              <label className={styles.fieldLabel}>Root tag</label>
              <input className={styles.textInput} value={rootTag}
                onChange={e => setRootTag(e.target.value)} placeholder="data"
                maxLength={64} aria-label="XML root tag name" />
            </div>
            <div>
              <label className={styles.fieldLabel}>Row tag</label>
              <input className={styles.textInput} value={rowTag}
                onChange={e => setRowTag(e.target.value)} placeholder="row"
                maxLength={64} aria-label="XML row tag name" />
            </div>
          </div>
        )}

        {error && <div className={styles.errorBanner}>{error}</div>}

        <button
          type="button"
          className={styles.primaryBtn}
          onClick={handleConvert}
          disabled={!inputText.trim() || converting}
        >
          {converting ? 'Converting…' : `Convert to ${outputFormat.toUpperCase()}`}
        </button>
      </div>

      {/* Right: output */}
      <div className={styles.outputCol}>
        {preview && (
          <div className={styles.panel}>
            <p className={styles.eyebrow}>Preview</p>
            <h2 className={styles.panelTitle}>{preview.rows.length} rows · {preview.headers.length} columns</h2>
            <div className={styles.previewTableWrap}>
              <table className={styles.previewTable}>
                <thead>
                  <tr>{preview.headers.map(h => <th key={h}>{h}</th>)}</tr>
                </thead>
                <tbody>
                  {preview.rows.slice(0, 8).map((r, i) => (
                    <tr key={i}>
                      {preview.headers.map(h => <td key={h}>{String(r[h] ?? '')}</td>)}
                    </tr>
                  ))}
                </tbody>
              </table>
              {preview.rows.length > 8 && (
                <p className={styles.moreRows}>…and {preview.rows.length - 8} more rows</p>
              )}
            </div>
          </div>
        )}

        <div className={styles.panel}>
          <div className={styles.panelHeader}>
            <div>
              <p className={styles.eyebrow}>Output</p>
              <h2 className={styles.panelTitle}>{outputFormat.toUpperCase()} Result</h2>
            </div>
            {output && (
              <div className={styles.headerActions}>
                <button type="button" className={styles.copyBtn} onClick={handleCopyOutput}>
                  {copiedOut ? '✓ Copied' : 'Copy'}
                </button>
                <button type="button" className={styles.secondaryBtn} onClick={handleDownload}>
                  Download
                </button>
              </div>
            )}
          </div>
          <textarea
            className={styles.dataTextarea}
            value={output}
            readOnly
            rows={14}
            spellCheck={false}
            placeholder="Converted output will appear here"
          />
          {output && (
            <p className={styles.outputMeta}>{output.length.toLocaleString()} characters</p>
          )}
        </div>
      </div>
    </div>
  )
}

// ─── Image Converter Tab ───────────────────────────────────────────────────────

function ImageConverterTab() {
  const [files,       setFiles]       = useState([])   // { file, name, previewUrl, status, error, resultBlob, resultName }
  const [targetFmt,   setTargetFmt]   = useState('image/png')
  const [quality,     setQuality]     = useState(90)
  const [converting,  setConverting]  = useState(false)
  const [allDone,     setAllDone]     = useState(false)

  function handleDrop(newFiles) {
    const imageFiles = newFiles.filter(f => f.type.startsWith('image/'))
    if (imageFiles.length === 0) return
    const entries = imageFiles.map(f => ({
      file: f,
      name: f.name,
      size: f.size,
      previewUrl: URL.createObjectURL(f),
      status: 'pending',
      error: null,
      resultBlob: null,
      resultName: null,
    }))
    setFiles(prev => [...prev, ...entries])
    setAllDone(false)
  }

  function removeFile(idx) {
    setFiles(prev => {
      const updated = [...prev]
      if (updated[idx].previewUrl) URL.revokeObjectURL(updated[idx].previewUrl)
      updated.splice(idx, 1)
      return updated
    })
  }

  function clearAll() {
    files.forEach(f => { if (f.previewUrl) URL.revokeObjectURL(f.previewUrl) })
    setFiles([])
    setAllDone(false)
  }

  async function handleConvertAll() {
    if (files.length === 0) return
    setConverting(true)
    setAllDone(false)

    const ext = FORMAT_EXT[targetFmt]
    const results = [...files]

    await trackTool('bulk.image', async () => {
      for (let i = 0; i < results.length; i++) {
        if (results[i].status === 'done') continue
        results[i] = { ...results[i], status: 'converting' }
        setFiles([...results])
        try {
          const { blob } = await convertImageToFormat(results[i].file, targetFmt, quality)
          const baseName = results[i].name.replace(/\.[^.]+$/, '')
          results[i] = {
            ...results[i],
            status: 'done',
            resultBlob: blob,
            resultName: `${baseName}.${ext}`,
          }
        } catch (e) {
          results[i] = { ...results[i], status: 'error', error: e.message }
        }
        setFiles([...results])
      }
    })

    const doneCount = results.filter(r => r.status === 'done').length
    logActivity('bulk.image', 'Converted image batch', {
      total: results.length,
      succeeded: doneCount,
      targetFormat: ext,
    })

    setConverting(false)
    setAllDone(true)
  }

  function downloadFile(entry) {
    if (!entry.resultBlob) return
    const url = URL.createObjectURL(entry.resultBlob)
    const a = document.createElement('a')
    a.href = url; a.download = entry.resultName; a.click()
    URL.revokeObjectURL(url)
  }

  function downloadAll() {
    files.filter(f => f.status === 'done').forEach(downloadFile)
  }

  const doneCount   = files.filter(f => f.status === 'done').length
  const errorCount  = files.filter(f => f.status === 'error').length
  const pendingCount = files.filter(f => f.status === 'pending').length

  return (
    <div className={styles.imageConverterLayout}>
      {/* Controls */}
      <div className={styles.panel}>
        <p className={styles.eyebrow}>Configuration</p>
        <h2 className={styles.panelTitle}>Image Converter</h2>

        <label className={styles.fieldLabel}>Target Format</label>
        <div className={styles.chipRow}>
          {[
            ['image/png',  'PNG'],
            ['image/jpeg', 'JPEG'],
            ['image/webp', 'WebP'],
          ].map(([v, l]) => (
            <button key={v} type="button"
              className={targetFmt === v ? styles.chipActive : styles.chip}
              onClick={() => setTargetFmt(v)}>
              {l}
            </button>
          ))}
        </div>

        {targetFmt !== 'image/png' && (
          <>
            <label className={styles.fieldLabel}>Quality: {quality}%</label>
            <input type="range" min={10} max={100} value={quality}
              className={styles.slider}
              onChange={e => setQuality(+e.target.value)} />
          </>
        )}

        <div className={styles.statsRow}>
          <span className={styles.statChip}>{files.length} files</span>
          {doneCount  > 0 && <span className={`${styles.statChip} ${styles.statDone}`}>{doneCount} done</span>}
          {errorCount > 0 && <span className={`${styles.statChip} ${styles.statError}`}>{errorCount} failed</span>}
        </div>

        <div className={styles.btnCol}>
          <button type="button" className={styles.primaryBtn}
            onClick={handleConvertAll}
            disabled={files.length === 0 || converting}>
            {converting ? 'Converting…' : `Convert All to ${FORMAT_EXT[targetFmt].toUpperCase()}`}
          </button>
          {doneCount > 0 && (
            <button type="button" className={styles.secondaryBtn} onClick={downloadAll}>
              Download All ({doneCount})
            </button>
          )}
          {files.length > 0 && (
            <button type="button" className={styles.ghostBtn} onClick={clearAll}
              disabled={converting}>
              Clear All
            </button>
          )}
        </div>
      </div>

      {/* File list + drop zone */}
      <div className={styles.imageRightCol}>
        <DropZone
          accept="image/*"
          multiple
          onFiles={handleDrop}
        >
          <span className={styles.dropIcon}>🖼</span>
          <p className={styles.dropText}>Drop images here (PNG, JPEG, GIF, WebP, BMP)</p>
          <p className={styles.dropHint}>or click to browse — multiple files supported</p>
        </DropZone>

        {files.length > 0 && (
          <div className={styles.fileGrid}>
            {files.map((entry, idx) => (
              <div key={idx} className={`${styles.fileCard} ${styles[`fileCard_${entry.status}`]}`}>
                <img
                  src={entry.previewUrl}
                  alt={entry.name}
                  className={styles.fileThumb}
                />
                <div className={styles.fileInfo}>
                  <span className={styles.fileName}>{entry.name}</span>
                  <span className={styles.fileSize}>{(entry.size / 1024).toFixed(1)} KB</span>
                  <span className={`${styles.fileStatus} ${styles[`status_${entry.status}`]}`}>
                    {entry.status === 'pending'    ? 'Pending'
                   : entry.status === 'converting' ? 'Converting…'
                   : entry.status === 'done'       ? '✓ Done'
                   : `✗ ${entry.error}`}
                  </span>
                </div>
                <div className={styles.fileActions}>
                  {entry.status === 'done' && (
                    <button type="button" className={styles.copyBtn}
                      onClick={() => downloadFile(entry)}>
                      ↓
                    </button>
                  )}
                  <button type="button" className={styles.removeBtn}
                    onClick={() => removeFile(idx)}
                    disabled={converting}
                    title="Remove file"
                    aria-label={`Remove ${entry.name}`}>
                    ×
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {allDone && doneCount > 0 && (
          <div className={styles.successBanner}>
            ✓ {doneCount} file{doneCount !== 1 ? 's' : ''} converted successfully.
            {errorCount > 0 && ` ${errorCount} failed.`}
          </div>
        )}
      </div>
    </div>
  )
}

// ─── Main component ───────────────────────────────────────────────────────────

export default function BulkConverterPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const isGuest = username === 'Guest User'
  const [activeTab, setActiveTab] = useState('data')

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.brand}>
            <span className={styles.brandMark} aria-hidden="true">⇄</span>
            <span className={styles.brandName}>UsefulTools</span>
          </div>
          <button className={styles.backBtn} onClick={() => navigate('/dashboard')}>Dashboard</button>
        </div>
        <UserMenu username={username} isGuest={isGuest} variant="light" onLogout={handleLogout} />
      </header>

      <section className={styles.hero}>
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>Sprint 23</div>
          <h1 className={styles.heroTitle}>Bulk Converter</h1>
          <p className={styles.heroSub}>
            Convert data files between CSV, JSON, and XML formats with live preview.
            Batch-convert images to PNG, JPEG, or WebP — all processed in your browser.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div><strong>3</strong><span>data formats</span></div>
          <div><strong>3</strong><span>image formats</span></div>
          <div><strong>∞</strong><span>files at once</span></div>
        </div>
      </section>

      <main className={styles.main}>
        <TabBar tabs={TABS} active={activeTab} onChange={setActiveTab} />
        {activeTab === 'data'  && <DataConverterTab />}
        {activeTab === 'image' && <ImageConverterTab />}
      </main>
    </div>
  )
}
