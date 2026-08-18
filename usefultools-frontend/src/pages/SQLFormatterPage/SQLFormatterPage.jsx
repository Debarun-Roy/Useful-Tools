/**
 * SQLFormatterPage.jsx — Sprint 23
 *
 * Professional SQL toolkit. All processing is client-side (backend frozen).
 *
 * Tabs
 *   1. Formatter  — Format SQL with keyword case, indentation, dialect
 *   2. Analyzer   — Query complexity, index hints, readable breakdown
 *   3. CRUD       — Generate INSERT/SELECT/UPDATE/DELETE from table schema
 *
 * Activity logging : 'sql.format', 'sql.analyze', 'sql.crud'
 * Metrics          : trackTool wraps each operation
 *
 * Privacy rule: log only operation type and query stats (clause count,
 * table count) — never log raw SQL or schema definitions.
 */

import { useState, useMemo, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logoutUser } from '../../api/apiClient'
import UserMenu from '../../components/UserMenu/UserMenu'
import { logActivity } from '../../utils/logActivity'
import { trackTool } from '../../utils/logMetric'
import styles from './SQLFormatterPage.module.css'

// ─── SQL Formatter ────────────────────────────────────────────────────────────

const SQL_KEYWORDS = new Set([
  'SELECT','FROM','WHERE','AND','OR','NOT','IN','EXISTS','BETWEEN','LIKE',
  'IS','NULL','JOIN','LEFT','RIGHT','INNER','OUTER','FULL','CROSS',
  'ON','AS','GROUP','BY','ORDER','HAVING','LIMIT','OFFSET','UNION','ALL',
  'INSERT','INTO','VALUES','UPDATE','SET','DELETE','CREATE','TABLE','DROP',
  'ALTER','ADD','COLUMN','INDEX','VIEW','DATABASE','SCHEMA','IF','EXISTS',
  'PRIMARY','KEY','FOREIGN','REFERENCES','UNIQUE','NOT','DEFAULT','CHECK',
  'CONSTRAINT','CASCADE','DISTINCT','COUNT','SUM','AVG','MIN','MAX',
  'CASE','WHEN','THEN','ELSE','END','WITH','RECURSIVE','EXCEPT','INTERSECT',
  'TRUNCATE','BEGIN','COMMIT','ROLLBACK','TRANSACTION','GRANT','REVOKE',
])

const SQL_FUNCTIONS = new Set([
  'COUNT','SUM','AVG','MIN','MAX','COALESCE','NULLIF','ISNULL','IFNULL',
  'NVL','UPPER','LOWER','TRIM','LTRIM','RTRIM','LENGTH','LEN','SUBSTR',
  'SUBSTRING','REPLACE','CHARINDEX','POSITION','CONCAT','NOW','CURDATE',
  'GETDATE','SYSDATE','DATE','YEAR','MONTH','DAY','HOUR','MINUTE','SECOND',
  'DATEDIFF','DATEADD','CONVERT','CAST','ROW_NUMBER','RANK','DENSE_RANK',
  'LEAD','LAG','FIRST_VALUE','LAST_VALUE','NTILE','OVER','PARTITION',
])

const CLAUSE_STARTERS = new Set([
  'SELECT','FROM','WHERE','GROUP','ORDER','HAVING','LIMIT','OFFSET',
  'JOIN','LEFT','RIGHT','INNER','OUTER','FULL','CROSS','ON',
  'UNION','EXCEPT','INTERSECT','INSERT','UPDATE','DELETE','CREATE',
  'DROP','ALTER','WITH',
])

/**
 * Tokenise SQL into words, strings, comments, punctuation.
 */
