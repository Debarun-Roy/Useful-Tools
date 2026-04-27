import { useEffect, useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logoutUser } from '../../api/apiClient'
import styles from './TextUtilitiesPage.module.css'
import { trackTool } from '../../utils/logMetric'
import { logActivity } from '../../utils/logActivity'
import { NotesProvider } from './NotesContext'
import NotesPanel from './NotesPanel'
import SaveNoteButton from './SaveNoteButton'

const TABS = [
  { id: 'counter', label: 'Counter',        icon: '#'  },
  { id: 'case',    label: 'Case Converter',  icon: 'Aa' },
  { id: 'diff',    label: 'Text Diff',       icon: '±'  },
  { id: 'regex',   label: 'Regex Tester',    icon: '.*' },
  { id: 'slug',    label: 'Slug Generator',  icon: '~'  },
  { id: 'lorem',   label: 'Lorem Ipsum',     icon: 'Li' },
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

// ── Word / Character Counter ──────────────────────────────────────────────────

function WordCounter() {
  const [text, setText] = useState('')

  const stats = useMemo(() => {
    const chars          = text.length
    const charsNoSpaces  = text.replace(/\s/g, '').length
    const words          = text.trim() === '' ? 0 : text.trim().split(/\s+/).length
    const uniqueWords    = text.trim() === ''
      ? 0
      : new Set((text.toLowerCase().match(/\b\w+\b/g) || [])).size
    const sentences      = text.trim() === ''
      ? 0
      : (text.match(/[.!?]+(\s|$)/g) || []).length
    const paragraphs     = text.trim() === ''
      ? 0
      : text.trim().split(/\n\s*\n/).filter(p => p.trim()).length || (text.trim() ? 1 : 0)
    const lines          = text === '' ? 0 : text.split('\n').length
    const readingTimeSec = words / 200           // 200 wpm average
    const readingMin     = Math.floor(readingTimeSec)
    const readingSec     = Math.round((readingTimeSec - readingMin) * 60)
    const readingLabel   = readingMin > 0
      ? `${readingMin}m ${readingSec}s`
      : `${readingSec}s`

    return { chars, charsNoSpaces, words, uniqueWords, sentences, paragraphs, lines, readingLabel }
  }, [text])

  // Debounced via logActivity (1500 ms per tool); rapid keystrokes coalesce
  // into a single entry capturing the user's settled input.
  useEffect(() => {
    if (!text.trim()) return
    logActivity(
      'text.transform',
      `Counted ${stats.words} words · ${stats.chars} chars`,
      { tool: 'counter', words: stats.words, chars: stats.chars }
    )
  }, [text, stats.words, stats.chars])

  const METRICS = [
    { label: 'Characters',   value: stats.chars,         icon: 'C'  },
    { label: 'No Spaces',    value: stats.charsNoSpaces, icon: 'C̲'  },
    { label: 'Words',        value: stats.words,         icon: 'W'  },
    { label: 'Unique Words', value: stats.uniqueWords,   icon: 'Uw' },
    { label: 'Sentences',    value: stats.sentences,     icon: 'S'  },
    { label: 'Paragraphs',   value: stats.paragraphs,    icon: 'P'  },
    { label: 'Lines',        value: stats.lines,         icon: 'L'  },
    { label: 'Read Time',    value: stats.readingLabel,  icon: '⏱'  },
  ]

  // Build a snapshot of metrics + the input itself for the saved note.
  const noteContent = text.trim()
    ? `${text}\n\n──\nWord-counter snapshot:\n` +
      `  Characters:   ${stats.chars}\n` +
      `  No spaces:    ${stats.charsNoSpaces}\n` +
      `  Words:        ${stats.words}\n` +
      `  Unique words: ${stats.uniqueWords}\n` +
      `  Sentences:    ${stats.sentences}\n` +
      `  Paragraphs:   ${stats.paragraphs}\n` +
      `  Lines:        ${stats.lines}\n` +
      `  Read time:    ${stats.readingLabel}`
    : ''

  const noteTitle = `${stats.words} word${stats.words !== 1 ? 's' : ''} — counter`

  return (
    <div className={styles.tabPanel}>
      <div className={styles.metricsGrid}>
        {METRICS.map(m => (
          <div key={m.label} className={styles.metricCard}>
            <span className={styles.metricIcon}>{m.icon}</span>
            <span className={styles.metricValue}>{m.value}</span>
            <span className={styles.metricLabel}>{m.label}</span>
          </div>
        ))}
      </div>
      <textarea
        className={styles.mainTextarea}
        value={text}
        onChange={e => setText(e.target.value)}
        placeholder="Type or paste your text here to analyse it…"
        rows={12}
      />
      <div className={styles.toolFooter}>
        <SaveNoteButton tool="counter" title={noteTitle} content={noteContent} />
      </div>
    </div>
  )
}

// ── Case Converter ────────────────────────────────────────────────────────────

function splitIntoWords(text) {
  return text
    .replace(/([a-z])([A-Z])/g, '$1 $2')         // camelCase → camel Case
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2')   // XMLParser → XML Parser
    .split(/[\s_\-./\\|,;:]+/)
    .filter(w => w.length > 0)
    .map(w => w.toLowerCase())
}

const CASES = [
  {
    id: 'camel',    label: 'camelCase',
    fn: t => {
      const w = splitIntoWords(t)
      return w.map((word, i) => i === 0 ? word : word[0].toUpperCase() + word.slice(1)).join('')
    },
  },
  {
    id: 'pascal',   label: 'PascalCase',
    fn: t => splitIntoWords(t).map(w => w[0].toUpperCase() + w.slice(1)).join(''),
  },
  {
    id: 'snake',    label: 'snake_case',
    fn: t => splitIntoWords(t).join('_'),
  },
  {
    id: 'screaming', label: 'SCREAMING_SNAKE',
    fn: t => splitIntoWords(t).join('_').toUpperCase(),
  },
  {
    id: 'kebab',    label: 'kebab-case',
    fn: t => splitIntoWords(t).join('-'),
  },
  {
    id: 'cobol',    label: 'COBOL-CASE',
    fn: t => splitIntoWords(t).join('-').toUpperCase(),
  },
  {
    id: 'dot',      label: 'dot.case',
    fn: t => splitIntoWords(t).join('.'),
  },
  {
    id: 'path',     label: 'path/case',
    fn: t => splitIntoWords(t).join('/'),
  },
  {
    id: 'title',    label: 'Title Case',
    fn: t => splitIntoWords(t).map(w => w[0].toUpperCase() + w.slice(1)).join(' '),
  },
  {
    id: 'sentence', label: 'Sentence case',
    fn: t => {
      const joined = splitIntoWords(t).join(' ')
      return joined ? joined[0].toUpperCase() + joined.slice(1) : ''
    },
  },
  {
    id: 'lower',    label: 'lowercase',
    fn: t => t.toLowerCase(),
  },
  {
    id: 'upper',    label: 'UPPERCASE',
    fn: t => t.toUpperCase(),
  },
  {
    id: 'toggle',   label: 'tOGGLE cASE',
    fn: t => t.split('').map(c => c === c.toUpperCase() ? c.toLowerCase() : c.toUpperCase()).join(''),
  },
  {
    id: 'alt',      label: 'AlTeRnAtInG cAsE',
    fn: t => t.split('').map((c, i) => i % 2 === 0 ? c.toLowerCase() : c.toUpperCase()).join(''),
  },
]

function CaseConverter() {
  const [input, setInput] = useState('')
  const [copied, setCopied] = useState(null)

  // Sprint 18: instrument the discrete user action (copy). We re-run the
  // case function inside trackTool so the captured duration reflects
  // a real conversion, not a no-op. The duplicate work is negligible —
  // case conversions are all microsecond-scale string manipulations.
  function handleCopy(id, conv) {
    const result = trackTool('text.transform', () => conv.fn(input))
    navigator.clipboard.writeText(result).catch(() => {})
    setCopied(id)
    setTimeout(() => setCopied(null), 2000)
    logActivity(
      'text.transform',
      `Converted text to ${conv.label} (${input.length} chars)`,
      { tool: 'case', mode: id, length: input.length }
    )
  }

  return (
    <div className={styles.tabPanel}>
      <textarea
        className={styles.mainTextarea}
        value={input}
        onChange={e => setInput(e.target.value)}
        placeholder="Type or paste text to convert…"
        rows={4}
      />
      <div className={styles.caseGrid}>
        {CASES.map(conv => {
          const result = input ? conv.fn(input) : ''
          return (
            <div key={conv.id} className={styles.caseCard}>
              <div className={styles.caseCardHeader}>
                <span className={styles.caseLabel}>{conv.label}</span>
                <div className={styles.caseCardActions}>
                  <SaveNoteButton
                    tool="case"
                    title={`${conv.label} — case`}
                    content={result}
                    disabled={!result}
                  />
                  <button
                    className={copied === conv.id ? styles.copyBtnDone : styles.copyBtn}
                    onClick={() => handleCopy(conv.id, conv)}
                    disabled={!input}
                  >
                    {copied === conv.id ? '✓' : 'Copy'}
                  </button>
                </div>
              </div>
              <code className={styles.caseResult}>
                {result || <span className={styles.placeholder}>—</span>}
              </code>
            </div>
          )
        })}
      </div>
    </div>
  )
}

// ── Text Diff ─────────────────────────────────────────────────────────────────

function computeLineDiff(text1, text2) {
  const a = text1.split('\n')
  const b = text2.split('\n')
  const m = a.length
  const n = b.length

  // Guard against extremely large inputs
  if (m * n > 500_000) {
    return [{ type: 'info', text: 'Texts are too large for line-by-line diff. Please use shorter inputs.' }]
  }

  // LCS dynamic programming
  const dp = Array.from({ length: m + 1 }, () => new Int32Array(n + 1))
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      dp[i][j] = a[i - 1] === b[j - 1]
        ? dp[i - 1][j - 1] + 1
        : Math.max(dp[i - 1][j], dp[i][j - 1])
    }
  }

  // Backtrack
  const result = []
  let i = m, j = n
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && a[i - 1] === b[j - 1]) {
      result.unshift({ type: 'same',    text: a[i - 1] }); i--; j--
    } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
      result.unshift({ type: 'added',   text: b[j - 1] }); j--
    } else {
      result.unshift({ type: 'removed', text: a[i - 1] }); i--
    }
  }
  return result
}

