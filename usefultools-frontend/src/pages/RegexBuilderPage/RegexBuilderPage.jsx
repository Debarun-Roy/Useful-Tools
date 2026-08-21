import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import {
  deleteRegexPattern,
  explainRegexPattern,
  getRegexPatterns,
  getSavedRegexPatterns,
  logoutUser,
  saveRegexPattern,
  testRegexPattern,
  validateRegexPattern,
} from '../../api/apiClient'
import UserMenu from '../../components/UserMenu/UserMenu'
import { logActivity } from '../../utils/logActivity'
import { trackTool } from '../../utils/logMetric'
import styles from './RegexBuilderPage.module.css'

const RECENT_KEY = 'usefultools.regex.recent'

const PALETTE_GROUPS = [
  {
    title: 'Anchors',
    items: [
      { label: 'Start', token: '^', description: 'Start of input or line' },
      { label: 'End', token: '$', description: 'End of input or line' },
      { label: 'Word edge', token: '\\b', description: 'Word boundary' },
      { label: 'Not edge', token: '\\B', description: 'Non-word boundary' },
    ],
  },
  {
    title: 'Character Classes',
    items: [
      { label: 'Digit', token: '\\d', description: 'Any digit' },
      { label: 'Not digit', token: '\\D', description: 'Any non-digit' },
      { label: 'Word', token: '\\w', description: 'Letter, digit, underscore' },
      { label: 'Space', token: '\\s', description: 'Whitespace' },
      { label: 'Any char', token: '.', description: 'Any character except line break' },
      { label: 'Custom set', token: '[abc]', description: 'One character from a set' },
      { label: 'Negated set', token: '[^abc]', description: 'One character outside a set' },
    ],
  },
  {
    title: 'Quantifiers',
    items: [
      { label: 'Zero or more', token: '*', description: 'Repeat previous token' },
      { label: 'One or more', token: '+', description: 'Repeat previous token' },
      { label: 'Optional', token: '?', description: 'Previous token optional' },
      { label: 'Exactly n', token: '{3}', description: 'Repeat exactly 3 times' },
      { label: 'Range', token: '{2,5}', description: 'Repeat 2 to 5 times' },
      { label: 'At least', token: '{2,}', description: 'Repeat at least 2 times' },
    ],
  },
  {
    title: 'Groups',
    items: [
      { label: 'Capture', token: '()', description: 'Capturing group' },
      { label: 'No capture', token: '(?:)', description: 'Non-capturing group' },
      { label: 'Either', token: '|', description: 'Alternative branch' },
      { label: 'Lookahead', token: '(?=)', description: 'Positive lookahead' },
      { label: 'Not ahead', token: '(?!)', description: 'Negative lookahead' },
    ],
  },
  {
    title: 'Shortcuts',
    items: [
      { label: 'Email core', token: '[^@]+@[^@]+\\.[^@]+', description: 'Simple email body' },
      { label: 'Slug', token: '[a-z0-9]+(?:-[a-z0-9]+)*', description: 'URL slug' },
      { label: 'IPv4 chunk', token: '(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)', description: '0-255 octet' },
      { label: 'Hex color', token: '#?[A-Fa-f0-9]{6}', description: 'Six-digit hex color' },
    ],
  },
]

function readRecentPatterns() {
  try {
    const parsed = JSON.parse(localStorage.getItem(RECENT_KEY) || '[]')
    return Array.isArray(parsed) ? parsed.slice(0, 8) : []
  } catch {
    return []
  }
}

function storeRecentPatterns(patterns) {
  try {
    localStorage.setItem(RECENT_KEY, JSON.stringify(patterns.slice(0, 8)))
  } catch {
    // Recent patterns are a convenience only.
  }
}

