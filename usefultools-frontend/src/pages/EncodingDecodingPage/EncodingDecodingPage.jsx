import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logoutUser } from '../../api/apiClient'
import UserMenu from '../../components/UserMenu/UserMenu'
import styles from './EncodingDecodingPage.module.css'
import { trackTool } from '../../utils/logMetric'
import { logActivity } from '../../utils/logActivity'

const TABS = [
  { id: 'base64', label: 'Base64',     icon: 'B64' },
  { id: 'url',    label: 'URL',        icon: 'URL' },
  { id: 'html',   label: 'HTML',       icon: 'HTML'},
  { id: 'jwt',    label: 'JWT',        icon: 'JWT' },
  { id: 'binary', label: 'Binary/Hex', icon: 'Bin' },
  { id: 'rot',    label: 'ROT13',      icon: 'ROT' },
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

// ── Base64 ────────────────────────────────────────────────────────────────────

function Base64Tool() {
  const [input, setInput] = useState('')
  const [mode, setMode] = useState('encode')

  const output = input
    ? trackTool('encoding.transform', () =>
        mode === 'encode' ? btoa(input) : atob(input))
    : ''

  // Debounced log: settle on a stable input/mode and one entry is recorded.
  useEffect(() => {
    if (!input || !output) return
    logActivity(
      'encoding.transform',
      `Base64 ${mode}d ${input.length} chars`,
      { tool: 'base64', mode, length: input.length }
    )
  }, [input, mode, output])

  return (
    <div className={styles.tabPanel}>
      <div className={styles.modeToggle}>
        <button
          className={mode === 'encode' ? styles.modeActive : styles.modeBtn}
          onClick={() => setMode('encode')}
        >
          Encode
        </button>
        <button
          className={mode === 'decode' ? styles.modeActive : styles.modeBtn}
          onClick={() => setMode('decode')}
        >
          Decode
        </button>
      </div>
      <textarea
        className={styles.mainTextarea}
        value={input}
        onChange={e => setInput(e.target.value)}
        placeholder={mode === 'encode' ? "Enter text to encode…" : "Enter Base64 to decode…"}
        rows={6}
      />
      <div className={styles.outputBox}>
        <code className={styles.outputCode}>
          {output || <span className={styles.placeholder}>Output will appear here…</span>}
        </code>
        <CopyButton text={output} />
      </div>
    </div>
  )
}

// ── URL ───────────────────────────────────────────────────────────────────────

function URLTool() {
  const [input, setInput] = useState('')
  const [mode, setMode] = useState('encode')

  const output = input
    ? trackTool('encoding.transform', () =>
        mode === 'encode' ? encodeURIComponent(input) : decodeURIComponent(input))
    : ''

  return (
    <div className={styles.tabPanel}>
      <div className={styles.modeToggle}>
        <button
          className={mode === 'encode' ? styles.modeActive : styles.modeBtn}
          onClick={() => setMode('encode')}
        >
          Encode
        </button>
        <button
          className={mode === 'decode' ? styles.modeActive : styles.modeBtn}
          onClick={() => setMode('decode')}
        >
          Decode
        </button>
      </div>
      <textarea
        className={styles.mainTextarea}
        value={input}
        onChange={e => setInput(e.target.value)}
        placeholder={mode === 'encode' ? "Enter URL to encode…" : "Enter encoded URL to decode…"}
        rows={6}
      />
      <div className={styles.outputBox}>
        <code className={styles.outputCode}>
          {output || <span className={styles.placeholder}>Output will appear here…</span>}
        </code>
        <CopyButton text={output} />
      </div>
    </div>
  )
}

// ── HTML ──────────────────────────────────────────────────────────────────────

function HTMLTool() {
  const [input, setInput] = useState('')
  const [mode, setMode] = useState('encode')

  const output = input
    ? trackTool('encoding.transform', () =>
        mode === 'encode'
          ? input
              .replace(/&/g, '&amp;')
              .replace(/</g, '&lt;')
              .replace(/>/g, '&gt;')
              .replace(/"/g, '&quot;')
              .replace(/'/g, '&#39;')
          : input
              .replace(/&lt;/g, '<')
              .replace(/&gt;/g, '>')
              .replace(/&quot;/g, '"')
              .replace(/&#39;/g, "'")
              .replace(/&amp;/g, '&'))
    : ''

  return (
    <div className={styles.tabPanel}>
      <div className={styles.modeToggle}>
        <button
          className={mode === 'encode' ? styles.modeActive : styles.modeBtn}
          onClick={() => setMode('encode')}
        >
          Encode
        </button>
        <button
          className={mode === 'decode' ? styles.modeActive : styles.modeBtn}
          onClick={() => setMode('decode')}
        >
          Decode
        </button>
      </div>
      <textarea
        className={styles.mainTextarea}
        value={input}
        onChange={e => setInput(e.target.value)}
        placeholder={mode === 'encode' ? "Enter HTML to encode…" : "Enter encoded HTML to decode…"}
        rows={6}
      />
      <div className={styles.outputBox}>
        <code className={styles.outputCode}>
          {output || <span className={styles.placeholder}>Output will appear here…</span>}
        </code>
        <CopyButton text={output} />
      </div>
    </div>
  )
}

// ── JWT ───────────────────────────────────────────────────────────────────────

function JWTTool() {
  const [input, setInput] = useState('')
  const [decoded, setDecoded] = useState(null)
  const [error, setError] = useState('')

  function decodeJWT() {
    if (!input.trim()) {
      setDecoded(null)
      setError('')
      return
    }
    try {
      const result = trackTool('encoding.transform', () => {
        const parts = input.split('.')
        if (parts.length !== 3) throw new Error('Invalid JWT format')
        const header = JSON.parse(atob(parts[0].replace(/-/g, '+').replace(/_/g, '/')))
        const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')))
        return { header, payload, signature: parts[2] }
      })
      setDecoded(result)
      setError('')
    } catch (e) {
      setDecoded(null)
      setError(e.message)
    }
  }
  
  // const output = trackTool('encoding.transform', () => decodeJwt())

  return (
    <div className={styles.tabPanel}>
      <textarea
        className={styles.mainTextarea}
        value={input}
        onChange={e => { setInput(e.target.value); decodeJWT() }}
        placeholder="Paste JWT token to decode…"
        rows={4}
      />
      <div className={styles.actionRow}>
        <button className={styles.primaryBtn} onClick={decodeJWT}>
          Decode
        </button>
      </div>
      {error && <div className={styles.errorBanner}>{error}</div>}
      {decoded && (
        <div className={styles.jwtOutput}>
          <div className={styles.jwtSection}>
            <h4>Header</h4>
            <pre>{JSON.stringify(decoded.header, null, 2)}</pre>
          </div>
          <div className={styles.jwtSection}>
            <h4>Payload</h4>
            <pre>{JSON.stringify(decoded.payload, null, 2)}</pre>
          </div>
          <div className={styles.jwtSection}>
            <h4>Signature</h4>
            <code>{decoded.signature}</code>
          </div>
        </div>
      )}
    </div>
  )
}

// ── Binary/Hex ────────────────────────────────────────────────────────────────

function BinaryHexTool() {
  const [input, setInput] = useState('')
  const [inputType, setInputType] = useState('text')
  const [outputType, setOutputType] = useState('binary')

  const output = (() => {
    if (!input) return ''
    try {
      return trackTool('encoding.transform', () => {
        let bytes
        switch (inputType) {
          case 'text': {
            bytes = new TextEncoder().encode(input)
            break
          }
          case 'hex': {
            if (!/^[0-9a-fA-F\s]+$/.test(input)) throw new Error('Invalid hex')
            bytes = new Uint8Array(input.replace(/\s/g, '').match(/.{1,2}/g).map(h => parseInt(h, 16)))
            break
          }
          case 'binary': {
            if (!/^[01\s]+$/.test(input)) throw new Error('Invalid binary')
            const binStr = input.replace(/\s/g, '')
            if (binStr.length % 8 !== 0) throw new Error('Binary length must be multiple of 8')
            bytes = new Uint8Array(binStr.match(/.{8}/g).map(b => parseInt(b, 2)))
            break
          }
          default:
            return ''
        }
        switch (outputType) {
          case 'text': {
            return new TextDecoder().decode(bytes)
          }
          case 'hex': {
            return Array.from(bytes).map(b => b.toString(16).padStart(2, '0')).join(' ')
          }
          case 'binary': {
            return Array.from(bytes).map(b => b.toString(2).padStart(8, '0')).join(' ')
          }
          default:
            return ''
        }
      })
    } catch (e) {
      return `Error: ${e.message}`
    }
  })()

  return (
    <div className={styles.tabPanel}>
      <div className={styles.convertRow}>
        <div className={styles.convertGroup}>
          <label>Input Type</label>
          <select value={inputType} onChange={e => setInputType(e.target.value)}>
            <option value="text">Text</option>
            <option value="hex">Hex</option>
            <option value="binary">Binary</option>
          </select>
        </div>
        <span className={styles.arrow}>→</span>
        <div className={styles.convertGroup}>
          <label>Output Type</label>
          <select value={outputType} onChange={e => setOutputType(e.target.value)}>
            <option value="text">Text</option>
            <option value="hex">Hex</option>
            <option value="binary">Binary</option>
          </select>
        </div>
      </div>
      <textarea
        className={styles.mainTextarea}
        value={input}
        onChange={e => setInput(e.target.value)}
        placeholder={`Enter ${inputType} to convert…`}
        rows={6}
      />
      <div className={styles.outputBox}>
        <code className={styles.outputCode}>
          {output || <span className={styles.placeholder}>Output will appear here…</span>}
        </code>
        <CopyButton text={output} />
      </div>
    </div>
  )
}

// ── ROT13 ─────────────────────────────────────────────────────────────────────

function ROTTool() {
  const [input, setInput] = useState('')
  const [shift, setShift] = useState(13)

  const output = input
    ? trackTool('encoding.transform', () =>
        input.replace(/[a-zA-Z]/g, c => {
          const base = c <= 'Z' ? 65 : 97
          return String.fromCharCode(((c.charCodeAt(0) - base + shift) % 26) + base)
        }))
    : ''

  return (
    <div className={styles.tabPanel}>
      <div className={styles.rotControls}>
        <label>Shift:</label>
        <input
          type="number"
          min={1}
          max={25}
          value={shift}
          onChange={e => setShift(parseInt(e.target.value) || 13)}
          className={styles.shiftInput}
        />
      </div>
      <textarea
        className={styles.mainTextarea}
        value={input}
        onChange={e => setInput(e.target.value)}
        placeholder="Enter text to rotate…"
        rows={6}
      />
      <div className={styles.outputBox}>
        <code className={styles.outputCode}>
          {output || <span className={styles.placeholder}>Output will appear here…</span>}
        </code>
        <CopyButton text={output} />
      </div>
    </div>
  )
}

// ── Page Shell ────────────────────────────────────────────────────────────────

export default function EncodingDecodingPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const isGuest = username === 'Guest User'
  const [activeTab, setActiveTab] = useState('base64')

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  function renderTab() {
    switch (activeTab) {
      case 'base64': return <Base64Tool />
      case 'url':    return <URLTool />
      case 'html':   return <HTMLTool />
      case 'jwt':    return <JWTTool />
      case 'binary': return <BinaryHexTool />
      case 'rot':    return <ROTTool />
      default:       return <Base64Tool />
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
            Dashboard
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
          <div className={styles.heroBadge}>Encoding & Decoding</div>
          <h1 className={styles.heroTitle}>
            Encode &<br />
            <span className={styles.heroAccent}>Decode</span>
          </h1>
          <p className={styles.heroSub}>
            Six essential encoding/decoding tools: Base64, URL, HTML entities, JWT decoder,
            binary/hex converter, and ROT cipher — all client-side for security and speed.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div className={styles.statCard}>
            <span className={styles.statValue}>6</span>
            <span className={styles.statLabel}>encoders</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>0</span>
            <span className={styles.statLabel}>server calls</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>∞</span>
            <span className={styles.statLabel}>security</span>
          </div>
        </div>
      </section>

      <main className={styles.main}>
        {/* ── Tab bar ─────────────────────────────────────────────── */}
        <nav className={styles.tabBar} aria-label="Encoding tools">
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
