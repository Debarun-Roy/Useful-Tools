import React, { useState, useEffect, useRef } from 'react'
import { fetchRequestHeaders } from '../../api/apiClient'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logoutUser } from '../../api/apiClient'
import styles from './WebDevHelpersPage.module.css'
import { trackTool } from '../../utils/logMetric'
import { logActivity } from '../../utils/logActivity'

const TABS = [
  { id: 'gradient', label: 'CSS Gradient',  icon: '∇' },
  { id: 'shadow',   label: 'Box Shadow',    icon: '☁' },
  { id: 'flexbox',  label: 'Flexbox',       icon: '⊞' },
  { id: 'robots',   label: 'Robots.txt',    icon: '🤖' },
  { id: 'favicon',  label: 'Favicon',       icon: '⭐' },
  { id: 'headers',  label: 'HTTP Headers',  icon: '📋' },
  { id: 'rest',     label: 'REST Tester',   icon: '⚡' },
]

// ── Shared helpers ────────────────────────────────────────────────────────────

function CopyButton({ text, disabled = false }) {
  const [copied, setCopied] = useState(false)
  function handleCopy() {
    navigator.clipboard.writeText(text).catch(() => {})
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }
  return (
    <button
      className={copied ? styles.copyBtnDone : styles.copyBtn}
      onClick={handleCopy}
      disabled={disabled || !text}
    >
      {copied ? '✓ Copied' : 'Copy'}
    </button>
  )
}

// ── CSS Gradient ──────────────────────────────────────────────────────────────

function GradientTool() {
  const [type, setType] = useState('linear')
  const [direction, setDirection] = useState('to right')
  const [colors, setColors] = useState(['#ff0000', '#0000ff'])
  const [stops, setStops] = useState(['', ''])

  const gradientCSS = (() => {
    const colorStops = colors.map((c, i) => `${c}${stops[i] ? ` ${stops[i]}` : ''}`).join(', ')
    if (type === 'linear') {
      return `background: linear-gradient(${direction}, ${colorStops});`
    } else {
      return `background: radial-gradient(${direction}, ${colorStops});`
    }
  })()

  // Debounced log: settles after the user finishes tweaking. logActivity
  // coalesces rapid changes into a single entry per 1500 ms. Skip the
  // initial mount so just opening the page doesn't record an entry.
  const didMount = useRef(false)
  useEffect(() => {
    if (!didMount.current) { didMount.current = true; return }
    logActivity(
      'webdev.generate',
      `Built ${type} gradient with ${colors.length} colors`,
      { tool: 'gradient', type, colorCount: colors.length }
    )
  }, [type, direction, colors, stops])

  function addColor() {
    setColors([...colors, '#ffffff'])
    setStops([...stops, ''])
  }

  function updateColor(index, color) {
    const newColors = [...colors]
    newColors[index] = color
    setColors(newColors)
  }

  function updateStop(index, stop) {
    const newStops = [...stops]
    newStops[index] = stop
    setStops(newStops)
  }

  function removeColor(index) {
    if (colors.length > 2) {
      setColors(colors.filter((_, i) => i !== index))
      setStops(stops.filter((_, i) => i !== index))
    }
  }

  return (
    <div className={styles.tabPanel}>
      <div className={styles.controls}>
        <div className={styles.controlGroup}>
          <label>Type</label>
          <select value={type} onChange={e => setType(e.target.value)}>
            <option value="linear">Linear</option>
            <option value="radial">Radial</option>
          </select>
        </div>
        <div className={styles.controlGroup}>
          <label>Direction</label>
          <select value={direction} onChange={e => setDirection(e.target.value)}>
            {type === 'linear' ? (
              <>
                <option value="to right">To Right</option>
                <option value="to left">To Left</option>
                <option value="to bottom">To Bottom</option>
                <option value="to top">To Top</option>
                <option value="45deg">45°</option>
                <option value="135deg">135°</option>
              </>
            ) : (
              <>
                <option value="circle">Circle</option>
                <option value="ellipse">Ellipse</option>
                <option value="circle at center">Circle at Center</option>
              </>
            )}
          </select>
        </div>
      </div>

      <div className={styles.colors}>
        <h4>Colors</h4>
        {colors.map((color, i) => (
          <div key={i} className={styles.colorRow}>
            <input
              type="color"
              value={color}
              onChange={e => updateColor(i, e.target.value)}
            />
            <input
              type="text"
              placeholder="e.g. 50%"
              value={stops[i]}
              onChange={e => updateStop(i, e.target.value)}
              className={styles.stopInput}
            />
            {colors.length > 2 && (
              <button onClick={() => removeColor(i)} className={styles.removeBtn}>×</button>
            )}
          </div>
        ))}
        <button onClick={addColor} className={styles.addBtn}>+ Add Color</button>
      </div>

      <div className={styles.preview}>
        <div
          className={styles.gradientBox}
          style={{ background: gradientCSS.replace('background: ', '') }}
        />
      </div>

      <div className={styles.output}>
        <code className={styles.cssCode}>{gradientCSS}</code>
        <CopyButton text={gradientCSS} />
      </div>
    </div>
  )
}