function TextDiff() {
  const [text1, setText1]       = useState('')
  const [text2, setText2]       = useState('')
  const [diffReady, setDiffReady] = useState(false)

  const diff = useMemo(
    () => (diffReady ? computeLineDiff(text1, text2) : []),
    [text1, text2, diffReady],
  )
  // const diff = trackTool('text.transform', () => computeDiff(left, right))

  const stats = useMemo(() => ({
    added:   diff.filter(d => d.type === 'added').length,
    removed: diff.filter(d => d.type === 'removed').length,
    same:    diff.filter(d => d.type === 'same').length,
  }), [diff])

  function handleCompare() {
    // Sprint 18: track the diff invocation. The actual compute happens
    // in the useMemo that observes diffReady — we record the click here
    // because that's the user-meaningful action; the captured duration
    // is near-zero, but the count is what matters for this tool.
    trackTool('text.transform', () => null)
    setDiffReady(true)
  }
  function handleClear()   { setText1(''); setText2(''); setDiffReady(false) }

  return (
    <div className={styles.tabPanel}>
      <div className={styles.diffInputs}>
        <div className={styles.diffInputBlock}>
          <label className={styles.diffLabel}>Original</label>
          <textarea
            className={styles.diffTextarea}
            value={text1}
            onChange={e => { setText1(e.target.value); setDiffReady(false) }}
            placeholder="Paste original text here…"
            rows={9}
            spellCheck={false}
          />
        </div>
        <div className={styles.diffInputBlock}>
          <label className={styles.diffLabel}>Modified</label>
          <textarea
            className={styles.diffTextarea}
            value={text2}
            onChange={e => { setText2(e.target.value); setDiffReady(false) }}
            placeholder="Paste modified text here…"
            rows={9}
            spellCheck={false}
          />
        </div>
      </div>

      <div className={styles.actionRow}>
        <button className={styles.primaryBtn} onClick={handleCompare} disabled={!text1 && !text2}>
          Compare →
        </button>
        <button className={styles.ghostBtn} onClick={handleClear}>Clear</button>
      </div>

      {diffReady && diff.length > 0 && (
        <>
          <div className={styles.diffStats}>
            <span className={styles.diffAdded}>+{stats.added} added</span>
            <span className={styles.diffRemoved}>−{stats.removed} removed</span>
            <span className={styles.diffSame}>{stats.same} unchanged</span>
          </div>
          <div className={styles.diffOutput}>
            {diff.map((line, idx) => (
              <div
                key={idx}
                className={
                  line.type === 'added'   ? styles.diffLineAdded   :
                  line.type === 'removed' ? styles.diffLineRemoved :
                  line.type === 'info'    ? styles.diffLineInfo    :
                  styles.diffLineSame
                }
              >
                <span className={styles.diffMarker}>
                  {line.type === 'added' ? '+' : line.type === 'removed' ? '−' : ' '}
                </span>
                <span className={styles.diffText}>{line.text || '\u00a0'}</span>
              </div>
            ))}
          </div>
          <div className={styles.toolFooter}>
            <SaveNoteButton
              tool="diff"
              title={`Diff: +${stats.added}/-${stats.removed}`}
              content={diff.map(line => {
                const marker = line.type === 'added' ? '+ '
                  : line.type === 'removed' ? '- '
                  : line.type === 'info'    ? '! '
                  : '  '
                return marker + (line.text || '')
              }).join('\n')}
            />
          </div>
        </>
      )}
    </div>
  )
}

