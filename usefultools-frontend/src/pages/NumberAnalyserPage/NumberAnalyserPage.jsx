import { startTransition, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  classifyNumber,
  fetchAllSeries,
  fetchBaseRepresentation,
  fetchSelectedSeries,
  logoutUser,
} from '../../api/apiClient'
import { useAuth } from '../../auth/AuthContext'
import styles from './NumberAnalyserPage.module.css'

const TABS = [
  { id: 'classify', label: 'Classify' },
  { id: 'base', label: 'Base Lab' },
  { id: 'series', label: 'Series Studio' },
]

const BASE_OPTIONS = [
  { value: 'all', label: 'All bases', hint: 'Binary, octal and hexadecimal for one number.' },
  { value: 'binary', label: 'Binary', hint: 'Only the binary representation.' },
  { value: 'octal', label: 'Octal', hint: 'Only the octal representation.' },
  { value: 'hex', label: 'Hex', hint: 'Only the hexadecimal representation.' },
  { value: 'all in range', label: 'All in range', hint: 'Every number from 0 to N, inclusive.' },
]

const SERIES_CATALOG = [
  {
    category: 'Base Representation',
    description: 'Represent numbers in alternate bases.',
    options: [
      { value: 'Binary', label: 'Binary' },
      { value: 'Octal', label: 'Octal' },
      { value: 'Hex', label: 'Hex' },
      { value: 'All', label: 'All bases' },
    ],
  },
  {
    category: 'Factorials',
    description: 'Factorial-derived growth families.',
    options: [
      { value: 'Factorial', label: 'Factorial' },
      { value: 'Superfactorial', label: 'Superfactorial' },
      { value: 'Hyperfactorial', label: 'Hyperfactorial' },
      { value: 'Primorial', label: 'Primorial' },
    ],
  },
  {
    category: 'Factors',
    description: 'Sequences driven by divisor structure.',
    options: [
      { value: 'Perfect', label: 'Perfect' },
      { value: 'Imperfect', label: 'Imperfect' },
      { value: 'Arithmetic', label: 'Arithmetic' },
      { value: 'Inharmonious', label: 'Inharmonious' },
      { value: 'Blum', label: 'Blum' },
      { value: 'Humble', label: 'Humble' },
      { value: 'Abundant', label: 'Abundant' },
      { value: 'Deficient', label: 'Deficient' },
      { value: 'Amicable', label: 'Amicable' },
      { value: 'Untouchable', label: 'Untouchable' },
    ],
  },
  {
    category: 'Number Theory',
    description: 'Core integer classes.',
    options: [
      { value: 'Integer', label: 'Integers' },
      { value: 'Natural', label: 'Natural' },
      { value: 'Odd', label: 'Odd' },
      { value: 'Even', label: 'Even' },
      { value: 'Whole', label: 'Whole' },
      { value: 'Negative', label: 'Negative' },
    ],
  },
  {
    category: 'Primes',
    description: 'Prime-related families and pairings.',
    options: [
      { value: 'Prime', label: 'Prime' },
      { value: 'Semi Prime', label: 'Semi Prime' },
      { value: 'Emirp', label: 'Emirp' },
      { value: 'Additive Prime', label: 'Additive Prime' },
      { value: 'Anagrammatic Prime', label: 'Anagrammatic Prime' },
      { value: 'Circular Prime', label: 'Circular Prime' },
      { value: 'Killer Prime', label: 'Killer Prime' },
      { value: 'Prime Palindrome', label: 'Prime Palindrome' },
      { value: 'Twin Primes', label: 'Twin Primes' },
      { value: 'Cousin Primes', label: 'Cousin Primes' },
      { value: 'Sexy Primes', label: 'Sexy Primes' },
      { value: 'Sophie German Primes', label: 'Sophie German Primes' },
    ],
  },
  {
    category: 'Patterns',
    description: 'Recurrences, figurate numbers and structural patterns.',
    options: [
      { value: 'Fibonacci', label: 'Fibonacci' },
      { value: 'Tribonacci', label: 'Tribonacci' },
      { value: 'Tetranacci', label: 'Tetranacci' },
      { value: 'Pentanacci', label: 'Pentanacci' },
      { value: 'Hexanacci', label: 'Hexanacci' },
      { value: 'Heptanacci', label: 'Heptanacci' },
      { value: 'Perrin', label: 'Perrin' },
      { value: 'Lucas', label: 'Lucas' },
      { value: 'Padovan', label: 'Padovan' },
      { value: 'Keith', label: 'Keith' },
      { value: 'Palindrome', label: 'Palindrome' },
      { value: 'Hypotenuse', label: 'Hypotenuse' },
      { value: 'Perfect Square', label: 'Perfect Square' },
      { value: 'Perfect Cube', label: 'Perfect Cube' },
      { value: 'Perfect Powers', label: 'Perfect Powers' },
      { value: 'Catalan Numbers', label: 'Catalan Numbers' },
      { value: 'Triangular Numbers', label: 'Triangular Numbers' },
      { value: 'Pentagonal Numbers', label: 'Pentagonal Numbers' },
      { value: 'Standard Hexagonal Numbers', label: 'Standard Hexagonal Numbers' },
      { value: 'Centered Hexagonal Numbers', label: 'Centered Hexagonal Numbers' },
      { value: 'Hexagonal Numbers', label: 'Hexagonal Numbers' },
      { value: 'Heptagonal Numbers', label: 'Heptagonal Numbers' },
      { value: 'Octagonal Numbers', label: 'Octagonal Numbers' },
      { value: 'Tetrahedral Numbers', label: 'Tetrahedral Numbers' },
      { value: 'Stella Octangula Numbers', label: 'Stella Octangula Numbers' },
    ],
  },
  {
    category: 'Recreational',
    description: 'Named curiosities and digit-based properties.',
    options: [
      { value: 'Armstrong', label: 'Armstrong' },
      { value: 'Harshad', label: 'Harshad' },
      { value: 'Disarium', label: 'Disarium' },
      { value: 'Happy', label: 'Happy' },
      { value: 'Sad', label: 'Sad' },
      { value: 'Duck', label: 'Duck' },
      { value: 'Dudeney', label: 'Dudeney' },
      { value: 'Buzz', label: 'Buzz' },
      { value: 'Spy', label: 'Spy' },
      { value: 'Kaprekar', label: 'Kaprekar' },
      { value: 'Tech', label: 'Tech' },
      { value: 'Magic', label: 'Magic' },
      { value: 'Smith', label: 'Smith' },
      { value: 'Munchausen', label: 'Munchausen' },
      { value: 'Repdigits', label: 'Repdigits' },
      { value: 'Gapful', label: 'Gapful' },
      { value: 'Hungry', label: 'Hungry' },
      { value: 'Pronic', label: 'Pronic' },
      { value: 'Neon', label: 'Neon' },
      { value: 'Automorphic', label: 'Automorphic' },
    ],
  },
]

