import { useState, useRef, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logoutUser } from '../../api/apiClient'
import UserMenu from '../../components/UserMenu/UserMenu'
import { logActivity } from '../../utils/logActivity'
import styles from './ImageToolsPage.module.css'
import { trackTool } from '../../utils/logMetric'

/*
 * ImageToolsPage
 * ──────────────
 * Pure client-side image utilities. Every tool uses the Canvas 2D API only —
 * no uploads, no server round-trips — so the user's image bytes never leave
 * the browser. Inputs are File objects or HTMLImageElements; outputs are
 * Blobs with object URLs for download.
 *
 * Tools:
 *   resize     Change width/height, optionally preserving aspect ratio.
 *   convert    Re-encode between PNG / JPG / WebP with quality slider.
 *   compress   Target-size JPEG/WebP compression by binary-searching quality.
 *   crop       Pick a rectangle using four numeric sliders.
 *   rotate     90° rotations and horizontal / vertical flip.
 *   filters    Grayscale / sepia / invert / brightness / contrast.
 *   info       Human-readable metadata (dimensions, size, MIME type).
 *   dataurl    Base64 data-URL export.
 *
 * ── Activity logging (Sprint 15, privacy-critical) ────────────────────────
 * The six TRANSFORMATION tools (resize, convert, compress, crop, rotate,
 * filters) call logActivity('image.process', …) on successful completion.
 * InfoTool and DataUrlTool are intentionally NOT logged:
 *   • InfoTool is a passive inspection — no transformation occurs.
 *   • DataUrlTool runs locally too, but logging "encoded file X to
 *     base64" could indirectly signal file content size or type via the
 *     payload, and there is no transformation result to surface on the
 *     Dashboard timeline. So we leave it silent.
 *
 * The CENTRAL rule for every call below is:
 *
 *     ✅  Log: operation name · output dimensions · format · quality ·
 *             filter intensities · rotation / flip flags
 *     ❌  NEVER log: filename · file bytes · data URL · base64 · source
 *             dimensions · crop x/y coordinates · any thumbnail / preview
 *
 * Crop coordinates (x, y) are excluded because they reveal which region
 * of the original image the user chose to keep — that is a form of
 * content-derived leakage even though the numbers alone look innocuous.
 * The output width and height are logged because they describe the result
 * the user produced, not the input content.
 */

const TABS = [
  { id: 'resize',   label: 'Resize',         icon: '⇔' },
  { id: 'convert',  label: 'Convert',        icon: '↔' },
  { id: 'compress', label: 'Compress',       icon: '📦' },
  { id: 'crop',     label: 'Crop',           icon: '✂' },
  { id: 'rotate',   label: 'Rotate / Flip',  icon: '⟳' },
  { id: 'filters',  label: 'Filters',        icon: '🎨' },
  { id: 'info',     label: 'Info',           icon: 'ℹ' },
  { id: 'dataurl',  label: 'Data URL',       icon: '{}' },
]

// ─── Shared helpers ─────────────────────────────────────────────────────────

function formatBytes(bytes) {
  if (!bytes || bytes < 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

/**
 * Maps a MIME type to the short label used in activity-log summaries.
 * Kept here alongside the other shared helpers because every transformation
 * tool uses it in its logActivity() call.
 */
function formatLabel(mime) {
  if (mime === 'image/png')  return 'PNG'
  if (mime === 'image/jpeg') return 'JPEG'
  if (mime === 'image/webp') return 'WebP'
  return mime || 'image'
}

/**
 * Load a File into an HTMLImageElement, resolving with { img, objectUrl }.
 * The object URL is preserved on the result so the caller can revoke it
 * when the file is replaced.
 */
function loadImage(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => resolve({ img, objectUrl: url })
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('The selected file could not be read as an image.'))
    }
    img.src = url
  })
}

/**
 * Draw an image onto a fresh canvas of the given size and return it.
 * Transform callback runs before drawImage so rotations/flips/crops compose.
 */
function canvasFromImage(img, width, height, transform) {
  const canvas = document.createElement('canvas')
  canvas.width  = Math.max(1, Math.round(width))
  canvas.height = Math.max(1, Math.round(height))
  const ctx = canvas.getContext('2d')
  if (transform) transform(ctx, canvas)
  else           ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
  return canvas
}

function canvasToBlob(canvas, mimeType, quality) {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      blob => blob ? resolve(blob) : reject(new Error('Canvas encoding failed.')),
      mimeType,
      quality,
    )
  })
}

function triggerDownload(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  // Defer revoke so the browser has time to start the download.
  setTimeout(() => URL.revokeObjectURL(url), 2000)
}

function baseName(filename) {
  if (!filename) return 'image'
  const i = filename.lastIndexOf('.')
  return i > 0 ? filename.slice(0, i) : filename
}

// ─── File picker ────────────────────────────────────────────────────────────
/**
 * A drop-or-click file input that calls onFile(file, objectUrl, img) when a
 * valid image is selected. Shows a small preview after loading.
 *
 * Each tool uses its own FilePicker instance (not a shared file across tabs):
 * tools are meant to be independent workflows, and keeping state local makes
 * resetting between uses trivial.
 */
