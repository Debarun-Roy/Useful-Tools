import { useState, useMemo, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logoutUser } from '../../api/apiClient'
import UserMenu from '../../components/UserMenu/UserMenu'
import { identifyHash, HASH_ALGORITHMS } from '../../utils/hashIdentifier'
import { generateKeys, estimateEntropyBits, sanitisePrefix } from '../../utils/keyGenerator'
import { logActivity } from '../../utils/logActivity'
import styles from './DevUtilsPage.module.css'

/*
 * DevUtilsPage — Sprint 15's new tool page.
 *
 * ── What it is ────────────────────────────────────────────────────────────
 * Two developer-focused micro-tools under one roof, both entirely client-side:
 *
 *   1. Hash Identifier — paste a hash, get a ranked list of likely algorithms
 *      (MD5 / SHA-1 / SHA-256 / BCrypt / Argon2 / JWT / LDAP / ...) with
 *      confidence tiers and one-line explanations.
 *
 *   2. API Key Generator — produce batches of cryptographically secure random
 *      strings in four formats (UUID v4, hex, base62, base64url) with
 *      optional prefix and live entropy estimate.
 *
 * Both tools live on the same page because they share an audience (engineers
 * debugging auth code, poking at tokens) and because neither alone is quite
 * big enough to warrant its own Dashboard slot. A tab bar keeps navigation
 * obvious.
 *
 * ── Activity logging (privacy-critical) ───────────────────────────────────
 * Both tools call logActivity() on successful operations, but the summaries
 * contain ONLY metadata — shape and counts — and the payload NEVER contains
 * the raw hash or the generated key material. Recording those would defeat
 * the point of running this locally in the browser. The exact rules:
 *
 *   Hash Identifier  →  summary: "Identified 32-char hexadecimal as MD5 (+2 others)"
 *                       payload: { length, charset, topCandidate, candidateCount }
 *                       NEVER the raw hash.
 *
 *   API Key Generator → summary: 'Generated 5 BASE62 keys · 32 chars · prefix "sk_live_"'
 *                       payload: { format, length, count, prefixLength }
 *                       NEVER the generated keys.
 */

// ─── Hash Identifier tab ────────────────────────────────────────────────────

