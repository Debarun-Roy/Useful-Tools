import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import {
  logoutUser,
  formatContent,
  minifyContent,
  validateContent,
  getContentStats,
  validateAgainstSchema,
  getSchemaTemplates,
  getSchemaTemplate,
} from '../../api/apiClient'
import { logActivity } from '../../utils/logActivity'
import UserMenu from '../../components/UserMenu/UserMenu'
import * as yaml from 'js-yaml'
import styles from './ResponseFormatterPage.module.css'

// ─── Constants ────────────────────────────────────────────────────────────────

const FORMAT_OPTIONS = [
  { id: 'json', label: 'JSON' },
  { id: 'xml',  label: 'XML'  },
  { id: 'yaml', label: 'YAML' },
]

const TABS = [
  { id: 'format',   label: 'Format',   icon: '✦' },
  { id: 'validate', label: 'Validate', icon: '✓' },
  { id: 'minify',   label: 'Minify',   icon: '⬛' },
  { id: 'stats',    label: 'Stats',    icon: '◎' },
]

// Built-in schema templates available even without a database
const BUILTIN_TEMPLATES = [
  {
    name: 'Simple Object',
    schemaJson: JSON.stringify({
      type: 'object',
      required: ['name'],
      properties: {
        name: { type: 'string' },
        age:  { type: 'number' },
        active: { type: 'boolean' },
      },
    }, null, 2),
  },
  {
    name: 'API Response',
    schemaJson: JSON.stringify({
      type: 'object',
      required: ['success', 'data'],
      properties: {
        success:   { type: 'boolean' },
        data:      { type: 'object'  },
        error:     { type: 'string'  },
        errorCode: { type: 'string'  },
      },
    }, null, 2),
  },
  {
    name: 'User Profile',
    schemaJson: JSON.stringify({
      type: 'object',
      required: ['id', 'username'],
      properties: {
        id:       { type: 'number' },
        username: { type: 'string' },
        email:    { type: 'string' },
        role:     { type: 'string' },
      },
    }, null, 2),
  },
  {
    name: 'Array of Items',
    schemaJson: JSON.stringify({
      type: 'array',
      items: {
        type: 'object',
        required: ['id', 'name'],
        properties: {
          id:   { type: 'number' },
          name: { type: 'string' },
        },
      },
    }, null, 2),
  },
]

// ─── Shared copy button ───────────────────────────────────────────────────────

function CopyButton({ text }) {
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    if (!text) return
    await navigator.clipboard.writeText(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <button
      className={copied ? styles.copyBtnDone : styles.copyBtn}
      onClick={handleCopy}
      disabled={!text}
    >
      {copied ? '✓ Copied' : 'Copy'}
    </button>
  )
}

// ─── Format tab ──────────────────────────────────────────────────────────────

function FormatTab({ input, format }) {
  const [output, setOutput] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  async function run() {
    if (!input.trim()) return
    setLoading(true)
    setError(null)
    setOutput('')

    if (format === 'yaml') {
      try {
        const parsed = yaml.load(input)
        if (parsed === undefined) throw new Error('Empty or unrecognised YAML document')
        const formatted = yaml.dump(parsed, { indent: 2, lineWidth: -1 })
        setOutput(formatted)
        logActivity('formatter.format', `Formatted YAML (${input.length} chars)`, { format, inputLength: input.length })
      } catch (err) {
        setError(err.message || 'Invalid YAML')
      } finally {
        setLoading(false)
      }
      return
    }

    const { data } = await formatContent(input, format)
    if (data?.success) {
      setOutput(data.data.output)
      logActivity('formatter.format', `Formatted ${format.toUpperCase()} (${input.length} chars)`, { format, inputLength: input.length })
    } else {
      setError(data?.error || 'Format failed')
    }
    setLoading(false)
  }

  return (
    <div className={styles.tabPanel}>
      <div className={styles.actionRow}>
        <button
          className={styles.primaryBtn}
          onClick={run}
          disabled={!input.trim() || loading}
        >
          {loading ? 'Formatting…' : `Format ${format.toUpperCase()}`}
        </button>
        {output && <CopyButton text={output} />}
      </div>

      {error && <div className={styles.errorBanner} role="alert">{error}</div>}

      {output && (
        <div className={styles.outputBlock}>
          <pre className={styles.codeOutput}>{output}</pre>
        </div>
      )}

      {!output && !error && (
        <p className={styles.hint}>
          Paste {format.toUpperCase()} into the input above, then click Format.
          {format === 'yaml' && ' YAML is processed entirely in your browser.'}
        </p>
      )}
    </div>
  )
}