function FilePicker({ onFile, label = 'Drop an image or click to browse', accept = 'image/*' }) {
  const inputRef = useRef(null)
  const [dragActive, setDragActive] = useState(false)
  const [error, setError] = useState('')

  async function handleFile(file) {
    setError('')
    if (!file) return
    if (!file.type.startsWith('image/')) {
      setError('Please select an image file (PNG, JPG, WebP, GIF, BMP, SVG).')
      return
    }
    try {
      const { img, objectUrl } = await loadImage(file)
      onFile(file, objectUrl, img)
    } catch (err) {
      setError(err.message)
    }
  }

  function onChange(e) {
    handleFile(e.target.files?.[0])
    // Reset so selecting the same file twice still fires onChange.
    e.target.value = ''
  }

  function onDrop(e) {
    e.preventDefault()
    setDragActive(false)
    handleFile(e.dataTransfer.files?.[0])
  }

  return (
    <div>
      <div
        className={dragActive ? styles.dropZoneActive : styles.dropZone}
        onClick={() => inputRef.current?.click()}
        onDragOver={e => { e.preventDefault(); setDragActive(true) }}
        onDragLeave={() => setDragActive(false)}
        onDrop={onDrop}
        role="button"
        tabIndex={0}
        onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') inputRef.current?.click() }}
      >
        <span className={styles.dropIcon} aria-hidden="true">🖼</span>
        <p className={styles.dropLabel}>{label}</p>
        <p className={styles.dropHint}>PNG · JPG · WebP · GIF · BMP up to ~50MB</p>
        <input
          ref={inputRef}
          type="file"
          accept={accept}
          onChange={onChange}
          className={styles.hiddenInput}
        />
      </div>
      {error && <div className={styles.errorBanner} role="alert">{error}</div>}
    </div>
  )
}

// ─── Tool: Resize ───────────────────────────────────────────────────────────

function ResizeTool() {
  const [source, setSource] = useState(null)  // { file, img, objectUrl }
  const [width,  setWidth]  = useState('')
  const [height, setHeight] = useState('')
  const [lockRatio, setLockRatio] = useState(true)
  const [format, setFormat] = useState('image/png')
  const [quality, setQuality] = useState(0.9)
  const [error,  setError]  = useState('')
  const [working, setWorking] = useState(false)

  function handleFile(file, objectUrl, img) {
    if (source?.objectUrl) URL.revokeObjectURL(source.objectUrl)
    setSource({ file, img, objectUrl })
    setWidth(String(img.naturalWidth))
    setHeight(String(img.naturalHeight))
    setError('')
  }

  function handleWidthChange(v) {
    setWidth(v)
    if (!lockRatio || !source) return
    const w = Number(v)
    if (Number.isFinite(w) && w > 0) {
      const ratio = source.img.naturalHeight / source.img.naturalWidth
      setHeight(String(Math.round(w * ratio)))
    }
  }

  function handleHeightChange(v) {
    setHeight(v)
    if (!lockRatio || !source) return
    const h = Number(v)
    if (Number.isFinite(h) && h > 0) {
      const ratio = source.img.naturalWidth / source.img.naturalHeight
      setWidth(String(Math.round(h * ratio)))
    }
  }

  async function handleResize() {
    if (!source) { setError('Please choose an image first.'); return }
    const w = Number(width), h = Number(height)
    if (!Number.isFinite(w) || !Number.isFinite(h) || w < 1 || h < 1) {
      setError('Width and height must be positive numbers.')
      return
    }
    // Sprint 18: track the discrete resize-and-download invocation.
    trackTool('image.process', () => null)
    if (w > 16384 || h > 16384) {
      setError('Dimensions are limited to 16384 × 16384 px.'); return
    }
    setWorking(true); setError('')
    try {
      const canvas = canvasFromImage(source.img, w, h)
      const blob = await canvasToBlob(canvas, format, format === 'image/png' ? undefined : quality)
      const ext = format === 'image/png' ? 'png' : format === 'image/jpeg' ? 'jpg' : 'webp'
      triggerDownload(blob, `${baseName(source.file.name)}-${w}x${h}.${ext}`)

      // ── Activity log ─────────────────────────────────────────────────────
      // Target dimensions + format (+ quality for lossy codecs). NEVER the
      // source filename and NEVER the image bytes.
      const qualityStr = format === 'image/png' ? '' : ` ${Math.round(quality * 100)}%`
      logActivity(
        'image.process',
        `Resized image to ${w}×${h} · ${formatLabel(format)}${qualityStr}`,
        {
          operation: 'resize',
          width:   w,
          height:  h,
          format,
          quality: format === 'image/png' ? null : quality,
        }
      )
    } catch (err) {
      setError(err.message || 'Resize failed.')
    } finally {
      setWorking(false)
    }
  }

  return (
    <div className={styles.toolPanel}>
      <FilePicker onFile={handleFile} />
      {source && (
        <>
          <div className={styles.preview}>
            <img src={source.objectUrl} alt="preview" />
            <div className={styles.previewMeta}>
              <span>Original: <strong>{source.img.naturalWidth} × {source.img.naturalHeight}</strong></span>
              <span>Size: <strong>{formatBytes(source.file.size)}</strong></span>
            </div>
          </div>
          <div className={styles.fieldGrid}>
            <div className={styles.field}>
              <label className={styles.fieldLabel}>Width (px)</label>
              <input
                className={styles.textInput}
                type="number" min={1} max={16384}
                value={width}
                onChange={e => handleWidthChange(e.target.value)}
              />
            </div>
            <div className={styles.field}>
              <label className={styles.fieldLabel}>Height (px)</label>
              <input
                className={styles.textInput}
                type="number" min={1} max={16384}
                value={height}
                onChange={e => handleHeightChange(e.target.value)}
              />
            </div>
          </div>
          <label className={styles.checkboxRow}>
            <input type="checkbox" checked={lockRatio} onChange={e => setLockRatio(e.target.checked)} />
            <span>Preserve aspect ratio</span>
          </label>
          <div className={styles.fieldGrid}>
            <div className={styles.field}>
              <label className={styles.fieldLabel}>Output format</label>
              <select className={styles.textInput} value={format} onChange={e => setFormat(e.target.value)}>
                <option value="image/png">PNG (lossless)</option>
                <option value="image/jpeg">JPEG</option>
                <option value="image/webp">WebP</option>
              </select>
            </div>
            {format !== 'image/png' && (
              <div className={styles.field}>
                <label className={styles.fieldLabel}>Quality ({Math.round(quality * 100)}%)</label>
                <input
                  type="range" min={0.1} max={1} step={0.05}
                  value={quality}
                  onChange={e => setQuality(Number(e.target.value))}
                />
              </div>
            )}
          </div>
          {error && <div className={styles.errorBanner} role="alert">{error}</div>}
          <button className={styles.primaryBtn} onClick={handleResize} disabled={working}>
            {working ? 'Resizing…' : '⬇ Resize & download'}
          </button>
        </>
      )}
    </div>
  )
}