// ── Box Shadow ────────────────────────────────────────────────────────────────

function ShadowTool() {
  const [hOffset, setHOffset] = useState(5)
  const [vOffset, setVOffset] = useState(5)
  const [blur, setBlur] = useState(10)
  const [spread, setSpread] = useState(0)
  const [color, setColor] = useState('#000000')
  const [inset, setInset] = useState(false)

  const shadowCSS = `box-shadow: ${inset ? 'inset ' : ''}${hOffset}px ${vOffset}px ${blur}px ${spread}px ${color};`

  return (
    <div className={styles.tabPanel}>
      <div className={styles.sliders}>
        <div className={styles.sliderGroup}>
          <label>Horizontal Offset: {hOffset}px</label>
          <input
            type="range"
            min="-50"
            max="50"
            value={hOffset}
            onChange={e => setHOffset(parseInt(e.target.value))}
          />
        </div>
        <div className={styles.sliderGroup}>
          <label>Vertical Offset: {vOffset}px</label>
          <input
            type="range"
            min="-50"
            max="50"
            value={vOffset}
            onChange={e => setVOffset(parseInt(e.target.value))}
          />
        </div>
        <div className={styles.sliderGroup}>
          <label>Blur: {blur}px</label>
          <input
            type="range"
            min="0"
            max="100"
            value={blur}
            onChange={e => setBlur(parseInt(e.target.value))}
          />
        </div>
        <div className={styles.sliderGroup}>
          <label>Spread: {spread}px</label>
          <input
            type="range"
            min="-50"
            max="50"
            value={spread}
            onChange={e => setSpread(parseInt(e.target.value))}
          />
        </div>
        <div className={styles.sliderGroup}>
          <label>Color</label>
          <input
            type="color"
            value={color}
            onChange={e => setColor(e.target.value)}
          />
        </div>
        <label className={styles.checkLabel}>
          <input
            type="checkbox"
            checked={inset}
            onChange={e => setInset(e.target.checked)}
          />
          Inset
        </label>
      </div>

      <div className={styles.preview}>
        <div
          className={styles.shadowBox}
          style={{ boxShadow: shadowCSS.replace('box-shadow: ', '') }}
        />
      </div>

      <div className={styles.output}>
        <code className={styles.cssCode}>{shadowCSS}</code>
        <CopyButton text={shadowCSS} />
      </div>
    </div>
  )
}

// ── Flexbox ───────────────────────────────────────────────────────────────────