// ─── Validate tab ─────────────────────────────────────────────────────────────

function ValidateTab({ input, format }) {
  const [subTab, setSubTab] = useState('syntax')

  // Syntax state
  const [syntaxResult, setSyntaxResult] = useState(null)
  const [syntaxLoading, setSyntaxLoading] = useState(false)

  // Schema state
  const [schema, setSchema] = useState('')
  const [schemaResult, setSchemaResult] = useState(null)
  const [schemaLoading, setSchemaLoading] = useState(false)
  const [schemaError, setSchemaError] = useState(null)
  const [templates, setTemplates] = useState(BUILTIN_TEMPLATES)
  const [selectedTemplate, setSelectedTemplate] = useState('')

  // Merge DB templates with built-ins on mount
  useEffect(() => {
    getSchemaTemplates()
      .then(({ data }) => {
        if (data?.success && data.data?.templates?.length > 0) {
          setTemplates([...BUILTIN_TEMPLATES, ...data.data.templates])
        }
      })
      .catch(() => {})
  }, [])

  async function runSyntax() {
    if (!input.trim()) return
    setSyntaxLoading(true)
    setSyntaxResult(null)

    if (format === 'yaml') {
      try {
        yaml.load(input)
        setSyntaxResult({ isValid: true })
        logActivity('formatter.format', 'Validated YAML syntax — valid', { format, operation: 'validate-syntax' })
      } catch (err) {
        setSyntaxResult({ isValid: false, error: err.message })
      } finally {
        setSyntaxLoading(false)
      }
      return
    }

    const { data } = await validateContent(input, format)
    if (data?.success) {
      setSyntaxResult(data.data)
      logActivity('formatter.format', `Validated ${format.toUpperCase()} syntax — ${data.data.isValid ? 'valid' : 'invalid'}`, { format, operation: 'validate-syntax' })
    }
    setSyntaxLoading(false)
  }

  async function handleTemplateChange(e) {
    const templateName = e.target.value
    setSelectedTemplate(templateName)
    if (!templateName) return

    // Check built-in templates first (no network call needed)
    const builtin = BUILTIN_TEMPLATES.find(t => t.name === templateName)
    if (builtin) {
      setSchema(builtin.schemaJson)
      return
    }

    // Fetch from database
    const { data } = await getSchemaTemplate(templateName)
    if (data?.success && data.data?.schemaJson) {
      setSchema(data.data.schemaJson)
    }
  }

  async function runSchema() {
    if (!input.trim() || !schema.trim()) return
    setSchemaLoading(true)
    setSchemaResult(null)
    setSchemaError(null)

    const { data } = await validateAgainstSchema(input, schema)
    if (data?.success) {
      setSchemaResult(data.data)
      logActivity('formatter.format', `Schema validation — ${data.data.isValid ? 'passed' : 'failed'}`, { format, operation: 'validate-schema' })
    } else {
      setSchemaError(data?.error || 'Validation failed')
    }
    setSchemaLoading(false)
  }

  const SUB_TABS = [
    { id: 'syntax', label: 'Syntax',          disabled: false },
    { id: 'schema', label: 'Schema (JSON)',    disabled: format !== 'json' },
  ]

  return (
    <div className={styles.tabPanel}>
      <div className={styles.subTabBar}>
        {SUB_TABS.map(st => (
          <button
            key={st.id}
            className={subTab === st.id ? styles.subTabActive : styles.subTab}
            onClick={() => !st.disabled && setSubTab(st.id)}
            disabled={st.disabled}
            title={st.disabled ? 'Schema validation is JSON only — switch format to JSON' : undefined}
          >
            {st.label}
          </button>
        ))}
      </div>

      {subTab === 'syntax' && (
        <>
          <div className={styles.actionRow}>
            <button
              className={styles.primaryBtn}
              onClick={runSyntax}
              disabled={!input.trim() || syntaxLoading}
            >
              {syntaxLoading ? 'Validating…' : `Validate ${format.toUpperCase()} Syntax`}
            </button>
          </div>

          {syntaxResult && (
            <div className={syntaxResult.isValid ? styles.validBadge : styles.invalidBadge} role="status">
              {syntaxResult.isValid
                ? `✓ Valid ${format.toUpperCase()}`
                : `✗ Invalid ${format.toUpperCase()}`}
            </div>
          )}

          {syntaxResult && !syntaxResult.isValid && (syntaxResult.error || syntaxResult.errorMessage) && (
            <div className={styles.errorBanner} role="alert">
              {syntaxResult.error || syntaxResult.errorMessage}
            </div>
          )}

          {!syntaxResult && (
            <p className={styles.hint}>Paste {format.toUpperCase()} above, then click Validate.</p>
          )}
        </>
      )}

      {subTab === 'schema' && (
        <>
          <div className={styles.field}>
            <label className={styles.fieldLabel}>
              Load template
              <span className={styles.optional}> — or paste your own schema below</span>
            </label>
            <select
              className={styles.selectInput}
              value={selectedTemplate}
              onChange={handleTemplateChange}
            >
              <option value="">— Select a template —</option>
              {templates.map(t => (
                <option key={t.name} value={t.name}>{t.name}</option>
              ))}
            </select>
          </div>

          <div className={styles.field}>
            <label className={styles.fieldLabel}>JSON Schema</label>
            <textarea
              className={styles.monoTextarea}
              rows={8}
              placeholder={'{\n  "type": "object",\n  "required": ["name"],\n  "properties": {\n    "name": { "type": "string" }\n  }\n}'}
              value={schema}
              onChange={e => setSchema(e.target.value)}
              spellCheck={false}
            />
          </div>

          <div className={styles.actionRow}>
            <button
              className={styles.primaryBtn}
              onClick={runSchema}
              disabled={!input.trim() || !schema.trim() || schemaLoading}
            >
              {schemaLoading ? 'Validating…' : 'Validate Against Schema'}
            </button>
          </div>

          {schemaError && <div className={styles.errorBanner} role="alert">{schemaError}</div>}

          {schemaResult && (
            <>
              <div className={schemaResult.isValid ? styles.validBadge : styles.invalidBadge} role="status">
                {schemaResult.isValid ? '✓ Schema validation passed' : '✗ Schema validation failed'}
              </div>
              {schemaResult.errors?.length > 0 && (
                <ul className={styles.errorList}>
                  {schemaResult.errors.map((err, i) => (
                    <li key={i} className={styles.errorItem}>{err}</li>
                  ))}
                </ul>
              )}
            </>
          )}
        </>
      )}
    </div>
  )
}

