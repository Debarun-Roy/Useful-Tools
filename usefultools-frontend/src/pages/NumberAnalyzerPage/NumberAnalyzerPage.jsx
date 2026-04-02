import { startTransition, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  classifyNumber,
  fetchAllSeries,
  fetchBaseRepresentation,
  fetchSelectedSeries,
  logoutUser,
  performBaseArithmetic,
} from '../../api/apiClient'
import { useAuth } from '../../auth/AuthContext'
import SeriesChart from './SeriesChart'
import styles from './NumberAnalyzerPage.module.css'

// ─── Constants ────────────────────────────────────────────────────────────────

const TABS = [
  { id: 'classify', label: 'Classify', icon: '◎' },
  { id: 'base',     label: 'Base Lab', icon: '⬡' },
  { id: 'series',   label: 'Series Studio', icon: '≋' },
]

// Hue assigned to each category — drives the card accent colour in CSS
const CATEGORY_HUE = {
  'Number Theory':       '210',
  'Primes':              '280',
  'Factors':             '340',
  'Recreational':        '40',
  'Patterns':            '160',
  'Base Representations':'100',
}

const BASE_OPTIONS = [
  { value: 'all',          label: 'All bases',     hint: 'Binary · Octal · Hex for one number' },
  { value: 'binary',       label: 'Binary',        hint: 'Base-2 only' },
  { value: 'octal',        label: 'Octal',         hint: 'Base-8 only' },
  { value: 'hex',          label: 'Hexadecimal',   hint: 'Base-16 only' },
  { value: 'all in range', label: 'Range',         hint: 'Every integer from 0 to N' },
]

const SERIES_CATALOG = [
  {
    category: 'Base Representation',
    icon: '⬡',
    options: ['Binary','Octal','Hex','All'],
  },
  {
    category: 'Factorials',
    icon: '!',
    options: ['Factorial','Superfactorial','Hyperfactorial','Primorial'],
  },
  {
    category: 'Factors',
    icon: '÷',
    options: ['Perfect','Imperfect','Arithmetic','Inharmonious','Blum','Humble','Abundant','Deficient','Amicable','Untouchable'],
  },
  {
    category: 'Number Theory',
    icon: '∈',
    options: ['Integer','Natural','Odd','Even','Whole','Negative'],
  },
  {
    category: 'Primes',
    icon: 'ℙ',
    options: ['Prime','Semi Prime','Emirp','Additive Prime','Anagrammatic Prime','Circular Prime','Killer Prime','Prime Palindrome','Twin Primes','Cousin Primes','Sexy Primes','Sophie German Primes'],
  },
  {
    category: 'Patterns',
    icon: '∿',
    options: ['Fibonacci','Tribonacci','Tetranacci','Pentanacci','Hexanacci','Heptanacci','Perrin','Lucas','Padovan','Keith','Palindrome','Hypotenuse','Perfect Square','Perfect Cube','Perfect Powers','Catalan Numbers','Triangular Numbers','Pentagonal Numbers','Standard Hexagonal Numbers','Centered Hexagonal Numbers','Hexagonal Numbers','Heptagonal Numbers','Octagonal Numbers','Tetrahedral Numbers','Stella Octangula Numbers'],
  },
  {
    category: 'Recreational',
    icon: '✦',
    options: ['Armstrong','Harshad','Disarium','Happy','Sad','Duck','Dudeney','Buzz','Spy','Kaprekar','Tech','Magic','Smith','Munchausen','Repdigits','Gapful','Hungry','Pronic','Neon','Automorphic'],
  },
]

const DEFAULT_SERIES_SELECTION = {
  'Number Theory': ['Integer','Odd','Even'],
  Primes:          ['Prime','Twin Primes'],
  Patterns:        ['Fibonacci','Perfect Square'],
  Recreational:    ['Happy','Armstrong'],
}

const ARITH_OPS = [
  { id: 'add',      symbol: '+',  label: 'Add' },
  { id: 'subtract', symbol: '−',  label: 'Subtract' },
  { id: 'multiply', symbol: '×',  label: 'Multiply' },
  { id: 'divide',   symbol: '÷',  label: 'Divide' },
]

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getIntegerError(value, label = 'Number') {
  const t = value.trim()
  if (!t) return `${label} is required.`
  if (!/^-?\d+$/.test(t)) return `${label} must be a whole number.`
  return ''
}