const DEFAULT_SERIES_SELECTION = {
  'Number Theory': ['Integer', 'Odd', 'Even'],
  Primes: ['Prime', 'Twin Primes'],
  Patterns: ['Fibonacci', 'Perfect Square'],
  Recreational: ['Happy', 'Armstrong'],
}

function getIntegerError(value, label = 'Number') {
  const trimmed = value.trim()

  if (!trimmed) return `${label} is required.`
  if (!/^-?\d+$/.test(trimmed)) return `${label} must be a whole number.`

  return ''
}

function getPositiveIntegerError(value, label = 'Terms') {
  const trimmed = value.trim()

  if (!trimmed) return `${label} is required.`
  if (!/^\d+$/.test(trimmed) || Number(trimmed) < 1) {
    return `${label} must be a positive integer.`
  }

  return ''
}

function getCategorySelections(selection, category) {
  return selection[category] || []
}

function countFindings(analysis) {
  return Object.values(analysis || {}).reduce(
    (total, category) => total + Object.keys(category || {}).length,
    0,
  )
}

function countActiveCategories(analysis) {
  return Object.values(analysis || {}).filter(category => Object.keys(category || {}).length > 0).length
}

function formatChoiceMap(selection) {
  return Object.entries(selection).reduce((acc, [category, values]) => {
    if (values.length > 0) acc[category] = values
    return acc
  }, {})
}

function displayPairs(value) {
  return Object.entries(value || {}).map(([key, item]) => ({
    key,
    value: String(item),
  }))
}