// ─── Tool: Convert ──────────────────────────────────────────────────────────

function ConvertTool() {
  const [source, setSource] = useState(null)
  const [target, setTarget] = useState('image/jpeg')
  const [quality, setQuality] = useState(0.92)
  const [bg, setBg] = useState('#ffffff') // fill for PNG→JPG transparency
  const [error, setError] = useState('')
  const [working, setWorking] = useState(false)

  function handleFile(file, objectUrl, img) {
    if (source?.objectUrl) URL.revokeObjectURL(source.objectUrl)
    setSource({ file, img, objectUrl })
    setError('')
  }

  async function handleConvert() {
    if (!source) { setError('Please choose an image first.'); return }
    trackTool('image.process', () => null)  // Sprint 18: count invocation
    setWorking(true); setError('')
    try {
      const { img } = source
      const canvas = canvasFromImage(img, img.naturalWidth, img.naturalHeight, (ctx, c) => {
        // When converting to JPEG (which has no alpha), flatten onto a solid
        // background colour so transparent PNG areas do not become black.
        if (target === 'image/jpeg') {
          ctx.fillStyle = bg
          ctx.fillRect(0, 0, c.width, c.height)
        }
        ctx.drawImage(img, 0, 0)
      })
      const blob = await canvasToBlob(canvas, target, target === 'image/png' ? undefined : quality)
      const ext = target === 'image/png' ? 'png' : target === 'image/jpeg' ? 'jpg' : 'webp'
      triggerDownload(blob, `${baseName(source.file.name)}.${ext}`)

      // ── Activity log ─────────────────────────────────────────────────────
      // Target format + quality. The background colour is INTENTIONALLY not
      // logged — it doesn't affect interpretation of the summary, and while
      // it isn't strictly private it's a UI detail that doesn't belong on
      // the Dashboard timeline.
      const qualityStr = target === 'image/png' ? '' : ` ${Math.round(quality * 100)}%`
      logActivity(
        'image.process',
        `Converted image to ${formatLabel(target)}${qualityStr}`,
        {
          operation: 'convert',
          target,
          quality: target === 'image/png' ? null : quality,
        }
      )
    } catch (err) {
      setError(err.message || 'Conversion failed.')
    } finally {
      setWorking(false)
    }
  }

  return (
    <div className={styles.toolPanel}>
      <FilePicker onFile={handleFile} />
      {source && (
        <>
          <div className={styles.preview}>
            <img src={source.objectUrl} alt="preview" />
            <div className={styles.previewMeta}>
              <span>Source: <strong>{source.file.type || 'unknown'}</strong></span>
              <span>Size: <strong>{formatBytes(source.file.size)}</strong></span>
            </div>
          </div>
          <div className={styles.fieldGrid}>
            <div className={styles.field}>
              <label className={styles.fieldLabel}>Convert to</label>
              <select className={styles.textInput} value={target} onChange={e => setTarget(e.target.value)}>
                <option value="image/png">PNG</option>
                <option value="image/jpeg">JPEG</option>
                <option value="image/webp">WebP</option>
              </select>
            </div>
            {target !== 'image/png' && (
              <div className={styles.field}>
                <label className={styles.fieldLabel}>Quality ({Math.round(quality * 100)}%)</label>
                <input
                  type="range" min={0.1} max={1} step={0.02}
                  value={quality}
                  onChange={e => setQuality(Number(e.target.value))}
                />
              </div>
            )}
            {target === 'image/jpeg' && (
              <div className={styles.field}>
                <label className={styles.fieldLabel}>Background (JPEG has no alpha)</label>
                <input type="color" value={bg} onChange={e => setBg(e.target.value)} />
              </div>
            )}
          </div>
          {error && <div className={styles.errorBanner} role="alert">{error}</div>}
          <button className={styles.primaryBtn} onClick={handleConvert} disabled={working}>
            {working ? 'Converting…' : '⬇ Convert & download'}
          </button>
        </>
      )}
    </div>
  )
}

// ─── Tool: Compress ─────────────────────────────────────────────────────────