function HashIdentifierTool() {
  const [input, setInput] = useState('')

  // identifyHash is pure and fast — compute synchronously on every render
  // rather than pushing it through state. useMemo avoids redundant re-runs
  // when an unrelated state (e.g. the outer tab switch) triggers a re-render.
  const result = useMemo(() => identifyHash(input), [input])

  // ── Log the identification to the activity timeline ──────────────────────
  //
  // logActivity debounces per-tool to 1500ms, so rapid keystrokes get
  // coalesced into ONE log entry reflecting the settled input. We only
  // log when there's at least one candidate — pure empty input or
  // unrecognised shapes aren't worth surfacing on the Dashboard.
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
      {
        length: result.length,
        charset: result.charset,
        topCandidate: top.name,
        topConfidence: top.confidence,
        candidateCount: result.candidates.length,
      }
    )
  }, [result])

  // ── Reference panel data ──────────────────────────────────────────────────
  // Only the registered algorithms get a row. The order here is the reading
  // order on the page; grouping by family keeps related entries together.
  const referenceRows = [
    HASH_ALGORITHMS.md5,
    HASH_ALGORITHMS.ntlm,
    HASH_ALGORITHMS.md4,
    HASH_ALGORITHMS.sha1,
    HASH_ALGORITHMS.ripemd160,
    HASH_ALGORITHMS.sha224,
    HASH_ALGORITHMS.sha256,
    HASH_ALGORITHMS.sha3_256,
    HASH_ALGORITHMS.sha384,
    HASH_ALGORITHMS.sha512,
    HASH_ALGORITHMS.sha3_512,
    HASH_ALGORITHMS.crc32,
    HASH_ALGORITHMS.bcrypt,
    HASH_ALGORITHMS.argon2,
    HASH_ALGORITHMS.scrypt,
    HASH_ALGORITHMS.md5crypt,
    HASH_ALGORITHMS.sha256crypt,
    HASH_ALGORITHMS.sha512crypt,
    HASH_ALGORITHMS.ldap_ssha,
    HASH_ALGORITHMS.ldap_sha,
    HASH_ALGORITHMS.jwt,
  ]

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
          autoCorrect="off"
          autoCapitalize="off"
        />
        {input && (
          <div className={styles.inputMeta}>
            <span>Length: <strong>{result.length}</strong></span>
            <span>Charset: <strong>{result.charset}</strong></span>
          </div>
        )}
      </div>

      {/* Results */}
      {!input.trim() && (
        <div className={styles.placeholderCard}>
          <p className={styles.placeholderTitle}>No input yet</p>
          <p className={styles.placeholderBody}>
            Candidate algorithms will appear here as you type. Detection is
            based on length, character set, and self-identifying prefixes
            (<code>$2a$</code>, <code>$argon2id$</code>, <code>{'{SSHA}'}</code>, JWT segments).
          </p>
        </div>
      )}

      {input.trim() && result.candidates.length === 0 && (
        <div className={styles.warnBanner} role="status">
          <strong>No confident match.</strong> The input doesn't match any of
          the common hash / credential shapes. Check for stray whitespace or
          partial copy-paste, and look at the reference below for what each
          format typically looks like.
        </div>
      )}

      {result.candidates.length > 0 && (
        <ul className={styles.candidatesList}>
          {result.candidates.map((cand) => {
            // Tailor the CSS modifier to the confidence value so the badge
            // colour reflects certainty at a glance.
            const badgeCls = `${styles.confidenceBadge} ${styles[`badge_${cand.confidence}`] || ''}`
            const cardCls  = `${styles.candidateCard} ${styles[`conf_${cand.confidence}`] || ''}`
            return (
              <li key={cand.name} className={cardCls}>
                <div className={styles.candidateHeader}>
                  <span className={styles.candidateName}>{cand.name}</span>
                  <span className={badgeCls}>{cand.confidence}</span>
                </div>
                <p className={styles.candidateNote}>{cand.note}</p>
              </li>
            )
          })}
        </ul>
      )}

      {/* Reference — always shown below the result */}
      <details className={styles.referenceBlock}>
        <summary className={styles.referenceSummary}>
          Reference: {referenceRows.length} known hash / credential formats
        </summary>
        <div className={styles.referenceGrid}>
          {referenceRows.map(row => (
            <div key={row.name} className={styles.referenceRow}>
              <span className={styles.referenceName}>{row.name}</span>
              <span className={styles.referenceNote}>{row.note}</span>
            </div>
          ))}
        </div>
      </details>
    </div>
  )
}

// ─── API Key Generator tab ──────────────────────────────────────────────────

// Static format options — drives the chip row and the length-slider label.
const FORMATS = [
  {
    id: 'uuid',
    label: 'UUID v4',
    hint:  'RFC 4122 v4. Fixed 36-char shape, dashes included.',
    lengthFixed: true,
  },
  {
    id: 'hex',
    label: 'Hex',
    hint:  'Lowercase hex. Byte length → 2× character length.',
    lengthFixed: false,
    min: 4,  max: 128, default: 32, step: 2,
    unit: 'bytes',
  },
  {
    id: 'base62',
    label: 'Base62',
    hint:  '[0-9A-Za-z]. Uniform via rejection sampling.',
    lengthFixed: false,
    min: 8,  max: 128, default: 32, step: 2,
    unit: 'chars',
  },
  {
    id: 'base64url',
    label: 'Base64-url',
    hint:  'URL-safe. Padding stripped. Byte length → ~1.33× character length.',
    lengthFixed: false,
    min: 4,  max: 128, default: 32, step: 2,
    unit: 'bytes',
  },
]