function getPositiveIntegerError(value, label = 'Terms') {
  const t = value.trim()
  if (!t) return `${label} is required.`
  if (!/^\d+$/.test(t) || Number(t) < 1) return `${label} must be a positive integer ≥ 1.`
  return ''
}

function countFindings(analysis) {
  return Object.values(analysis || {}).reduce(
    (n, cat) => n + Object.keys(cat || {}).length, 0)
}

function countActiveCategories(analysis) {
  return Object.values(analysis || {}).filter(
    cat => Object.keys(cat || {}).length > 0).length
}

function formatChoiceMap(sel) {
  return Object.entries(sel).reduce((acc, [cat, vals]) => {
    if (vals.length > 0) acc[cat] = vals
    return acc
  }, {})
}

function displayPairs(value) {
  return Object.entries(value || {}).map(([k, v]) => ({ key: k, value: String(v) }))
}

/**
 * Normalises the series API response to a consistent shape:
 *   { mode, categories: { categoryName: { seriesName: { strategy, values, ... } } } }
 *
 * The /all endpoint already returns this shape. The /selected endpoint
 * returns a flat { category → { name → { key → value } } } map.
 */
function normalizeSeriesResult(result) {
  if (!result) return null
  if (result.categories) return result

  const categories = Object.entries(result).reduce((acc, [category, seriesMap]) => {
    acc[category] = Object.entries(seriesMap || {}).reduce((seriesAcc, [name, values]) => {
      const valueMap = values || {}
      const count = Object.keys(valueMap).length
      seriesAcc[name] = {
        strategy: 'exact',
        requestedTerms: count,
        returnedTerms: count,
        fulfilledRequest: true,
        values: valueMap,
      }
      return seriesAcc
    }, {})
    return acc
  }, {})

  return { mode: 'legacy', categories }
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function Pulse() {
  return <span className={styles.pulse} aria-hidden="true" />
}

function BaseResultPanel({ choice, result }) {
  if (result === null || result === undefined) return null

  if (typeof result === 'string') {
    return (
      <div className={styles.resultBlock}>
        <div className={styles.monoCard}>
          <span className={styles.monoLabel}>Result</span>
          <code className={styles.monoValue}>{result}</code>
        </div>
      </div>
    )
  }

  if (choice === 'all in range') {
    return (
      <div className={styles.resultBlock}>
        <div className={styles.resultBlockHeader}>
          <h3 className={styles.resultBlockTitle}>Range output</h3>
          <span className={styles.dimText}>Every integer 0 → N</span>
        </div>
        <div className={styles.monoTable}>
          {Object.entries(result).map(([num, reps]) => (
            <div key={num} className={styles.monoRow}>
              <span className={styles.monoRowKey}>{num}</span>
              <span className={styles.monoRowSep}>→</span>
              <span className={styles.monoRowVal}>
                {displayPairs(reps).map(e => `${e.key}: ${e.value}`).join('  ·  ')}
              </span>
            </div>
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className={styles.resultBlock}>
      <div className={styles.kvGrid}>
        {displayPairs(result).map(e => (
          <div key={e.key} className={styles.kvItem}>
            <span className={styles.kvLabel}>Base {e.key}</span>
            <code className={styles.kvCode}>{e.value}</code>
          </div>
        ))}
      </div>
    </div>
  )
}

function SeriesResults({ result }) {
  const normalized = normalizeSeriesResult(result)
  if (!normalized) return null

  const categories = normalized.categories || {}
  return (
    <div className={styles.resultBlock}>
      <div className={styles.resultBlockHeader}>
        <h3 className={styles.resultBlockTitle}>Generated series</h3>
      </div>
      {normalized.mode === 'mixed' && (
        <div className={styles.infoBanner}>
          All mode now bounds sparse or finite series to the first {normalized.boundedSearchLimit} candidates.
        </div>
      )}
      {Object.entries(categories).map(([category, seriesMap]) => (
        <div key={category} className={styles.seriesCategory}>
          <div className={styles.seriesCategoryHeader}>
            <span className={styles.seriesCategoryIcon}>
              {SERIES_CATALOG.find(c => c.category === category)?.icon ?? '•'}
            </span>
            <h4 className={styles.seriesCategoryName}>{category}</h4>
            <span className={styles.seriesCount}>{Object.keys(seriesMap || {}).length} series</span>
          </div>
          {Object.entries(seriesMap || {}).map(([name, series]) => {
            const values = series?.values || {}
            const returnedTerms = series?.returnedTerms ?? Object.keys(values).length
            const requestedTerms = series?.requestedTerms ?? returnedTerms
            const sampled = series?.strategy === 'bounded-search'
            const fulfilled = series?.fulfilledRequest ?? true

            return (
              <div key={`${category}-${name}`} className={styles.seriesItem}>
                <div className={styles.seriesItemHeader}>
                  <span className={styles.seriesItemName}>{name}</span>
                  <div className={styles.seriesMeta}>
                    <span className={styles.seriesItemCount}>
                      {returnedTerms}
                      {sampled ? ` / ${requestedTerms}` : ''} terms
                    </span>
                    {sampled && (
                      <span className={styles.seriesBadge}>Sampled to {series.searchedUpTo}</span>
                    )}
                    {sampled && !fulfilled && (
                      <span className={styles.seriesWarning}>Request not fully satisfied</span>
                    )}
                  </div>
                </div>
                {returnedTerms > 0 ? (
                  <div className={styles.seriesTerms}>
                    {displayPairs(values).map(e => (
                      <span key={`${name}-${e.key}`} className={styles.termChip}>
                        <span className={styles.termIndex}>{e.key}</span>
                        <code className={styles.termValue}>{e.value}</code>
                      </span>
                    ))}
                  </div>
                ) : (
                  <p className={styles.emptySeries}>No values were found within the applied search bound.</p>
                )}
              </div>
            )
          })}
        </div>
      ))}
    </div>
  )
}

// ─── Main component ───────────────────────────────────────────────────────────

export default function NumberAnalyserPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()

  const [activeTab, setActiveTab] = useState('classify')

  // ── Classify state ─────────────────────────────────────────────────────────
  const [classifyInput,   setClassifyInput]   = useState('153')
  const [classifyLoading, setClassifyLoading] = useState(false)
  const [classifyError,   setClassifyError]   = useState('')
  const [classifyResult,  setClassifyResult]  = useState(null)
  const [classifyTime,    setClassifyTime]    = useState(null)

  // ── Base Lab state ─────────────────────────────────────────────────────────
  const [baseTabMode,  setBaseTabMode]  = useState('convert')  // 'convert' | 'arithmetic'
  const [baseInput,    setBaseInput]    = useState('42')
  const [baseChoice,   setBaseChoice]   = useState('all')
  const [baseLoading,  setBaseLoading]  = useState(false)
  const [baseError,    setBaseError]    = useState('')
  const [baseResult,   setBaseResult]   = useState(null)

  // Base Arithmetic sub-state
  const [arithNum1,    setArithNum1]    = useState('')
  const [arithNum2,    setArithNum2]    = useState('')
  const [arithBase,    setArithBase]    = useState(16)
  const [arithOp,      setArithOp]      = useState('add')
  const [arithResult,  setArithResult]  = useState(null)
  const [arithLoading, setArithLoading] = useState(false)
  const [arithError,   setArithError]   = useState('')

  // ── Series state ───────────────────────────────────────────────────────────
  const [seriesTerms,     setSeriesTerms]     = useState('12')
  const [seriesMode,      setSeriesMode]      = useState('selected')
  const [seriesSelection, setSeriesSelection] = useState(DEFAULT_SERIES_SELECTION)
  const [seriesLoading,   setSeriesLoading]   = useState(false)
  const [seriesError,     setSeriesError]     = useState('')
  const [seriesResult,    setSeriesResult]    = useState(null)

  // ── Logout ─────────────────────────────────────────────────────────────────
  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  // ── Classify ───────────────────────────────────────────────────────────────
  async function handleClassifySubmit(e) {
    e.preventDefault()
    const err = getIntegerError(classifyInput)
    if (err) { setClassifyError(err); setClassifyResult(null); return }

    setClassifyLoading(true)
    setClassifyError('')
    setClassifyResult(null)
    setClassifyTime(null)
    const t0 = performance.now()

    try {
      const { data } = await classifyNumber(classifyInput.trim())
      if (data.success) {
        setClassifyTime(Math.round(performance.now() - t0))
        startTransition(() => setClassifyResult(data.data))
      } else {
        setClassifyError(data.error || 'Could not analyse the supplied number.')
      }
    } catch {
      setClassifyError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setClassifyLoading(false)
    }
  }

  // ── Base conversion ────────────────────────────────────────────────────────
  async function handleBaseSubmit(e) {
    e.preventDefault()
    const err = getIntegerError(baseInput)
    if (err) { setBaseError(err); setBaseResult(null); return }

    setBaseLoading(true)
    setBaseError('')
    setBaseResult(null)

    try {
      const { data } = await fetchBaseRepresentation(baseInput.trim(), baseChoice)
      if (data.success) startTransition(() => setBaseResult(data.data))
      else setBaseError(data.error || 'Could not fetch the requested base representation.')
    } catch {
      setBaseError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setBaseLoading(false)
    }
  }

  // ── Base arithmetic ────────────────────────────────────────────────────────
  async function handleArithSubmit(e) {
    e.preventDefault()
    const n1 = arithNum1.trim()
    const n2 = arithNum2.trim()
    if (!n1) { setArithError('First number is required.'); return }
    if (!n2) { setArithError('Second number is required.'); return }
    if (isNaN(arithBase) || arithBase < 2 || arithBase > 62) {
      setArithError('Base must be between 2 and 62.'); return
    }

    setArithLoading(true)
    setArithError('')
    setArithResult(null)

    try {
      const { data } = await performBaseArithmetic(n1, n2, arithBase, arithOp)
      if (data.success) startTransition(() => setArithResult(data.data))
      else setArithError(data.error || 'Calculation failed. Check your inputs.')
    } catch {
      setArithError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setArithLoading(false)
    }
  }

  // ── Series ─────────────────────────────────────────────────────────────────
  function toggleSeriesOption(category, value) {
    setSeriesResult(null)
    setSeriesSelection(cur => {
      const prev = cur[category] || []
      return {
        ...cur,
        [category]: prev.includes(value)
          ? prev.filter(v => v !== value)
          : [...prev, value],
      }
    })
  }

  function setCategory(category, values) {
    setSeriesResult(null)
    setSeriesSelection(cur => ({ ...cur, [category]: values }))
  }

  async function handleSeriesSubmit(e) {
    e.preventDefault()
    const err = getPositiveIntegerError(seriesTerms)
    if (err) { setSeriesError(err); return }

    const choiceMap = formatChoiceMap(seriesSelection)
    if (seriesMode === 'selected' && Object.keys(choiceMap).length === 0) {
      setSeriesError('Select at least one series before using Selected mode.')
      return
    }

    setSeriesLoading(true)
    setSeriesError('')
    setSeriesResult(null)

    try {
      const req = seriesMode === 'all'
        ? fetchAllSeries(Number(seriesTerms.trim()))
        : fetchSelectedSeries(Number(seriesTerms.trim()), choiceMap)
      const { data } = await req
      if (data.success) startTransition(() => setSeriesResult(data.data))
      else setSeriesError(data.error || 'Could not generate the requested series.')
    } catch {
      setSeriesError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setSeriesLoading(false)
    }
  }

  // ── Derived ────────────────────────────────────────────────────────────────
  const analysis       = classifyResult?.analysis || {}
  const totalFindings  = countFindings(analysis)
  const activeCats     = countActiveCategories(analysis)

  const normalizedSeries     = normalizeSeriesResult(seriesResult)
  const arithOpSymbol        = ARITH_OPS.find(o => o.id === arithOp)?.symbol ?? '?'

  // ──────────────────────────────────────────────────────────────────────────

  return (
    <div className={styles.page}>

      {/* ── Header ─────────────────────────────────────────────────────── */}
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

      {/* ── Hero ───────────────────────────────────────────────────────── */}
      <section className={styles.hero}>
        <div className={styles.heroGrid} aria-hidden="true" />
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>Sprint 3 · Number Analyser</div>
          <h1 className={styles.heroTitle}>
            Number<br/>
            <span className={styles.heroTitleAccent}>Observatory</span>
          </h1>
          <p className={styles.heroSub}>
            Classify integers across 60+ mathematical categories, explore base representations,
            perform base arithmetic, and generate charted number sequences.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div className={styles.statCard}>
            <span className={styles.statValue}>60+</span>
            <span className={styles.statLabel}>classifications</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>62</span>
            <span className={styles.statLabel}>number bases</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>7</span>
            <span className={styles.statLabel}>series families</span>
          </div>
        </div>
      </section>

      <main className={styles.main}>

        {/* ── Tab bar ──────────────────────────────────────────────────── */}
        <nav className={styles.tabBar} aria-label="Analyser tools">
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

        {/* ══════════════════════════════════════════════════════════════ */}
        {/* CLASSIFY TAB                                                   */}
        {/* ══════════════════════════════════════════════════════════════ */}
        {activeTab === 'classify' && (
          <div className={styles.panel}>

            <form className={styles.inputCard} onSubmit={handleClassifySubmit}>
              <div className={styles.inputCardHeader}>
                <div>
                  <h2 className={styles.cardTitle}>Classify a number</h2>
                  <p className={styles.cardHint}>
                    Enter any integer. The engine checks it against every known mathematical
                    category — including a prime factorization — and returns all matches.
                  </p>
                </div>
              </div>

              <div className={styles.numberInputRow}>
                <div className={styles.numberInputWrap}>
                  <span className={styles.numberInputPrefix}>ℕ</span>
                  <input
                    className={styles.numberInput}
                    type="text"
                    inputMode="numeric"
                    value={classifyInput}
                    onChange={e => {
                      setClassifyInput(e.target.value)
                      setClassifyError('')
                      setClassifyResult(null)
                    }}
                    placeholder="e.g. 360"
                    disabled={classifyLoading}
                    autoFocus
                  />
                </div>
                <button
                  className={styles.analyseBtn}
                  type="submit"
                  disabled={classifyLoading}
                >
                  {classifyLoading
                    ? <><Pulse /><span>Analysing…</span></>
                    : 'Analyse →'}
                </button>
              </div>

              {classifyError && (
                <div className={styles.errorBanner} role="alert">{classifyError}</div>
              )}
            </form>

            {/* Results */}
            {classifyResult && (
              <>
                {/* Summary strip */}
                <div className={styles.summaryStrip}>
                  <div className={styles.summaryNum}>
                    <span className={styles.summaryNumLabel}>Analysed</span>
                    <code className={styles.summaryNumValue}>{classifyResult.number}</code>
                  </div>
                  <div className={styles.summaryMetrics}>
                    <div className={styles.metricBadge}>
                      <span className={styles.metricN}>{totalFindings}</span>
                      <span className={styles.metricLabel}>findings</span>
                    </div>
                    <div className={styles.metricBadge}>
                      <span className={styles.metricN}>{activeCats}</span>
                      <span className={styles.metricLabel}>categories</span>
                    </div>
                    {classifyTime !== null && (
                      <div className={styles.metricBadge}>
                        <span className={styles.metricN}>{classifyTime}</span>
                        <span className={styles.metricLabel}>ms</span>
                      </div>
                    )}
                  </div>
                </div>

                {/* Category cards */}
                <div className={styles.categoryGrid}>
                  {Object.entries(analysis).map(([category, findings]) => {
                    const entries = Object.values(findings || {})
                    const hue = CATEGORY_HUE[category] ?? '210'
                    return (
                      <div
                        key={category}
                        className={styles.categoryCard}
                        style={{ '--hue': hue }}
                      >
                        <div className={styles.categoryCardHeader}>
                          <h3 className={styles.categoryName}>{category}</h3>
                          <span className={styles.categoryCount}>{entries.length}</span>
                        </div>
                        {entries.length > 0 ? (
                          <ul className={styles.findingList}>
                            {entries.map((txt, i) => (
                              <li key={i} className={styles.findingItem}>
                                <span className={styles.findingDot} aria-hidden="true">·</span>
                                {txt}
                              </li>
                            ))}
                          </ul>
                        ) : (
                          <p className={styles.emptyCategory}>No matches in this category.</p>
                        )}
                      </div>
                    )
                  })}
                </div>
              </>
            )}
          </div>
        )}

        {/* ══════════════════════════════════════════════════════════════ */}
        {/* BASE LAB TAB                                                   */}
        {/* ══════════════════════════════════════════════════════════════ */}
        {activeTab === 'base' && (
          <div className={styles.panel}>

            {/* ── Mode toggle: Convert | Arithmetic ────────────────────── */}
            <div className={styles.modeToggle}>
              <button
                type="button"
                className={baseTabMode === 'convert' ? styles.modeActive : styles.modeBtn}
                onClick={() => { setBaseTabMode('convert'); setArithResult(null); setArithError('') }}
              >
                Convert
              </button>
              <button
                type="button"
                className={baseTabMode === 'arithmetic' ? styles.modeActive : styles.modeBtn}
                onClick={() => { setBaseTabMode('arithmetic'); setBaseResult(null); setBaseError('') }}
              >
                Arithmetic
              </button>
            </div>

            {/* ── Convert sub-panel ─────────────────────────────────────── */}
            {baseTabMode === 'convert' && (
              <>
                <form className={styles.inputCard} onSubmit={handleBaseSubmit}>
                  <div className={styles.inputCardHeader}>
                    <div>
                      <h2 className={styles.cardTitle}>Base representations</h2>
                      <p className={styles.cardHint}>
                        See any integer in binary, octal, hexadecimal, or all 62 bases at once.
                        Range mode covers every integer from 0 to N.
                      </p>
                    </div>
                  </div>

                  <div className={styles.numberInputRow}>
                    <div className={styles.numberInputWrap}>
                      <span className={styles.numberInputPrefix}>ℕ</span>
                      <input
                        className={styles.numberInput}
                        type="text"
                        inputMode="numeric"
                        value={baseInput}
                        onChange={e => {
                          setBaseInput(e.target.value)
                          setBaseError('')
                          setBaseResult(null)
                        }}
                        placeholder="e.g. 42"
                        disabled={baseLoading}
                      />
                    </div>
                  </div>

                  <div className={styles.baseOptionGrid}>
                    {BASE_OPTIONS.map(opt => (
                      <button
                        key={opt.value}
                        type="button"
                        className={baseChoice === opt.value
                          ? styles.baseOptionActive
                          : styles.baseOption}
                        onClick={() => { setBaseChoice(opt.value); setBaseResult(null) }}
                        disabled={baseLoading}
                      >
                        <span className={styles.baseOptionLabel}>{opt.label}</span>
                        <span className={styles.baseOptionHint}>{opt.hint}</span>
                      </button>
                    ))}
                  </div>

                  <div className={styles.actionRow}>
                    <button className={styles.analyseBtn} type="submit" disabled={baseLoading}>
                      {baseLoading
                        ? <><Pulse /><span>Loading…</span></>
                        : 'Fetch →'}
                    </button>
                  </div>

                  {baseError && (
                    <div className={styles.errorBanner} role="alert">{baseError}</div>
                  )}
                </form>

                <BaseResultPanel choice={baseChoice} result={baseResult} />
              </>
            )}

            {/* ── Arithmetic sub-panel ──────────────────────────────────── */}
            {baseTabMode === 'arithmetic' && (
              <form className={styles.inputCard} onSubmit={handleArithSubmit}>
                <div>
                  <h2 className={styles.cardTitle}>Base arithmetic</h2>
                  <p className={styles.cardHint}>
                    Add, subtract, multiply or divide two numbers expressed in any base from 2 to 62.
                    For bases ≤ 36, letters are case-insensitive (A = a = digit 10 in hex).
                    For bases 37–62, uppercase A–Z represent digits 10–35 and
                    lowercase a–z represent digits 36–61.
                  </p>
                </div>

                {/* Base input + operation toggle on the same row */}
                <div className={styles.seriesControls}>
                  {/* Base selector */}
                  <div className={styles.numberInputWrap} style={{ maxWidth: 200 }}>
                    <span className={styles.numberInputPrefix}>Base</span>
                    <input
                      className={styles.numberInput}
                      type="number"
                      min="2"
                      max="62"
                      value={arithBase}
                      onChange={e => {
                        setArithBase(Number(e.target.value))
                        setArithResult(null)
                        setArithError('')
                      }}
                      disabled={arithLoading}
                    />
                  </div>

                  {/* Operation buttons */}
                  <div className={styles.modeToggle}>
                    {ARITH_OPS.map(op => (
                      <button
                        key={op.id}
                        type="button"
                        className={arithOp === op.id ? styles.modeActive : styles.modeBtn}
                        onClick={() => { setArithOp(op.id); setArithResult(null) }}
                        disabled={arithLoading}
                        title={op.label}
                        style={{ fontFamily: 'var(--font-mono)', fontSize: '1.1rem', minWidth: 40 }}
                      >
                        {op.symbol}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Number inputs */}
                <div className={styles.numberInputRow}>
                  <div className={styles.numberInputWrap}>
                    <span className={styles.numberInputPrefix}>{arithBase}</span>
                    <input
                      className={styles.numberInput}
                      type="text"
                      value={arithNum1}
                      onChange={e => {
                        // Auto-uppercase for bases ≤ 36 (standard hex/octal/etc.)
                        const val = arithBase <= 36 ? e.target.value.toUpperCase() : e.target.value
                        setArithNum1(val)
                        setArithResult(null)
                        setArithError('')
                      }}
                      placeholder={arithBase <= 16 ? 'e.g. FF' : 'First number'}
                      disabled={arithLoading}
                      style={{ fontFamily: 'var(--font-mono)' }}
                    />
                  </div>

                  <span style={{
                    fontFamily: 'var(--font-mono)',
                    fontSize: '1.5rem',
                    color: 'var(--clr-text-muted)',
                    alignSelf: 'flex-end',
                    paddingBottom: 12,
                    flexShrink: 0,
                  }}>
                    {arithOpSymbol}
                  </span>

                  <div className={styles.numberInputWrap}>
                    <span className={styles.numberInputPrefix}>{arithBase}</span>
                    <input
                      className={styles.numberInput}
                      type="text"
                      value={arithNum2}
                      onChange={e => {
                        const val = arithBase <= 36 ? e.target.value.toUpperCase() : e.target.value
                        setArithNum2(val)
                        setArithResult(null)
                        setArithError('')
                      }}
                      placeholder={arithBase <= 16 ? 'e.g. 1A' : 'Second number'}
                      disabled={arithLoading}
                      style={{ fontFamily: 'var(--font-mono)' }}
                    />
                  </div>
                </div>

                {arithError && (
                  <div className={styles.errorBanner} role="alert">{arithError}</div>
                )}

                {/* Result strip */}
                {arithResult && (
                  <div className={styles.summaryStrip}>
                    <div className={styles.summaryNum}>
                      <span className={styles.summaryNumLabel}>
                        Result (base {arithResult.base})
                      </span>
                      <code className={styles.summaryNumValue}>{arithResult.result}</code>
                    </div>
                    <div className={styles.summaryMetrics}>
                      <div className={styles.metricBadge}>
                        <span className={styles.metricN} style={{ fontSize: '1rem' }}>
                          {arithResult.number1}
                        </span>
                        <span className={styles.metricLabel}>operand 1</span>
                      </div>
                      <div className={styles.metricBadge}>
                        <span className={styles.metricN} style={{ fontSize: '1rem' }}>
                          {arithResult.number2}
                        </span>
                        <span className={styles.metricLabel}>operand 2</span>
                      </div>
                      <div className={styles.metricBadge}>
                        <span className={styles.metricN} style={{ fontSize: '1rem' }}>
                          {arithResult.decimalResult}
                        </span>
                        <span className={styles.metricLabel}>decimal</span>
                      </div>
                    </div>
                  </div>
                )}

                <div className={styles.actionRow}>
                  <button className={styles.analyseBtn} type="submit" disabled={arithLoading}>
                    {arithLoading
                      ? <><Pulse /><span>Calculating…</span></>
                      : 'Calculate →'}
                  </button>
                </div>
              </form>
            )}
          </div>
        )}

        {/* ══════════════════════════════════════════════════════════════ */}
        {/* SERIES STUDIO TAB                                              */}
        {/* ══════════════════════════════════════════════════════════════ */}
        {activeTab === 'series' && (
          <div className={styles.panel}>

            <form className={styles.inputCard} onSubmit={handleSeriesSubmit}>
              <div className={styles.inputCardHeader}>
                <div>
                  <h2 className={styles.cardTitle}>Series Studio</h2>
                  <p className={styles.cardHint}>
                    Generate the first N terms of any number sequence — from Fibonacci
                    to Sophie Germain primes to Munchausen numbers. Numeric sequences
                    are automatically charted below.
                  </p>
                </div>
              </div>

              <div className={styles.seriesControls}>
                <div className={styles.numberInputWrap} style={{ maxWidth: 220 }}>
                  <span className={styles.numberInputPrefix}>N</span>
                  <input
                    className={styles.numberInput}
                    type="text"
                    inputMode="numeric"
                    value={seriesTerms}
                    onChange={e => {
                      setSeriesTerms(e.target.value)
                      setSeriesError('')
                      setSeriesResult(null)
                    }}
                    placeholder="terms, e.g. 12"
                    disabled={seriesLoading}
                  />
                </div>

                <div className={styles.modeToggle}>
                  <button
                    type="button"
                    className={seriesMode === 'selected' ? styles.modeActive : styles.modeBtn}
                    onClick={() => { setSeriesMode('selected'); setSeriesResult(null) }}
                    disabled={seriesLoading}
                  >
                    Selected
                  </button>
                  <button
                    type="button"
                    className={seriesMode === 'all' ? styles.modeActive : styles.modeBtn}
                    onClick={() => { setSeriesMode('all'); setSeriesResult(null) }}
                    disabled={seriesLoading}
                  >
                    All
                  </button>
                </div>
              </div>

              {seriesMode === 'selected' && (
                <div className={styles.catalogSection}>
                  <div className={styles.catalogActions}>
                    <button
                      type="button"
                      className={styles.ghostBtn}
                      onClick={() => { setSeriesSelection(DEFAULT_SERIES_SELECTION); setSeriesResult(null) }}
                      disabled={seriesLoading}
                    >
                      Starter set
                    </button>
                    <button
                      type="button"
                      className={styles.ghostBtn}
                      onClick={() => { setSeriesSelection({}); setSeriesResult(null) }}
                      disabled={seriesLoading}
                    >
                      Clear all
                    </button>
                  </div>

                  <div className={styles.catalogGrid}>
                    {SERIES_CATALOG.map(cat => {
                      const sel = seriesSelection[cat.category] || []
                      return (
                        <div key={cat.category} className={styles.catalogCard}>
                          <div className={styles.catalogCardHeader}>
                            <span className={styles.catalogIcon}>{cat.icon}</span>
                            <h4 className={styles.catalogName}>{cat.category}</h4>
                            <button
                              type="button"
                              className={styles.miniBtn}
                              onClick={() => setCategory(cat.category, cat.options)}
                              disabled={seriesLoading}
                            >All</button>
                            <button
                              type="button"
                              className={styles.miniBtn}
                              onClick={() => setCategory(cat.category, [])}
                              disabled={seriesLoading}
                            >None</button>
                          </div>
                          <div className={styles.chipCloud}>
                            {cat.options.map(opt => (
                              <button
                                key={opt}
                                type="button"
                                className={sel.includes(opt) ? styles.chipActive : styles.chip}
                                onClick={() => toggleSeriesOption(cat.category, opt)}
                                disabled={seriesLoading}
                              >
                                {opt}
                              </button>
                            ))}
                          </div>
                        </div>
                      )
                    })}
                  </div>
                </div>
              )}

              <div className={styles.actionRow}>
                <button className={styles.analyseBtn} type="submit" disabled={seriesLoading}>
                  {seriesLoading
                    ? <><Pulse /><span>Generating…</span></>
                    : seriesMode === 'all'
                      ? 'Generate all →'
                      : 'Generate selected →'}
                </button>
              </div>

              {seriesError && (
                <div className={styles.errorBanner} role="alert">{seriesError}</div>
              )}
            </form>

            {/* ── Results table ──────────────────────────────────────── */}
            <SeriesResults result={seriesResult} />

            {/* ── SVG charts (Sprint 8) — shown only once results exist ── */}
            {seriesResult && (
              <SeriesChart categories={normalizedSeries?.categories} />
            )}
          </div>
        )}

      </main>
    </div>
  )
}