/**
 * PlaceholderGeneratorPage.jsx — Sprint 22
 *
 * Placeholder & fake-data generator suite. All generation is client-side
 * (backend frozen). No external library dependency — all data tables are
 * embedded directly.
 *
 * Tabs
 *   1. Lorem Ipsum  — standard / professional / startup prose variations
 *   2. Fake Data    — names, emails, addresses, companies, phones, dates, UUIDs
 *   3. Image        — SVG placeholder with custom dimensions, colour, text
 *   4. JSON Sample  — structured JSON sample data generator with seed support
 *
 * Activity logging : 'placeholder.text', 'placeholder.data',
 *                    'placeholder.image', 'placeholder.json'
 * Metrics          : trackTool wraps each generation operation
 *
 * Privacy rule: log only operation type + count — never generated content
 */

import { useState, useMemo, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logoutUser } from '../../api/apiClient'
import UserMenu from '../../components/UserMenu/UserMenu'
import { logActivity } from '../../utils/logActivity'
import { trackTool } from '../../utils/logMetric'
import styles from './PlaceholderGeneratorPage.module.css'

// ─── Data tables ──────────────────────────────────────────────────────────────

const FIRST_NAMES = [
  'James','Mary','John','Patricia','Robert','Jennifer','Michael','Linda',
  'William','Barbara','David','Elizabeth','Richard','Susan','Joseph','Jessica',
  'Thomas','Sarah','Charles','Karen','Liam','Emma','Noah','Olivia','Elijah',
  'Ava','Oliver','Sophia','Lucas','Isabella','Mason','Mia','Logan','Amelia',
  'Ethan','Harper','Aiden','Evelyn','Jackson','Abigail','Sebastian','Emily',
  'Mateo','Ella','Jack','Elizabeth','Owen','Camila','Theodore','Luna',
]

const LAST_NAMES = [
  'Smith','Johnson','Williams','Brown','Jones','Garcia','Miller','Davis',
  'Rodriguez','Martinez','Hernandez','Lopez','Gonzalez','Wilson','Anderson',
  'Thomas','Taylor','Moore','Jackson','Martin','Lee','Perez','Thompson',
  'White','Harris','Sanchez','Clark','Ramirez','Lewis','Robinson','Walker',
  'Young','Allen','King','Wright','Scott','Torres','Nguyen','Hill','Flores',
  'Green','Adams','Nelson','Baker','Hall','Rivera','Campbell','Mitchell',
]

const DOMAINS = [
  'gmail.com','yahoo.com','outlook.com','hotmail.com','proton.me',
  'icloud.com','company.com','enterprise.io','techcorp.dev','acme.org',
]

const STREETS = [
  'Main St','Oak Ave','Maple Dr','Cedar Ln','Pine Rd','Elm St','Washington Blvd',
  'Park Ave','Lake Dr','River Rd','Highland Ave','Sunset Blvd','Forest Way',
  'Valley Rd','Summit Dr','Meadow Ln','Brook St','Ridge Ave','Harbor Dr',
]

const CITIES = [
  'New York','Los Angeles','Chicago','Houston','Phoenix','Philadelphia',
  'San Antonio','San Diego','Dallas','San Jose','Austin','Jacksonville',
  'Fort Worth','Columbus','Charlotte','Indianapolis','San Francisco','Seattle',
  'Denver','Nashville','Oklahoma City','Portland','Las Vegas','Memphis',
]

const STATES = ['AL','AK','AZ','AR','CA','CO','CT','DE','FL','GA','HI','ID',
  'IL','IN','IA','KS','KY','LA','ME','MD','MA','MI','MN','MS','MO','MT',
  'NE','NV','NH','NJ','NM','NY','NC','ND','OH','OK','OR','PA','RI','SC',
  'SD','TN','TX','UT','VT','VA','WA','WV','WI','WY']

const COMPANY_PREFIXES = [
  'Acme','Apex','Atlas','Blue','Cedar','Delta','Echo','Falcon','Global',
  'Horizon','Innovative','Jade','Knight','Lunar','Matrix','Nexus','Orbit',
  'Peak','Quantum','Rapid','Solar','Titan','Ultra','Vertex','Wave','Zenith',
]

const COMPANY_SUFFIXES = [
  'Solutions','Technologies','Systems','Group','Partners','Ventures','Labs',
  'Industries','Services','Consulting','Digital','Analytics','Networks','Cloud',
]