function CompressTool() {
  const [source, setSource] = useState(null)
  const [format, setFormat] = useState('image/jpeg')
  const [mode, setMode] = useState('quality')  // 'quality' | 'target-size'
  const [quality, setQuality] = useState(0.7)
  const [targetKB, setTargetKB] = useState(200)
  const [result, setResult] = useState(null)   // { blob, size, quality }
  const [error, setError] = useState('')
  const [working, setWorking] = useState(false)

  function handleFile(file, objectUrl, img) {
    if (source?.objectUrl) URL.revokeObjectURL(source.objectUrl)
    setSource({ file, img, objectUrl })
    setResult(null)
    setError('')
  }

  async function compressAtQuality(q) {
    const { img } = source
    const canvas = canvasFromImage(img, img.naturalWidth, img.naturalHeight)
    return canvasToBlob(canvas, format, q)
  }

  async function handleCompress() {
    if (!source) { setError('Please choose an image first.'); return }
    trackTool('image.process', () => null)  // Sprint 18: count invocation
    setWorking(true); setError(''); setResult(null)
    try {
      let blob, usedQuality
      if (mode === 'quality') {
        blob = await compressAtQuality(quality)
        usedQuality = quality
      } else {
        // Binary-search the quality slider to hit the target file size.
        // 12 iterations converges to within ~0.02% of the target.
        const targetBytes = Math.max(1024, targetKB * 1024)
        let lo = 0.05, hi = 1.0, best = null, bestQ = 1.0
        for (let i = 0; i < 12; i++) {
          const mid = (lo + hi) / 2
          // eslint-disable-next-line no-await-in-loop
          const attempt = await compressAtQuality(mid)
          if (attempt.size > targetBytes) {
            hi = mid
          } else {
            best = attempt
            bestQ = mid
            lo = mid
          }
        }
        blob = best || (await compressAtQuality(0.05))
        usedQuality = bestQ
      }
      setResult({ blob, size: blob.size, quality: usedQuality })

      // ── Activity log ─────────────────────────────────────────────────────
      // Compression mode + target parameters + the quality actually used.
      // The resulting file size is a meaningful outcome metric and does not
      // reveal image content — it describes compression effectiveness only.
      const modeLabel = mode === 'quality'
        ? `quality ${Math.round(usedQuality * 100)}%`
        : `target ${targetKB} KB (quality ${Math.round(usedQuality * 100)}%)`
      logActivity(
        'image.process',
        `Compressed image to ${formatLabel(format)} · ${modeLabel} · ${formatBytes(blob.size)}`,
        {
          operation:  'compress',
          format,
          mode,
          quality:    usedQuality,
          targetKB:   mode === 'target-size' ? targetKB : null,
          outputSize: blob.size,
        }
      )
    } catch (err) {
      setError(err.message || 'Compression failed.')
    } finally {
      setWorking(false)
    }
  }

  function handleDownload() {
    if (!result || !source) return
    const ext = format === 'image/jpeg' ? 'jpg' : 'webp'
    triggerDownload(result.blob, `${baseName(source.file.name)}-compressed.${ext}`)
  }

  const savedPct = source && result
    ? Math.max(0, Math.round((1 - result.size / source.file.size) * 100))
    : 0

  return (
    <div className={styles.toolPanel}>
      <FilePicker onFile={handleFile} />
      {source && (
        <>
          <div className={styles.preview}>
            <img src={source.objectUrl} alt="preview" />
            <div className={styles.previewMeta}>
              <span>Original: <strong>{formatBytes(source.file.size)}</strong></span>
              <span>Type: <strong>{source.file.type || 'unknown'}</strong></span>
            </div>
          </div>
          <div className={styles.fieldGrid}>
            <div className={styles.field}>
              <label className={styles.fieldLabel}>Format</label>
              <select className={styles.textInput} value={format} onChange={e => setFormat(e.target.value)}>
                <option value="image/jpeg">JPEG</option>
                <option value="image/webp">WebP</option>
              </select>
            </div>
            <div className={styles.field}>
              <label className={styles.fieldLabel}>Mode</label>
              <select className={styles.textInput} value={mode} onChange={e => setMode(e.target.value)}>
                <option value="quality">Quality target</option>
                <option value="target-size">Target file size</option>
              </select>
            </div>
            {mode === 'quality' ? (
              <div className={styles.field}>
                <label className={styles.fieldLabel}>Quality ({Math.round(quality * 100)}%)</label>
                <input
                  type="range" min={0.05} max={1} step={0.05}
                  value={quality}
                  onChange={e => setQuality(Number(e.target.value))}
                />
              </div>
            ) : (
              <div className={styles.field}>
                <label className={styles.fieldLabel}>Target size (KB)</label>
                <input
                  className={styles.textInput}
                  type="number" min={1} max={10240}
                  value={targetKB}
                  onChange={e => setTargetKB(Number(e.target.value) || 1)}
                />
              </div>
            )}
          </div>
          {error && <div className={styles.errorBanner} role="alert">{error}</div>}
          <div className={styles.actionRow}>
            <button className={styles.primaryBtn} onClick={handleCompress} disabled={working}>
              {working ? 'Compressing…' : 'Compress'}
            </button>
            {result && (
              <button className={styles.secondaryBtn} onClick={handleDownload}>
                ⬇ Download ({formatBytes(result.size)})
              </button>
            )}
          </div>
          {result && (
            <div className={styles.successBanner}>
              Compressed to <strong>{formatBytes(result.size)}</strong>
              {' '}({savedPct}% smaller) — quality used: {Math.round(result.quality * 100)}%
            </div>
          )}
        </>
      )}
    </div>
  )
}

// ─── Tool: Crop ─────────────────────────────────────────────────────────────