// ── Regex Tester ──────────────────────────────────────────────────────────────

function RegexTester() {
  const [pattern,  setPattern]  = useState('')
  const [flags,    setFlags]    = useState({ g: true, i: false, m: false, s: false })
  const [testStr,  setTestStr]  = useState('')

  const flagStr = Object.entries(flags).filter(([, v]) => v).map(([k]) => k).join('')

  const result = useMemo(() => trackTool('text.transform', () => {
    if (!pattern) return null
    try {
      const re = new RegExp(pattern, flagStr)
      const matches = []
      let m
      if (flagStr.includes('g')) {
        // Global: find all matches
        while ((m = re.exec(testStr)) !== null && matches.length < 500) {
          matches.push({
            match:       m[0],
            index:       m.index,
            end:         m.index + m[0].length,
            groups:      m.slice(1),
            namedGroups: m.groups || {},
          })
          // Prevent infinite loop on zero-length matches
          if (m[0].length === 0) { re.lastIndex++ }
        }
      } else {
        // Non-global: find first match only
        m = re.exec(testStr)
        if (m) {
          matches.push({
            match:       m[0],
            index:       m.index,
            end:         m.index + m[0].length,
            groups:      m.slice(1),
            namedGroups: m.groups || {},
          })
        }
      }
      return { matches, error: null }
    } catch (e) {
      return { matches: [], error: e.message }
    }
  }), [pattern, flagStr, testStr])

  const highlighted = useMemo(() => {
    if (!result || result.error || result.matches.length === 0 || !testStr) return null
    const segs = []
    let last = 0
    for (const m of result.matches) {
      if (m.index > last) segs.push({ text: testStr.slice(last, m.index), isMatch: false })
      segs.push({ text: m.match, isMatch: true })
      last = m.end
    }
    if (last < testStr.length) segs.push({ text: testStr.slice(last), isMatch: false })
    return segs
  }, [result, testStr])

  const FLAG_HINTS = { g: 'Global', i: 'Case Insensitive', m: 'Multiline', s: 'Dot All' }

  return (
    <div className={styles.tabPanel}>
      <div className={styles.regexRow}>
        <div className={styles.regexInputWrap}>
          <span className={styles.regexDelim}>/</span>
          <input
            className={styles.regexInput}
            value={pattern}
            onChange={e => setPattern(e.target.value)}
            placeholder="pattern"
            spellCheck={false}
            autoComplete="off"
          />
          <span className={styles.regexDelim}>/</span>
          <span className={styles.regexFlagDisplay}>{flagStr}</span>
        </div>
        <div className={styles.flagGroup}>
          {Object.keys(flags).map(f => (
            <button
              key={f}
              className={flags[f] ? styles.flagActive : styles.flagBtn}
              onClick={() => setFlags(fl => ({ ...fl, [f]: !fl[f] }))}
              title={FLAG_HINTS[f]}
            >
              {f}
            </button>
          ))}
        </div>
      </div>

      {result?.error && (
        <div className={styles.errorBanner} role="alert">
          Invalid pattern: {result.error}
        </div>
      )}

      <textarea
        className={styles.mainTextarea}
        value={testStr}
        onChange={e => setTestStr(e.target.value)}
        placeholder="Enter test string here…"
        rows={6}
        spellCheck={false}
      />

      {highlighted && (
        <div className={styles.regexHighlightBox}>
          {highlighted.map((seg, i) =>
            seg.isMatch
              ? <mark key={i} className={styles.regexMatch}>{seg.text}</mark>
              : <span key={i}>{seg.text}</span>
          )}
        </div>
      )}

      {result && !result.error && testStr && (
        <div className={styles.matchSummary}>
          <span className={styles.matchCount}>
            {result.matches.length} match{result.matches.length !== 1 ? 'es' : ''}
          </span>
          {result.matches.length > 0 && (
            <ul className={styles.matchList}>
              {result.matches.slice(0, 20).map((m, i) => (
                <li key={i} className={styles.matchItem}>
                  <span className={styles.matchNum}>#{i + 1}</span>
                  <code className={styles.matchText}>{JSON.stringify(m.match)}</code>
                  <span className={styles.matchPos}>idx {m.index}–{m.end}</span>
                  {m.groups.length > 0 && (
                    <span className={styles.matchGroups}>
                      groups: {m.groups.map(g => JSON.stringify(g)).join(', ')}
                    </span>
                  )}
                </li>
              ))}
              {result.matches.length > 20 && (
                <li className={styles.matchMore}>… and {result.matches.length - 20} more</li>
              )}
            </ul>
          )}
        </div>
      )}

      {result && !result.error && testStr && (
        <div className={styles.toolFooter}>
          <SaveNoteButton
            tool="regex"
            title={`/${pattern || '(empty)'}/${flagStr} — ${result.matches.length} match${result.matches.length !== 1 ? 'es' : ''}`}
            content={
              `Pattern: /${pattern}/${flagStr}\n` +
              `Test string:\n${testStr}\n\n` +
              `Matches (${result.matches.length}):\n` +
              result.matches.slice(0, 100).map((m, i) =>
                `  #${i + 1} ${JSON.stringify(m.match)} @ ${m.index}-${m.end}` +
                (m.groups.length ? ` · groups: ${m.groups.map(g => JSON.stringify(g)).join(', ')}` : '')
              ).join('\n')
            }
            disabled={result.matches.length === 0}
          />
        </div>
      )}
    </div>
  )
}