const LOREM_WORDS = [
  'lorem','ipsum','dolor','sit','amet','consectetur','adipiscing','elit',
  'sed','do','eiusmod','tempor','incididunt','ut','labore','et','dolore',
  'magna','aliqua','enim','ad','minim','veniam','quis','nostrud',
  'exercitation','ullamco','laboris','nisi','aliquip','ex','ea','commodo',
  'consequat','duis','aute','irure','in','reprehenderit','voluptate','velit',
  'esse','cillum','fugiat','nulla','pariatur','excepteur','sint','occaecat',
  'cupidatat','non','proident','sunt','culpa','qui','officia','deserunt',
  'mollit','anim','id','est','laborum',
]

const PROFESSIONAL_WORDS = [
  'leverage','synergy','paradigm','scalable','holistic','agile','robust',
  'innovative','dynamic','strategic','comprehensive','optimized','efficient',
  'enterprise','solution','framework','ecosystem','streamlined','integrated',
  'best-in-class','cutting-edge','next-generation','end-to-end','turnkey',
  'value-added','cross-functional','mission-critical','data-driven','cloud-native',
  'microservices','containerized','orchestrated','automated','intelligent',
]

const STARTUP_WORDS = [
  'disruptive','unicorn','pivot','iterate','ship','move','fast','growth',
  'hacking','product-market','fit','minimum','viable','freemium','b2b','b2c',
  'saas','runway','burn-rate','churn','ltv','cac','mrr','arr','seed',
  'series','pitch','deck','traction','hockey-stick','10x','100x','scale',
]

// ─── Seeded pseudo-random ─────────────────────────────────────────────────────

function mulberry32(seed) {
  return function() {
    seed |= 0; seed = seed + 0x6D2B79F5 | 0
    let t = Math.imul(seed ^ seed >>> 15, 1 | seed)
    t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t
    return ((t ^ t >>> 14) >>> 0) / 4294967296
  }
}

function makeRng(seed) {
  if (!seed) return () => Math.random()
  const n = parseInt(seed, 10)
  return mulberry32(isNaN(n) ? seed.split('').reduce((a, c) => a + c.charCodeAt(0), 0) : n)
}

function pick(arr, rng) { return arr[Math.floor(rng() * arr.length)] }
function randInt(lo, hi, rng) { return lo + Math.floor(rng() * (hi - lo + 1)) }

// ─── Fake data generators ─────────────────────────────────────────────────────

function genName(rng)    { return `${pick(FIRST_NAMES, rng)} ${pick(LAST_NAMES, rng)}` }
function genEmail(rng) {
  const first = pick(FIRST_NAMES, rng).toLowerCase()
  const last  = pick(LAST_NAMES, rng).toLowerCase()
  const sep   = pick(['.', '_', ''], rng)
  return `${first}${sep}${last}${randInt(1, 99, rng)}@${pick(DOMAINS, rng)}`
}
function genPhone(rng) {
  const area = randInt(200, 999, rng)
  const mid  = randInt(200, 999, rng)
  const end  = randInt(1000, 9999, rng)
  return `(${area}) ${mid}-${end}`
}
function genAddress(rng) {
  const num    = randInt(1, 9999, rng)
  const street = pick(STREETS, rng)
  const city   = pick(CITIES, rng)
  const state  = pick(STATES, rng)
  const zip    = randInt(10000, 99999, rng)
  return `${num} ${street}, ${city}, ${state} ${zip}`
}
function genCompany(rng) {
  return `${pick(COMPANY_PREFIXES, rng)} ${pick(COMPANY_SUFFIXES, rng)}`
}
function genDate(rng) {
  const y = randInt(1970, 2024, rng)
  const m = String(randInt(1, 12, rng)).padStart(2, '0')
  const d = String(randInt(1, 28, rng)).padStart(2, '0')
  return `${y}-${m}-${d}`
}
function genUUID(rng) {
  const hex = () => Math.floor(rng() * 16).toString(16)
  const seg = n => Array.from({ length: n }, hex).join('')
  return `${seg(8)}-${seg(4)}-4${seg(3)}-${(8 + Math.floor(rng() * 4)).toString(16)}${seg(3)}-${seg(12)}`
}
function genNumber(lo, hi, rng) { return randInt(lo, hi, rng) }
function genBoolean(rng) { return rng() > 0.5 }
function genUrl(rng) {
  const domain = pick(COMPANY_PREFIXES, rng).toLowerCase()
  const tld = pick(['com','io','dev','org','net'], rng)
  return `https://www.${domain}.${tld}`
}