function CropTool() {
  const [source, setSource] = useState(null)
  const [crop, setCrop] = useState({ x: 0, y: 0, w: 100, h: 100 })
  const [format, setFormat] = useState('image/png')
  const [error, setError] = useState('')
  const [working, setWorking] = useState(false)

  function handleFile(file, objectUrl, img) {
    if (source?.objectUrl) URL.revokeObjectURL(source.objectUrl)
    setSource({ file, img, objectUrl })
    setCrop({ x: 0, y: 0, w: img.naturalWidth, h: img.naturalHeight })
    setError('')
  }

  async function handleCrop() {
    if (!source) { setError('Please choose an image first.'); return }
    const { x, y, w, h } = crop
    const { img } = source
    if (w < 1 || h < 1) { setError('Crop width and height must be at least 1 px.'); return }
    if (x + w > img.naturalWidth || y + h > img.naturalHeight) {
      setError('Crop rectangle extends beyond the image.'); return
    }
    trackTool('image.process', () => null)  // Sprint 18: count invocation
    setWorking(true); setError('')
    try {
      const canvas = canvasFromImage(img, w, h, (ctx) => {
        ctx.drawImage(img, x, y, w, h, 0, 0, w, h)
      })
      const blob = await canvasToBlob(canvas, format, 0.95)
      const ext = format === 'image/png' ? 'png' : format === 'image/jpeg' ? 'jpg' : 'webp'
      triggerDownload(blob, `${baseName(source.file.name)}-cropped.${ext}`)

      // ── Activity log ─────────────────────────────────────────────────────
      // ONLY the output dimensions + format. We DELIBERATELY exclude the
      // crop x/y coordinates: they reveal which region of the original
      // image the user chose to keep, which is a form of content-derived
      // information even though the numbers alone look innocuous.
      logActivity(
        'image.process',
        `Cropped image to ${w}×${h} · ${formatLabel(format)}`,
        {
          operation: 'crop',
          width:  w,
          height: h,
          format,
        }
      )
    } catch (err) {
      setError(err.message || 'Crop failed.')
    } finally {
      setWorking(false)
    }
  }

  const maxW = source?.img.naturalWidth || 100
  const maxH = source?.img.naturalHeight || 100

  return (
    <div className={styles.toolPanel}>
      <FilePicker onFile={handleFile} />
      {source && (
        <>
          <div className={styles.preview}>
            <div className={styles.cropStage}>
              <img src={source.objectUrl} alt="preview" />
              {/*
                Crop overlay is scaled to the rendered preview via percent
                values so it stays aligned at any container size.
              */}
              <div
                className={styles.cropOverlay}
                style={{
                  left:   `${(crop.x / maxW) * 100}%`,
                  top:    `${(crop.y / maxH) * 100}%`,
                  width:  `${(crop.w / maxW) * 100}%`,
                  height: `${(crop.h / maxH) * 100}%`,
                }}
              />
            </div>
            <div className={styles.previewMeta}>
              <span>Image: <strong>{maxW} × {maxH}</strong></span>
            </div>
          </div>
          <div className={styles.fieldGrid}>
            <div className={styles.field}>
              <label className={styles.fieldLabel}>X (px): {crop.x}</label>
              <input type="range" min={0} max={Math.max(0, maxW - 1)}
                     value={crop.x}
                     onChange={e => setCrop(c => ({ ...c, x: Math.min(Number(e.target.value), maxW - c.w) }))} />
            </div>
            <div className={styles.field}>
              <label className={styles.fieldLabel}>Y (px): {crop.y}</label>
              <input type="range" min={0} max={Math.max(0, maxH - 1)}
                     value={crop.y}
                     onChange={e => setCrop(c => ({ ...c, y: Math.min(Number(e.target.value), maxH - c.h) }))} />
            </div>
            <div className={styles.field}>
              <label className={styles.fieldLabel}>Width (px): {crop.w}</label>
              <input type="range" min={1} max={maxW}
                     value={crop.w}
                     onChange={e => setCrop(c => ({ ...c, w: Math.min(Number(e.target.value), maxW - c.x) }))} />
            </div>
            <div className={styles.field}>
              <label className={styles.fieldLabel}>Height (px): {crop.h}</label>
              <input type="range" min={1} max={maxH}
                     value={crop.h}
                     onChange={e => setCrop(c => ({ ...c, h: Math.min(Number(e.target.value), maxH - c.y) }))} />
            </div>
          </div>
          <div className={styles.field}>
            <label className={styles.fieldLabel}>Output format</label>
            <select className={styles.textInput} value={format} onChange={e => setFormat(e.target.value)}>
              <option value="image/png">PNG</option>
              <option value="image/jpeg">JPEG</option>
              <option value="image/webp">WebP</option>
            </select>
          </div>
          {error && <div className={styles.errorBanner} role="alert">{error}</div>}
          <button className={styles.primaryBtn} onClick={handleCrop} disabled={working}>
            {working ? 'Cropping…' : '⬇ Crop & download'}
          </button>
        </>
      )}
    </div>
  )
}

// ─── Tool: Rotate / Flip ────────────────────────────────────────────────────

