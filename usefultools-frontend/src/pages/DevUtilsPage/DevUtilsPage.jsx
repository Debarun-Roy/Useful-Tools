import { useState, useMemo, useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logoutUser } from '../../api/apiClient'
import UserMenu from '../../components/UserMenu/UserMenu'
import { identifyHash, HASH_ALGORITHMS } from '../../utils/hashIdentifier'
import { generateKeys, estimateEntropyBits, sanitisePrefix } from '../../utils/keyGenerator'
import { logActivity } from '../../utils/logActivity'
import QRCode from 'qrcode'
import styles from './DevUtilsPage.module.css'
import { trackTool } from '../../utils/logMetric'

/*
 * DevUtilsPage — Sprint 16 update.
 *
 * ── Tools ─────────────────────────────────────────────────────────────────
 *   1. Hash Identifier      — paste a hash, get a ranked list of algorithms
 *   2. API Key Generator    — cryptographically secure keys in 4 formats
 *   3. QR Code Generator    — encode any text/URL as a QR code (client-side)
 *                             Requires: npm install qrcode
 *   4. Cron Builder         — visual cron expression builder + human explainer
 *
 * All four tools run entirely in the browser. No data leaves the page.
 *
 * ── Activity logging ──────────────────────────────────────────────────────
 *   qrcode.generate  → payload: { length, hasUrl }  — NEVER the encoded text
 *   cron.build       → payload: { expression }      — expression only (not secret)
 */

// ─── Hash Identifier tab ────────────────────────────────────────────────────