const FIELD_GENERATORS = {
  name:    genName,
  email:   genEmail,
  phone:   genPhone,
  address: genAddress,
  company: genCompany,
  date:    genDate,
  uuid:    genUUID,
  url:     genUrl,
  age:     (rng) => genNumber(18, 80, rng),
  salary:  (rng) => genNumber(30000, 200000, rng) * 100 / 100,
  boolean: genBoolean,
}

const AVAILABLE_FIELDS = Object.keys(FIELD_GENERATORS)

// ─── Lorem ipsum generators ───────────────────────────────────────────────────

function genLoremSentence(wordList, rng, minW = 8, maxW = 16) {
  const count = randInt(minW, maxW, rng)
  const words = Array.from({ length: count }, () => pick(wordList, rng))
  words[0] = words[0].charAt(0).toUpperCase() + words[0].slice(1)
  return words.join(' ') + '.'
}

function genLoremParagraph(wordList, rng, minS = 3, maxS = 7) {
  const count = randInt(minS, maxS, rng)
  return Array.from({ length: count }, () => genLoremSentence(wordList, rng)).join(' ')
}

function generateLorem({ variant, paragraphs, seed }) {
  const rng = makeRng(seed)
  const wordList = variant === 'professional' ? PROFESSIONAL_WORDS
                 : variant === 'startup'      ? STARTUP_WORDS
                 : LOREM_WORDS

  const paras = Array.from({ length: paragraphs }, () => genLoremParagraph(wordList, rng))

  // For standard lorem, always start with the classic opening
  if (variant === 'standard' && paragraphs > 0) {
    paras[0] = 'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.'
  }

  return paras.join('\n\n')
}

// ─── JSON sample generator ────────────────────────────────────────────────────

function generateJSONSample({ selectedFields, count, seed, wrapArray }) {
  const rng = makeRng(seed)
  const records = Array.from({ length: count }, () => {
    const obj = {}
    for (const field of selectedFields) {
      if (FIELD_GENERATORS[field]) obj[field] = FIELD_GENERATORS[field](rng)
    }
    return obj
  })
  return JSON.stringify(wrapArray ? records : records[0], null, 2)
}

// ─── SVG placeholder ──────────────────────────────────────────────────────────