function RotateTool() {
  const [source, setSource] = useState(null)
  const [rotation, setRotation] = useState(0)  // 0, 90, 180, 270
  const [flipH, setFlipH] = useState(false)
  const [flipV, setFlipV] = useState(false)
  const [format, setFormat] = useState('image/png')
  const [error, setError] = useState('')
  const [working, setWorking] = useState(false)
  const canvasRef = useRef(null)

  function handleFile(file, objectUrl, img) {
    if (source?.objectUrl) URL.revokeObjectURL(source.objectUrl)
    setSource({ file, img, objectUrl })
    setRotation(0); setFlipH(false); setFlipV(false)
    setError('')
  }

  /**
   * Renders the transformed image to the given canvas. Used both for the
   * preview (into canvasRef.current) and the download (into a fresh canvas).
   *
   * Order: rotate first, then flip around the rotated centre — this matches
   * the behaviour the user sees in the live preview.
   */
  const render = useCallback((canvas) => {
    if (!source) return
    const { img } = source
    const swap = rotation % 180 !== 0
    const w = swap ? img.naturalHeight : img.naturalWidth
    const h = swap ? img.naturalWidth  : img.naturalHeight
    canvas.width = w
    canvas.height = h
    const ctx = canvas.getContext('2d')
    ctx.clearRect(0, 0, w, h)
    ctx.save()
    ctx.translate(w / 2, h / 2)
    ctx.rotate((rotation * Math.PI) / 180)
    ctx.scale(flipH ? -1 : 1, flipV ? -1 : 1)
    ctx.drawImage(img, -img.naturalWidth / 2, -img.naturalHeight / 2)
    ctx.restore()
  }, [source, rotation, flipH, flipV])

  useEffect(() => {
    if (canvasRef.current && source) render(canvasRef.current)
  }, [render, source])

  async function handleDownload() {
    if (!source) { setError('Please choose an image first.'); return }
    trackTool('image.process', () => null)  // Sprint 18: count invocation
    setWorking(true); setError('')
    try {
      const canvas = document.createElement('canvas')
      render(canvas)
      const blob = await canvasToBlob(canvas, format, 0.95)
      const ext = format === 'image/png' ? 'png' : format === 'image/jpeg' ? 'jpg' : 'webp'
      triggerDownload(blob, `${baseName(source.file.name)}-transformed.${ext}`)

      // ── Activity log ─────────────────────────────────────────────────────
      // The three transformation flags describe what happened in fully
      // content-independent terms. The human-readable summary enumerates
      // which ones were applied (or "no change" if the user exported an
      // unmodified copy — a rare but valid flow).
      const ops = []
      if (rotation !== 0) ops.push(`${rotation}°`)
      if (flipH) ops.push('flipped H')
      if (flipV) ops.push('flipped V')
      const opsLabel = ops.length > 0 ? ops.join(' + ') : 'no change'
      logActivity(
        'image.process',
        `Rotated/flipped image · ${opsLabel} · ${formatLabel(format)}`,
        {
          operation: 'rotate',
          rotation,
          flipH,
          flipV,
          format,
        }
      )
    } catch (err) {
      setError(err.message || 'Rotation failed.')
    } finally {
      setWorking(false)
    }
  }

  return (
    <div className={styles.toolPanel}>
      <FilePicker onFile={handleFile} />
      {source && (
        <>
          <div className={styles.canvasPreview}>
            <canvas ref={canvasRef} />
          </div>
          <div className={styles.rotateControls}>
            <button type="button" className={styles.secondaryBtn} onClick={() => setRotation(r => (r + 270) % 360)}>↶ Rotate −90°</button>
            <button type="button" className={styles.secondaryBtn} onClick={() => setRotation(r => (r + 90)  % 360)}>↷ Rotate +90°</button>
            <button type="button" className={styles.secondaryBtn} onClick={() => setRotation(r => (r + 180) % 360)}>⟳ 180°</button>
            <button type="button" className={flipH ? styles.secondaryBtnActive : styles.secondaryBtn}
                    onClick={() => setFlipH(f => !f)}>⇄ Flip horizontal</button>
            <button type="button" className={flipV ? styles.secondaryBtnActive : styles.secondaryBtn}
                    onClick={() => setFlipV(f => !f)}>⇅ Flip vertical</button>
            <button type="button" className={styles.secondaryBtn}
                    onClick={() => { setRotation(0); setFlipH(false); setFlipV(false) }}>↺ Reset</button>
          </div>
          <div className={styles.field}>
            <label className={styles.fieldLabel}>Output format</label>
            <select className={styles.textInput} value={format} onChange={e => setFormat(e.target.value)}>
              <option value="image/png">PNG</option>
              <option value="image/jpeg">JPEG</option>
              <option value="image/webp">WebP</option>
            </select>
          </div>
          {error && <div className={styles.errorBanner} role="alert">{error}</div>}
          <button className={styles.primaryBtn} onClick={handleDownload} disabled={working}>
            {working ? 'Exporting…' : '⬇ Download'}
          </button>
        </>
      )}
    </div>
  )
}

// ─── Tool: Filters ──────────────────────────────────────────────────────────