function FlexboxTool() {
  const [flexDirection, setFlexDirection] = useState('row')
  const [justifyContent, setJustifyContent] = useState('flex-start')
  const [alignItems, setAlignItems] = useState('stretch')
  const [flexWrap, setFlexWrap] = useState('nowrap')

  const containerStyle = {
    display: 'flex',
    flexDirection,
    justifyContent,
    alignItems,
    flexWrap,
    width: '100%',
    height: '200px',
    border: '2px dashed var(--clr-border)',
    background: 'var(--clr-bg)',
  }

  const itemStyle = {
    width: '50px',
    height: '50px',
    background: 'var(--clr-primary)',
    margin: '5px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: '#fff',
    fontWeight: 'bold',
  }

  const cssCode = `.container {
  display: flex;
  flex-direction: ${flexDirection};
  justify-content: ${justifyContent};
  align-items: ${alignItems};
  flex-wrap: ${flexWrap};
}`

  return (
    <div className={styles.tabPanel}>
      <div className={styles.controls}>
        <div className={styles.controlGroup}>
          <label>Flex Direction</label>
          <select value={flexDirection} onChange={e => setFlexDirection(e.target.value)}>
            <option value="row">Row</option>
            <option value="row-reverse">Row Reverse</option>
            <option value="column">Column</option>
            <option value="column-reverse">Column Reverse</option>
          </select>
        </div>
        <div className={styles.controlGroup}>
          <label>Justify Content</label>
          <select value={justifyContent} onChange={e => setJustifyContent(e.target.value)}>
            <option value="flex-start">Flex Start</option>
            <option value="flex-end">Flex End</option>
            <option value="center">Center</option>
            <option value="space-between">Space Between</option>
            <option value="space-around">Space Around</option>
            <option value="space-evenly">Space Evenly</option>
          </select>
        </div>
        <div className={styles.controlGroup}>
          <label>Align Items</label>
          <select value={alignItems} onChange={e => setAlignItems(e.target.value)}>
            <option value="stretch">Stretch</option>
            <option value="flex-start">Flex Start</option>
            <option value="flex-end">Flex End</option>
            <option value="center">Center</option>
            <option value="baseline">Baseline</option>
          </select>
        </div>
        <div className={styles.controlGroup}>
          <label>Flex Wrap</label>
          <select value={flexWrap} onChange={e => setFlexWrap(e.target.value)}>
            <option value="nowrap">No Wrap</option>
            <option value="wrap">Wrap</option>
            <option value="wrap-reverse">Wrap Reverse</option>
          </select>
        </div>
      </div>

      <div className={styles.preview}>
        <div style={containerStyle}>
          {[1, 2, 3].map(i => (
            <div key={i} style={itemStyle}>{i}</div>
          ))}
        </div>
      </div>

      <div className={styles.output}>
        <pre className={styles.cssCode}>{cssCode}</pre>
        <CopyButton text={cssCode} />
      </div>
    </div>
  )
}

// ── Robots.txt ────────────────────────────────────────────────────────────────

function RobotsTool() {
  const [userAgent, setUserAgent] = useState('*')
  const [disallow, setDisallow] = useState(['/admin/', '/private/'])
  const [allow, setAllow] = useState([])
  const [sitemap, setSitemap] = useState('https://example.com/sitemap.xml')
  const [crawlDelay, setCrawlDelay] = useState('')

  const robotsTxt = `User-agent: ${userAgent}
${disallow.map(d => `Disallow: ${d}`).join('\n')}
${allow.map(a => `Allow: ${a}`).join('\n')}
${crawlDelay ? `Crawl-delay: ${crawlDelay}` : ''}
${sitemap ? `Sitemap: ${sitemap}` : ''}`.trim()

  function addDisallow() {
    setDisallow([...disallow, ''])
  }

  function updateDisallow(index, value) {
    const newDisallow = [...disallow]
    newDisallow[index] = value
    setDisallow(newDisallow)
  }

  function removeDisallow(index) {
    setDisallow(disallow.filter((_, i) => i !== index))
  }

  function addAllow() {
    setAllow([...allow, ''])
  }

  function updateAllow(index, value) {
    const newAllow = [...allow]
    newAllow[index] = value
    setAllow(newAllow)
  }

  function removeAllow(index) {
    setAllow(allow.filter((_, i) => i !== index))
  }

  return (
    <div className={styles.tabPanel}>
      <div className={styles.robotsForm}>
        <div className={styles.controlGroup}>
          <label>User-agent</label>
          <input
            type="text"
            value={userAgent}
            onChange={e => setUserAgent(e.target.value)}
            placeholder="*"
          />
        </div>
        <div className={styles.controlGroup}>
          <label>Disallow</label>
          {disallow.map((d, i) => (
            <div key={i} className={styles.pathRow}>
              <input
                type="text"
                value={d}
                onChange={e => updateDisallow(i, e.target.value)}
                placeholder="/path/"
              />
              <button onClick={() => removeDisallow(i)} className={styles.removeBtn}>×</button>
            </div>
          ))}
          <button onClick={addDisallow} className={styles.addBtn}>+ Add Disallow</button>
        </div>
        <div className={styles.controlGroup}>
          <label>Allow</label>
          {allow.map((a, i) => (
            <div key={i} className={styles.pathRow}>
              <input
                type="text"
                value={a}
                onChange={e => updateAllow(i, e.target.value)}
                placeholder="/path/"
              />
              <button onClick={() => removeAllow(i)} className={styles.removeBtn}>×</button>
            </div>
          ))}
          <button onClick={addAllow} className={styles.addBtn}>+ Add Allow</button>
        </div>
        <div className={styles.controlGroup}>
          <label>Crawl Delay (seconds)</label>
          <input
            type="number"
            value={crawlDelay}
            onChange={e => setCrawlDelay(e.target.value)}
            placeholder="Optional"
          />
        </div>
        <div className={styles.controlGroup}>
          <label>Sitemap URL</label>
          <input
            type="url"
            value={sitemap}
            onChange={e => setSitemap(e.target.value)}
            placeholder="https://example.com/sitemap.xml"
          />
        </div>
      </div>

      <div className={styles.output}>
        <pre className={styles.txtCode}>{robotsTxt}</pre>
        <CopyButton text={robotsTxt} />
      </div>
    </div>
  )
}