// ─── Minify tab ───────────────────────────────────────────────────────────────

function MinifyTab({ input, format }) {
  const [output, setOutput] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [sizes, setSizes] = useState(null)

  async function run() {
    if (!input.trim()) return
    setLoading(true)
    setError(null)
    setOutput('')
    setSizes(null)

    if (format === 'yaml') {
      try {
        const parsed = yaml.load(input)
        if (parsed === undefined) throw new Error('Empty or unrecognised YAML document')
        const minified = yaml.dump(parsed, { flowLevel: 0, lineWidth: -1 }).trim()
        setOutput(minified)
        setSizes({ originalSize: input.length, minifiedSize: minified.length })
        logActivity('formatter.format', `Minified YAML (${input.length} → ${minified.length} chars)`, { format, inputLength: input.length })
      } catch (err) {
        setError(err.message || 'Invalid YAML')
      } finally {
        setLoading(false)
      }
      return
    }

    const { data } = await minifyContent(input, format)
    if (data?.success) {
      setOutput(data.data.output)
      setSizes({ originalSize: data.data.originalSize, minifiedSize: data.data.minifiedSize })
      logActivity('formatter.format', `Minified ${format.toUpperCase()} (${data.data.originalSize} → ${data.data.minifiedSize} chars)`, { format, operation: 'minify' })
    } else {
      setError(data?.error || 'Minification failed')
    }
    setLoading(false)
  }

  const saved = sizes ? sizes.originalSize - sizes.minifiedSize : 0
  const pct   = sizes?.originalSize > 0 ? Math.round((saved / sizes.originalSize) * 100) : 0

  return (
    <div className={styles.tabPanel}>
      <div className={styles.actionRow}>
        <button
          className={styles.primaryBtn}
          onClick={run}
          disabled={!input.trim() || loading}
        >
          {loading ? 'Minifying…' : `Minify ${format.toUpperCase()}`}
        </button>
        {output && <CopyButton text={output} />}
      </div>

      {error && <div className={styles.errorBanner} role="alert">{error}</div>}

      {sizes && (
        <div className={styles.sizeBadges}>
          <span className={styles.sizeBadge}>
            Original: <strong>{sizes.originalSize.toLocaleString()}</strong> chars
          </span>
          <span className={styles.sizeSep} aria-hidden="true">→</span>
          <span className={styles.sizeBadgeGreen}>
            Minified: <strong>{sizes.minifiedSize.toLocaleString()}</strong> chars
          </span>
          <span className={styles.sizeBadge}>
            Saved: <strong>{saved.toLocaleString()}</strong> chars ({pct}%)
          </span>
        </div>
      )}

      {output && (
        <div className={styles.outputBlock}>
          <pre className={styles.codeOutput}>{output}</pre>
        </div>
      )}

      {!output && !error && (
        <p className={styles.hint}>Paste {format.toUpperCase()} above, then click Minify.</p>
      )}
    </div>
  )
}