// ── Slug Generator ────────────────────────────────────────────────────────────

function SlugGenerator() {
  const [input,     setInput]     = useState('')
  const [separator, setSeparator] = useState('-')
  const [lowercase, setLowercase] = useState(true)
  const [maxLength, setMaxLength] = useState('')

  const slug = useMemo(() => trackTool('text.transform', () => {
    let s = input
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')           // strip diacritics
      .replace(/&/g, 'and')                       // & → and
      .replace(/[^a-zA-Z0-9\s\-_]/g, '')         // strip non-alphanumeric
      .trim()
      .replace(/[\s\-_]+/g, separator)            // collapse separators

    if (lowercase) s = s.toLowerCase()

    const max = parseInt(maxLength, 10)
    if (max > 0 && s.length > max) {
      s = s.slice(0, max).replace(new RegExp(`[${separator}]$`), '')
    }

    return s
  }), [input, separator, lowercase, maxLength])

  const SEPS = [
    { value: '-', label: 'Hyphen (-)' },
    { value: '_', label: 'Underscore (_)' },
    { value: '.', label: 'Dot (.)' },
  ]

  return (
    <div className={styles.tabPanel}>
      <div className={styles.fieldGroup}>
        <label className={styles.fieldLabel}>Input Text</label>
        <input
          className={styles.textInput}
          value={input}
          onChange={e => setInput(e.target.value)}
          placeholder="My Amazing Blog Post Title!"
        />
      </div>

      <div className={styles.slugOptionsRow}>
        <div className={styles.fieldGroup}>
          <label className={styles.fieldLabel}>Word Separator</label>
          <div className={styles.sepToggle}>
            {SEPS.map(s => (
              <button
                key={s.value}
                className={separator === s.value ? styles.sepActive : styles.sepBtn}
                onClick={() => setSeparator(s.value)}
              >
                {s.label}
              </button>
            ))}
          </div>
        </div>

        <div className={styles.fieldGroup}>
          <label className={styles.fieldLabel}>Max Length (optional)</label>
          <input
            className={styles.textInput}
            style={{ maxWidth: 120 }}
            type="number"
            min={1}
            value={maxLength}
            onChange={e => setMaxLength(e.target.value)}
            placeholder="e.g. 60"
          />
        </div>

        <label className={styles.checkLabel}>
          <input
            type="checkbox"
            checked={lowercase}
            onChange={e => setLowercase(e.target.checked)}
          />
          Lowercase
        </label>
      </div>

      <div className={styles.slugResultBox}>
        <code className={styles.slugCode}>{slug || <span className={styles.placeholder}>your-slug-will-appear-here</span>}</code>
        <CopyButton text={slug} />
      </div>

      {slug && (
        <p className={styles.slugMeta}>{slug.length} characters</p>
      )}

      {slug && (
        <div className={styles.toolFooter}>
          <SaveNoteButton
            tool="slug"
            title={`Slug: ${slug.slice(0, 40)}`}
            content={`Source: ${input}\nSlug:   ${slug}\nLength: ${slug.length} chars\nSeparator: "${separator}"  Lowercase: ${lowercase}`}
          />
        </div>
      )}
    </div>
  )
}

