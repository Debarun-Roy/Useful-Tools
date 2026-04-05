import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logoutUser } from '../../api/apiClient'
import styles from './WebDevHelpersPage.module.css'

const TABS = [
  { id: 'gradient', label: 'CSS Gradient', icon: '∇' },
  { id: 'shadow',   label: 'Box Shadow',   icon: '☁' },
  { id: 'flexbox',  label: 'Flexbox',      icon: '⊞' },
  { id: 'robots',   label: 'Robots.txt',   icon: '🤖' },
  { id: 'favicon',  label: 'Favicon',      icon: '⭐' },
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
            Flexbox playground, robots.txt creator, and favicon generator.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div className={styles.statCard}>
            <span className={styles.statValue}>5</span>
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