function FiltersTool() {
  const [source, setSource] = useState(null)
  const [grayscale,  setGrayscale]  = useState(0)
  const [sepia,      setSepia]      = useState(0)
  const [invert,     setInvert]     = useState(0)
  const [brightness, setBrightness] = useState(100)
  const [contrast,   setContrast]   = useState(100)
  const [saturation, setSaturation] = useState(100)
  const [blur,       setBlur]       = useState(0)
  const [format,     setFormat]     = useState('image/png')
  const [error,      setError]      = useState('')
  const [working,    setWorking]    = useState(false)
  const canvasRef = useRef(null)

  function handleFile(file, objectUrl, img) {
    if (source?.objectUrl) URL.revokeObjectURL(source.objectUrl)
    setSource({ file, img, objectUrl })
    setError('')
  }

  const filterString = [
    `grayscale(${grayscale}%)`,
    `sepia(${sepia}%)`,
    `invert(${invert}%)`,
    `brightness(${brightness}%)`,
    `contrast(${contrast}%)`,
    `saturate(${saturation}%)`,
    `blur(${blur}px)`,
  ].join(' ')

  const render = useCallback((canvas) => {
    if (!source) return
    const { img } = source
    canvas.width = img.naturalWidth
    canvas.height = img.naturalHeight
    const ctx = canvas.getContext('2d')
    ctx.filter = filterString
    ctx.drawImage(img, 0, 0)
    ctx.filter = 'none'
  }, [source, filterString])

  useEffect(() => {
    if (canvasRef.current && source) render(canvasRef.current)
  }, [render, source])

  function reset() {
    setGrayscale(0); setSepia(0); setInvert(0)
    setBrightness(100); setContrast(100); setSaturation(100)
    setBlur(0)
  }

  async function handleDownload() {
    if (!source) { setError('Please choose an image first.'); return }
    trackTool('image.process', () => null)  // Sprint 18: count invocation
    setWorking(true); setError('')
    try {
      const canvas = document.createElement('canvas')
      render(canvas)
      const blob = await canvasToBlob(canvas, format, 0.95)
      const ext = format === 'image/png' ? 'png' : format === 'image/jpeg' ? 'jpg' : 'webp'
      triggerDownload(blob, `${baseName(source.file.name)}-filtered.${ext}`)

      // ── Activity log ─────────────────────────────────────────────────────
      // Filter intensities are user-chosen parameters, not derived from
      // image content, so they're safe to record. The summary lists only
      // the non-default ones for readability; the payload is complete so
      // the same transformation could be reconstructed from it.
      const applied = []
      if (grayscale > 0)      applied.push(`grayscale ${grayscale}%`)
      if (sepia > 0)          applied.push(`sepia ${sepia}%`)
      if (invert > 0)         applied.push(`invert ${invert}%`)
      if (brightness !== 100) applied.push(`brightness ${brightness}%`)
      if (contrast !== 100)   applied.push(`contrast ${contrast}%`)
      if (saturation !== 100) applied.push(`saturation ${saturation}%`)
      if (blur > 0)           applied.push(`blur ${blur}px`)
      const appliedLabel = applied.length > 0 ? applied.join(', ') : 'no filters'
      logActivity(
        'image.process',
        `Applied filters to image · ${appliedLabel} · ${formatLabel(format)}`,
        {
          operation: 'filters',
          format,
          grayscale,
          sepia,
          invert,
          brightness,
          contrast,
          saturation,
          blur,
        }
      )
    } catch (err) {
      setError(err.message || 'Filter export failed.')
    } finally {
      setWorking(false)
    }
  }

  return (
    <div className={styles.toolPanel}>
      <FilePicker onFile={handleFile} />
      {source && (
        <>
          <div className={styles.canvasPreview}>
            <canvas ref={canvasRef} />
          </div>
          <div className={styles.fieldGrid}>
            <div className={styles.field}>
              <label className={styles.fieldLabel}>Grayscale ({grayscale}%)</label>
              <input type="range" min={0} max={100} value={grayscale} onChange={e => setGrayscale(Number(e.target.value))} />
            </div>
            <div className={styles.field}>
              <label className={styles.fieldLabel}>Sepia ({sepia}%)</label>
              <input type="range" min={0} max={100} value={sepia} onChange={e => setSepia(Number(e.target.value))} />
            </div>
            <div className={styles.field}>
              <label className={styles.fieldLabel}>Invert ({invert}%)</label>
              <input type="range" min={0} max={100} value={invert} onChange={e => setInvert(Number(e.target.value))} />
            </div>
            <div className={styles.field}>
              <label className={styles.fieldLabel}>Brightness ({brightness}%)</label>
              <input type="range" min={0} max={200} value={brightness} onChange={e => setBrightness(Number(e.target.value))} />
            </div>
            <div className={styles.field}>
              <label className={styles.fieldLabel}>Contrast ({contrast}%)</label>
              <input type="range" min={0} max={200} value={contrast} onChange={e => setContrast(Number(e.target.value))} />
            </div>
            <div className={styles.field}>
              <label className={styles.fieldLabel}>Saturation ({saturation}%)</label>
              <input type="range" min={0} max={200} value={saturation} onChange={e => setSaturation(Number(e.target.value))} />
            </div>
            <div className={styles.field}>
              <label className={styles.fieldLabel}>Blur ({blur}px)</label>
              <input type="range" min={0} max={20} value={blur} onChange={e => setBlur(Number(e.target.value))} />
            </div>
            <div className={styles.field}>
              <label className={styles.fieldLabel}>Output format</label>
              <select className={styles.textInput} value={format} onChange={e => setFormat(e.target.value)}>
                <option value="image/png">PNG</option>
                <option value="image/jpeg">JPEG</option>
                <option value="image/webp">WebP</option>
              </select>
            </div>
          </div>
          {error && <div className={styles.errorBanner} role="alert">{error}</div>}
          <div className={styles.actionRow}>
            <button className={styles.primaryBtn} onClick={handleDownload} disabled={working}>
              {working ? 'Exporting…' : '⬇ Download'}
            </button>
            <button className={styles.secondaryBtn} onClick={reset}>↺ Reset filters</button>
          </div>
        </>
      )}
    </div>
  )
}

// ─── Tool: Info ─────────────────────────────────────────────────────────────

