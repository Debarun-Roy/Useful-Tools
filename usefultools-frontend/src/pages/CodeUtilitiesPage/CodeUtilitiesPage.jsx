import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logoutUser } from '../../api/apiClient'
import yaml from 'js-yaml'
import Papa from 'papaparse'
import UserMenu from '../../components/UserMenu/UserMenu'
import styles from './CodeUtilitiesPage.module.css'
import { trackTool } from '../../utils/logMetric'
import { logActivity } from '../../utils/logActivity'

const TABS = [
  { id: 'json', label: 'JSON', icon: '{}' },
  { id: 'yaml', label: 'YAML', icon: 'Y' },
  { id: 'csv',  label: 'CSV',  icon: 'C' },
  { id: 'md',   label: 'Markdown', icon: 'M' },
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

// ── JSON ──────────────────────────────────────────────────────────────────────

function JSONTool() {
  const [input, setInput] = useState('')
  const [output, setOutput] = useState('')
  const [error, setError] = useState('')

  function formatJSON(spaces = 2) {
    if (!input.trim()) {
      setOutput('')
      setError('')
      return
    }
    try {
      const formatted = trackTool('code.format', () => {
        const parsed = JSON.parse(input)
        return JSON.stringify(parsed, null, spaces)
      })
      setOutput(formatted)
      setError('')
      logActivity(
        'code.format',
        `Prettified JSON (${input.length} → ${formatted.length} chars, ${spaces}-space indent)`,
        { tool: 'json', mode: 'prettify', spaces, inputLength: input.length }
      )
    } catch (e) {
      setOutput('')
      setError(e.message)
    }
  }

  function minifyJSON() {
    if (!input.trim()) {
      setOutput('')
      setError('')
      return
    }
    try {
      const minified = trackTool('code.format', () => {
        const parsed = JSON.parse(input)
        return JSON.stringify(parsed)
      })
      setOutput(minified)
      setError('')
      logActivity(
        'code.format',
        `Minified JSON (${input.length} → ${minified.length} chars)`,
        { tool: 'json', mode: 'minify', inputLength: input.length }
      )
    } catch (e) {
      setOutput('')
      setError(e.message)
    }
  }

  return (
    <div className={styles.tabPanel}>
      <div className={styles.actionRow}>
        <button className={styles.primaryBtn} onClick={() => formatJSON(2)}>
          Prettify
        </button>
        <button className={styles.primaryBtn} onClick={() => formatJSON(4)}>
          Prettify (4 spaces)
        </button>
        <button className={styles.ghostBtn} onClick={minifyJSON}>
          Minify
        </button>
      </div>
      {error && <div className={styles.errorBanner}>{error}</div>}
      <div className={styles.ioRow}>
        <div className={styles.ioBlock}>
          <label className={styles.ioLabel}>Input JSON</label>
          <textarea
            className={styles.mainTextarea}
            value={input}
            onChange={e => setInput(e.target.value)}
            placeholder="Paste JSON here…"
            rows={12}
          />
        </div>
        <div className={styles.ioBlock}>
          <label className={styles.ioLabel}>Output JSON</label>
          <textarea
            className={styles.mainTextarea}
            value={output}
            readOnly
            placeholder="Formatted JSON will appear here…"
            rows={12}
          />
          <CopyButton text={output} />
        </div>
      </div>
    </div>
  )
}

// ── YAML ──────────────────────────────────────────────────────────────────────

function YAMLTool() {
  const [input, setInput] = useState('')
  const [output, setOutput] = useState('')
  const [mode, setMode] = useState('yaml-to-json')
  const [error, setError] = useState('')

  function convert() {
    if (!input.trim()) {
      setOutput('')
      setError('')
      return
    }
    try {
      const result = trackTool('code.format', () => {
        if (mode === 'yaml-to-json') {
          const parsed = yaml.load(input)
          return JSON.stringify(parsed, null, 2)
        } else {
          const parsed = JSON.parse(input)
          return yaml.dump(parsed)
        }
      })
      setOutput(result)
      setError('')
      logActivity(
        'code.format',
        `Converted ${mode === 'yaml-to-json' ? 'YAML → JSON' : 'JSON → YAML'} (${input.length} chars)`,
        { tool: 'yaml', mode, inputLength: input.length }
      )
    } catch (e) {
      setOutput('')
      setError(e.message)
    }
  }

  return (
    <div className={styles.tabPanel}>
      <div className={styles.modeToggle}>
        <button
          className={mode === 'yaml-to-json' ? styles.modeActive : styles.modeBtn}
          onClick={() => setMode('yaml-to-json')}
        >
          YAML → JSON
        </button>
        <button
          className={mode === 'json-to-yaml' ? styles.modeActive : styles.modeBtn}
          onClick={() => setMode('json-to-yaml')}
        >
          JSON → YAML
        </button>
      </div>
      <div className={styles.actionRow}>
        <button className={styles.primaryBtn} onClick={convert}>
          Convert
        </button>
      </div>
      {error && <div className={styles.errorBanner}>{error}</div>}
      <div className={styles.ioRow}>
        <div className={styles.ioBlock}>
          <label className={styles.ioLabel}>
            Input {mode === 'yaml-to-json' ? 'YAML' : 'JSON'}
          </label>
          <textarea
            className={styles.mainTextarea}
            value={input}
            onChange={e => setInput(e.target.value)}
            placeholder={`Paste ${mode === 'yaml-to-json' ? 'YAML' : 'JSON'} here…`}
            rows={12}
          />
        </div>
        <div className={styles.ioBlock}>
          <label className={styles.ioLabel}>
            Output {mode === 'yaml-to-json' ? 'JSON' : 'YAML'}
          </label>
          <textarea
            className={styles.mainTextarea}
            value={output}
            readOnly
            placeholder={`Converted ${mode === 'yaml-to-json' ? 'JSON' : 'YAML'} will appear here…`}
            rows={12}
          />
          <CopyButton text={output} />
        </div>
      </div>
    </div>
  )
}

// ── CSV ───────────────────────────────────────────────────────────────────────

function CSVTool() {
  const [input, setInput] = useState('')
  const [output, setOutput] = useState('')
  const [mode, setMode] = useState('csv-to-json')
  const [error, setError] = useState('')

  function convert() {
    if (!input.trim()) {
      setOutput('')
      setError('')
      return
    }
    try {
      const result = trackTool('code.format', () => {
        if (mode === 'csv-to-json') {
          const parsed = Papa.parse(input, { header: true, skipEmptyLines: true })
          if (parsed.errors.length > 0) throw new Error(parsed.errors[0].message)
          return JSON.stringify(parsed.data, null, 2)
        } else {
          const parsed = JSON.parse(input)
          return Papa.unparse(parsed)
        }
      })
      setOutput(result)
      setError('')
      logActivity(
        'code.format',
        `Converted ${mode === 'csv-to-json' ? 'CSV → JSON' : 'JSON → CSV'} (${input.length} chars)`,
        { tool: 'csv', mode, inputLength: input.length }
      )
    } catch (e) {
      setOutput('')
      setError(e.message)
    }
  }

  return (
    <div className={styles.tabPanel}>
      <div className={styles.modeToggle}>
        <button
          className={mode === 'csv-to-json' ? styles.modeActive : styles.modeBtn}
          onClick={() => setMode('csv-to-json')}
        >
          CSV → JSON
        </button>
        <button
          className={mode === 'json-to-csv' ? styles.modeActive : styles.modeBtn}
          onClick={() => setMode('json-to-csv')}
        >
          JSON → CSV
        </button>
      </div>
      <div className={styles.actionRow}>
        <button className={styles.primaryBtn} onClick={convert}>
          Convert
        </button>
      </div>
      {error && <div className={styles.errorBanner}>{error}</div>}
      <div className={styles.ioRow}>
        <div className={styles.ioBlock}>
          <label className={styles.ioLabel}>
            Input {mode === 'csv-to-json' ? 'CSV' : 'JSON'}
          </label>
          <textarea
            className={styles.mainTextarea}
            value={input}
            onChange={e => setInput(e.target.value)}
            placeholder={`Paste ${mode === 'csv-to-json' ? 'CSV' : 'JSON'} here…`}
            rows={12}
          />
        </div>
        <div className={styles.ioBlock}>
          <label className={styles.ioLabel}>
            Output {mode === 'csv-to-json' ? 'JSON' : 'CSV'}
          </label>
          <textarea
            className={styles.mainTextarea}
            value={output}
            readOnly
            placeholder={`Converted ${mode === 'csv-to-json' ? 'JSON' : 'CSV'} will appear here…`}
            rows={12}
          />
          <CopyButton text={output} />
        </div>
      </div>
    </div>
  )
}

// ── Markdown ──────────────────────────────────────────────────────────────────

function MarkdownTool() {
  const [input, setInput] = useState('')
  const [output, setOutput] = useState('')

  function renderMarkdown() {
    const html = trackTool('code.format', () => {
      // Simple markdown renderer
      let h = input
        .replace(/^### (.*$)/gim, '<h3>$1</h3>')
        .replace(/^## (.*$)/gim, '<h2>$1</h2>')
        .replace(/^# (.*$)/gim, '<h1>$1</h1>')
        .replace(/\*\*(.*)\*\*/gim, '<strong>$1</strong>')
        .replace(/\*(.*)\*/gim, '<em>$1</em>')
        .replace(/!\[([^\]]*)\]\(([^)]*)\)/gim, '<img alt="$1" src="$2" />')
        .replace(/\[([^\]]*)\]\(([^)]*)\)/gim, '<a href="$2">$1</a>')
        .replace(/`([^`]*)`/gim, '<code>$1</code>')
        .replace(/\n\n/gim, '</p><p>')
        .replace(/\n/gim, '<br>')
      return '<p>' + h + '</p>'
    })
    setOutput(html)
    if (input.trim()) {
      logActivity(
        'code.format',
        `Rendered Markdown (${input.length} chars)`,
        { tool: 'markdown', inputLength: input.length }
      )
    }
  }

  return (
    <div className={styles.tabPanel}>
      <div className={styles.actionRow}>
        <button className={styles.primaryBtn} onClick={renderMarkdown}>
          Render
        </button>
      </div>
      <div className={styles.ioRow}>
        <div className={styles.ioBlock}>
          <label className={styles.ioLabel}>Markdown Input</label>
          <textarea
            className={styles.mainTextarea}
            value={input}
            onChange={e => setInput(e.target.value)}
            placeholder="Paste Markdown here…"
            rows={12}
          />
        </div>
        <div className={styles.ioBlock}>
          <label className={styles.ioLabel}>HTML Output</label>
          <div
            className={styles.htmlOutput}
            dangerouslySetInnerHTML={{ __html: output }}
          />
          <CopyButton text={output} />
        </div>
      </div>
    </div>
  )
}

// ── Page Shell ────────────────────────────────────────────────────────────────

export default function CodeUtilitiesPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const isGuest = username === 'Guest User'
  const [activeTab, setActiveTab] = useState('json')

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  function renderTab() {
    switch (activeTab) {
      case 'json': {
        return <JSONTool />
      }
      case 'yaml': {
        return <YAMLTool />
      }
      case 'csv': {
        return <CSVTool />
      }
      case 'md': {
        return <MarkdownTool />
      }
      default: {
        return <JSONTool />
      }
    }
  }

  return (
    <div className={styles.page}>
      {/* ── Header ──────────────────────────────────────────────────── */}
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.brand}>
            <span className={styles.brandMark} aria-hidden="true">💻</span>
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
          <div className={styles.heroBadge}>Code Utilities</div>
          <h1 className={styles.heroTitle}>
            Code<br />
            <span className={styles.heroAccent}>Utilities</span>
          </h1>
          <p className={styles.heroSub}>
            Format JSON, convert between YAML/JSON/CSV, and render Markdown to HTML.
            All processing happens client-side for privacy and speed.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div className={styles.statCard}>
            <span className={styles.statValue}>4</span>
            <span className={styles.statLabel}>formats</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>0</span>
            <span className={styles.statLabel}>server calls</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>∞</span>
            <span className={styles.statLabel}>privacy</span>
          </div>
        </div>
      </section>

      <main className={styles.main}>
        {/* ── Tab bar ─────────────────────────────────────────────── */}
        <nav className={styles.tabBar} aria-label="Code utilities">
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