// ── Lorem Ipsum Generator ─────────────────────────────────────────────────────

const LOREM_WORDS = [
  'lorem','ipsum','dolor','sit','amet','consectetur','adipiscing','elit','sed','do',
  'eiusmod','tempor','incididunt','ut','labore','et','dolore','magna','aliqua','enim',
  'ad','minim','veniam','quis','nostrud','exercitation','ullamco','laboris','nisi',
  'aliquip','ex','ea','commodo','consequat','duis','aute','irure','in','reprehenderit',
  'voluptate','velit','esse','cillum','fugiat','nulla','pariatur','excepteur','sint',
  'occaecat','cupidatat','non','proident','sunt','culpa','qui','officia','deserunt',
  'mollit','anim','id','est','laborum','perspiciatis','unde','omnis','iste','natus',
  'accusantium','doloremque','laudantium','totam','rem','aperiam','eaque','ipsa','quae',
  'ab','illo','inventore','veritatis','quasi','architecto','beatae','vitae','dicta',
  'explicabo','nemo','ipsam','quia','voluptas','aspernatur','odit','aut','fugit',
  'consequuntur','magni','dolores','eos','ratione','sequi','nesciunt','neque','porro',
  'quisquam','qui','dolorem','quia','dolor','adipisci','velit','numquam','eius',
  'modi','tempora','incidunt','labore','magnam','aliquam','quaerat','voluptatem',
]