function makeId() {
  try {
    if (crypto?.randomUUID) return crypto.randomUUID()
  } catch {
    // Fall through to a timestamp/random fallback.
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function ExportButton({ pattern, flagString, description }) {
  function handleExport() {
    if (!pattern) return
    const payload = {
      pattern,
      flags: flagString || '',
      display: `/${pattern}/${flagString || ''}`,
      description: description || '',
      exportedAt: new Date().toISOString(),
    }
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `regex-${Date.now()}.json`
    anchor.click()
    URL.revokeObjectURL(url)
  }

  return (
    <button
      type="button"
      className={styles.secondaryBtn}
      onClick={handleExport}
      disabled={!pattern}
      title="Download pattern as JSON"
    >
      Export
    </button>
  )
}

function CopyButton({ text, label = 'Copy', doneLabel = 'Copied' }) {
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    if (!text) return
    await navigator.clipboard.writeText(text).catch(() => {})
    setCopied(true)
    setTimeout(() => setCopied(false), 1400)
  }

  return (
    <button
      type="button"
      className={copied ? styles.secondaryBtnDone : styles.secondaryBtn}
      onClick={handleCopy}
      disabled={!text}
    >
      {copied ? doneLabel : label}
    </button>
  )
}

function escapeDisplay(text) {
  if (text === '\n') return '\\n'
  if (text === '\t') return '\\t'
  return text
}

function buildHighlightedSegments(testString, matches = []) {
  if (!testString || matches.length === 0) return [{ text: testString, match: false }]

  const ordered = matches
    .map(match => ({
      start: Number(match.startIndex),
      end: Number(match.endIndex),
      text: match.text ?? '',
    }))
    .filter(match => Number.isFinite(match.start) && Number.isFinite(match.end))
    .sort((a, b) => a.start - b.start || a.end - b.end)

  const segments = []
  let last = 0
  for (const match of ordered) {
    if (match.start < last) continue
    if (match.start > last) {
      segments.push({ text: testString.slice(last, match.start), match: false })
    }
    segments.push({ text: testString.slice(match.start, match.end), match: true })
    last = match.end
  }
  if (last < testString.length) {
    segments.push({ text: testString.slice(last), match: false })
  }
  return segments.length ? segments : [{ text: testString, match: false }]
}

export default function RegexBuilderPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const isGuest = username === 'Guest User'

  const [pattern, setPattern] = useState('')
  const [segments, setSegments] = useState([])
  const [testString, setTestString] = useState('user@example.com\nhttps://example.com\n#3366ff')
  const [flags, setFlags] = useState({ i: false, m: true, s: false })
  const [commonPatterns, setCommonPatterns] = useState([])
  const [savedPatterns, setSavedPatterns] = useState([])
  const [recentPatterns, setRecentPatterns] = useState(() => readRecentPatterns())
  const [libraryFilter, setLibraryFilter] = useState('')
  const [activeCategory, setActiveCategory] = useState('all')
  const [validation, setValidation] = useState(null)
  const [testResult, setTestResult] = useState(null)
  const [explanation, setExplanation] = useState(null)
  const [loading, setLoading] = useState(false)
  const [saveState, setSaveState] = useState({ description: '', category: 'custom', message: '' })
  const [error, setError] = useState('')

  const flagString = Object.entries(flags)
    .filter(([, enabled]) => enabled)
    .map(([flag]) => flag)
    .join('')

  const backendPattern = useMemo(() => {
    return flagString ? `(?${flagString})${pattern}` : pattern
  }, [flagString, pattern])

  const displayPattern = pattern ? `/${pattern}/${flagString}` : '/pattern/'

  useEffect(() => {
    getRegexPatterns()
      .then(({ data }) => {
        if (!data?.success || !data.data?.patterns) return
        const flattened = Object.entries(data.data.patterns).flatMap(([category, entries]) =>
          entries.map(entry => ({ ...entry, category }))
        )
        setCommonPatterns(flattened)
      })
      .catch(() => setError('Pattern library could not be loaded.'))

    getSavedRegexPatterns()
      .then(({ data }) => {
        if (data?.success && Array.isArray(data.data?.patterns)) {
          setSavedPatterns(data.data.patterns)
        }
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    if (!pattern.trim()) {
      setValidation(null)
      setExplanation(null)
      setTestResult(null)
      return
    }

    let cancelled = false
    const timer = setTimeout(async () => {
      setLoading(true)
      setError('')
      try {
        const [validationResponse, explanationResponse] = await Promise.all([
          trackTool('regex.build', () => validateRegexPattern(backendPattern)),
          trackTool('regex.build', () => explainRegexPattern(backendPattern)),
        ])

        if (!cancelled) {
          setValidation(validationResponse.data?.data || null)
          setExplanation(explanationResponse.data?.data || null)
        }

        if (testString.length > 0) {
          const resultResponse = await trackTool('regex.test', () =>
            testRegexPattern(backendPattern, testString)
          )
          if (!cancelled) {
            const data = resultResponse.data?.data || null
            setTestResult(data)
            if (data?.patternValid) {
              logActivity('regex.test', `Tested regex with ${data.matchCount || 0} matches`, {
                patternLength: pattern.length,
                testLength: testString.length,
                matchCount: data.matchCount || 0,
              })
            }
          }
        } else if (!cancelled) {
          setTestResult(null)
        }
      } catch {
        if (!cancelled) setError('Regex validation failed. Please try again.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }, 1500)

    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [backendPattern, pattern, testString])

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  function appendToken(item) {
    setPattern(current => current + item.token)
    setSegments(current => [
      ...current,
      { id: makeId(), label: item.label, token: item.token },
    ])
    logActivity('regex.build', `Added ${item.label} to regex builder`, {
      tokenLength: item.token.length,
    })
  }

  function removeSegment(id, token) {
    setSegments(current => current.filter(segment => segment.id !== id))
    setPattern(current => {
      const idx = current.lastIndexOf(token)
      return idx >= 0
        ? current.slice(0, idx) + current.slice(idx + token.length)
        : current
    })
  }

  function clearBuilder() {
    setPattern('')
    setSegments([])
    setValidation(null)
    setExplanation(null)
    setTestResult(null)
  }

  function applyPattern(entry) {
    const nextPattern = entry.pattern || ''
    setPattern(nextPattern)
    setSegments([{ id: makeId(), label: entry.name || 'Pattern', token: nextPattern }])
    if (entry.example || entry.exampleString) {
      setTestString(entry.example || entry.exampleString)
    }
    const nextRecent = [
      { pattern: nextPattern, label: entry.name || entry.description || 'Custom pattern' },
      ...recentPatterns.filter(item => item.pattern !== nextPattern),
    ].slice(0, 8)
    setRecentPatterns(nextRecent)
    storeRecentPatterns(nextRecent)
    logActivity('regex.build', 'Loaded regex pattern into builder', {
      patternLength: nextPattern.length,
      source: entry.id ? 'saved' : 'library',
    })
  }

  async function handleSave() {
    if (!pattern.trim() || validation?.isValid === false) return
    setSaveState(current => ({ ...current, message: '' }))
    const description = saveState.description.trim() || 'Saved regex pattern'
    const { data } = await saveRegexPattern({
      pattern,
      description,
      category: saveState.category.trim() || 'custom',
      exampleString: testString.slice(0, 500),
    })

    if (data?.success) {
      setSaveState(current => ({ ...current, description: '', message: 'Pattern saved.' }))
      logActivity('regex.save', 'Saved regex pattern', {
        patternLength: pattern.length,
        hasExample: Boolean(testString.trim()),
      })
      const saved = await getSavedRegexPatterns()
      if (saved.data?.success) setSavedPatterns(saved.data.data?.patterns || [])
    } else {
      setSaveState(current => ({
        ...current,
        message: data?.error || 'Could not save pattern.',
      }))
    }
  }

  async function handleDeleteSaved(id) {
    const { data } = await deleteRegexPattern(id)
    if (data?.success) {
      setSavedPatterns(current => current.filter(item => item.id !== id))
    }
  }

  function toggleFlag(flag) {
    setFlags(current => ({ ...current, [flag]: !current[flag] }))
  }

  const categories = useMemo(() => {
    return ['all', ...Array.from(new Set(commonPatterns.map(patternEntry => patternEntry.category))).sort()]
  }, [commonPatterns])

  const filteredPatterns = useMemo(() => {
    const query = libraryFilter.trim().toLowerCase()
    return commonPatterns.filter(entry => {
      const inCategory = activeCategory === 'all' || entry.category === activeCategory
      if (!inCategory) return false
      if (!query) return true
      return [entry.name, entry.description, entry.pattern, entry.category]
        .filter(Boolean)
        .some(value => value.toLowerCase().includes(query))
    })
  }, [activeCategory, commonPatterns, libraryFilter])

  const highlighted = useMemo(() => {
    return buildHighlightedSegments(testString, testResult?.matches || [])
  }, [testResult, testString])

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.brand}>
            <span className={styles.brandMark} aria-hidden="true">.*</span>
            <span className={styles.brandName}>UsefulTools</span>
          </div>
          <button className={styles.backBtn} onClick={() => navigate('/dashboard')}>
            Dashboard
          </button>
          <button className={styles.backBtn} onClick={() => navigate('/text-utils')}>
            Regex Tester
          </button>
        </div>
        <UserMenu username={username} isGuest={isGuest} variant="light" onLogout={handleLogout} />
      </header>

      <section className={styles.hero}>
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>Sprint 19</div>
          <h1 className={styles.heroTitle}>Regex Builder</h1>
          <p className={styles.heroSub}>
            Compose patterns from reusable blocks, test matches live, explain tokens,
            and save the expressions you use often.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div>
            <strong>{commonPatterns.length}</strong>
            <span>library patterns</span>
          </div>
          <div>
            <strong>{testResult?.matchCount ?? 0}</strong>
            <span>matches</span>
          </div>
          <div>
            <strong>{validation?.isValid ? 'valid' : validation?.isValid === false ? 'fix' : 'ready'}</strong>
            <span>pattern state</span>
          </div>
        </div>
      </section>

      <main className={styles.main}>
        <section className={styles.builderGrid}>
          <div className={styles.panel}>
            <div className={styles.panelHeader}>
              <div>
                <p className={styles.eyebrow}>Build</p>
                <h2 className={styles.panelTitle}>Component Palette</h2>
              </div>
              <button type="button" className={styles.ghostBtn} onClick={clearBuilder}>
                Clear
              </button>
            </div>

            <div className={styles.palette}>
              {PALETTE_GROUPS.map(group => (
                <div className={styles.paletteGroup} key={group.title}>
                  <h3>{group.title}</h3>
                  <div className={styles.paletteButtons}>
                    {group.items.map(item => (
                      <button
                        key={`${group.title}-${item.label}`}
                        type="button"
                        className={styles.paletteButton}
                        onClick={() => appendToken(item)}
                        title={item.description}
                      >
                        <span>{item.label}</span>
                        <code>{item.token}</code>
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className={styles.panel}>
            <div className={styles.panelHeader}>
              <div>
                <p className={styles.eyebrow}>Pattern</p>
                <h2 className={styles.panelTitle}>Canvas</h2>
              </div>
              <div className={styles.headerActions}>
                <ExportButton
                  pattern={pattern}
                  flagString={flagString}
                  description={saveState.description}
                />
                <CopyButton text={pattern} />
              </div>
            </div>

            <div className={styles.segmentCanvas} aria-label="Regex builder canvas">
              {segments.length === 0 && (
                <span className={styles.canvasEmpty}>Add components from the palette or type below.</span>
              )}
              {segments.map(segment => (
                <button
                  key={segment.id}
                  type="button"
                  className={styles.segmentChip}
                  onClick={() => removeSegment(segment.id, segment.token)}
                  title="Remove this component"
                >
                  <span>{segment.label}</span>
                  <code>{segment.token}</code>
                </button>
              ))}
            </div>

            <label className={styles.fieldLabel} htmlFor="regex-pattern">Generated regex</label>
            <div className={styles.regexInputWrap}>
              <span>/</span>
              <input
                id="regex-pattern"
                className={styles.regexInput}
                value={pattern}
                onChange={event => setPattern(event.target.value)}
                placeholder="^[a-z0-9]+$"
                spellCheck={false}
                autoComplete="off"
                maxLength={2000}
                aria-label="Regex pattern"
              />
              <span>/{flagString}</span>
            </div>

            <div className={styles.flagRow}>
              {[
                ['i', 'Case-insensitive'],
                ['m', 'Multiline anchors'],
                ['s', 'Dot matches line breaks'],
              ].map(([flag, label]) => (
                <button
                  key={flag}
                  type="button"
                  className={flags[flag] ? styles.flagActive : styles.flagButton}
                  onClick={() => toggleFlag(flag)}
                  title={label}
                  aria-pressed={flags[flag]}
                >
                  {flag}
                </button>
              ))}
            </div>

            <div className={styles.statusRow}>
              {loading && <span className={styles.neutralBadge}>Checking...</span>}
              {!loading && validation?.isValid && <span className={styles.validBadge}>Valid</span>}
              {!loading && validation?.isValid === false && (
                <span className={styles.invalidBadge}>Invalid</span>
              )}
              <code className={styles.displayPattern}>{displayPattern}</code>
            </div>

            {validation?.error && <div className={styles.errorBanner}>{validation.error}</div>}

            <div className={styles.saveBox}>
              <input
                className={styles.textInput}
                value={saveState.description}
                onChange={event => setSaveState(current => ({ ...current, description: event.target.value }))}
                placeholder="Pattern description"
                maxLength={200}
                aria-label="Pattern description"
              />
              <button
                type="button"
                className={styles.primaryBtn}
                onClick={handleSave}
                disabled={!pattern.trim() || validation?.isValid === false}
              >
                Save Pattern
              </button>
            </div>
            {saveState.message && <div className={styles.inlineMessage}>{saveState.message}</div>}
          </div>
        </section>

        <section className={styles.testGrid}>
          <div className={styles.panel}>
            <div className={styles.panelHeader}>
              <div>
                <p className={styles.eyebrow}>Test</p>
                <h2 className={styles.panelTitle}>Live Matches</h2>
              </div>
              <span className={styles.countBadge}>{testResult?.matchCount ?? 0}</span>
            </div>

            <textarea
              className={styles.testTextarea}
              value={testString}
              onChange={event => setTestString(event.target.value)}
              rows={8}
              spellCheck={false}
              placeholder="Paste multi-line test text here"
            />

            {testResult?.error && <div className={styles.errorBanner}>{testResult.error}</div>}

            <div className={styles.highlightBox}>
              {highlighted.map((segment, index) => (
                segment.match
                  ? <mark key={index} className={styles.matchMark}>{escapeDisplay(segment.text)}</mark>
                  : <span key={index}>{escapeDisplay(segment.text)}</span>
              ))}
            </div>

            {testResult?.matches?.length > 0 && (
              <ul className={styles.matchList}>
                {testResult.matches.slice(0, 25).map((match, index) => (
                  <li key={`${match.startIndex}-${index}`}>
                    <span>#{index + 1}</span>
                    <code>{JSON.stringify(match.text)}</code>
                    <span>{match.startIndex}-{match.endIndex}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className={styles.panel}>
            <div className={styles.panelHeader}>
              <div>
                <p className={styles.eyebrow}>Explain</p>
                <h2 className={styles.panelTitle}>Pattern Breakdown</h2>
              </div>
            </div>
            {explanation?.summary && <p className={styles.explainSummary}>{explanation.summary}</p>}
            {explanation?.error && <div className={styles.errorBanner}>{explanation.error}</div>}
            <div className={styles.explainList}>
              {explanation?.parts?.length > 0 ? explanation.parts.slice(0, 80).map((part, index) => (
                <div key={`${part.token}-${index}`} className={styles.explainItem}>
                  <code>{part.token}</code>
                  <span>{part.explanation}</span>
                </div>
              )) : (
                <p className={styles.mutedText}>Type or build a regex to see a token-by-token explanation.</p>
              )}
            </div>
          </div>
        </section>

        <section className={styles.libraryGrid}>
          <div className={styles.panel}>
            <div className={styles.panelHeader}>
              <div>
                <p className={styles.eyebrow}>Library</p>
                <h2 className={styles.panelTitle}>Common Patterns</h2>
              </div>
              <input
                className={styles.searchInput}
                value={libraryFilter}
                onChange={event => setLibraryFilter(event.target.value)}
                placeholder="Search library"
                maxLength={100}
                aria-label="Search pattern library"
              />
            </div>

            <div className={styles.categoryTabs}>
              {categories.map(category => (
                <button
                  key={category}
                  type="button"
                  className={activeCategory === category ? styles.categoryActive : styles.categoryButton}
                  onClick={() => setActiveCategory(category)}
                >
                  {category}
                </button>
              ))}
            </div>

            <div className={styles.patternList}>
              {filteredPatterns.slice(0, 80).map(entry => (
                <button
                  type="button"
                  key={`${entry.category}-${entry.name}`}
                  className={styles.patternItem}
                  onClick={() => applyPattern(entry)}
                >
                  <span>
                    <strong>{entry.name}</strong>
                    <small>{entry.category} - {entry.description}</small>
                  </span>
                  <code>{entry.pattern}</code>
                </button>
              ))}
            </div>
          </div>

          <div className={styles.sideStack}>
            <div className={styles.panel}>
              <div className={styles.panelHeader}>
                <div>
                  <p className={styles.eyebrow}>Saved</p>
                  <h2 className={styles.panelTitle}>Your Patterns</h2>
                </div>
              </div>
              <div className={styles.compactList}>
                {savedPatterns.length === 0 && (
                  <p className={styles.mutedText}>Saved patterns will appear here.</p>
                )}
                {savedPatterns.map(entry => (
                  <div key={entry.id} className={styles.savedItem}>
                    <button type="button" onClick={() => applyPattern(entry)}>
                      <strong>{entry.description || 'Saved pattern'}</strong>
                      <code>{entry.pattern}</code>
                    </button>
                    <button
                      type="button"
                      className={styles.deleteBtn}
                      onClick={() => handleDeleteSaved(entry.id)}
                      title="Delete pattern"
                    >
                      x
                    </button>
                  </div>
                ))}
              </div>
            </div>

            <div className={styles.panel}>
              <div className={styles.panelHeader}>
                <div>
                  <p className={styles.eyebrow}>Recent</p>
                  <h2 className={styles.panelTitle}>Local Recents</h2>
                </div>
              </div>
              <div className={styles.compactList}>
                {recentPatterns.length === 0 && (
                  <p className={styles.mutedText}>Library selections are remembered in this browser.</p>
                )}
                {recentPatterns.map(entry => (
                  <button
                    key={entry.pattern}
                    type="button"
                    className={styles.recentItem}
                    onClick={() => applyPattern(entry)}
                  >
                    <strong>{entry.label}</strong>
                    <code>{entry.pattern}</code>
                  </button>
                ))}
              </div>
            </div>
          </div>
        </section>

        {error && <div className={styles.errorBanner}>{error}</div>}
      </main>
    </div>
  )
}