function generateSVGPlaceholder({ width, height, bgColor, textColor, text }) {
  const displayText = text || `${width}×${height}`
  const fontSize = Math.max(12, Math.min(32, Math.floor(Math.min(width, height) / 8)))
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
  <rect width="${width}" height="${height}" fill="${bgColor}"/>
  <text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle"
    font-family="sans-serif" font-size="${fontSize}" fill="${textColor}" font-weight="600">
    ${displayText}
  </text>
</svg>`
}

// ─── Tabs ─────────────────────────────────────────────────────────────────────

const TABS = [
  { id: 'lorem',  label: 'Lorem Ipsum', icon: '¶' },
  { id: 'data',   label: 'Fake Data',   icon: '⊞' },
  { id: 'image',  label: 'Image',       icon: '🖼' },
  { id: 'json',   label: 'JSON Sample', icon: '{}' },
]

// ─── Shared sub-components ────────────────────────────────────────────────────

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

function CopyButton({ text }) {
  const [copied, setCopied] = useState(false)
  function handleCopy() {
    if (!text) return
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true); setTimeout(() => setCopied(false), 1400)
    })
  }
  return (
    <button type="button" className={styles.copyBtn} onClick={handleCopy} disabled={!text}>
      {copied ? '✓ Copied' : 'Copy'}
    </button>
  )
}

// ─── Lorem tab ────────────────────────────────────────────────────────────────

function LoremTab() {
  const [variant,    setVariant]    = useState('standard')
  const [paragraphs, setParagraphs] = useState(3)
  const [seed,       setSeed]       = useState('')

  const output = useMemo(() => {
    return trackTool('placeholder.text', () =>
      generateLorem({ variant, paragraphs, seed: seed || null })
    )
  }, [variant, paragraphs, seed])

  function handleGenerate() {
    logActivity('placeholder.text', 'Generated lorem ipsum', { variant, paragraphs })
  }

  return (
    <div className={styles.twoColLayout}>
      <div className={styles.panel}>
        <p className={styles.eyebrow}>Configuration</p>
        <h2 className={styles.panelTitle}>Lorem Ipsum Generator</h2>

        <label className={styles.fieldLabel}>Variant</label>
        <div className={styles.variantGrid}>
          {[
            { id: 'standard',     label: 'Standard',     desc: 'Classic Lorem ipsum filler text' },
            { id: 'professional', label: 'Professional', desc: 'Corporate-speak and business jargon' },
            { id: 'startup',      label: 'Startup',      desc: 'Disruption, pivots and growth hacking' },
          ].map(v => (
            <button
              key={v.id}
              type="button"
              className={variant === v.id ? styles.variantActive : styles.variantBtn}
              onClick={() => { setVariant(v.id); handleGenerate() }}
            >
              <span className={styles.variantName}>{v.label}</span>
              <span className={styles.variantDesc}>{v.desc}</span>
            </button>
          ))}
        </div>

        <label className={styles.fieldLabel}>Paragraphs</label>
        <div className={styles.spinnerRow}>
          <button className={styles.spinBtn} onClick={() => setParagraphs(p => Math.max(1, p - 1))}>−</button>
          <span className={styles.spinVal}>{paragraphs}</span>
          <button className={styles.spinBtn} onClick={() => setParagraphs(p => Math.min(20, p + 1))}>+</button>
        </div>

        <label className={styles.fieldLabel}>
          Seed <span className={styles.hint}>(optional — same seed → same output)</span>
        </label>
        <input
          className={styles.textInput}
          value={seed}
          onChange={e => setSeed(e.target.value)}
          placeholder="e.g. 42 or 'myproject'" maxLength={50}
        />
      </div>

      <div className={styles.panel}>
        <div className={styles.outputHeader}>
          <div>
            <p className={styles.eyebrow}>Output</p>
            <h2 className={styles.panelTitle}>Generated Text</h2>
          </div>
          <CopyButton text={output} />
        </div>
        <textarea
          className={styles.outputTextarea}
          value={output}
          readOnly
          rows={16}
          aria-label="Generated lorem ipsum"
        />
        <p className={styles.outputMeta}>
          {output.split(/\s+/).filter(Boolean).length} words ·{' '}
          {output.length} characters
        </p>
      </div>
    </div>
  )
}

// ─── Fake Data tab ────────────────────────────────────────────────────────────

function FakeDataTab() {
  const [count,  setCount]  = useState(5)
  const [seed,   setSeed]   = useState('')
  const [fields, setFields] = useState(['name', 'email', 'company'])
  const [format, setFormat] = useState('table')

  function toggleField(f) {
    setFields(prev =>
      prev.includes(f) ? (prev.length > 1 ? prev.filter(x => x !== f) : prev) : [...prev, f]
    )
  }

  const records = useMemo(() => {
    const rng = makeRng(seed || null)
    return trackTool('placeholder.data', () =>
      Array.from({ length: count }, () => {
        const obj = {}
        for (const f of fields) {
          if (FIELD_GENERATORS[f]) obj[f] = FIELD_GENERATORS[f](rng)
        }
        return obj
      })
    )
  }, [count, fields, seed])

  const output = useMemo(() => {
    if (format === 'json') return JSON.stringify(records, null, 2)
    if (format === 'csv') {
      const headers = fields.join(',')
      const rows = records.map(r => fields.map(f => `"${String(r[f]).replace(/"/g, '""')}"`).join(','))
      return [headers, ...rows].join('\n')
    }
    return null // table renders via JSX
  }, [records, format, fields])

  function handleCopy() {
    const text = output || records.map(r => fields.map(f => r[f]).join('\t')).join('\n')
    navigator.clipboard.writeText(text)
    logActivity('placeholder.data', 'Copied fake data', { count, fields: fields.length, format })
  }

  function handleDownload() {
    if (!output) return
    const ext = format === 'json' ? 'json' : 'csv'
    const mime = format === 'json' ? 'application/json' : 'text/csv'
    const blob = new Blob([output], { type: mime })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = `fake-data-${Date.now()}.${ext}`; a.click()
    URL.revokeObjectURL(url)
    logActivity('placeholder.data', 'Downloaded fake data', { count, fields: fields.length, format })
  }

  return (
    <div className={styles.fakeDataLayout}>
      <div className={styles.panel}>
        <p className={styles.eyebrow}>Configuration</p>
        <h2 className={styles.panelTitle}>Fake Data Generator</h2>

        <label className={styles.fieldLabel}>Fields</label>
        <div className={styles.fieldCheckGrid}>
          {AVAILABLE_FIELDS.map(f => (
            <label key={f} className={styles.checkLabel}>
              <input
                type="checkbox"
                checked={fields.includes(f)}
                onChange={() => toggleField(f)}
              />
              {f}
            </label>
          ))}
        </div>

        <label className={styles.fieldLabel}>Records</label>
        <div className={styles.spinnerRow}>
          <button className={styles.spinBtn} onClick={() => setCount(c => Math.max(1, c - 1))}>−</button>
          <span className={styles.spinVal}>{count}</span>
          <button className={styles.spinBtn} onClick={() => setCount(c => Math.min(100, c + 1))}>+</button>
        </div>

        <label className={styles.fieldLabel}>Format</label>
        <div className={styles.formatBtnRow}>
          {['table', 'json', 'csv'].map(f => (
            <button
              key={f}
              type="button"
              className={format === f ? styles.fmtActive : styles.fmtBtn}
              onClick={() => setFormat(f)}
            >
              {f.toUpperCase()}
            </button>
          ))}
        </div>

        <label className={styles.fieldLabel}>
          Seed <span className={styles.hint}>(optional)</span>
        </label>
        <input
          className={styles.textInput}
          value={seed}
          onChange={e => setSeed(e.target.value)}
          placeholder="e.g. 42" maxLength={50}
        />

        <div className={styles.dataActions}>
          <button type="button" className={styles.copyBtn} onClick={handleCopy}>Copy</button>
          {format !== 'table' && (
            <button type="button" className={styles.secondaryBtn} onClick={handleDownload}>
              Download .{format === 'json' ? 'json' : 'csv'}
            </button>
          )}
        </div>
      </div>

      <div className={styles.panel}>
        <p className={styles.eyebrow}>Result</p>
        <h2 className={styles.panelTitle}>{count} Records</h2>

        {format === 'table' ? (
          <div className={styles.tableWrap}>
            <table className={styles.dataTable}>
              <thead>
                <tr>{fields.map(f => <th key={f}>{f}</th>)}</tr>
              </thead>
              <tbody>
                {records.map((r, i) => (
                  <tr key={i}>
                    {fields.map(f => <td key={f}>{String(r[f])}</td>)}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <textarea
            className={styles.outputTextarea}
            value={output}
            readOnly
            rows={18}
            spellCheck={false}
          />
        )}
      </div>
    </div>
  )
}

// ─── Image tab ────────────────────────────────────────────────────────────────

function ImageTab() {
  const [width,     setWidth]     = useState(800)
  const [height,    setHeight]    = useState(600)
  const [bgColor,   setBgColor]   = useState('#cccccc')
  const [textColor, setTextColor] = useState('#666666')
  const [customText, setCustomText] = useState('')

  const svgContent = useMemo(() =>
    trackTool('placeholder.image', () =>
      generateSVGPlaceholder({ width, height, bgColor, textColor, text: customText })
    ),
    [width, height, bgColor, textColor, customText]
  )

  const dataUrl = useMemo(() =>
    `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svgContent)}`,
    [svgContent]
  )

  function downloadSVG() {
    const blob = new Blob([svgContent], { type: 'image/svg+xml' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = `placeholder-${width}x${height}.svg`; a.click()
    URL.revokeObjectURL(url)
    logActivity('placeholder.image', 'Downloaded SVG placeholder', { width, height })
  }

  function copyURL() {
    navigator.clipboard.writeText(dataUrl)
    logActivity('placeholder.image', 'Copied SVG data URL', { width, height })
  }

  function copySVGCode() {
    navigator.clipboard.writeText(svgContent)
  }

  // Common preset sizes
  const PRESETS = [
    { label: 'HD',        w: 1280, h: 720 },
    { label: 'Full HD',   w: 1920, h: 1080 },
    { label: 'Square',    w: 600,  h: 600 },
    { label: 'Banner',    w: 728,  h: 90 },
    { label: 'Thumbnail', w: 320,  h: 240 },
    { label: 'Avatar',    w: 128,  h: 128 },
    { label: 'OG Image',  w: 1200, h: 630 },
  ]

  return (
    <div className={styles.imageLayout}>
      <div className={styles.panel}>
        <p className={styles.eyebrow}>Configuration</p>
        <h2 className={styles.panelTitle}>Image Placeholder</h2>

        <label className={styles.fieldLabel}>Presets</label>
        <div className={styles.presetRow}>
          {PRESETS.map(p => (
            <button
              key={p.label}
              type="button"
              className={styles.presetBtn}
              onClick={() => { setWidth(p.w); setHeight(p.h) }}
            >
              {p.label}<br />
              <span className={styles.presetDim}>{p.w}×{p.h}</span>
            </button>
          ))}
        </div>

        <div className={styles.dimRow}>
          <div>
            <label className={styles.fieldLabel}>Width (px)</label>
            <input
              type="number" className={styles.textInput} value={width} min={1} max={4096}
              onChange={e => setWidth(Math.max(1, Math.min(4096, +e.target.value)))}
            />
          </div>
          <div>
            <label className={styles.fieldLabel}>Height (px)</label>
            <input
              type="number" className={styles.textInput} value={height} min={1} max={4096}
              onChange={e => setHeight(Math.max(1, Math.min(4096, +e.target.value)))}
            />
          </div>
        </div>

        <div className={styles.colorRow}>
          <div>
            <label className={styles.fieldLabel}>Background</label>
            <div className={styles.colorPickRow}>
              <input type="color" className={styles.colorPickSm} value={bgColor} onChange={e => setBgColor(e.target.value)} />
              <input className={styles.textInput} value={bgColor} onChange={e => setBgColor(e.target.value)} style={{ width: 90 }} />
            </div>
          </div>
          <div>
            <label className={styles.fieldLabel}>Text Color</label>
            <div className={styles.colorPickRow}>
              <input type="color" className={styles.colorPickSm} value={textColor} onChange={e => setTextColor(e.target.value)} />
              <input className={styles.textInput} value={textColor} onChange={e => setTextColor(e.target.value)} style={{ width: 90 }} />
            </div>
          </div>
        </div>

        <label className={styles.fieldLabel}>Custom Text <span className={styles.hint}>(leave blank for dimensions)</span></label>
        <input
          className={styles.textInput}
          value={customText}
          onChange={e => setCustomText(e.target.value)}
          placeholder={`${width}×${height}`}
          maxLength={120}
          aria-label="Custom placeholder text"
        />

        <div className={styles.imageActions}>
          <button type="button" className={styles.primaryBtn} onClick={downloadSVG}>Download SVG</button>
          <button type="button" className={styles.secondaryBtn} onClick={copyURL}>Copy Data URL</button>
          <button type="button" className={styles.secondaryBtn} onClick={copySVGCode}>Copy SVG Code</button>
        </div>
      </div>

      <div className={styles.panel}>
        <p className={styles.eyebrow}>Preview</p>
        <h2 className={styles.panelTitle}>{width} × {height}</h2>
        <div className={styles.imgPreviewWrap}>
          <img
            src={dataUrl}
            alt={`Placeholder ${width}x${height}`}
            className={styles.imgPreview}
            style={{ maxWidth: '100%', maxHeight: 400 }}
          />
        </div>
        <div className={styles.imgHtmlSnippet}>
          <p className={styles.eyebrow}>HTML snippet</p>
          <code className={styles.snippetCode}>
            {`<img src="data:image/svg+xml;..." width="${width}" height="${height}" alt="placeholder">`}
          </code>
        </div>
      </div>
    </div>
  )
}

// ─── JSON Sample tab ──────────────────────────────────────────────────────────

function JSONSampleTab() {
  const [selectedFields, setSelectedFields] = useState(['name', 'email', 'company', 'address'])
  const [count,      setCount]      = useState(3)
  const [seed,       setSeed]       = useState('')
  const [wrapArray,  setWrapArray]  = useState(true)
  const [copied,     setCopied]     = useState(false)

  function toggleField(f) {
    setSelectedFields(prev =>
      prev.includes(f) ? (prev.length > 1 ? prev.filter(x => x !== f) : prev) : [...prev, f]
    )
  }

  const output = useMemo(() =>
    trackTool('placeholder.json', () =>
      generateJSONSample({ selectedFields, count: wrapArray ? count : 1, seed: seed || null, wrapArray })
    ),
    [selectedFields, count, seed, wrapArray]
  )

  function handleCopy() {
    navigator.clipboard.writeText(output).then(() => {
      setCopied(true); setTimeout(() => setCopied(false), 1400)
      logActivity('placeholder.json', 'Copied JSON sample', { fields: selectedFields.length, count })
    })
  }

  function handleDownload() {
    const blob = new Blob([output], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = `sample-data-${Date.now()}.json`; a.click()
    URL.revokeObjectURL(url)
    logActivity('placeholder.json', 'Downloaded JSON sample', { fields: selectedFields.length, count })
  }

  return (
    <div className={styles.twoColLayout}>
      <div className={styles.panel}>
        <p className={styles.eyebrow}>Configuration</p>
        <h2 className={styles.panelTitle}>JSON Sample Generator</h2>

        <label className={styles.fieldLabel}>Fields to Include</label>
        <div className={styles.fieldCheckGrid}>
          {AVAILABLE_FIELDS.map(f => (
            <label key={f} className={styles.checkLabel}>
              <input
                type="checkbox"
                checked={selectedFields.includes(f)}
                onChange={() => toggleField(f)}
              />
              {f}
            </label>
          ))}
        </div>

        <label className={styles.fieldLabel}>Output</label>
        <label className={styles.checkLabel} style={{ marginBottom: 'var(--space-sm)' }}>
          <input
            type="checkbox"
            checked={wrapArray}
            onChange={e => setWrapArray(e.target.checked)}
          />
          Wrap in array
        </label>

        {wrapArray && (
          <>
            <label className={styles.fieldLabel}>Record Count</label>
            <div className={styles.spinnerRow}>
              <button className={styles.spinBtn} onClick={() => setCount(c => Math.max(1, c - 1))}>−</button>
              <span className={styles.spinVal}>{count}</span>
              <button className={styles.spinBtn} onClick={() => setCount(c => Math.min(50, c + 1))}>+</button>
            </div>
          </>
        )}

        <label className={styles.fieldLabel}>
          Seed <span className={styles.hint}>(optional — for reproducible data)</span>
        </label>
        <input
          className={styles.textInput}
          value={seed}
          onChange={e => setSeed(e.target.value)}
          placeholder="e.g. 42" maxLength={50}
        />

        <div className={styles.dataActions}>
          <button type="button" className={styles.primaryBtn} onClick={handleCopy}>
            {copied ? '✓ Copied' : 'Copy JSON'}
          </button>
          <button type="button" className={styles.secondaryBtn} onClick={handleDownload}>
            Download .json
          </button>
        </div>
      </div>

      <div className={styles.panel}>
        <p className={styles.eyebrow}>Output</p>
        <h2 className={styles.panelTitle}>JSON Preview</h2>
        <textarea
          className={styles.outputTextarea}
          value={output}
          readOnly
          rows={22}
          spellCheck={false}
          aria-label="Generated JSON sample"
        />
        <p className={styles.outputMeta}>
          {output.length} characters · {wrapArray ? `${count} records` : '1 record'} · {selectedFields.length} fields
        </p>
      </div>
    </div>
  )
}

// ─── Main component ───────────────────────────────────────────────────────────

export default function PlaceholderGeneratorPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const isGuest = username === 'Guest User'
  const [activeTab, setActiveTab] = useState('lorem')

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
            <span className={styles.brandMark} aria-hidden="true">¶</span>
            <span className={styles.brandName}>UsefulTools</span>
          </div>
          <button className={styles.backBtn} onClick={() => navigate('/dashboard')}>Dashboard</button>
        </div>
        <UserMenu username={username} isGuest={isGuest} variant="light" onLogout={handleLogout} />
      </header>

      <section className={styles.hero}>
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>Sprint 22</div>
          <h1 className={styles.heroTitle}>Placeholder Generator</h1>
          <p className={styles.heroSub}>
            Generate Lorem Ipsum in three flavours, fake data records in table/JSON/CSV,
            SVG image placeholders in any size, and structured JSON sample data — all with seed support.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div><strong>3</strong><span>text modes</span></div>
          <div><strong>{Object.keys(FIELD_GENERATORS).length}</strong><span>data fields</span></div>
          <div><strong>∞</strong><span>seeded</span></div>
        </div>
      </section>

      <main className={styles.main}>
        <TabBar tabs={TABS} active={activeTab} onChange={setActiveTab} />
        {activeTab === 'lorem' && <LoremTab />}
        {activeTab === 'data'  && <FakeDataTab />}
        {activeTab === 'image' && <ImageTab />}
        {activeTab === 'json'  && <JSONSampleTab />}
      </main>
    </div>
  )
}