function BaseResultPanel({ choice, result }) {
  if (result === null || result === undefined) return null

  if (typeof result === 'string') {
    return (
      <section className={styles.resultSection}>
        <div className={styles.metricCard}>
          <span className={styles.metricLabel}>Result</span>
          <code className={styles.singleValue}>{result}</code>
        </div>
      </section>
    )
  }

  if (choice === 'all in range') {
    return (
      <section className={styles.resultSection}>
        <div className={styles.resultHeader}>
          <h2 className={styles.sectionTitle}>Range output</h2>
          <p className={styles.sectionHint}>Each row contains the representations returned for one number.</p>
        </div>
        <div className={styles.stack}>
          {Object.entries(result).map(([number, representations]) => (
            <article key={number} className={styles.resultCard}>
              <div className={styles.resultCardHeader}>
                <h3 className={styles.resultCardTitle}>{number}</h3>
              </div>
              <div className={styles.keyValueGrid}>
                {displayPairs(representations).map(entry => (
                  <div key={`${number}-${entry.key}`} className={styles.kvRow}>
                    <span className={styles.kvKey}>{entry.key}</span>
                    <code className={styles.kvValue}>{entry.value}</code>
                  </div>
                ))}
              </div>
            </article>
          ))}
        </div>
      </section>
    )
  }

  return (
    <section className={styles.resultSection}>
      <div className={styles.resultHeader}>
        <h2 className={styles.sectionTitle}>Representations</h2>
      </div>
      <article className={styles.resultCard}>
        <div className={styles.keyValueGrid}>
          {displayPairs(result).map(entry => (
            <div key={entry.key} className={styles.kvRow}>
              <span className={styles.kvKey}>{entry.key}</span>
              <code className={styles.kvValue}>{entry.value}</code>
            </div>
          ))}
        </div>
      </article>
    </section>
  )
}