function tokenise(sql) {
  const tokens = []
  let i = 0
  while (i < sql.length) {
    // Single-line comment
    if (sql[i] === '-' && sql[i+1] === '-') {
      const end = sql.indexOf('\n', i)
      tokens.push({ type: 'comment', value: end === -1 ? sql.slice(i) : sql.slice(i, end) })
      i = end === -1 ? sql.length : end
      continue
    }
    // Block comment
    if (sql[i] === '/' && sql[i+1] === '*') {
      const end = sql.indexOf('*/', i + 2)
      const v = end === -1 ? sql.slice(i) : sql.slice(i, end + 2)
      tokens.push({ type: 'comment', value: v })
      i = end === -1 ? sql.length : end + 2
      continue
    }
    // String literal
    if (sql[i] === "'" || sql[i] === '"' || sql[i] === '`') {
      const q = sql[i]
      let j = i + 1
      while (j < sql.length && (sql[j] !== q || sql[j-1] === '\\')) j++
      tokens.push({ type: 'string', value: sql.slice(i, j + 1) })
      i = j + 1
      continue
    }
    // Whitespace
    if (/\s/.test(sql[i])) {
      let j = i
      while (j < sql.length && /\s/.test(sql[j])) j++
      tokens.push({ type: 'ws', value: sql.slice(i, j) })
      i = j
      continue
    }
    // Word / identifier
    if (/[a-zA-Z_$]/.test(sql[i])) {
      let j = i
      while (j < sql.length && /[\w$]/.test(sql[j])) j++
      const word = sql.slice(i, j)
      const up = word.toUpperCase()
      const type = SQL_KEYWORDS.has(up) ? 'keyword'
                 : SQL_FUNCTIONS.has(up) ? 'function'
                 : 'identifier'
      tokens.push({ type, value: word })
      i = j
      continue
    }
    // Number
    if (/[\d.]/.test(sql[i])) {
      let j = i
      while (j < sql.length && /[\d.eE+\-]/.test(sql[j])) j++
      tokens.push({ type: 'number', value: sql.slice(i, j) })
      i = j
      continue
    }
    // Punctuation / operator
    tokens.push({ type: 'punct', value: sql[i] })
    i++
  }
  return tokens.filter(t => !(t.type === 'ws'))
}