function CopyButton({ text, label = 'Copy', compact = false }) {
  const [copied, setCopied] = useState(false)
  function handleCopy() {
    navigator.clipboard.writeText(text).catch(() => {})
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }
  const cls = compact
    ? (copied ? styles.copyBtnDoneCompact : styles.copyBtnCompact)
    : (copied ? styles.copyBtnDone         : styles.copyBtn)
  return (
    <button
      type="button"
      className={cls}
      onClick={handleCopy}
      disabled={!text}
      aria-label={label}
    >
      {copied ? '✓ Copied' : label}
    </button>
  )
}

function ApiKeyGeneratorTool() {
  const [format, setFormat] = useState('uuid')
  const [length, setLength] = useState(32)
  const [prefix, setPrefix] = useState('')
  const [count,  setCount]  = useState(1)
  const [keys,   setKeys]   = useState([])
  const [error,  setError]  = useState('')

  // Current format metadata (for slider bounds and labels).
  const fmt = FORMATS.find(f => f.id === format) || FORMATS[0]

  // When the format changes, reset the length to that format's default.
  // Prevents a slider set to e.g. 8 hex bytes looking weird when switching
  // to base62 where 8 means chars instead of bytes.
  function handleFormatChange(newFormat) {
    setFormat(newFormat)
    const nf = FORMATS.find(f => f.id === newFormat)
    if (nf && !nf.lengthFixed) setLength(nf.default)
    setKeys([])
    setError('')
  }

  const entropyBits = useMemo(
    () => estimateEntropyBits({ format, length }),
    [format, length]
  )

  // Human label for the entropy strip — 122+ is safe for session tokens,
  // below 80 is a red flag for production secrets.
  const entropyTag = entropyBits >= 122 ? 'Strong'
                    : entropyBits >= 80  ? 'OK'
                    : 'Weak'

  function handleGenerate() {
    setError('')
    try {
      const cleanPrefix = sanitisePrefix(prefix)
      const safeCount = Math.max(1, Math.min(count | 0, 100))
      const out = generateKeys({
        format,
        length: fmt.lengthFixed ? 0 : length,
        prefix: cleanPrefix,
        count:  safeCount,
      })
      setKeys(out)

      // ── Activity log ─────────────────────────────────────────────────────
      // Log the *parameters* only. The key bodies stay in local state and
      // never enter the activity payload. Prefix length (not the prefix
      // itself) is recorded so we don't leak anything like "sk_live_".
      const lengthPart = fmt.lengthFixed
        ? ''
        : ` · ${length} ${fmt.unit}`
      const prefixPart = cleanPrefix
        ? ` · prefix "${cleanPrefix}"`
        : ''
      logActivity(
        'key.generate',
        `Generated ${safeCount} ${format.toUpperCase()} key${safeCount > 1 ? 's' : ''}${lengthPart}${prefixPart}`,
        {
          format,
          length: fmt.lengthFixed ? null : length,
          count: safeCount,
          prefixLength: cleanPrefix.length,
        }
      )
    } catch (e) {
      setError(e.message || 'Could not generate keys.')
    }
  }

  function handleCopyAll() {
    if (keys.length === 0) return
    navigator.clipboard.writeText(keys.join('\n')).catch(() => {})
  }

  return (
    <div className={styles.tabPanel}>

      {/* ── Format chips ────────────────────────────────────────────────── */}
      <div className={styles.field}>
        <label className={styles.fieldLabel}>Format</label>
        <div className={styles.formatChips} role="radiogroup" aria-label="Key format">
          {FORMATS.map(f => (
            <button
              key={f.id}
              type="button"
              role="radio"
              aria-checked={f.id === format}
              className={f.id === format ? styles.chipActive : styles.chip}
              onClick={() => handleFormatChange(f.id)}
            >
              {f.label}
            </button>
          ))}
        </div>
        <p className={styles.formatHint}>{fmt.hint}</p>
      </div>

      {/* ── Length slider (hidden for fixed-length formats like UUID) ───── */}
      {!fmt.lengthFixed && (
        <div className={styles.field}>
          <label className={styles.fieldLabel} htmlFor="length-slider">
            Length <span className={styles.sliderValue}>{length} {fmt.unit}</span>
          </label>
          <input
            id="length-slider"
            type="range"
            min={fmt.min}
            max={fmt.max}
            step={fmt.step}
            value={length}
            onChange={e => setLength(Number(e.target.value))}
          />
          <div className={styles.sliderMinMax}>
            <span>{fmt.min} {fmt.unit}</span>
            <span>{fmt.max} {fmt.unit}</span>
          </div>
        </div>
      )}

      {/* ── Prefix ─────────────────────────────────────────────────────── */}
      <div className={styles.controlsGrid}>
        <div className={styles.field}>
          <label className={styles.fieldLabel} htmlFor="key-prefix">
            Prefix <span className={styles.optional}>— optional</span>
          </label>
          <input
            id="key-prefix"
            type="text"
            className={styles.textInput}
            placeholder="sk_live_"
            value={prefix}
            onChange={e => setPrefix(e.target.value)}
            maxLength={64}
          />
          <p className={styles.formatHint}>
            Letters, digits, <code>_</code>, <code>-</code> only. Trimmed to 32 characters.
          </p>
        </div>

        <div className={styles.field}>
          <label className={styles.fieldLabel} htmlFor="key-count">
            Count <span className={styles.sliderValue}>{count}</span>
          </label>
          <input
            id="key-count"
            type="number"
            className={styles.textInput}
            min={1}
            max={100}
            value={count}
            onChange={e => setCount(Math.max(1, Math.min(Number(e.target.value) || 1, 100)))}
          />
        </div>
      </div>

      {/* ── Entropy strip ──────────────────────────────────────────────── */}
      <div className={styles.entropyStrip}>
        <span className={styles.entropyIcon} aria-hidden="true">⚡</span>
        <span>
          Estimated entropy: <strong>{entropyBits} bits</strong> per key
        </span>
        <span className={entropyTag === 'Weak' ? styles.entropyTag : styles.entropyTagMild}>
          {entropyTag}
        </span>
      </div>

      {/* ── Action row ─────────────────────────────────────────────────── */}
      <div className={styles.actionRow}>
        <button className={styles.primaryBtn} onClick={handleGenerate}>
          Generate {count > 1 ? `${count} keys` : 'key'}
        </button>
        {keys.length > 1 && (
          <button className={styles.secondaryBtn} onClick={handleCopyAll}>
            Copy all
          </button>
        )}
      </div>

      {error && <div className={styles.warnBanner} role="alert">{error}</div>}

      {/* ── Output list ────────────────────────────────────────────────── */}
      {keys.length > 0 && (
        <ul className={styles.keysList}>
          {keys.map((k, i) => (
            <li key={i} className={styles.keyRow}>
              <code className={styles.keyText}>{k}</code>
              <CopyButton text={k} compact />
            </li>
          ))}
        </ul>
      )}

      {/* ── Security note ──────────────────────────────────────────────── */}
      <div className={styles.securityNote}>
        <strong>🔒 Your keys never leave this page.</strong> Generation runs
        entirely in your browser using <code>crypto.getRandomValues()</code>.
        The activity log records <em>that you generated keys</em> and the
        parameters you chose — it never records the key bodies themselves.
      </div>
    </div>
  )
}

// ─── Main page shell ────────────────────────────────────────────────────────

const TABS = [
  { id: 'hash',   label: 'Hash Identifier',   icon: '#' },
  { id: 'keygen', label: 'API Key Generator', icon: '🔑' },
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
      default:       return <HashIdentifierTool />
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
          <div className={styles.heroBadge}>Dev Utilities</div>
          <h1 className={styles.heroTitle}>
            Dev<br />
            <span className={styles.heroAccent}>Utilities</span>
          </h1>
          <p className={styles.heroSub}>
            Identify unknown hashes, tokens and credentials, and generate
            cryptographically secure keys in the formats engineers actually
            need. Everything runs in your browser — nothing is uploaded.
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