const CLASSIC = 'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.'

function rnd(min, max) { return Math.floor(Math.random() * (max - min + 1)) + min }
function randWord()     { return LOREM_WORDS[Math.floor(Math.random() * LOREM_WORDS.length)] }

function genSentence() {
  const words = Array.from({ length: rnd(6, 14) }, randWord)
  const s = words.join(' ')
  return s[0].toUpperCase() + s.slice(1) + '.'
}

function genParagraph(sentences) {
  return Array.from({ length: sentences }, genSentence).join(' ')
}

function LoremIpsum() {
  const [paragraphs,  setParagraphs]  = useState(3)
  const [sentences,   setSentences]   = useState(4)
  const [startClassic, setStartClassic] = useState(true)
  const [output,      setOutput]      = useState('')

  function generate() {
    const out = trackTool('text.transform', () => {
      const paras = Array.from({ length: paragraphs }, (_, i) =>
        i === 0 && startClassic ? CLASSIC : genParagraph(sentences)
      )
      return paras.join('\n\n')
    })
    setOutput(out)
  }

  return (
    <div className={styles.tabPanel}>
      <div className={styles.loremOptions}>
        <div className={styles.loremControl}>
          <label className={styles.fieldLabel}>Paragraphs</label>
          <div className={styles.stepper}>
            <button className={styles.stepBtn} onClick={() => setParagraphs(p => Math.max(1, p - 1))}>−</button>
            <span className={styles.stepVal}>{paragraphs}</span>
            <button className={styles.stepBtn} onClick={() => setParagraphs(p => Math.min(30, p + 1))}>+</button>
          </div>
        </div>

        <div className={styles.loremControl}>
          <label className={styles.fieldLabel}>Sentences per Paragraph</label>
          <div className={styles.stepper}>
            <button className={styles.stepBtn} onClick={() => setSentences(s => Math.max(1, s - 1))}>−</button>
            <span className={styles.stepVal}>{sentences}</span>
            <button className={styles.stepBtn} onClick={() => setSentences(s => Math.min(12, s + 1))}>+</button>
          </div>
        </div>

        <label className={styles.checkLabel}>
          <input
            type="checkbox"
            checked={startClassic}
            onChange={e => setStartClassic(e.target.checked)}
          />
          Start with classic "Lorem ipsum…"
        </label>
      </div>

      <div className={styles.actionRow}>
        <button className={styles.primaryBtn} onClick={generate}>
          Generate
        </button>
        {output && <CopyButton text={output} />}
      </div>

      {output && (
        <textarea
          className={styles.mainTextarea}
          value={output}
          readOnly
          rows={14}
        />
      )}

      {output && (
        <div className={styles.toolFooter}>
          <SaveNoteButton
            tool="lorem"
            title={`Lorem: ${paragraphs}p × ${sentences}s`}
            content={output}
          />
        </div>
      )}
    </div>
  )
}