function HashIdentifierTool() {
  const [input, setInput] = useState('')
  const result = useMemo(
    () => trackTool('hash.identify', () => identifyHash(input)),
    [input]
  )

  useEffect(() => {
    if (!result.normalized || result.candidates.length === 0) return
    const top = result.candidates[0]
    const extra = result.candidates.length - 1
    const extraSuffix = extra > 0 ? ` (+${extra} other${extra > 1 ? 's' : ''})` : ''
    const shapeLabel = result.charset === 'hex'
      ? `${result.length}-char hexadecimal`
      : result.charset === 'base64' || result.charset === 'base64url'
        ? `${result.length}-char ${result.charset}`
        : `${result.length}-char string`
    logActivity(
      'hash.identify',
      `Identified ${shapeLabel} as ${top.name}${extraSuffix}`,
      { length: result.length, charset: result.charset, topCandidate: top.name, topConfidence: top.confidence, candidateCount: result.candidates.length }
    )
  }, [result])

  const referenceRows = [
    HASH_ALGORITHMS.md5, HASH_ALGORITHMS.ntlm, HASH_ALGORITHMS.md4,
    HASH_ALGORITHMS.sha1, HASH_ALGORITHMS.ripemd160, HASH_ALGORITHMS.sha224,
    HASH_ALGORITHMS.sha256, HASH_ALGORITHMS.sha3_256, HASH_ALGORITHMS.sha384,
    HASH_ALGORITHMS.sha512, HASH_ALGORITHMS.sha3_512, HASH_ALGORITHMS.crc32,
    HASH_ALGORITHMS.bcrypt, HASH_ALGORITHMS.argon2, HASH_ALGORITHMS.scrypt,
    HASH_ALGORITHMS.md5crypt, HASH_ALGORITHMS.sha256crypt, HASH_ALGORITHMS.sha512crypt,
    HASH_ALGORITHMS.ldap_ssha, HASH_ALGORITHMS.ldap_sha, HASH_ALGORITHMS.jwt,
  ]

  const CONFIDENCE_META = {
    definitive: { label: 'Definitive', cls: styles.confDefinitive },
    high:       { label: 'High',       cls: styles.confHigh },
    medium:     { label: 'Medium',     cls: styles.confMedium },
    low:        { label: 'Low',        cls: styles.confLow },
  }

  return (
    <div className={styles.tabPanel}>
      <div className={styles.field}>
        <label className={styles.fieldLabel} htmlFor="hash-input">
          Hash, token, or credential
          <span className={styles.optional}> — everything stays in your browser</span>
        </label>
        <textarea
          id="hash-input"
          className={styles.monoTextarea}
          rows={4}
          placeholder="Paste a hash or credential string here — e.g. 5f4dcc3b5aa765d61d8327deb882cf99"
          value={input}
          onChange={e => setInput(e.target.value)}
          spellCheck={false}
          autoCapitalize="off"
          autoCorrect="off"
        />
      </div>

      {result.normalized && (
        <div className={styles.resultBlock}>
          <div className={styles.resultMeta}>
            <span className={styles.metaItem}>Length: <strong>{result.length}</strong></span>
            <span className={styles.metaItem}>Charset: <strong>{result.charset}</strong></span>
          </div>
          {result.candidates.length === 0 ? (
            <p className={styles.noMatch}>No known algorithm matched this input.</p>
          ) : (
            <ul className={styles.candidateList}>
              {result.candidates.map((c, i) => {
                const meta = CONFIDENCE_META[c.confidence] ?? CONFIDENCE_META.low
                return (
                  <li key={i} className={styles.candidateRow}>
                    <span className={`${styles.confBadge} ${meta.cls}`}>{meta.label}</span>
                    <span className={styles.candidateName}>{c.name}</span>
                    <span className={styles.candidateNote}>{c.note}</span>
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      )}

      <details className={styles.referenceAccordion}>
        <summary className={styles.referenceSummary}>Algorithm reference</summary>
        <table className={styles.referenceTable}>
          <thead>
            <tr>
              <th>Algorithm</th><th>Typical length</th><th>Charset</th><th>Notes</th>
            </tr>
          </thead>
          <tbody>
            {referenceRows.map(alg => alg && (
              <tr key={alg.name}>
                <td className={styles.monoCell}>{alg.name}</td>
                <td>{alg.lengths?.join(' / ') ?? '—'}</td>
                <td>{alg.charset ?? '—'}</td>
                <td className={styles.noteCell}>{alg.note ?? ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  )
}

// ─── API Key Generator tab ───────────────────────────────────────────────────

const FORMAT_OPTIONS = [
  { id: 'uuid',      label: 'UUID v4',    example: 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx' },
  { id: 'hex',       label: 'Hex',        example: 'a3f8c2...' },
  { id: 'base62',    label: 'Base62',     example: 'aB3kZ9...' },
  { id: 'base64url', label: 'Base64url',  example: 'aB3k-Z9...' },
]

function ApiKeyGeneratorTool() {
  const [format, setFormat]     = useState('uuid')
  const [length, setLength]     = useState(32)
  const [count, setCount]       = useState(3)
  const [prefix, setPrefix]     = useState('')
  const [keys, setKeys]         = useState([])
  const [copied, setCopied]     = useState(null)

  const entropy = useMemo(() => estimateEntropyBits(format, length), [format, length])
  const sanitised = useMemo(() => sanitisePrefix(prefix), [prefix])

  function generate() {
    const result = trackTool('key.generate', () =>
      generateKeys({ format, length, count, prefix: sanitised })
    )
    setKeys(result)
    logActivity(
      'key.generate',
      `Generated ${count} ${format.toUpperCase()} key${count > 1 ? 's' : ''} · ${length} chars${sanitised ? ` · prefix "${sanitised}"` : ''}`,
      { format, length, count, prefixLength: sanitised.length }
    )
  }

  async function copyKey(key, idx) {
    await navigator.clipboard.writeText(key)
    setCopied(idx)
    setTimeout(() => setCopied(null), 1500)
  }

  async function copyAll() {
    await navigator.clipboard.writeText(keys.join('\n'))
    setCopied('all')
    setTimeout(() => setCopied(null), 1500)
  }

  return (
    <div className={styles.tabPanel}>
      <div className={styles.controlRow}>
        <div className={styles.formatChips}>
          {FORMAT_OPTIONS.map(f => (
            <button
              key={f.id}
              className={format === f.id ? styles.chipActive : styles.chip}
              onClick={() => setFormat(f.id)}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {format !== 'uuid' && (
        <div className={styles.field}>
          <label className={styles.fieldLabel}>
            Length: <strong>{length}</strong> chars
          </label>
          <input
            type="range" min={16} max={128} step={8}
            value={length}
            onChange={e => setLength(Number(e.target.value))}
            className={styles.slider}
          />
        </div>
      )}

      <div className={styles.field}>
        <label className={styles.fieldLabel} htmlFor="key-prefix">
          Prefix <span className={styles.optional}>(optional — alphanumeric + _ -)</span>
        </label>
        <input
          id="key-prefix"
          type="text"
          className={styles.textInput}
          placeholder="e.g. sk_live_"
          value={prefix}
          onChange={e => setPrefix(e.target.value)}
          maxLength={24}
        />
      </div>

      <div className={styles.field}>
        <label className={styles.fieldLabel}>
          Count: <strong>{count}</strong>
        </label>
        <input
          type="range" min={1} max={10} step={1}
          value={count}
          onChange={e => setCount(Number(e.target.value))}
          className={styles.slider}
        />
      </div>

      <div className={styles.entropyStrip}>
        <span className={styles.entropyLabel}>Entropy</span>
        <span className={styles.entropyValue}>{entropy} bits</span>
        <span className={styles.entropyTip}>
          {entropy >= 128 ? '🟢 Cryptographically strong' : entropy >= 80 ? '🟡 Acceptable' : '🔴 Weak'}
        </span>
      </div>

      <div className={styles.actionRow}>
        <button className={styles.primaryBtn} onClick={generate}>Generate</button>
        {keys.length > 0 && (
          <button className={styles.secondaryBtn} onClick={copyAll}>
            {copied === 'all' ? '✓ Copied all' : 'Copy all'}
          </button>
        )}
      </div>

      {keys.length > 0 && (
        <ul className={styles.keyList}>
          {keys.map((key, i) => (
            <li key={i} className={styles.keyRow}>
              <code className={styles.keyCode}>{key}</code>
              <button className={styles.copyBtn} onClick={() => copyKey(key, i)}>
                {copied === i ? '✓' : 'Copy'}
              </button>
            </li>
          ))}
        </ul>
      )}

      <p className={styles.securityNote}>
        Keys are generated using <code>crypto.getRandomValues()</code> — the same
        source your OS uses. They are never sent anywhere.
      </p>
    </div>
  )
}

// ─── QR Code Generator tab ───────────────────────────────────────────────────

const QR_ERROR_LEVELS = [
  { value: 'L', label: 'L — Low (7%)',    desc: 'Most data, least damage tolerance' },
  { value: 'M', label: 'M — Medium (15%)', desc: 'Good balance' },
  { value: 'Q', label: 'Q — Quartile (25%)', desc: 'Better for printed labels' },
  { value: 'H', label: 'H — High (30%)',  desc: 'Best for damaged/dirty environments' },
]

function QRCodeTool() {
  const [text, setText]           = useState('')
  const [errorLevel, setErrorLevel] = useState('M')
  const [size, setSize]           = useState(256)
  const [darkColor, setDarkColor] = useState('#000000')
  const [lightColor, setLightColor] = useState('#ffffff')
  const [qrDataUrl, setQrDataUrl] = useState(null)
  const [error, setError]         = useState(null)
  const [generating, setGenerating] = useState(false)

  async function generate() {
    if (!text.trim()) return
    setGenerating(true)
    setError(null)
    try {
      const dataUrl = await trackTool('qrcode.generate', () =>
        QRCode.toDataURL(text.trim(), {
          errorCorrectionLevel: errorLevel,
          width: size,
          margin: 2,
          color: { dark: darkColor, light: lightColor },
        })
      )
      setQrDataUrl(dataUrl)
      const isUrl = /^https?:\/\//i.test(text.trim())
      logActivity(
        'qrcode.generate',
        `Generated QR code for ${isUrl ? 'URL' : 'text'} (${text.trim().length} chars)`,
        { length: text.trim().length, hasUrl: isUrl, errorLevel, size }
      )
    } catch (err) {
      setError(err.message ?? 'Failed to generate QR code')
    } finally {
      setGenerating(false)
    }
  }

  function download() {
    if (!qrDataUrl) return
    const a = document.createElement('a')
    a.href = qrDataUrl
    a.download = 'qrcode.png'
    a.click()
  }

  async function copyImage() {
    if (!qrDataUrl) return
    try {
      const blob = await (await fetch(qrDataUrl)).blob()
      await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })])
    } catch {
      // fallback: copy the data URL text
      await navigator.clipboard.writeText(qrDataUrl)
    }
  }

  return (
    <div className={styles.tabPanel}>
      <div className={styles.field}>
        <label className={styles.fieldLabel} htmlFor="qr-input">
          Text or URL to encode
          <span className={styles.optional}> — generated entirely in your browser</span>
        </label>
        <textarea
          id="qr-input"
          className={styles.monoTextarea}
          rows={4}
          placeholder="https://example.com  or  any text you want to encode…"
          value={text}
          onChange={e => { setText(e.target.value); setQrDataUrl(null) }}
          spellCheck={false}
        />
      </div>

      <div className={styles.qrOptionsRow}>
        <div className={styles.field}>
          <label className={styles.fieldLabel}>Error correction level</label>
          <select
            className={styles.selectInput}
            value={errorLevel}
            onChange={e => { setErrorLevel(e.target.value); setQrDataUrl(null) }}
          >
            {QR_ERROR_LEVELS.map(l => (
              <option key={l.value} value={l.value}>{l.label}</option>
            ))}
          </select>
          <span className={styles.optional}>
            {QR_ERROR_LEVELS.find(l => l.value === errorLevel)?.desc}
          </span>
        </div>

        <div className={styles.field}>
          <label className={styles.fieldLabel}>
            Size: <strong>{size}×{size}px</strong>
          </label>
          <input
            type="range" min={128} max={512} step={64}
            value={size}
            onChange={e => { setSize(Number(e.target.value)); setQrDataUrl(null) }}
            className={styles.slider}
          />
        </div>

        <div className={styles.colorRow}>
          <div className={styles.colorField}>
            <label className={styles.fieldLabel}>Dark colour</label>
            <input
              type="color" value={darkColor}
              onChange={e => { setDarkColor(e.target.value); setQrDataUrl(null) }}
              className={styles.colorInput}
            />
          </div>
          <div className={styles.colorField}>
            <label className={styles.fieldLabel}>Light colour</label>
            <input
              type="color" value={lightColor}
              onChange={e => { setLightColor(e.target.value); setQrDataUrl(null) }}
              className={styles.colorInput}
            />
          </div>
        </div>
      </div>

      <div className={styles.actionRow}>
        <button
          className={styles.primaryBtn}
          onClick={generate}
          disabled={!text.trim() || generating}
        >
          {generating ? 'Generating…' : 'Generate QR'}
        </button>
      </div>

      {error && (
        <div className={styles.errorBanner} role="alert">{error}</div>
      )}

      {qrDataUrl && (
        <div className={styles.qrResult}>
          <div className={styles.qrImageWrap}>
            <img
              src={qrDataUrl}
              alt="Generated QR code"
              className={styles.qrImage}
              style={{ width: Math.min(size, 320), height: Math.min(size, 320) }}
            />
          </div>
          <div className={styles.qrActions}>
            <button className={styles.primaryBtn} onClick={download}>
              ⬇ Download PNG
            </button>
            <button className={styles.secondaryBtn} onClick={copyImage}>
              Copy image
            </button>
          </div>
          <p className={styles.securityNote}>
            QR codes encode data at up to ~3 KB. For URLs, keep them short.
            Error correction level {errorLevel} can restore up to{' '}
            { { L: '7%', M: '15%', Q: '25%', H: '30%' }[errorLevel] } of damaged data.
          </p>
        </div>
      )}
    </div>
  )
}

// ─── Cron Builder tab ────────────────────────────────────────────────────────

const CRON_PRESETS = [
  { label: 'Every minute',         value: '* * * * *' },
  { label: 'Every 5 minutes',      value: '*/5 * * * *' },
  { label: 'Every 15 minutes',     value: '*/15 * * * *' },
  { label: 'Every 30 minutes',     value: '*/30 * * * *' },
  { label: 'Hourly',               value: '0 * * * *' },
  { label: 'Daily at midnight',    value: '0 0 * * *' },
  { label: 'Daily at noon',        value: '0 12 * * *' },
  { label: 'Weekdays at 9 AM',     value: '0 9 * * 1-5' },
  { label: 'Weekly (Mon midnight)',value: '0 0 * * 1' },
  { label: 'Monthly (1st, midnight)', value: '0 0 1 * *' },
  { label: 'Yearly (Jan 1st)',     value: '0 0 1 1 *' },
]

const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec']
const DAYS_OF_WEEK = ['Sun','Mon','Tue','Wed','Thu','Fri','Sat']

function describeCronField(value, kind) {
  if (value === '*') return `every ${kind}`
  if (/^\*\/(\d+)$/.test(value)) {
    const n = value.match(/^\*\/(\d+)$/)[1]
    return `every ${n} ${kind}${n > 1 ? 's' : ''}`
  }
  if (/^(\d+)-(\d+)$/.test(value)) {
    const [, a, b] = value.match(/^(\d+)-(\d+)$/)
    if (kind === 'day of week') return `${DAYS_OF_WEEK[a] ?? a}–${DAYS_OF_WEEK[b] ?? b}`
    if (kind === 'month') return `${MONTHS[a - 1] ?? a}–${MONTHS[b - 1] ?? b}`
    return `${kind} ${a}–${b}`
  }
  if (/^\d+(,\d+)+$/.test(value)) {
    const parts = value.split(',')
    if (kind === 'day of week') return parts.map(p => DAYS_OF_WEEK[p] ?? p).join(', ')
    if (kind === 'month') return parts.map(p => MONTHS[p - 1] ?? p).join(', ')
    return `${kind}s ${parts.join(', ')}`
  }
  if (/^\d+$/.test(value)) {
    if (kind === 'day of week') return DAYS_OF_WEEK[value] ?? `day ${value}`
    if (kind === 'month') return MONTHS[value - 1] ?? `month ${value}`
    return `${kind} ${value}`
  }
  return value
}

function describeCron(minute, hour, dom, month, dow) {
  const minuteDesc = describeCronField(minute, 'minute')
  const hourDesc   = describeCronField(hour,   'hour')
  const domDesc    = describeCronField(dom,    'day of month')
  const monthDesc  = describeCronField(month,  'month')
  const dowDesc    = describeCronField(dow,    'day of week')

  // Compose natural language
  let timeClause = ''
  if (minute === '*' && hour === '*') {
    timeClause = 'every minute'
  } else if (minute.startsWith('*/') && hour === '*') {
    timeClause = `${minuteDesc}`
  } else if (minute === '0' && hour === '*') {
    timeClause = 'at the start of every hour'
  } else if (hour === '*') {
    timeClause = `at ${minuteDesc} past every hour`
  } else {
    const h = /^\d+$/.test(hour) ? hour.padStart(2, '0') : hour
    const m = /^\d+$/.test(minute) ? minute.padStart(2, '0') : minute
    if (/^\d+$/.test(hour) && /^\d+$/.test(minute)) {
      const hNum = parseInt(hour, 10)
      const mNum = parseInt(minute, 10)
      const ampm = hNum >= 12 ? 'PM' : 'AM'
      const h12 = hNum % 12 === 0 ? 12 : hNum % 12
      const mStr = String(mNum).padStart(2, '0')
      timeClause = `at ${h12}:${mStr} ${ampm}`
    } else {
      timeClause = `at ${hourDesc}:${minuteDesc}`
    }
  }

  let whenClause = ''
  const domStar = dom === '*'
  const dowStar = dow === '*'
  const monthStar = month === '*'

  if (!domStar && !dowStar) {
    whenClause = `, on ${domDesc} and ${dowDesc}`
  } else if (!domStar) {
    whenClause = `, on the ${domDesc}`
  } else if (!dowStar) {
    whenClause = `, on ${dowDesc}`
  }

  if (!monthStar) {
    whenClause += ` in ${monthDesc}`
  }

  const raw = `${minute} ${hour} ${dom} ${month} ${dow}`
  return { description: `Runs ${timeClause}${whenClause}`, raw }
}

function validateCronField(value, kind) {
  if (value === '') return 'Required'
  if (value === '*') return null
  if (/^\*\/\d+$/.test(value)) {
    const n = parseInt(value.split('/')[1], 10)
    if (kind === 'minute' && (n < 1 || n > 59)) return '1–59'
    if (kind === 'hour' && (n < 1 || n > 23)) return '1–23'
    return null
  }
  if (/^\d+([-,]\d+)*$/.test(value)) return null
  return 'Invalid syntax'
}

function CronBuilderTool() {
  const [fields, setFields] = useState({ minute: '0', hour: '0', dom: '*', month: '*', dow: '*' })
  const [copied, setCopied] = useState(false)

  const setField = useCallback((k, v) => setFields(f => ({ ...f, [k]: v })), [])

  const errors = useMemo(() => ({
    minute: validateCronField(fields.minute, 'minute'),
    hour:   validateCronField(fields.hour,   'hour'),
    dom:    validateCronField(fields.dom,    'dom'),
    month:  validateCronField(fields.month,  'month'),
    dow:    validateCronField(fields.dow,    'dow'),
  }), [fields])

  const hasErrors = Object.values(errors).some(Boolean)

  const { description, raw } = useMemo(() =>
    describeCron(fields.minute, fields.hour, fields.dom, fields.month, fields.dow),
    [fields]
  )

  function applyPreset(value) {
    const [minute, hour, dom, month, dow] = value.split(' ')
    setFields({ minute, hour, dom, month, dow })
  }

  async function copy() {
    trackTool('cron.build', () => raw)  // Sprint 18: count invocation
    await navigator.clipboard.writeText(raw)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
    logActivity('cron.build', `Built cron expression: ${raw}`, { expression: raw })
  }

  const FIELD_DEFS = [
    { key: 'minute', label: 'Minute',       hint: '0–59  * */n  n-m  n,m' },
    { key: 'hour',   label: 'Hour',         hint: '0–23  * */n  n-m  n,m' },
    { key: 'dom',    label: 'Day of month', hint: '1–31  * */n  n-m  n,m' },
    { key: 'month',  label: 'Month',        hint: '1–12  * */n  n-m  n,m  (Jan–Dec)' },
    { key: 'dow',    label: 'Day of week',  hint: '0–6   * n-m  n,m  (0=Sun)' },
  ]

  return (
    <div className={styles.tabPanel}>

      {/* Preset strip */}
      <div className={styles.presetStrip}>
        <span className={styles.presetLabel}>Presets:</span>
        <div className={styles.presetChips}>
          {CRON_PRESETS.map(p => (
            <button
              key={p.value}
              className={styles.chip}
              onClick={() => applyPreset(p.value)}
            >
              {p.label}
            </button>
          ))}
        </div>
      </div>

      {/* Five-field grid */}
      <div className={styles.cronGrid}>
        {FIELD_DEFS.map(({ key, label, hint }) => (
          <div key={key} className={styles.cronField}>
            <label className={styles.fieldLabel}>{label}</label>
            <input
              type="text"
              className={`${styles.cronInput} ${errors[key] ? styles.cronInputError : ''}`}
              value={fields[key]}
              onChange={e => setField(key, e.target.value)}
              spellCheck={false}
              autoCapitalize="off"
            />
            {errors[key]
              ? <span className={styles.cronError}>{errors[key]}</span>
              : <span className={styles.cronHint}>{hint}</span>
            }
            <button
              className={styles.cronAnyBtn}
              onClick={() => setField(key, '*')}
              title="Set to * (any)"
            >*</button>
          </div>
        ))}
      </div>

      {/* Live expression */}
      <div className={styles.cronOutput}>
        <code className={styles.cronExpression}>{raw}</code>
        <button className={styles.copyBtn} onClick={copy} disabled={hasErrors}>
          {copied ? '✓ Copied' : 'Copy'}
        </button>
      </div>

      {/* Human description */}
      <div className={styles.cronDescription}>
        <span className={styles.cronDescIcon}>🕐</span>
        <span>{description}</span>
      </div>

      {/* Quick-reference table */}
      <details className={styles.referenceAccordion}>
        <summary className={styles.referenceSummary}>Cron syntax reference</summary>
        <table className={styles.referenceTable}>
          <thead><tr><th>Syntax</th><th>Meaning</th><th>Example</th></tr></thead>
          <tbody>
            <tr><td><code>*</code></td><td>Any value</td><td><code>* * * * *</code> — every minute</td></tr>
            <tr><td><code>*/n</code></td><td>Every n units</td><td><code>*/5 * * * *</code> — every 5 min</td></tr>
            <tr><td><code>n-m</code></td><td>Range</td><td><code>0 9-17 * * *</code> — hourly, 9am–5pm</td></tr>
            <tr><td><code>n,m</code></td><td>List</td><td><code>0 0 * * 1,5</code> — Mon &amp; Fri midnight</td></tr>
            <tr><td><code>n</code></td><td>Exact value</td><td><code>30 6 * * *</code> — 6:30 AM daily</td></tr>
          </tbody>
        </table>
      </details>
    </div>
  )
}

// ─── Main page shell ────────────────────────────────────────────────────────

const TABS = [
  { id: 'hash',   label: 'Hash Identifier',   icon: '#' },
  { id: 'keygen', label: 'API Key Generator', icon: '🔑' },
  { id: 'qrcode', label: 'QR Code',           icon: '▣' },
  { id: 'cron',   label: 'Cron Builder',      icon: '⏰' },
]

export default function DevUtilsPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState('hash')
  const isGuest = username === 'Guest User'

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  function renderTab() {
    switch (activeTab) {
      case 'hash':   return <HashIdentifierTool />
      case 'keygen': return <ApiKeyGeneratorTool />
      case 'qrcode': return <QRCodeTool />
      case 'cron':   return <CronBuilderTool />
      default:       return <HashIdentifierTool />
    }
  }

  return (
    <div className={styles.page}>

      {/* ── Header ──────────────────────────────────────────────────── */}
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.brand}>
            <span className={styles.brandMark} aria-hidden="true">🧑‍💻</span>
            <span className={styles.brandName}>UsefulTools</span>
          </div>
          <button className={styles.backBtn} onClick={() => navigate('/dashboard')}>
            Dashboard
          </button>
        </div>
        <div className={styles.headerRight}>
          <UserMenu username={username} isGuest={isGuest} variant="light" onLogout={handleLogout} />
        </div>
      </header>

      {/* ── Hero ────────────────────────────────────────────────────── */}
      <section className={styles.hero}>
        <div className={styles.heroGrid} aria-hidden="true" />
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>Dev Utilities</div>
          <h1 className={styles.heroTitle}>
            Dev<br />
            <span className={styles.heroAccent}>Utilities</span>
          </h1>
          <p className={styles.heroSub}>
            Hash identifier, API key generator, QR code generator, and visual
            cron expression builder. Everything runs in your browser — nothing is uploaded.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div className={styles.statCard}>
            <span className={styles.statValue}>{TABS.length}</span>
            <span className={styles.statLabel}>tools</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>0</span>
            <span className={styles.statLabel}>server calls</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>100%</span>
            <span className={styles.statLabel}>private</span>
          </div>
        </div>
      </section>

      <main className={styles.main}>
        {/* ── Tab bar ─────────────────────────────────────────────── */}
        <nav className={styles.tabBar} aria-label="Dev utilities">
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