function formatSQL(sql, { indent = 2, keywordCase = 'upper', dialect = 'standard' }) {
  if (!sql.trim()) return ''

  const indentStr = ' '.repeat(Math.max(1, indent))
  const tokens = tokenise(sql)
  const lines = []
  let line = ''
  let depth = 0
  let parenDepth = 0

  function applyCase(word) {
    const up = word.toUpperCase()
    if (!SQL_KEYWORDS.has(up) && !SQL_FUNCTIONS.has(up)) return word
    if (keywordCase === 'upper') return up
    if (keywordCase === 'lower') return word.toLowerCase()
    if (keywordCase === 'title') return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()
    return word
  }

  function flush() {
    const trimmed = line.trim()
    if (trimmed) lines.push(indentStr.repeat(depth) + trimmed)
    line = ''
  }

  for (let idx = 0; idx < tokens.length; idx++) {
    const tok = tokens[idx]
    const up = tok.value.toUpperCase()

    if (tok.type === 'comment') {
      flush()
      lines.push(indentStr.repeat(depth) + tok.value)
      continue
    }

    if (tok.type === 'keyword') {
      const isClauseStart = CLAUSE_STARTERS.has(up) && parenDepth === 0

      if (isClauseStart) {
        flush()
        // Sub-clauses indent one level deeper than SELECT etc.
        const isSubClause = ['ON','WHERE','HAVING','GROUP','ORDER','LIMIT','OFFSET'].includes(up)
        if (isSubClause) {
          line = applyCase(tok.value)
        } else {
          line = applyCase(tok.value)
        }
      } else {
        line += (line ? ' ' : '') + applyCase(tok.value)
      }
      continue
    }

    if (tok.type === 'punct') {
      if (tok.value === '(') {
        parenDepth++
        line += tok.value
      } else if (tok.value === ')') {
        parenDepth = Math.max(0, parenDepth - 1)
        line += tok.value
      } else if (tok.value === ',') {
        if (parenDepth === 0) {
          // Top-level comma: new item on next line at same depth
          line += ','
          flush()
        } else {
          line += ', '
        }
      } else if (tok.value === ';') {
        line += ';'
        flush()
        lines.push('')  // blank line between statements
      } else {
        line += ' ' + tok.value + ' '
      }
      continue
    }

    // Default: word, number, string
    const display = tok.type === 'keyword' || tok.type === 'function'
      ? applyCase(tok.value) : tok.value
    line += (line && !/[\s(]$/.test(line) ? ' ' : '') + display
  }

  flush()

  // Clean up excessive blank lines
  const result = lines.join('\n').replace(/\n{3,}/g, '\n\n')
  return result
}

// ─── SQL Analyzer ─────────────────────────────────────────────────────────────

function analyzeSQL(sql) {
  if (!sql.trim()) return null
  const upper = sql.toUpperCase()

  // Clause detection
  const clauses = {
    hasSelect:   /\bSELECT\b/.test(upper),
    hasFrom:     /\bFROM\b/.test(upper),
    hasWhere:    /\bWHERE\b/.test(upper),
    hasGroupBy:  /\bGROUP\s+BY\b/.test(upper),
    hasHaving:   /\bHAVING\b/.test(upper),
    hasOrderBy:  /\bORDER\s+BY\b/.test(upper),
    hasLimit:    /\bLIMIT\b/.test(upper),
    hasJoin:     /\b(INNER|LEFT|RIGHT|FULL|CROSS)?\s*JOIN\b/.test(upper),
    hasSubquery: /\(\s*SELECT\b/.test(upper),
    hasUnion:    /\bUNION\b/.test(upper),
    hasDistinct: /\bDISTINCT\b/.test(upper),
    hasAggregate:/\b(COUNT|SUM|AVG|MIN|MAX)\s*\(/.test(upper),
    hasWindow:   /\bOVER\s*\(/.test(upper),
    hasCTE:      /\bWITH\b/.test(upper),
    isInsert:    /^\s*INSERT\b/.test(upper),
    isUpdate:    /^\s*UPDATE\b/.test(upper),
    isDelete:    /^\s*DELETE\b/.test(upper),
    isCreate:    /^\s*CREATE\b/.test(upper),
    isDrop:      /^\s*DROP\b/.test(upper),
    isAlter:     /^\s*ALTER\b/.test(upper),
  }

  // Count joins
  const joinCount = (upper.match(/\bJOIN\b/g) || []).length

  // Count tables referenced (rough)
  const fromMatches = [...upper.matchAll(/\bFROM\s+([a-zA-Z_]\w*)/g)]
  const joinMatches = [...upper.matchAll(/\bJOIN\s+([a-zA-Z_]\w*)/g)]
  const tableCount = new Set([...fromMatches, ...joinMatches].map(m => m[1])).size

  // Detect missing WHERE on UPDATE/DELETE
  const dangerousMutation = (clauses.isUpdate || clauses.isDelete) && !clauses.hasWhere

  // Complexity score (0–100)
  let complexity = 0
  if (clauses.hasSelect)    complexity += 5
  if (clauses.hasWhere)     complexity += 5
  if (clauses.hasGroupBy)   complexity += 10
  if (clauses.hasHaving)    complexity += 10
  if (clauses.hasOrderBy)   complexity += 5
  if (clauses.hasJoin)      complexity += joinCount * 10
  if (clauses.hasSubquery)  complexity += 20
  if (clauses.hasUnion)     complexity += 10
  if (clauses.hasWindow)    complexity += 20
  if (clauses.hasCTE)       complexity += 15
  if (clauses.hasAggregate) complexity += 10
  complexity = Math.min(100, complexity)

  const complexityLabel = complexity < 20 ? 'Simple'
                        : complexity < 50 ? 'Moderate'
                        : complexity < 75 ? 'Complex'
                        : 'Very Complex'

  // Index hints
  const hints = []
  if (clauses.hasWhere && !clauses.hasJoin) {
    const colMatch = upper.match(/\bWHERE\s+([a-zA-Z_]\w*)\s*=/)
    if (colMatch) hints.push(`Consider an index on "${colMatch[1].toLowerCase()}" (used in WHERE equality filter)`)
  }
  if (joinCount > 0) {
    hints.push(`${joinCount} JOIN(s) detected — ensure JOIN columns are indexed on both tables`)
  }
  if (clauses.hasOrderBy) {
    hints.push('ORDER BY may cause a filesort — consider an index on the sort column(s)')
  }
  if (clauses.hasGroupBy) {
    hints.push('GROUP BY may benefit from a composite index covering both WHERE and GROUP BY columns')
  }
  if (clauses.hasSubquery) {
    hints.push('Correlated subqueries can be slow — consider rewriting as a JOIN or CTE')
  }
  if (clauses.hasDistinct) {
    hints.push('DISTINCT forces a sort/dedup pass — ensure the result set genuinely has duplicates')
  }
  if (dangerousMutation) {
    hints.unshift('⚠ UPDATE/DELETE without WHERE will affect ALL rows — add a WHERE clause!')
  }
  if (hints.length === 0) hints.push('No specific index recommendations for this query.')

  // Readable breakdown
  const breakdown = []
  const queryType = clauses.isInsert ? 'INSERT'
                  : clauses.isUpdate ? 'UPDATE'
                  : clauses.isDelete ? 'DELETE'
                  : clauses.isCreate ? 'CREATE'
                  : clauses.isDrop   ? 'DROP'
                  : clauses.isAlter  ? 'ALTER'
                  : clauses.hasSelect ? 'SELECT'
                  : 'UNKNOWN'

  breakdown.push(`Query type: ${queryType}`)
  if (tableCount > 0) breakdown.push(`Tables referenced: ${tableCount}`)
  if (joinCount > 0)  breakdown.push(`Joins: ${joinCount}`)
  if (clauses.hasSubquery) breakdown.push('Contains: subquery')
  if (clauses.hasCTE)      breakdown.push('Contains: Common Table Expression (WITH)')
  if (clauses.hasWindow)   breakdown.push('Contains: window function (OVER)')
  if (clauses.hasAggregate) breakdown.push('Contains: aggregate function(s)')
  if (clauses.hasGroupBy)  breakdown.push('Contains: GROUP BY')
  if (clauses.hasHaving)   breakdown.push('Contains: HAVING (post-aggregation filter)')
  if (clauses.hasDistinct) breakdown.push('Contains: DISTINCT')
  if (clauses.hasUnion)    breakdown.push('Contains: UNION')

  return {
    queryType, complexity, complexityLabel,
    tableCount, joinCount,
    clauses, hints, breakdown,
    dangerousMutation,
  }
}

// ─── CRUD generator ───────────────────────────────────────────────────────────

function parseSchemaCols(schemaText) {
  // Accept: col_name TYPE, col_name TYPE NOT NULL, etc.
  const lines = schemaText.split(/[\n,]/).map(l => l.trim()).filter(Boolean)
  return lines.map(line => {
    const m = line.match(/^([a-zA-Z_]\w*)(?:\s+(.+))?$/)
    if (!m) return null
    const name = m[1]
    const rest = (m[2] || '').toUpperCase()
    const isPK = rest.includes('PRIMARY KEY') || rest.includes('PRIMARYKEY')
    const type = (rest.split(/\s/)[0] || 'TEXT')
    return { name, type, isPK }
  }).filter(Boolean)
}

function generateCRUD(tableName, schemaText, dialect) {
  if (!tableName.trim() || !schemaText.trim()) return ''
  const cols = parseSchemaCols(schemaText)
  if (cols.length === 0) return '-- No columns parsed. Use format: col_name TYPE'

  const allCols   = cols.map(c => c.name)
  const pkCols    = cols.filter(c => c.isPK).map(c => c.name)
  const nonPkCols = cols.filter(c => !c.isPK).map(c => c.name)
  const pk        = pkCols.length > 0 ? pkCols : [allCols[0]]
  const insertCols = nonPkCols.length > 0 ? nonPkCols : allCols

  const ph = (name, i) =>
    dialect === 'postgresql' ? `$${i + 1}`
    : dialect === 'sqlserver' ? `@${name}`
    : `?` // mysql / standard

  const selectAll = `-- SELECT all rows\nSELECT ${allCols.join(', ')}\nFROM ${tableName};`

  const selectById = `-- SELECT by primary key\nSELECT ${allCols.join(', ')}\nFROM ${tableName}\nWHERE ${pk.map((p, i) => `${p} = ${ph(p, i)}`).join('\n  AND ')};`

  const insert = `-- INSERT a new row\nINSERT INTO ${tableName} (\n  ${insertCols.join(',\n  ')}\n)\nVALUES (\n  ${insertCols.map((c, i) => ph(c, i)).join(',\n  ')}\n);`

  const updateSets = nonPkCols.length > 0 ? nonPkCols : allCols.filter(c => !pk.includes(c))
  const updatePlaceholders = updateSets.map((c, i) => `${c} = ${ph(c, i)}`)
  const pkPlaceholders = pk.map((p, i) => `${p} = ${ph(p, updateSets.length + i)}`)
  const update = updateSets.length > 0
    ? `-- UPDATE by primary key\nUPDATE ${tableName}\nSET\n  ${updatePlaceholders.join(',\n  ')}\nWHERE ${pkPlaceholders.join('\n  AND ')};`
    : `-- UPDATE (no non-PK columns found)`

  const del = `-- DELETE by primary key\nDELETE FROM ${tableName}\nWHERE ${pk.map((p, i) => `${p} = ${ph(p, i)}`).join('\n  AND ')};`

  const createColDefs = cols.map(c => {
    let def = `  ${c.name} ${c.type}`
    if (c.isPK) def += ' PRIMARY KEY'
    return def
  }).join(',\n')

  const createTable = `-- CREATE TABLE\nCREATE TABLE IF NOT EXISTS ${tableName} (\n${createColDefs}\n);`

  return [createTable, selectAll, selectById, insert, update, del].join('\n\n')
}

// ─── Shared sub-components ────────────────────────────────────────────────────

function TabBar({ tabs, active, onChange }) {
  return (
    <div className={styles.tabBar} role="tablist">
      {tabs.map(t => (
        <button
          key={t.id}
          role="tab"
          aria-selected={active === t.id}
          className={active === t.id ? styles.tabActive : styles.tab}
          onClick={() => onChange(t.id)}
        >
          <span className={styles.tabIcon}>{t.icon}</span>
          {t.label}
        </button>
      ))}
    </div>
  )
}

function CopyButton({ text, label = 'Copy', doneLabel = 'Copied!' }) {
  const [done, setDone] = useState(false)
  function handle() {
    if (!text) return
    navigator.clipboard.writeText(text).then(() => {
      setDone(true); setTimeout(() => setDone(false), 1400)
    })
  }
  return (
    <button type="button" className={styles.copyBtn} onClick={handle} disabled={!text}>
      {done ? doneLabel : label}
    </button>
  )
}

// ─── Formatter Tab ─────────────────────────────────────────────────────────────

const SAMPLE_SQL = `SELECT u.id, u.name, u.email, COUNT(o.id) AS order_count, SUM(o.total) AS revenue FROM users u LEFT JOIN orders o ON u.id = o.user_id WHERE u.created_at >= '2024-01-01' AND u.status = 'active' GROUP BY u.id, u.name, u.email HAVING COUNT(o.id) > 0 ORDER BY revenue DESC LIMIT 25;`

function FormatterTab() {
  const [input,       setInput]       = useState(SAMPLE_SQL)
  const [indent,      setIndent]      = useState(2)
  const [keywordCase, setKeywordCase] = useState('upper')
  const [dialect,     setDialect]     = useState('standard')

  const output = useMemo(() => {
    if (!input.trim()) return ''
    return trackTool('sql.format', () =>
      formatSQL(input, { indent, keywordCase, dialect })
    )
  }, [input, indent, keywordCase, dialect])

  function handleFormat() {
    const tokens = tokenise(input)
    const clauseCount = tokens.filter(t => t.type === 'keyword' && CLAUSE_STARTERS.has(t.value.toUpperCase())).length
    logActivity('sql.format', 'Formatted SQL query', {
      inputLength: input.length,
      clauseCount,
      keywordCase,
      dialect,
    })
  }

  function downloadSQL() {
    const blob = new Blob([output], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = `query-${Date.now()}.sql`; a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className={styles.twoColLayout}>
      {/* Controls */}
      <div className={styles.panel}>
        <p className={styles.eyebrow}>Configuration</p>
        <h2 className={styles.panelTitle}>SQL Formatter</h2>

        <label className={styles.fieldLabel}>Dialect</label>
        <div className={styles.chipRow}>
          {['standard','mysql','postgresql','sqlserver','sqlite'].map(d => (
            <button key={d} type="button"
              className={dialect === d ? styles.chipActive : styles.chip}
              onClick={() => setDialect(d)}>
              {d === 'sqlserver' ? 'SQL Server' : d.charAt(0).toUpperCase() + d.slice(1)}
            </button>
          ))}
        </div>

        <label className={styles.fieldLabel}>Keyword Case</label>
        <div className={styles.chipRow}>
          {[['upper','UPPERCASE'],['lower','lowercase'],['title','Titlecase']].map(([v,l]) => (
            <button key={v} type="button"
              className={keywordCase === v ? styles.chipActive : styles.chip}
              onClick={() => setKeywordCase(v)}>
              {l}
            </button>
          ))}
        </div>

        <label className={styles.fieldLabel}>Indent Size: {indent} spaces</label>
        <input type="range" min={1} max={8} value={indent}
          className={styles.slider} onChange={e => setIndent(+e.target.value)} />

        <div className={styles.btnRow}>
          <button type="button" className={styles.primaryBtn}
            onClick={handleFormat} disabled={!input.trim()}>
            Format SQL
          </button>
          <button type="button" className={styles.ghostBtn}
            onClick={() => setInput(SAMPLE_SQL)}>
            Load Sample
          </button>
          <button type="button" className={styles.ghostBtn}
            onClick={() => setInput('')}>
            Clear
          </button>
        </div>
      </div>

      {/* Editor + Output */}
      <div className={styles.editorCol}>
        <div className={styles.panel}>
          <div className={styles.panelHeader}>
            <div>
              <p className={styles.eyebrow}>Input</p>
              <h2 className={styles.panelTitle}>Raw SQL</h2>
            </div>
          </div>
          <textarea
            className={styles.sqlTextarea}
            value={input}
            onChange={e => setInput(e.target.value)}
            spellCheck={false}
            placeholder="Paste your SQL query here…"
            rows={10}
          />
        </div>

        <div className={styles.panel}>
          <div className={styles.panelHeader}>
            <div>
              <p className={styles.eyebrow}>Output</p>
              <h2 className={styles.panelTitle}>Formatted SQL</h2>
            </div>
            <div className={styles.headerActions}>
              <CopyButton text={output} />
              <button type="button" className={styles.secondaryBtn}
                onClick={downloadSQL} disabled={!output}>
                Download
              </button>
            </div>
          </div>
          <pre className={styles.sqlOutput}>{output || <span className={styles.placeholder}>Formatted output will appear here</span>}</pre>
        </div>
      </div>
    </div>
  )
}

// ─── Analyzer Tab ──────────────────────────────────────────────────────────────

function AnalyzerTab() {
  const [input, setInput] = useState(SAMPLE_SQL)

  const analysis = useMemo(() => {
    if (!input.trim()) return null
    return trackTool('sql.analyze', () => analyzeSQL(input))
  }, [input])

  function handleAnalyze() {
    if (!analysis) return
    logActivity('sql.analyze', 'Analyzed SQL query', {
      queryType: analysis.queryType,
      complexity: analysis.complexity,
      tableCount: analysis.tableCount,
      joinCount: analysis.joinCount,
    })
  }

  function ComplexityBar({ value }) {
    const color = value < 30 ? '#3fb950' : value < 60 ? '#d29922' : '#f85149'
    return (
      <div className={styles.complexityBar}>
        <div className={styles.complexityFill} style={{ width: `${value}%`, background: color }} />
      </div>
    )
  }

  return (
    <div className={styles.analyzerLayout}>
      <div className={styles.panel}>
        <p className={styles.eyebrow}>Input</p>
        <h2 className={styles.panelTitle}>SQL Query Analyzer</h2>
        <textarea
          className={styles.sqlTextarea}
          value={input}
          onChange={e => { setInput(e.target.value); handleAnalyze() }}
          spellCheck={false}
          placeholder="Paste a SQL query to analyze…"
          rows={8}
        />
        <div className={styles.btnRow}>
          <button type="button" className={styles.primaryBtn} onClick={handleAnalyze} disabled={!input.trim()}>
            Analyze
          </button>
          <button type="button" className={styles.ghostBtn} onClick={() => setInput(SAMPLE_SQL)}>
            Load Sample
          </button>
        </div>
      </div>

      {analysis && (
        <>
          <div className={styles.panel}>
            <p className={styles.eyebrow}>Complexity</p>
            <h2 className={styles.panelTitle}>{analysis.complexityLabel}</h2>
            <ComplexityBar value={analysis.complexity} />
            <p className={styles.complexityScore}>{analysis.complexity} / 100</p>

            <div className={styles.statsGrid}>
              <div className={styles.statCard}>
                <span className={styles.statLabel}>Query Type</span>
                <span className={styles.statValue}>{analysis.queryType}</span>
              </div>
              <div className={styles.statCard}>
                <span className={styles.statLabel}>Tables</span>
                <span className={styles.statValue}>{analysis.tableCount}</span>
              </div>
              <div className={styles.statCard}>
                <span className={styles.statLabel}>Joins</span>
                <span className={styles.statValue}>{analysis.joinCount}</span>
              </div>
              <div className={styles.statCard}>
                <span className={styles.statLabel}>Subquery</span>
                <span className={styles.statValue}>{analysis.clauses.hasSubquery ? 'Yes' : 'No'}</span>
              </div>
            </div>
          </div>

          <div className={styles.panel}>
            <p className={styles.eyebrow}>Breakdown</p>
            <h2 className={styles.panelTitle}>Query Structure</h2>
            <ul className={styles.breakdownList}>
              {analysis.breakdown.map((item, i) => (
                <li key={i} className={styles.breakdownItem}>{item}</li>
              ))}
            </ul>
          </div>

          <div className={`${styles.panel} ${analysis.dangerousMutation ? styles.panelDanger : ''}`}>
            <p className={styles.eyebrow}>{analysis.dangerousMutation ? '⚠ Warnings' : 'Index Hints'}</p>
            <h2 className={styles.panelTitle}>Performance Recommendations</h2>
            <ul className={styles.hintList}>
              {analysis.hints.map((hint, i) => (
                <li key={i} className={`${styles.hintItem} ${hint.startsWith('⚠') ? styles.hintDanger : ''}`}>
                  {hint}
                </li>
              ))}
            </ul>
          </div>
        </>
      )}
    </div>
  )
}

// ─── CRUD Generator Tab ────────────────────────────────────────────────────────

const SAMPLE_SCHEMA = `id INTEGER PRIMARY KEY
name TEXT NOT NULL
email TEXT NOT NULL
created_at TEXT
status TEXT DEFAULT 'active'`

function CRUDTab() {
  const [tableName,  setTableName]  = useState('users')
  const [schema,     setSchema]     = useState(SAMPLE_SCHEMA)
  const [dialect,    setDialect]    = useState('standard')

  const output = useMemo(() => {
    if (!tableName.trim() || !schema.trim()) return ''
    return trackTool('sql.crud', () => generateCRUD(tableName, schema, dialect))
  }, [tableName, schema, dialect])

  function handleGenerate() {
    const cols = parseSchemaCols(schema)
    logActivity('sql.crud', 'Generated CRUD queries', {
      columnCount: cols.length,
      dialect,
    })
  }

  function downloadCRUD() {
    const blob = new Blob([output], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = `crud-${tableName}-${Date.now()}.sql`; a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className={styles.twoColLayout}>
      <div className={styles.panel}>
        <p className={styles.eyebrow}>Configuration</p>
        <h2 className={styles.panelTitle}>CRUD Generator</h2>

        <label className={styles.fieldLabel}>Table Name</label>
        <input
          className={styles.textInput}
          value={tableName}
          onChange={e => setTableName(e.target.value)}
          placeholder="e.g. users"
        />

        <label className={styles.fieldLabel}>Dialect</label>
        <div className={styles.chipRow}>
          {['standard','mysql','postgresql','sqlserver'].map(d => (
            <button key={d} type="button"
              className={dialect === d ? styles.chipActive : styles.chip}
              onClick={() => setDialect(d)}>
              {d === 'sqlserver' ? 'SQL Server' : d.charAt(0).toUpperCase() + d.slice(1)}
            </button>
          ))}
        </div>

        <label className={styles.fieldLabel}>
          Column Schema <span className={styles.hint}>(one per line: col_name TYPE [flags])</span>
        </label>
        <textarea
          className={styles.sqlTextarea}
          value={schema}
          onChange={e => setSchema(e.target.value)}
          spellCheck={false}
          placeholder={`id INTEGER PRIMARY KEY\nname TEXT NOT NULL\nemail TEXT`}
          rows={8}
        />

        <div className={styles.btnRow}>
          <button type="button" className={styles.primaryBtn}
            onClick={handleGenerate} disabled={!tableName.trim() || !schema.trim()}>
            Generate CRUD
          </button>
          <button type="button" className={styles.ghostBtn}
            onClick={() => { setTableName('users'); setSchema(SAMPLE_SCHEMA) }}>
            Reset Sample
          </button>
        </div>
      </div>

      <div className={styles.panel}>
        <div className={styles.panelHeader}>
          <div>
            <p className={styles.eyebrow}>Output</p>
            <h2 className={styles.panelTitle}>Generated SQL</h2>
          </div>
          <div className={styles.headerActions}>
            <CopyButton text={output} />
            <button type="button" className={styles.secondaryBtn}
              onClick={downloadCRUD} disabled={!output}>
              Download
            </button>
          </div>
        </div>
        <pre className={styles.sqlOutput}>{output || <span className={styles.placeholder}>Configure table and columns above</span>}</pre>
      </div>
    </div>
  )
}

// ─── Main component ───────────────────────────────────────────────────────────

const TABS = [
  { id: 'formatter', label: 'Formatter', icon: '⊟' },
  { id: 'analyzer',  label: 'Analyzer',  icon: '◎' },
  { id: 'crud',      label: 'CRUD Gen',  icon: '⊞' },
]

export default function SQLFormatterPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const isGuest = username === 'Guest User'
  const [activeTab, setActiveTab] = useState('formatter')

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.brand}>
            <span className={styles.brandMark} aria-hidden="true">SQL</span>
            <span className={styles.brandName}>UsefulTools</span>
          </div>
          <button className={styles.backBtn} onClick={() => navigate('/dashboard')}>Dashboard</button>
        </div>
        <UserMenu username={username} isGuest={isGuest} variant="light" onLogout={handleLogout} />
      </header>

      <section className={styles.hero}>
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>Sprint 23</div>
          <h1 className={styles.heroTitle}>SQL Formatter</h1>
          <p className={styles.heroSub}>
            Format SQL with customizable indentation and keyword case. Analyze query
            complexity, get index recommendations, and generate full CRUD suites from a table schema.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div><strong>5</strong><span>dialects</span></div>
          <div><strong>3</strong><span>keyword cases</span></div>
          <div><strong>CRUD</strong><span>generator</span></div>
        </div>
      </section>

      <main className={styles.main}>
        <TabBar tabs={TABS} active={activeTab} onChange={setActiveTab} />
        {activeTab === 'formatter' && <FormatterTab />}
        {activeTab === 'analyzer'  && <AnalyzerTab />}
        {activeTab === 'crud'      && <CRUDTab />}
      </main>
    </div>
  )
}