// ── Favicon ───────────────────────────────────────────────────────────────────

function FaviconTool() {
  const [text, setText] = useState('F')
  const [bgColor, setBgColor] = useState('#007bff')
  const [textColor, setTextColor] = useState('#ffffff')
  const [size, setSize] = useState(32)

  function generateFavicon() {
    const canvas = document.createElement('canvas')
    canvas.width = size
    canvas.height = size
    const ctx = canvas.getContext('2d')

    // Background
    ctx.fillStyle = bgColor
    ctx.fillRect(0, 0, size, size)

    // Text
    ctx.fillStyle = textColor
    ctx.font = `${size * 0.6}px Arial`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(text, size / 2, size / 2)

    return canvas.toDataURL('image/png')
  }

  const faviconData = generateFavicon()

  return (
    <div className={styles.tabPanel}>
      <div className={styles.controls}>
        <div className={styles.controlGroup}>
          <label>Text</label>
          <input
            type="text"
            value={text}
            onChange={e => setText(e.target.value.slice(0, 2))}
            maxLength={2}
          />
        </div>
        <div className={styles.controlGroup}>
          <label>Background Color</label>
          <input
            type="color"
            value={bgColor}
            onChange={e => setBgColor(e.target.value)}
          />
        </div>
        <div className={styles.controlGroup}>
          <label>Text Color</label>
          <input
            type="color"
            value={textColor}
            onChange={e => setTextColor(e.target.value)}
          />
        </div>
        <div className={styles.controlGroup}>
          <label>Size</label>
          <select value={size} onChange={e => setSize(parseInt(e.target.value))}>
            <option value={16}>16x16</option>
            <option value={32}>32x32</option>
            <option value={64}>64x64</option>
          </select>
        </div>
      </div>

      <div className={styles.preview}>
        <img src={faviconData} alt="Favicon Preview" />
      </div>

      <div className={styles.output}>
        <p>Download the favicon and add to your HTML:</p>
        <code className={styles.htmlCode}>
          {`<link rel="icon" type="image/png" href="favicon.png">`}
        </code>
        <br />
        <a
          href={faviconData}
          download="favicon.png"
          className={styles.downloadBtn}
        >
          Download PNG
        </a>
      </div>
    </div>
  )
}

// ── Page Shell ────────────────────────────────────────────────────────────────