// ─── Stats tab ────────────────────────────────────────────────────────────────

function StatsTab({ input, format }) {
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  async function run() {
    if (!input.trim()) return
    setLoading(true)
    setError(null)
    setStats(null)

    if (format === 'yaml') {
      try {
        const parsed = yaml.load(input)
        if (parsed === undefined) throw new Error('Empty or unrecognised YAML document')
        const minified = yaml.dump(parsed, { flowLevel: 0, lineWidth: -1 }).trim()
        const originalSize = input.length
        const minifiedSize = minified.length
        const charactersSaved = originalSize - minifiedSize
        const compressionRatio = originalSize > 0
          ? Math.round((charactersSaved / originalSize) * 10000) / 100
          : 0
        setStats({ format: 'yaml', originalSize, minifiedSize, charactersSaved, compressionRatio })
        logActivity('formatter.format', `Analyzed YAML stats`, { format, operation: 'stats' })
      } catch (err) {
        setError(err.message || 'Invalid YAML')
      } finally {
        setLoading(false)
      }
      return
    }

    const { data } = await getContentStats(input, format)
    if (data?.success) {
      setStats(data.data)
      logActivity('formatter.format', `Analyzed ${format.toUpperCase()} stats`, { format, operation: 'stats' })
    } else {
      setError(data?.error || 'Stats failed')
    }
    setLoading(false)
  }

  return (
    <div className={styles.tabPanel}>
      <div className={styles.actionRow}>
        <button
          className={styles.primaryBtn}
          onClick={run}
          disabled={!input.trim() || loading}
        >
          {loading ? 'Analyzing…' : `Analyze ${format.toUpperCase()}`}
        </button>
      </div>

      {error && <div className={styles.errorBanner} role="alert">{error}</div>}

      {stats && (
        <div className={styles.statsGrid}>
          <div className={styles.dataCard}>
            <span className={styles.dataLabel}>Original</span>
            <span className={styles.dataValue}>{stats.originalSize?.toLocaleString()}</span>
            <span className={styles.dataUnit}>characters</span>
          </div>
          <div className={styles.dataCard}>
            <span className={styles.dataLabel}>Minified</span>
            <span className={styles.dataValue}>{stats.minifiedSize?.toLocaleString()}</span>
            <span className={styles.dataUnit}>characters</span>
          </div>
          <div className={styles.dataCard}>
            <span className={styles.dataLabel}>Saved</span>
            <span className={styles.dataValue}>{stats.charactersSaved?.toLocaleString()}</span>
            <span className={styles.dataUnit}>characters</span>
          </div>
          <div className={`${styles.dataCard} ${styles.dataCardAccent}`}>
            <span className={styles.dataLabel}>Compression</span>
            <span className={styles.dataValue}>{stats.compressionRatio}%</span>
            <span className={styles.dataUnit}>ratio</span>
          </div>
        </div>
      )}

      {!stats && !error && (
        <p className={styles.hint}>Paste {format.toUpperCase()} above, then click Analyze.</p>
      )}
    </div>
  )
}