// ── Page Shell ────────────────────────────────────────────────────────────────

export default function TextUtilitiesPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState('counter')

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  function renderTab() {
    switch (activeTab) {
      case 'counter': return <WordCounter />
      case 'case':    return <CaseConverter />
      case 'diff':    return <TextDiff />
      case 'regex':   return <RegexTester />
      case 'slug':    return <SlugGenerator />
      case 'lorem':   return <LoremIpsum />
      default:        return <WordCounter />
    }
  }

  return (
    <NotesProvider username={username}>
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
          <div className={styles.heroBadge}>Text Utilities</div>
          <h1 className={styles.heroTitle}>
            Text<br />
            <span className={styles.heroAccent}>Utilities</span>
          </h1>
          <p className={styles.heroSub}>
            Six developer-friendly text tools — word counter, case converter, diff checker,
            regex tester, slug generator, and Lorem Ipsum — all computed client-side with zero latency.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div className={styles.statCard}>
            <span className={styles.statValue}>6</span>
            <span className={styles.statLabel}>tools</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>14</span>
            <span className={styles.statLabel}>case formats</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>0ms</span>
            <span className={styles.statLabel}>server lag</span>
          </div>
        </div>
      </section>

      <main className={styles.main}>

        {/* ── Saved notes panel (collapsible) ─────────────────────── */}
        <NotesPanel />

        {/* ── Tab bar ─────────────────────────────────────────────── */}
        <nav className={styles.tabBar} aria-label="Text utilities">
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
    </NotesProvider>
  )
}