function SeriesResults({ result }) {
  if (!result) return null

  return (
    <section className={styles.resultSection}>
      <div className={styles.resultHeader}>
        <h2 className={styles.sectionTitle}>Generated series</h2>
        <p className={styles.sectionHint}>Keys and values are rendered exactly as returned by the backend.</p>
      </div>
      <div className={styles.stack}>
        {Object.entries(result).map(([category, seriesMap]) => (
          <article key={category} className={styles.resultCard}>
            <div className={styles.resultCardHeader}>
              <h3 className={styles.resultCardTitle}>{category}</h3>
              <span className={styles.metricPill}>{Object.keys(seriesMap || {}).length} series</span>
            </div>
            <div className={styles.stack}>
              {Object.entries(seriesMap || {}).map(([seriesName, values]) => (
                <section key={`${category}-${seriesName}`} className={styles.innerCard}>
                  <div className={styles.innerCardHeader}>
                    <h4 className={styles.innerCardTitle}>{seriesName}</h4>
                    <span className={styles.innerCardCount}>{Object.keys(values || {}).length} entries</span>
                  </div>
                  <div className={styles.keyValueGrid}>
                    {displayPairs(values).map(entry => (
                      <div key={`${category}-${seriesName}-${entry.key}`} className={styles.kvRow}>
                        <span className={styles.kvKey}>{entry.key}</span>
                        <code className={styles.kvValue}>{entry.value}</code>
                      </div>
                    ))}
                  </div>
                </section>
              ))}
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}

export default function NumberAnalyserPage() {
  const [activeTab, setActiveTab] = useState('classify')
  const [classifyInput, setClassifyInput] = useState('153')
  const [classifyLoading, setClassifyLoading] = useState(false)
  const [classifyError, setClassifyError] = useState('')
  const [classifyResult, setClassifyResult] = useState(null)

  const [baseInput, setBaseInput] = useState('42')
  const [baseChoice, setBaseChoice] = useState('all')
  const [baseLoading, setBaseLoading] = useState(false)
  const [baseError, setBaseError] = useState('')
  const [baseResult, setBaseResult] = useState(null)

  const [seriesTerms, setSeriesTerms] = useState('12')
  const [seriesMode, setSeriesMode] = useState('selected')
  const [seriesSelection, setSeriesSelection] = useState(DEFAULT_SERIES_SELECTION)
  const [seriesLoading, setSeriesLoading] = useState(false)
  const [seriesError, setSeriesError] = useState('')
  const [seriesResult, setSeriesResult] = useState(null)

  const { username, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  async function handleClassifySubmit(event) {
    event.preventDefault()

    const inputError = getIntegerError(classifyInput)
    if (inputError) {
      setClassifyError(inputError)
      setClassifyResult(null)
      return
    }

    setClassifyLoading(true)
    setClassifyError('')
    setClassifyResult(null)

    try {
      const { data } = await classifyNumber(classifyInput.trim())
      if (data.success) {
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

  async function handleBaseSubmit(event) {
    event.preventDefault()

    const inputError = getIntegerError(baseInput)
    if (inputError) {
      setBaseError(inputError)
      setBaseResult(null)
      return
    }

    setBaseLoading(true)
    setBaseError('')
    setBaseResult(null)

    try {
      const { data } = await fetchBaseRepresentation(baseInput.trim(), baseChoice)
      if (data.success) {
        startTransition(() => setBaseResult(data.data))
      } else {
        setBaseError(data.error || 'Could not fetch the requested base representation.')
      }
    } catch {
      setBaseError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setBaseLoading(false)
    }
  }

  function toggleSeriesSelection(category, value) {
    setSeriesResult(null)
    setSeriesSelection(current => {
      const currentValues = getCategorySelections(current, category)
      const nextValues = currentValues.includes(value)
        ? currentValues.filter(item => item !== value)
        : [...currentValues, value]

      return {
        ...current,
        [category]: nextValues,
      }
    })
  }

  function selectCategory(category, values) {
    setSeriesResult(null)
    setSeriesSelection(current => ({
      ...current,
      [category]: values,
    }))
  }

  function clearAllSeriesSelections() {
    setSeriesResult(null)
    setSeriesSelection({})
  }

  async function handleSeriesSubmit(event) {
    event.preventDefault()

    const inputError = getPositiveIntegerError(seriesTerms)
    if (inputError) {
      setSeriesError(inputError)
      setSeriesResult(null)
      return
    }

    const formattedChoiceMap = formatChoiceMap(seriesSelection)
    if (seriesMode === 'selected' && Object.keys(formattedChoiceMap).length === 0) {
      setSeriesError('Select at least one series before using selected mode.')
      setSeriesResult(null)
      return
    }

    setSeriesLoading(true)
    setSeriesError('')
    setSeriesResult(null)

    try {
      const request = seriesMode === 'all'
        ? fetchAllSeries(Number(seriesTerms.trim()))
        : fetchSelectedSeries(Number(seriesTerms.trim()), formattedChoiceMap)

      const { data } = await request

      if (data.success) {
        startTransition(() => setSeriesResult(data.data))
      } else {
        setSeriesError(data.error || 'Could not generate the requested series.')
      }
    } catch {
      setSeriesError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setSeriesLoading(false)
    }
  }

  const classifyAnalysis = classifyResult?.analysis || {}
  const totalFindings = countFindings(classifyAnalysis)
  const activeCategories = countActiveCategories(classifyAnalysis)

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <span className={styles.brandIcon} aria-hidden="true">#</span>
          <span className={styles.brandName}>UsefulTools</span>
          <button className={styles.backBtn} onClick={() => navigate('/dashboard')}>
            Back to dashboard
          </button>
        </div>
        <div className={styles.headerRight}>
          <span className={styles.usernameLabel}>{username}</span>
          <button className={styles.logoutBtn} onClick={handleLogout}>Sign out</button>
        </div>
      </header>

      <main className={styles.main}>
        <section className={styles.hero}>
          <div>
            <span className={styles.eyebrow}>Sprint 3</span>
            <h1 className={styles.title}>Number Analyser</h1>
            <p className={styles.subtitle}>
              Frontend coverage for the current Java analyzer stack: classification,
              base representations, and sequence generation.
            </p>
          </div>
          <div className={styles.heroStats}>
            <div className={styles.metricCard}>
              <span className={styles.metricLabel}>Tabs</span>
              <strong className={styles.metricValue}>3</strong>
            </div>
            <div className={styles.metricCard}>
              <span className={styles.metricLabel}>Series categories</span>
              <strong className={styles.metricValue}>{SERIES_CATALOG.length}</strong>
            </div>
          </div>
        </section>

        <nav className={styles.tabBar} aria-label="Number analyser tools">
          {TABS.map(tab => (
            <button
              key={tab.id}
              className={activeTab === tab.id ? styles.tabActive : styles.tab}
              onClick={() => setActiveTab(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </nav>

        {activeTab === 'classify' && (
          <section className={styles.panel}>
            <div className={styles.panelHeader}>
              <div>
                <h2 className={styles.sectionTitle}>Classify a single number</h2>
                <p className={styles.sectionHint}>
                  Calls `POST /api/analyzer/classify` and renders every category the service returns.
                </p>
              </div>
            </div>

            <form className={styles.controlCard} onSubmit={handleClassifySubmit}>
              <div className={styles.formRow}>
                <label className={styles.fieldLabel} htmlFor="classify-number">Integer</label>
                <input
                  id="classify-number"
                  className={styles.input}
                  type="text"
                  inputMode="numeric"
                  value={classifyInput}
                  onChange={event => {
                    setClassifyInput(event.target.value)
                    setClassifyError('')
                    setClassifyResult(null)
                  }}
                  placeholder="e.g. 153"
                  disabled={classifyLoading}
                />
              </div>
              <div className={styles.actionRow}>
                <button className={styles.primaryBtn} type="submit" disabled={classifyLoading}>
                  {classifyLoading ? 'Analysing...' : 'Analyse number'}
                </button>
              </div>
              {classifyError && <div className={styles.errorBanner}>{classifyError}</div>}
            </form>

            {classifyResult && (
              <>
                <section className={styles.summaryGrid}>
                  <div className={styles.metricCard}>
                    <span className={styles.metricLabel}>Analysed number</span>
                    <strong className={styles.metricValue}>{classifyResult.number}</strong>
                  </div>
                  <div className={styles.metricCard}>
                    <span className={styles.metricLabel}>Findings</span>
                    <strong className={styles.metricValue}>{totalFindings}</strong>
                  </div>
                  <div className={styles.metricCard}>
                    <span className={styles.metricLabel}>Active categories</span>
                    <strong className={styles.metricValue}>{activeCategories}</strong>
                  </div>
                </section>

                <section className={styles.resultSection}>
                  <div className={styles.grid}>
                    {Object.entries(classifyAnalysis).map(([category, findings]) => {
                      const entries = Object.values(findings || {})
                      return (
                        <article key={category} className={styles.resultCard}>
                          <div className={styles.resultCardHeader}>
                            <h3 className={styles.resultCardTitle}>{category}</h3>
                            <span className={styles.metricPill}>{entries.length}</span>
                          </div>
                          {entries.length > 0 ? (
                            <ul className={styles.findingList}>
                              {entries.map((entry, index) => (
                                <li key={`${category}-${index}`} className={styles.findingItem}>{entry}</li>
                              ))}
                            </ul>
                          ) : (
                            <p className={styles.emptyState}>No matches returned for this category.</p>
                          )}
                        </article>
                      )
                    })}
                  </div>
                </section>
              </>
            )}
          </section>
        )}

        {activeTab === 'base' && (
          <section className={styles.panel}>
            <div className={styles.panelHeader}>
              <div>
                <h2 className={styles.sectionTitle}>Base representations</h2>
                <p className={styles.sectionHint}>
                  Covers `POST /api/analyzer/base-representation`, including the range mode exposed by the backend.
                </p>
              </div>
            </div>

            <form className={styles.controlCard} onSubmit={handleBaseSubmit}>
              <div className={styles.formRow}>
                <label className={styles.fieldLabel} htmlFor="base-number">Integer</label>
                <input
                  id="base-number"
                  className={styles.input}
                  type="text"
                  inputMode="numeric"
                  value={baseInput}
                  onChange={event => {
                    setBaseInput(event.target.value)
                    setBaseError('')
                    setBaseResult(null)
                  }}
                  placeholder="e.g. 42"
                  disabled={baseLoading}
                />
              </div>

              <div className={styles.optionGrid}>
                {BASE_OPTIONS.map(option => (
                  <button
                    key={option.value}
                    type="button"
                    className={baseChoice === option.value ? styles.optionCardActive : styles.optionCard}
                    onClick={() => {
                      setBaseChoice(option.value)
                      setBaseResult(null)
                    }}
                    disabled={baseLoading}
                  >
                    <span className={styles.optionTitle}>{option.label}</span>
                    <span className={styles.optionHint}>{option.hint}</span>
                  </button>
                ))}
              </div>

              <div className={styles.actionRow}>
                <button className={styles.primaryBtn} type="submit" disabled={baseLoading}>
                  {baseLoading ? 'Loading...' : 'Fetch representation'}
                </button>
              </div>
              {baseError && <div className={styles.errorBanner}>{baseError}</div>}
            </form>

            <BaseResultPanel choice={baseChoice} result={baseResult} />
          </section>
        )}

        {activeTab === 'series' && (
          <section className={styles.panel}>
            <div className={styles.panelHeader}>
              <div>
                <h2 className={styles.sectionTitle}>Series Studio</h2>
                <p className={styles.sectionHint}>
                  Uses the exact category and selection strings expected by the current Java controllers.
                </p>
              </div>
            </div>

            <form className={styles.controlCard} onSubmit={handleSeriesSubmit}>
              <div className={styles.formRow}>
                <label className={styles.fieldLabel} htmlFor="series-terms">Terms</label>
                <input
                  id="series-terms"
                  className={styles.input}
                  type="text"
                  inputMode="numeric"
                  value={seriesTerms}
                  onChange={event => {
                    setSeriesTerms(event.target.value)
                    setSeriesError('')
                    setSeriesResult(null)
                  }}
                  placeholder="e.g. 12"
                  disabled={seriesLoading}
                />
              </div>

              <div className={styles.modeRow}>
                <button
                  type="button"
                  className={seriesMode === 'selected' ? styles.modeBtnActive : styles.modeBtn}
                  onClick={() => {
                    setSeriesMode('selected')
                    setSeriesResult(null)
                  }}
                  disabled={seriesLoading}
                >
                  Selected series
                </button>
                <button
                  type="button"
                  className={seriesMode === 'all' ? styles.modeBtnActive : styles.modeBtn}
                  onClick={() => {
                    setSeriesMode('all')
                    setSeriesResult(null)
                  }}
                  disabled={seriesLoading}
                >
                  All series
                </button>
              </div>

              {seriesMode === 'selected' && (
                <>
                  <div className={styles.inlineActions}>
                    <button
                      type="button"
                      className={styles.secondaryBtn}
                      onClick={() => {
                        setSeriesSelection(DEFAULT_SERIES_SELECTION)
                        setSeriesResult(null)
                      }}
                      disabled={seriesLoading}
                    >
                      Use starter set
                    </button>
                    <button
                      type="button"
                      className={styles.secondaryBtn}
                      onClick={clearAllSeriesSelections}
                      disabled={seriesLoading}
                    >
                      Clear selections
                    </button>
                  </div>

                  <div className={styles.catalogGrid}>
                    {SERIES_CATALOG.map(category => {
                      const selected = getCategorySelections(seriesSelection, category.category)

                      return (
                        <article key={category.category} className={styles.catalogCard}>
                          <div className={styles.catalogHeader}>
                            <div>
                              <h3 className={styles.catalogTitle}>{category.category}</h3>
                              <p className={styles.catalogHint}>{category.description}</p>
                            </div>
                            <div className={styles.inlineActions}>
                              <button
                                type="button"
                                className={styles.miniBtn}
                                onClick={() => selectCategory(category.category, category.options.map(option => option.value))}
                                disabled={seriesLoading}
                              >
                                All
                              </button>
                              <button
                                type="button"
                                className={styles.miniBtn}
                                onClick={() => selectCategory(category.category, [])}
                                disabled={seriesLoading}
                              >
                                None
                              </button>
                            </div>
                          </div>
                          <div className={styles.chipGrid}>
                            {category.options.map(option => (
                              <button
                                key={`${category.category}-${option.value}`}
                                type="button"
                                className={selected.includes(option.value) ? styles.chipActive : styles.chip}
                                onClick={() => toggleSeriesSelection(category.category, option.value)}
                                disabled={seriesLoading}
                              >
                                {option.label}
                              </button>
                            ))}
                          </div>
                        </article>
                      )
                    })}
                  </div>
                </>
              )}

              <div className={styles.actionRow}>
                <button className={styles.primaryBtn} type="submit" disabled={seriesLoading}>
                  {seriesLoading ? 'Generating...' : seriesMode === 'all' ? 'Generate all series' : 'Generate selected series'}
                </button>
              </div>
              {seriesError && <div className={styles.errorBanner}>{seriesError}</div>}
            </form>

            <SeriesResults result={seriesResult} />
          </section>
        )}
      </main>
    </div>
  )
}