// ─── Main page ────────────────────────────────────────────────────────────────

export default function ResponseFormatterPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState('format')
  const [input, setInput] = useState('')
  const [format, setFormat] = useState('json')
  const isGuest = username === 'Guest User'

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  function renderTab() {
    const props = { input, format }
    switch (activeTab) {
      case 'format':   return <FormatTab   {...props} />
      case 'validate': return <ValidateTab {...props} />
      case 'minify':   return <MinifyTab   {...props} />
      case 'stats':    return <StatsTab    {...props} />
      default:         return <FormatTab   {...props} />
    }
  }

  const placeholders = {
    json: '{\n  "name": "John",\n  "age": 30,\n  "city": "New York"\n}',
    xml:  '<root>\n  <name>John</name>\n  <age>30</age>\n  <city>New York</city>\n</root>',
    yaml: 'name: John\nage: 30\ncity: New York',
  }

  return (
    <div className={styles.page}>

      {/* ── Header ───────────────────────────────────────────────── */}
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

      {/* ── Hero ─────────────────────────────────────────────────── */}
      <section className={styles.hero}>
        <div className={styles.heroGrid} aria-hidden="true" />
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>API Formatter</div>
          <h1 className={styles.heroTitle}>
            API<br />
            <span className={styles.heroAccent}>Formatter</span>
          </h1>
          <p className={styles.heroSub}>
            Format, validate, minify and analyze JSON, XML, and YAML.
            JSON Schema validation with built-in template presets.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div className={styles.heroStatCard}>
            <span className={styles.heroStatValue}>3</span>
            <span className={styles.heroStatLabel}>formats</span>
          </div>
          <div className={styles.heroStatCard}>
            <span className={styles.heroStatValue}>4</span>
            <span className={styles.heroStatLabel}>operations</span>
          </div>
          <div className={styles.heroStatCard}>
            <span className={styles.heroStatValue}>schema</span>
            <span className={styles.heroStatLabel}>support</span>
          </div>
        </div>
      </section>

      <main className={styles.main}>

        {/* ── Shared input panel ───────────────────────────────────── */}
        <div className={styles.inputPanel}>
          <div className={styles.inputHeader}>
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
            {input && (
              <button className={styles.clearBtn} onClick={() => setInput('')}>
                Clear
              </button>
            )}
          </div>
          <textarea
            className={styles.inputTextarea}
            rows={10}
            placeholder={placeholders[format]}
            value={input}
            onChange={e => setInput(e.target.value)}
            spellCheck={false}
            autoCapitalize="off"
            autoCorrect="off"
          />
          <div className={styles.inputMeta}>
            <span>{input.length.toLocaleString()} chars</span>
            <span>{input.split('\n').length} lines</span>
            <span className={styles.yamlNote}>
              {format === 'yaml' && '— YAML processed in browser'}
            </span>
          </div>
        </div>

        {/* ── Tab bar ──────────────────────────────────────────────── */}
        <nav className={styles.tabBar} aria-label="Formatter operations">
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