function InfoTool() {
  const [source, setSource] = useState(null)

  function handleFile(file, objectUrl, img) {
    if (source?.objectUrl) URL.revokeObjectURL(source.objectUrl)
    setSource({ file, img, objectUrl })
  }

  const info = source
    ? [
        { label: 'Filename',       value: source.file.name },
        { label: 'MIME type',      value: source.file.type || 'unknown' },
        { label: 'Size',           value: `${formatBytes(source.file.size)} (${source.file.size.toLocaleString()} bytes)` },
        { label: 'Natural width',  value: `${source.img.naturalWidth} px` },
        { label: 'Natural height', value: `${source.img.naturalHeight} px` },
        { label: 'Aspect ratio',   value: (source.img.naturalWidth / source.img.naturalHeight).toFixed(4) },
        { label: 'Megapixels',     value: ((source.img.naturalWidth * source.img.naturalHeight) / 1_000_000).toFixed(2) + ' MP' },
        { label: 'Last modified',  value: new Date(source.file.lastModified).toLocaleString() },
      ]
    : []

  // NOTE: No logActivity() here. Inspecting metadata is a passive read and
  // there's no "result" to surface on the Dashboard timeline.

  return (
    <div className={styles.toolPanel}>
      <FilePicker onFile={handleFile} />
      {source && (
        <>
          <div className={styles.preview}>
            <img src={source.objectUrl} alt="preview" />
          </div>
          <div className={styles.infoTable}>
            {info.map(row => (
              <div key={row.label} className={styles.infoRow}>
                <span className={styles.infoLabel}>{row.label}</span>
                <span className={styles.infoValue}>{row.value}</span>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}

// ─── Tool: Data URL ─────────────────────────────────────────────────────────

function DataUrlTool() {
  const [source, setSource] = useState(null)
  const [dataUrl, setDataUrl] = useState('')
  const [copied, setCopied] = useState(false)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState('')

  async function handleFile(file, objectUrl, img) {
    if (source?.objectUrl) URL.revokeObjectURL(source.objectUrl)
    setSource({ file, img, objectUrl })
    setDataUrl(''); setError('')
    setWorking(true)
    try {
      // FileReader preserves the original bytes, which matters for users
      // who want the exact same encoding as the source file (not re-encoded).
      const reader = new FileReader()
      reader.onload = () => { setDataUrl(reader.result); setWorking(false) }
      reader.onerror = () => { setError('Failed to read the file.'); setWorking(false) }
      reader.readAsDataURL(file)
    } catch (err) {
      setError(err.message || 'Read failed.')
      setWorking(false)
    }
  }

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(dataUrl)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      setError('Clipboard access was denied.')
    }
  }

  // NOTE: No logActivity() here. Data-URL export is closer to file I/O than
  // to a transformation — and logging "file encoded to base64" would tie an
  // activity entry to a specific file the user chose to export. Left silent.

  return (
    <div className={styles.toolPanel}>
      <FilePicker onFile={handleFile} />
      {source && (
        <>
          <div className={styles.preview}>
            <img src={source.objectUrl} alt="preview" />
            <div className={styles.previewMeta}>
              <span>Data URL length: <strong>{dataUrl.length.toLocaleString()}</strong> chars</span>
              <span>Original size: <strong>{formatBytes(source.file.size)}</strong></span>
            </div>
          </div>
          {working && <div className={styles.successBanner}>Encoding…</div>}
          {error && <div className={styles.errorBanner} role="alert">{error}</div>}
          {dataUrl && (
            <>
              <textarea
                className={styles.dataUrlArea}
                value={dataUrl}
                readOnly
                rows={8}
                onFocus={e => e.target.select()}
              />
              <div className={styles.actionRow}>
                <button className={styles.primaryBtn} onClick={handleCopy}>
                  {copied ? '✓ Copied' : '📋 Copy data URL'}
                </button>
              </div>
            </>
          )}
        </>
      )}
    </div>
  )
}

// ─── Main page shell ────────────────────────────────────────────────────────

export default function ImageToolsPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState('resize')
  const isGuest = username === 'Guest User'

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  function renderTab() {
    switch (activeTab) {
      case 'resize':   return <ResizeTool />
      case 'convert':  return <ConvertTool />
      case 'compress': return <CompressTool />
      case 'crop':     return <CropTool />
      case 'rotate':   return <RotateTool />
      case 'filters':  return <FiltersTool />
      case 'info':     return <InfoTool />
      case 'dataurl':  return <DataUrlTool />
      default:         return <ResizeTool />
    }
  }

  return (
    <div className={styles.page}>

      {/* ── Header ──────────────────────────────────────────────────── */}
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.brand}>
            <span className={styles.brandMark} aria-hidden="true">#</span>
            <span className={styles.brandName}>UsefulTools</span>
          </div>
          <button className={styles.backBtn} onClick={() => navigate('/dashboard')}>
            ← Dashboard
          </button>
        </div>
        <div className={styles.headerRight}>
          <UserMenu username={username} isGuest={isGuest} variant="light" />
          <button className={styles.logoutBtn} onClick={handleLogout}>Sign out</button>
        </div>
      </header>

      {/* ── Hero ────────────────────────────────────────────────────── */}
      <section className={styles.hero}>
        <div className={styles.heroGrid} aria-hidden="true" />
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>Image Tools</div>
          <h1 className={styles.heroTitle}>
            Image<br />
            <span className={styles.heroAccent}>Tools</span>
          </h1>
          <p className={styles.heroSub}>
            Resize, convert, compress, crop, rotate, and filter images entirely in
            your browser. Files never leave your device — every transformation
            runs client-side on the HTML5 canvas.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div className={styles.statCard}>
            <span className={styles.statValue}>{TABS.length}</span>
            <span className={styles.statLabel}>tools</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>0</span>
            <span className={styles.statLabel}>uploads</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>100%</span>
            <span className={styles.statLabel}>private</span>
          </div>
        </div>
      </section>

      <main className={styles.main}>
        {/* ── Tab bar ─────────────────────────────────────────────── */}
        <nav className={styles.tabBar} aria-label="Image tools">
          {TABS.map(tab => (
            <button
              key={tab.id}
              className={activeTab === tab.id ? styles.tabActive : styles.tab}
              onClick={() => setActiveTab(tab.id)}
            >
              <span className={styles.tabIcon} aria-hidden="true">{tab.icon}</span>
              {tab.label}
            </button>
          ))}
        </nav>

        <div className={styles.content}>
          {renderTab()}
        </div>
      </main>
    </div>
  )
}