export default function WebDevHelpersPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState('gradient')

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  function renderTab() {
    switch (activeTab) {
      case 'gradient': {
        return <GradientTool />
      }
      case 'shadow': {
        return <ShadowTool />
      }
      case 'flexbox': {
        return <FlexboxTool />
      }
      case 'robots': {
        return <RobotsTool />
      }
      case 'favicon': {
        return <FaviconTool />
      }
      case 'headers': {
        return <HttpHeadersTool />
      }
      case 'rest': {
        return <RestTesterTool />
      }
      default: {
        return <GradientTool />
      }
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
          <span className={styles.userBadge}>{username}</span>
          <button className={styles.logoutBtn} onClick={handleLogout}>Sign out</button>
        </div>
      </header>

      {/* ── Hero ────────────────────────────────────────────────────── */}
      <section className={styles.hero}>
        <div className={styles.heroGrid} aria-hidden="true" />
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>Web Dev Helpers</div>
          <h1 className={styles.heroTitle}>
            Web Dev<br />
            <span className={styles.heroAccent}>Helpers</span>
          </h1>
          <p className={styles.heroSub}>
            Essential web development tools: CSS gradient generator, box-shadow builder,
            Flexbox playground, robots.txt creator, favicon generator, HTTP header viewer, 
            and REST API tester — all in one place to supercharge your workflow.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div className={styles.statCard}>
            <span className={styles.statValue}>{TABS.length}</span>
            <span className={styles.statLabel}>helpers</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>0</span>
            <span className={styles.statLabel}>server calls</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>∞</span>
            <span className={styles.statLabel}>productivity</span>
          </div>
        </div>
      </section>

      <main className={styles.main}>
        {/* ── Tab bar ─────────────────────────────────────────────── */}
        <nav className={styles.tabBar} aria-label="Web dev helpers">
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

// ─── HTTP Headers Tool ────────────────────────────────────────────────────────
 
function HttpHeadersTool() {
  const [data,     setData]     = React.useState(null)
  const [loading,  setLoading]  = React.useState(true)
  const [error,    setError]    = React.useState(null)
  const [copied,   setCopied]   = React.useState(null)
 
  async function load() {
    setLoading(true)
    setError(null)
    try {
      const { data: res } = await fetchRequestHeaders()
      if (res?.success) setData(res.data)
      else setError(res?.message || 'Failed to fetch headers')
    } catch {
      setError('Network error — make sure you are logged in')
    } finally {
      setLoading(false)
    }
  }
 
  React.useEffect(() => { load() }, [])
 
  async function copyValue(value) {
    await navigator.clipboard.writeText(value)
    setCopied(value)
    setTimeout(() => setCopied(null), 1400)
  }
 
  const headerCount = data?.headers ? Object.keys(data.headers).length : 0
 
  return (
    <div className={styles.tabPanel}>
      <div className={styles.headerToolBar}>
        <div>
          <p className={styles.toolDescription}>
            These are the exact HTTP request headers your browser sent to the
            server for this page request. Cookie and authorization headers are
            redacted.
          </p>
        </div>
        <button className={styles.refreshBtn} onClick={load} disabled={loading}>
          {loading ? '…' : '↻ Refresh'}
        </button>
      </div>
 
      {loading && (
        <div className={styles.loadingCard}>Fetching headers from server…</div>
      )}
 
      {error && !loading && (
        <div className={styles.errorCard}>{error}</div>
      )}
 
      {data && !loading && (
        <>
          {/* Meta row */}
          <div className={styles.headerMetaRow}>
            <span className={styles.metaBadge}>{data.protocol}</span>
            <span className={styles.metaBadge}>{data.method}</span>
            <span className={styles.metaBadge}>
              {headerCount} header{headerCount !== 1 ? 's' : ''}
            </span>
            {data.remoteAddr && (
              <span className={styles.metaBadge}>from {data.remoteAddr}</span>
            )}
          </div>
 
          {/* Headers table */}
          <div className={styles.headersTableWrap}>
            <table className={styles.headersTable}>
              <thead>
                <tr>
                  <th>Header</th>
                  <th>Value</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {Object.entries(data.headers).map(([name, value]) => (
                  <tr key={name} className={styles.headerRow}>
                    <td className={styles.headerNameCell}>{name}</td>
                    <td className={styles.headerValueCell}>{value}</td>
                    <td>
                      <button
                        className={styles.copyBtnSm}
                        onClick={() => copyValue(value)}
                      >
                        {copied === value ? '✓' : 'Copy'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
 
          {/* Redacted notice */}
          <p className={styles.redactedNote}>
            🔒 <strong>Cookie</strong> and <strong>X-XSRF-TOKEN</strong> headers
            are redacted server-side to protect your session credentials.
          </p>
        </>
      )}
    </div>
  )
}
 
 
// ─── REST Tester Tool ─────────────────────────────────────────────────────────
 
const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']
const NO_BODY_METHODS = ['GET', 'HEAD', 'OPTIONS']
 
function RestTesterTool() {
  const [url,         setUrl]         = React.useState('')
  const [method,      setMethod]      = React.useState('GET')
  const [reqHeaders,  setReqHeaders]  = React.useState([{ key: '', value: '' }])
  const [body,        setBody]        = React.useState('')
  const [contentType, setContentType] = React.useState('json')
  const [response,    setResponse]    = React.useState(null)
  const [sending,     setSending]     = React.useState(false)
  const [abortCtrl,   setAbortCtrl]   = React.useState(null)
 
  const hasBody = !NO_BODY_METHODS.includes(method)
 
  function addHeader() {
    setReqHeaders(prev => [...prev, { key: '', value: '' }])
  }
 
  function updateHeader(idx, field, val) {
    setReqHeaders(prev => prev.map((h, i) =>
      i === idx ? { ...h, [field]: val } : h
    ))
  }
 
  function removeHeader(idx) {
    setReqHeaders(prev => prev.filter((_, i) => i !== idx))
  }
 
  async function send() {
    const target = url.trim()
    if (!target) return
 
    const ctrl = new AbortController()
    setAbortCtrl(ctrl)
    setSending(true)
    setResponse(null)
 
    const start = performance.now()
 
    // Build header object from rows (skip empty keys)
    const headerObj = {}
    reqHeaders.forEach(({ key, value }) => {
      if (key.trim()) headerObj[key.trim()] = value
    })
 
    // Auto Content-Type for body requests
    if (hasBody && !Object.keys(headerObj).some(k => k.toLowerCase() === 'content-type')) {
      if (contentType === 'json')  headerObj['Content-Type'] = 'application/json'
      if (contentType === 'form')  headerObj['Content-Type'] = 'application/x-www-form-urlencoded'
      if (contentType === 'text')  headerObj['Content-Type'] = 'text/plain'
    }
 
    try {
      // Sprint 18: track the full request lifecycle including fetch + parse.
      // trackTool is promise-aware so the timing reflects the round-trip.
      await trackTool('webdev.generate', async () => {
        const res = await fetch(target, {
          method,
          headers: headerObj,
          body: hasBody && body.trim() ? body : undefined,
          signal: ctrl.signal,
          // Do NOT include credentials:'include' — this is not an internal request
        })
   
        const elapsed = Math.round(performance.now() - start)
   
        // Collect response headers
        const resHeaders = {}
        res.headers.forEach((v, k) => { resHeaders[k] = v })
   
        // Read body
        let rawBody = ''
        try { rawBody = await res.text() } catch { rawBody = '[Could not read response body]' }
   
        // Pretty-print JSON if possible
        let displayBody = rawBody
        let isJson = false
        const ct = res.headers.get('content-type') || ''
        if (ct.includes('json') || (rawBody.trimStart().startsWith('{') || rawBody.trimStart().startsWith('['))) {
          try {
            displayBody = JSON.stringify(JSON.parse(rawBody), null, 2)
            isJson = true
          } catch { /* not valid JSON */ }
        }
   
        setResponse({ status: res.status, statusText: res.statusText, headers: resHeaders, body: displayBody, isJson, elapsed })
      })
 
    } catch (err) {
      const elapsed = Math.round(performance.now() - start)
      if (err.name === 'AbortError') {
        setResponse({ aborted: true, elapsed })
      } else {
        const isTypeerror = err instanceof TypeError
        setResponse({
          error: err.message || 'Request failed',
          corsLikely: isTypeerror,
          elapsed,
        })
      }
    } finally {
      setSending(false)
      setAbortCtrl(null)
    }
  }
 
  function abort() {
    abortCtrl?.abort()
  }
 
  function getStatusColor(status) {
    if (status >= 200 && status < 300) return styles.status2xx
    if (status >= 300 && status < 400) return styles.status3xx
    if (status >= 400 && status < 500) return styles.status4xx
    return styles.status5xx
  }
 
  return (
    <div className={styles.tabPanel}>
 
      {/* ── URL + Method ──────────────────────────────────────────── */}
      <div className={styles.requestBar}>
        <select
          className={styles.methodSelect}
          value={method}
          onChange={e => setMethod(e.target.value)}
        >
          {HTTP_METHODS.map(m => (
            <option key={m} value={m}>{m}</option>
          ))}
        </select>
        <input
          type="url"
          className={styles.urlInput}
          placeholder="https://api.example.com/endpoint"
          value={url}
          onChange={e => setUrl(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && !sending && send()}
          spellCheck={false}
          autoComplete="off"
        />
        {sending
          ? <button className={styles.abortBtn} onClick={abort}>Cancel</button>
          : <button className={styles.sendBtn} onClick={send} disabled={!url.trim()}>Send</button>
        }
      </div>
 
      {/* ── Request Headers ───────────────────────────────────────── */}
      <div className={styles.section}>
        <div className={styles.sectionHeader}>
          <span className={styles.sectionTitle}>Request Headers</span>
          <button className={styles.addHeaderBtn} onClick={addHeader}>+ Add</button>
        </div>
        <div className={styles.headersList}>
          {reqHeaders.map((h, i) => (
            <div key={i} className={styles.headerInputRow}>
              <input
                type="text"
                className={styles.headerKeyInput}
                placeholder="Header-Name"
                value={h.key}
                onChange={e => updateHeader(i, 'key', e.target.value)}
                spellCheck={false}
              />
              <input
                type="text"
                className={styles.headerValueInput}
                placeholder="value"
                value={h.value}
                onChange={e => updateHeader(i, 'value', e.target.value)}
                spellCheck={false}
              />
              <button
                className={styles.removeHeaderBtn}
                onClick={() => removeHeader(i)}
                disabled={reqHeaders.length === 1}
              >×</button>
            </div>
          ))}
        </div>
      </div>
 
      {/* ── Request Body ──────────────────────────────────────────── */}
      {hasBody && (
        <div className={styles.section}>
          <div className={styles.sectionHeader}>
            <span className={styles.sectionTitle}>Body</span>
            <div className={styles.contentTypeChips}>
              {['json', 'form', 'text'].map(ct => (
                <button
                  key={ct}
                  className={contentType === ct ? styles.ctChipActive : styles.ctChip}
                  onClick={() => setContentType(ct)}
                >
                  {ct}
                </button>
              ))}
            </div>
          </div>
          <textarea
            className={styles.bodyTextarea}
            rows={6}
            placeholder={
              contentType === 'json'
                ? '{\n  "key": "value"\n}'
                : contentType === 'form'
                  ? 'key=value&other=123'
                  : 'Plain text body…'
            }
            value={body}
            onChange={e => setBody(e.target.value)}
            spellCheck={false}
          />
        </div>
      )}
 
      {/* ── Response ──────────────────────────────────────────────── */}
      {response && (
        <div className={styles.responsePanel}>
 
          {response.aborted && (
            <div className={styles.responseAborted}>Request cancelled.</div>
          )}
 
          {response.corsLikely && (
            <div className={styles.corsWarning}>
              <strong>⚠ CORS or network error</strong>
              <p>
                The browser blocked this request. The server at{' '}
                <code>{url}</code> either doesn't send{' '}
                <code>Access-Control-Allow-Origin</code> headers, or the URL
                is unreachable. This tester works for any CORS-enabled
                endpoint (public APIs, localhost) and fails gracefully on
                those that don't allow cross-origin requests.
              </p>
              <p className={styles.corsDetail}>Raw error: {response.error}</p>
            </div>
          )}
 
          {response.error && !response.corsLikely && (
            <div className={styles.responseError}>
              <strong>Request failed:</strong> {response.error}
            </div>
          )}
 
          {response.status && (
            <>
              {/* Status bar */}
              <div className={styles.statusBar}>
                <span className={`${styles.statusCode} ${getStatusColor(response.status)}`}>
                  {response.status} {response.statusText}
                </span>
                <span className={styles.responseTime}>{response.elapsed} ms</span>
              </div>
 
              {/* Response headers */}
              {Object.keys(response.headers).length > 0 && (
                <details className={styles.resHeadersAccordion}>
                  <summary className={styles.resHeadersSummary}>
                    Response headers ({Object.keys(response.headers).length})
                  </summary>
                  <table className={styles.resHeadersTable}>
                    <tbody>
                      {Object.entries(response.headers).map(([k, v]) => (
                        <tr key={k}>
                          <td className={styles.resHeaderKey}>{k}</td>
                          <td className={styles.resHeaderVal}>{v}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </details>
              )}
 
              {/* Response body */}
              <div className={styles.responseBodyWrap}>
                <div className={styles.responseBodyHeader}>
                  <span className={styles.sectionTitle}>Response body</span>
                  {response.isJson && (
                    <span className={styles.jsonBadge}>JSON</span>
                  )}
                </div>
                <pre className={styles.responseBody}>{response.body}</pre>
              </div>
            </>
          )}
        </div>
      )}
 
      {!response && !sending && (
        <div className={styles.restPlaceholder}>
          <span className={styles.restPlaceholderIcon}>⚡</span>
          <p>Enter a URL and press <strong>Send</strong> to make a request.</p>
          <p className={styles.restPlaceholderSub}>
            Requests run directly in your browser. CORS-enabled endpoints
            (public APIs, localhost) work out of the box.
          </p>
        </div>
      )}
 
    </div>
  )
}
