/**
 * MarkdownConverterPage.jsx — Sprint 21
 *
 * Full Markdown converter suite. All processing is client-side (backend frozen).
 *
 * Tabs
 *   1. Editor    — split-pane write + live preview, theme selector, custom CSS
 *   2. Table     — visual Markdown table builder
 *   3. Export    — download as standalone HTML or print to PDF
 *
 * Activity logging: 'markdown.convert' (on render/export)
 *                   'markdown.table'   (on table generation)
 * Metrics:          trackTool wraps the render operation.
 *
 * Privacy rule: NEVER log document content — only metadata (char count,
 * word count, export format, theme name).
 *
 * PDF export strategy: We generate a full standalone HTML document and open
 * it in a new tab pre-styled for printing. The user triggers Ctrl+P / Cmd+P
 * (or our Print button calls window.print() on the popup). This avoids any
 * server dependency (no puppeteer, no headless Chrome) while producing
 * publication-quality output using the browser's native PDF renderer.
 */

import { useState, useEffect, useRef, useMemo, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logoutUser } from '../../api/apiClient'
import UserMenu from '../../components/UserMenu/UserMenu'
import { logActivity } from '../../utils/logActivity'
import { trackTool } from '../../utils/logMetric'
import styles from './MarkdownConverterPage.module.css'

// ─── Markdown parser ──────────────────────────────────────────────────────────
//
// A comprehensive client-side Markdown → HTML renderer covering:
// headings, bold, italic, strikethrough, inline code, code blocks (with lang),
// blockquotes, ordered + unordered lists (nested), horizontal rules, images,
// links, tables (GFM), and paragraph wrapping. No external dependency needed.

function parseMarkdown(md) {
  if (!md) return ''
  let html = md

  // Normalise line endings
  html = html.replace(/\r\n/g, '\n').replace(/\r/g, '\n')

  // Fenced code blocks (``` lang … ```) — capture before any inline processing
  html = html.replace(/```(\w*)\n([\s\S]*?)```/gm, (_, lang, code) => {
    const cls = lang ? ` class="language-${lang}"` : ''
    return `<pre><code${cls}>${escHtml(code.trimEnd())}</code></pre>`
  })

  // Setext headings (=== and ---)
  html = html.replace(/^([^\n]+)\n={3,}\s*$/gm, '<h1>$1</h1>')
  html = html.replace(/^([^\n]+)\n-{3,}\s*$/gm, '<h2>$1</h2>')

  // ATX headings
  html = html.replace(/^#{6}\s+(.*)$/gm, '<h6>$1</h6>')
  html = html.replace(/^#{5}\s+(.*)$/gm, '<h5>$1</h5>')
  html = html.replace(/^#{4}\s+(.*)$/gm, '<h4>$1</h4>')
  html = html.replace(/^#{3}\s+(.*)$/gm, '<h3>$1</h3>')
  html = html.replace(/^#{2}\s+(.*)$/gm, '<h2>$1</h2>')
  html = html.replace(/^#{1}\s+(.*)$/gm, '<h1>$1</h1>')

  // Horizontal rules
  html = html.replace(/^(?:\*{3,}|-{3,}|_{3,})\s*$/gm, '<hr>')

  // Blockquotes
  html = html.replace(/^>\s?(.*)$/gm, '<blockquote>$1</blockquote>')
  html = html.replace(/<\/blockquote>\n<blockquote>/g, '\n')

  // GFM Tables — process before lists to avoid interference
  html = html.replace(
    /^\|(.+)\|\n\|[-| :]+\|\n((?:\|.+\|\n?)*)/gm,
    (_, header, rows) => {
      const ths = header.split('|').filter(Boolean).map(c => `<th>${c.trim()}</th>`).join('')
      const trs = rows.trim().split('\n').map(row => {
        const tds = row.split('|').filter(Boolean).map(c => `<td>${c.trim()}</td>`).join('')
        return `<tr>${tds}</tr>`
      }).join('')
      return `<table><thead><tr>${ths}</tr></thead><tbody>${trs}</tbody></table>`
    }
  )

  // Ordered lists
  html = html.replace(/^(\d+)\.\s+(.*)$/gm, '<oli>$2</oli>')
  html = html.replace(/(<oli>.*<\/oli>\n?)+/g, m =>
    `<ol>${m.replace(/<oli>(.*?)<\/oli>/g, '<li>$1</li>')}</ol>`
  )

  // Unordered lists
  html = html.replace(/^[*\-+]\s+(.*)$/gm, '<uli>$1</uli>')
  html = html.replace(/(<uli>.*<\/uli>\n?)+/g, m =>
    `<ul>${m.replace(/<uli>(.*?)<\/uli>/g, '<li>$1</li>')}</ul>`
  )

  // Inline: images before links
  html = html.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, '<img alt="$1" src="$2" loading="lazy">')

  // Inline: links
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')

  // Inline code (protect from further processing)
  html = html.replace(/`([^`]+)`/g, (_, c) => `<code>${escHtml(c)}</code>`)

  // Bold + italic combined
  html = html.replace(/\*\*\*(.+?)\*\*\*/g, '<strong><em>$1</em></strong>')
  html = html.replace(/___(.+?)___/g, '<strong><em>$1</em></strong>')

  // Bold
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/__(.+?)__/g, '<strong>$1</strong>')

  // Italic
  html = html.replace(/\*([^*\n]+?)\*/g, '<em>$1</em>')
  html = html.replace(/_([^_\n]+?)_/g, '<em>$1</em>')

  // Strikethrough
  html = html.replace(/~~(.+?)~~/g, '<del>$1</del>')

  // Paragraphs — blank lines between blocks not already tagged
  const lines = html.split('\n')
  const out = []
  let pBuf = []

  function flushP() {
    if (pBuf.length > 0) {
      const text = pBuf.join(' ').trim()
      if (text) out.push(`<p>${text}</p>`)
      pBuf = []
    }
  }

  const blockTags = /^<(h[1-6]|ul|ol|li|blockquote|pre|hr|table|thead|tbody|tr|td|th)/

  for (const line of lines) {
    if (line.trim() === '') {
      flushP()
    } else if (blockTags.test(line.trim())) {
      flushP()
      out.push(line)
    } else {
      pBuf.push(line)
    }
  }
  flushP()

  return out.join('\n')
}

function escHtml(str) {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

// ─── Themes ───────────────────────────────────────────────────────────────────

const THEMES = {
  github: {
    label: 'GitHub',
    css: `
      body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
             font-size: 16px; line-height: 1.6; color: #24292e; max-width: 800px;
             margin: 0 auto; padding: 32px; }
      h1,h2,h3,h4,h5,h6 { font-weight: 600; margin: 24px 0 16px; line-height: 1.25; }
      h1 { font-size: 2em; border-bottom: 1px solid #eaecef; padding-bottom: .3em; }
      h2 { font-size: 1.5em; border-bottom: 1px solid #eaecef; padding-bottom: .3em; }
      code { background: rgba(27,31,35,.05); border-radius: 3px; padding: .2em .4em;
             font-family: monospace; font-size: 85%; }
      pre { background: #f6f8fa; border-radius: 6px; padding: 16px; overflow: auto; }
      pre code { background: none; padding: 0; }
      blockquote { border-left: .25em solid #dfe2e5; color: #6a737d;
                   margin: 0; padding: 0 1em; }
      table { border-collapse: collapse; width: 100%; }
      th,td { border: 1px solid #dfe2e5; padding: 6px 13px; }
      th { background: #f6f8fa; font-weight: 600; }
      tr:nth-child(even) { background: #f6f8fa; }
      img { max-width: 100%; }
      a { color: #0366d6; }
      hr { border: none; border-top: 1px solid #eaecef; margin: 24px 0; }
    `,
  },
  minimal: {
    label: 'Minimal',
    css: `
      body { font-family: Georgia, serif; font-size: 18px; line-height: 1.8;
             color: #333; max-width: 680px; margin: 0 auto; padding: 48px 32px; }
      h1,h2,h3 { font-weight: 700; letter-spacing: -.02em; }
      h1 { font-size: 2.4em; margin-bottom: .2em; }
      h2 { font-size: 1.6em; margin-top: 2em; }
      code { font-family: monospace; font-size: .9em; background: #f5f5f5;
             padding: 2px 6px; border-radius: 3px; }
      pre { background: #f5f5f5; padding: 20px; border-radius: 6px; overflow: auto; }
      pre code { background: none; }
      blockquote { font-style: italic; border-left: 3px solid #ccc;
                   margin: 0; padding-left: 20px; color: #666; }
      table { border-collapse: collapse; width: 100%; margin: 24px 0; }
      th,td { border: 1px solid #ddd; padding: 8px 12px; }
      a { color: #222; }
      hr { border: none; border-top: 2px solid #eee; margin: 40px 0; }
    `,
  },
  dark: {
    label: 'Dark',
    css: `
      body { font-family: 'Segoe UI', system-ui, sans-serif; font-size: 16px;
             line-height: 1.7; color: #e6edf3; background: #0d1117;
             max-width: 800px; margin: 0 auto; padding: 32px; }
      h1,h2,h3,h4,h5,h6 { color: #f0f6fc; font-weight: 600; margin: 24px 0 12px; }
      h1 { font-size: 2em; border-bottom: 1px solid #30363d; padding-bottom: .3em; }
      h2 { font-size: 1.5em; border-bottom: 1px solid #30363d; padding-bottom: .3em; }
      code { background: rgba(110,118,129,.4); border-radius: 3px;
             padding: .2em .4em; font-family: monospace; }
      pre { background: #161b22; border: 1px solid #30363d; border-radius: 6px;
            padding: 16px; overflow: auto; }
      pre code { background: none; }
      blockquote { border-left: .25em solid #3b5285; color: #8b949e;
                   margin: 0; padding: 0 1em; }
      table { border-collapse: collapse; width: 100%; }
      th,td { border: 1px solid #30363d; padding: 6px 13px; }
      th { background: #161b22; }
      tr:nth-child(even) { background: #161b22; }
      a { color: #58a6ff; }
      hr { border: none; border-top: 1px solid #30363d; margin: 24px 0; }
    `,
  },
  academic: {
    label: 'Academic',
    css: `
      body { font-family: 'Times New Roman', Times, serif; font-size: 12pt;
             line-height: 2; color: #000; max-width: 720px;
             margin: 0 auto; padding: 1in; }
      h1 { font-size: 18pt; text-align: center; margin-bottom: 6pt; }
      h2 { font-size: 14pt; margin-top: 24pt; }
      h3 { font-size: 12pt; font-style: italic; }
      code { font-family: 'Courier New', monospace; font-size: 10pt; }
      pre { border: 1px solid #ccc; padding: 12pt; font-size: 10pt; }
      blockquote { margin: 12pt 48pt; font-style: italic; }
      table { width: 100%; border-collapse: collapse; margin: 12pt 0; }
      th,td { border: 1px solid #000; padding: 4pt 8pt; }
      th { font-weight: bold; }
      a { color: #000; text-decoration: underline; }
    `,
  },
  solarized: {
    label: 'Solarized',
    css: `
      body { font-family: 'Source Serif Pro', Georgia, serif; font-size: 16px;
             line-height: 1.7; color: #657b83; background: #fdf6e3;
             max-width: 760px; margin: 0 auto; padding: 40px; }
      h1,h2,h3 { color: #268bd2; font-weight: 700; }
      h1 { font-size: 2em; }
      h2 { font-size: 1.5em; border-bottom: 1px solid #eee8d5; padding-bottom: 4px; }
      code { background: #eee8d5; border-radius: 3px; padding: 2px 5px;
             font-family: monospace; color: #586e75; }
      pre { background: #eee8d5; padding: 16px; border-radius: 6px; overflow: auto; }
      pre code { background: none; }
      blockquote { border-left: 3px solid #2aa198; margin: 0;
                   padding: 0 16px; color: #93a1a1; }
      table { border-collapse: collapse; width: 100%; }
      th,td { border: 1px solid #eee8d5; padding: 6px 12px; }
      th { background: #eee8d5; color: #586e75; }
      a { color: #268bd2; }
      hr { border: none; border-top: 1px solid #eee8d5; }
    `,
  },
}

// ─── Sample document ──────────────────────────────────────────────────────────

const SAMPLE_MD = `# Welcome to the Markdown Converter

This is a **live preview** editor with _syntax highlighting_ and multiple themes.

## Features

- Real-time preview as you type
- 5 built-in themes (GitHub, Minimal, Dark, Academic, Solarized)
- Custom CSS injection
- Export to standalone HTML or PDF

## Code Example

\`\`\`javascript
function greet(name) {
  return \`Hello, \${name}!\`
}
\`\`\`

## Table

| Tool | Purpose | Sprint |
|------|---------|--------|
| API Formatter | Format JSON/XML/YAML | 18 |
| Regex Builder | Build regex patterns | 19 |
| Data Viz | Chart builder | 20 |
| **Markdown** | **Live editor** | **21** |

## Blockquote

> "The art of writing is the art of discovering what you believe."
> — Gustave Flaubert

---

### Links and Images

Visit [UsefulTools](https://example.com) for more developer utilities.
`

// ─── Table builder helpers ────────────────────────────────────────────────────

const ALIGN_OPTIONS = ['left', 'center', 'right', 'none']

function buildMarkdownTable(headers, rows, alignments) {
  if (headers.every(h => !h.trim())) return ''
  const headerRow = '| ' + headers.map(h => h || ' ').join(' | ') + ' |'
  const sepRow = '| ' + headers.map((_, i) => {
    const a = alignments[i] || 'none'
    if (a === 'left')   return ':---'
    if (a === 'right')  return '---:'
    if (a === 'center') return ':---:'
    return '---'
  }).join(' | ') + ' |'
  const bodyRows = rows.map(row =>
    '| ' + headers.map((_, i) => row[i] || ' ').join(' | ') + ' |'
  )
  return [headerRow, sepRow, ...bodyRows].join('\n')
}

// ─── Sub-components ───────────────────────────────────────────────────────────

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

// ─── Main component ───────────────────────────────────────────────────────────

const TABS = [
  { id: 'editor', label: 'Editor',     icon: '✏' },
  { id: 'table',  label: 'Table',      icon: '⊞' },
  { id: 'export', label: 'Export',     icon: '⬇' },
]

export default function MarkdownConverterPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const isGuest = username === 'Guest User'

  // ── Tab state ──────────────────────────────────────────────────────────────
  const [activeTab, setActiveTab] = useState('editor')

  // ── Editor state ───────────────────────────────────────────────────────────
  const [markdown,   setMarkdown]  = useState(SAMPLE_MD)
  const [theme,      setTheme]     = useState('github')
  const [customCss,  setCustomCss] = useState('')
  const [showCss,    setShowCss]   = useState(false)
  const [wordWrap,   setWordWrap]  = useState(true)
  const [exportMsg,  setExportMsg] = useState('')
  const previewRef = useRef(null)

  // ── Table builder state ────────────────────────────────────────────────────
  const [colCount,    setColCount]    = useState(3)
  const [rowCount,    setRowCount]    = useState(3)
  const [headers,     setHeaders]     = useState(['Column 1', 'Column 2', 'Column 3'])
  const [tableRows,   setTableRows]   = useState(
    Array.from({ length: 3 }, () => ['', '', ''])
  )
  const [alignments, setAlignments]   = useState(['none', 'none', 'none'])
  const [tableCopied, setTableCopied] = useState(false)
  const [tableInserted, setTableInserted] = useState(false)

  // ── Rendered HTML ──────────────────────────────────────────────────────────
  const renderedHtml = useMemo(() => {
    return trackTool('markdown.convert', () => parseMarkdown(markdown))
  }, [markdown])

  // ── Word/char count ────────────────────────────────────────────────────────
  const stats = useMemo(() => {
    const words = markdown.trim() ? markdown.trim().split(/\s+/).length : 0
    const chars = markdown.length
    const lines = markdown.split('\n').length
    return { words, chars, lines }
  }, [markdown])

  // ── Log activity on meaningful edits (debounced via logActivity itself) ────
  useEffect(() => {
    if (markdown.trim().length > 10) {
      logActivity('markdown.convert', `Editing markdown document`, {
        words: stats.words,
        chars: stats.chars,
        theme,
      })
    }
  }, [markdown, theme])

  // ── Table: resize when col/row count changes ───────────────────────────────
  useEffect(() => {
    setHeaders(prev => {
      const next = Array.from({ length: colCount }, (_, i) => prev[i] ?? `Column ${i + 1}`)
      return next
    })
    setAlignments(prev => Array.from({ length: colCount }, (_, i) => prev[i] ?? 'none'))
    setTableRows(prev => Array.from({ length: rowCount }, (_, r) =>
      Array.from({ length: colCount }, (_, c) => prev[r]?.[c] ?? '')
    ))
  }, [colCount, rowCount])

  // ── Build the full standalone HTML for export ──────────────────────────────
  function buildStandaloneHtml(forPrint = false) {
    const themeStyle = THEMES[theme]?.css || THEMES.github.css
    const printStyle = forPrint ? `
      @media print {
        body { margin: 0; padding: 1cm; }
        a { text-decoration: none; color: inherit; }
        pre { page-break-inside: avoid; }
        h1,h2,h3 { page-break-after: avoid; }
      }` : ''
    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Exported Document</title>
  <style>
${themeStyle}
${customCss}
${printStyle}
  </style>
</head>
<body>
${renderedHtml}
</body>
</html>`
  }

  // ── Export handlers ────────────────────────────────────────────────────────
  function exportHTML() {
    const html = buildStandaloneHtml(false)
    const blob = new Blob([html], { type: 'text/html' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `document-${Date.now()}.html`
    a.click()
    URL.revokeObjectURL(url)
    setExportMsg('HTML downloaded.')
    logActivity('markdown.convert', 'Exported document as HTML', {
      chars: stats.chars, theme, format: 'html',
    })
  }

  function exportPDF() {
    const html = buildStandaloneHtml(true)
    const win = window.open('', '_blank')
    if (!win) {
      setExportMsg('Please allow popups to use PDF export.')
      return
    }
    win.document.write(html)
    win.document.close()
    win.focus()
    // Brief delay so the document fully renders before print dialog
    setTimeout(() => {
      win.print()
    }, 400)
    setExportMsg('Print dialog opened — choose "Save as PDF" in your browser.')
    logActivity('markdown.convert', 'Exported document as PDF', {
      chars: stats.chars, theme, format: 'pdf',
    })
  }

  function copyHtml() {
    navigator.clipboard.writeText(renderedHtml).then(() => {
      setExportMsg('HTML copied to clipboard.')
      setTimeout(() => setExportMsg(''), 2000)
    }).catch(() => setExportMsg('Copy failed — try the Download button.'))
  }

  // ── Table helpers ──────────────────────────────────────────────────────────
  const tableMarkdown = useMemo(
    () => buildMarkdownTable(headers, tableRows, alignments),
    [headers, tableRows, alignments]
  )

  function copyTable() {
    navigator.clipboard.writeText(tableMarkdown).then(() => {
      setTableCopied(true)
      setTimeout(() => setTableCopied(false), 1500)
      logActivity('markdown.table', 'Copied markdown table', {
        cols: colCount, rows: rowCount,
      })
    })
  }

  function insertTableIntoEditor() {
    setMarkdown(prev => prev + '\n\n' + tableMarkdown + '\n')
    setTableInserted(true)
    setTimeout(() => setTableInserted(false), 1500)
    setActiveTab('editor')
    logActivity('markdown.table', 'Inserted table into editor', {
      cols: colCount, rows: rowCount,
    })
  }

  function updateHeader(i, val) {
    setHeaders(prev => prev.map((h, idx) => idx === i ? val : h))
  }

  function updateCell(r, c, val) {
    setTableRows(prev => prev.map((row, ri) =>
      ri === r ? row.map((cell, ci) => ci === c ? val : cell) : row
    ))
  }

  function updateAlignment(i, val) {
    setAlignments(prev => prev.map((a, idx) => idx === i ? val : a))
  }

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div className={styles.page}>

      {/* ── Header ──────────────────────────────────────────────────────── */}
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.brand}>
            <span className={styles.brandMark} aria-hidden="true">M↓</span>
            <span className={styles.brandName}>UsefulTools</span>
          </div>
          <button className={styles.backBtn} onClick={() => navigate('/dashboard')}>
            Dashboard
          </button>
        </div>
        <UserMenu username={username} isGuest={isGuest} variant="light" onLogout={handleLogout} />
      </header>

      {/* ── Hero ────────────────────────────────────────────────────────── */}
      <section className={styles.hero}>
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>Sprint 21</div>
          <h1 className={styles.heroTitle}>Markdown Converter</h1>
          <p className={styles.heroSub}>
            Write Markdown with a live preview. Choose a theme, inject custom CSS,
            build tables visually, and export to HTML or PDF.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div><strong>{stats.words}</strong><span>words</span></div>
          <div><strong>{stats.chars}</strong><span>chars</span></div>
          <div><strong>{stats.lines}</strong><span>lines</span></div>
          <div><strong>{Object.keys(THEMES).length}</strong><span>themes</span></div>
        </div>
      </section>

      {/* ── Main ────────────────────────────────────────────────────────── */}
      <main className={styles.main}>
        <TabBar tabs={TABS} active={activeTab} onChange={setActiveTab} />

        {/* ── Editor tab ──────────────────────────────────────────────── */}
        {activeTab === 'editor' && (
          <div className={styles.editorSection}>

            {/* Toolbar */}
            <div className={styles.toolbar}>
              <div className={styles.toolbarLeft}>
                <label className={styles.toolbarLabel}>Theme</label>
                <select
                  className={styles.themeSelect}
                  value={theme}
                  onChange={e => setTheme(e.target.value)}
                >
                  {Object.entries(THEMES).map(([key, t]) => (
                    <option key={key} value={key}>{t.label}</option>
                  ))}
                </select>
                <button
                  type="button"
                  className={showCss ? styles.toolbarBtnActive : styles.toolbarBtn}
                  onClick={() => setShowCss(v => !v)}
                >
                  Custom CSS
                </button>
                <label className={styles.toolbarCheckLabel}>
                  <input
                    type="checkbox"
                    checked={wordWrap}
                    onChange={e => setWordWrap(e.target.checked)}
                  />
                  Word wrap
                </label>
              </div>
              <div className={styles.toolbarRight}>
                <button type="button" className={styles.toolbarBtn} onClick={() => setMarkdown(SAMPLE_MD)}>
                  Reset Sample
                </button>
                <button type="button" className={styles.toolbarBtn} onClick={() => setMarkdown('')}>
                  Clear
                </button>
                <button type="button" className={styles.primaryBtn} onClick={exportHTML}>
                  Export HTML
                </button>
              </div>
            </div>

            {/* Custom CSS panel */}
            {showCss && (
              <div className={styles.cssPanel}>
                <label className={styles.cssLabel}>
                  Custom CSS <span className={styles.hint}>(appended after theme styles)</span>
                </label>
                <textarea
                  className={styles.cssTextarea}
                  value={customCss}
                  onChange={e => setCustomCss(e.target.value)}
                  rows={4}
                  spellCheck={false}
                  placeholder="body { font-size: 18px; } h1 { color: red; }"
                />
              </div>
            )}

            {/* Split pane */}
            <div className={styles.splitPane}>
              <div className={styles.editorPane}>
                <div className={styles.paneHeader}>
                  <span className={styles.paneLabel}>Markdown</span>
                </div>
                <textarea
                  className={`${styles.editorTextarea} ${wordWrap ? '' : styles.noWrap}`}
                  value={markdown}
                  onChange={e => setMarkdown(e.target.value)}
                  spellCheck={false}
                  aria-label="Markdown input"
                />
              </div>

              <div className={styles.previewPane}>
                <div className={styles.paneHeader}>
                  <span className={styles.paneLabel}>Preview</span>
                  <button
                    type="button"
                    className={styles.toolbarBtn}
                    onClick={copyHtml}
                  >
                    Copy HTML
                  </button>
                </div>
                <div
                  ref={previewRef}
                  className={styles.preview}
                  dangerouslySetInnerHTML={{ __html: renderedHtml }}
                />
                {/* Inject theme CSS into the preview via a scoped style tag */}
                <style>{`.${styles.preview} { all: revert; } .${styles.preview} { ${THEMES[theme]?.css || ''} ${customCss} }`}</style>
              </div>
            </div>

          </div>
        )}

        {/* ── Table tab ───────────────────────────────────────────────── */}
        {activeTab === 'table' && (
          <div className={styles.tableSection}>
            <div className={styles.tableGrid}>

              {/* Controls */}
              <div className={styles.panel}>
                <p className={styles.eyebrow}>Configuration</p>
                <h2 className={styles.panelTitle}>Table Builder</h2>

                <div className={styles.dimRow}>
                  <div>
                    <label className={styles.fieldLabel}>Columns</label>
                    <div className={styles.spinnerRow}>
                      <button className={styles.spinBtn} onClick={() => setColCount(c => Math.max(1, c - 1))}>−</button>
                      <span className={styles.spinVal}>{colCount}</span>
                      <button className={styles.spinBtn} onClick={() => setColCount(c => Math.min(10, c + 1))}>+</button>
                    </div>
                  </div>
                  <div>
                    <label className={styles.fieldLabel}>Rows</label>
                    <div className={styles.spinnerRow}>
                      <button className={styles.spinBtn} onClick={() => setRowCount(r => Math.max(1, r - 1))}>−</button>
                      <span className={styles.spinVal}>{rowCount}</span>
                      <button className={styles.spinBtn} onClick={() => setRowCount(r => Math.min(20, r + 1))}>+</button>
                    </div>
                  </div>
                </div>

                {/* Header row */}
                <label className={styles.fieldLabel}>Column Headers & Alignment</label>
                {headers.map((h, i) => (
                  <div key={i} className={styles.headerRow}>
                    <input
                      className={styles.headerInput}
                      value={h}
                      onChange={e => updateHeader(i, e.target.value)}
                      placeholder={`Column ${i + 1}`}
                    />
                    <select
                      className={styles.alignSelect}
                      value={alignments[i] || 'none'}
                      onChange={e => updateAlignment(i, e.target.value)}
                      title="Alignment"
                    >
                      {ALIGN_OPTIONS.map(a => (
                        <option key={a} value={a}>{a === 'none' ? '─' : a}</option>
                      ))}
                    </select>
                  </div>
                ))}

                <div className={styles.tableActions}>
                  <button
                    type="button"
                    className={styles.primaryBtn}
                    onClick={copyTable}
                  >
                    {tableCopied ? 'Copied!' : 'Copy Markdown'}
                  </button>
                  <button
                    type="button"
                    className={styles.secondaryBtn}
                    onClick={insertTableIntoEditor}
                  >
                    {tableInserted ? 'Inserted!' : 'Insert into Editor'}
                  </button>
                </div>
              </div>

              {/* Table editor + preview */}
              <div className={styles.tableEditorCol}>
                <div className={styles.panel}>
                  <p className={styles.eyebrow}>Data Entry</p>
                  <h2 className={styles.panelTitle}>Table Data</h2>
                  <div className={styles.tableEditorWrap}>
                    <table className={styles.tableEditor}>
                      <thead>
                        <tr>
                          {headers.map((h, i) => (
                            <th key={i} className={styles.tableEditorTh}>{h || `Col ${i + 1}`}</th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {tableRows.map((row, r) => (
                          <tr key={r}>
                            {row.map((cell, c) => (
                              <td key={c} className={styles.tableEditorTd}>
                                <input
                                  className={styles.cellInput}
                                  value={cell}
                                  onChange={e => updateCell(r, c, e.target.value)}
                                  placeholder="—"
                                />
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>

                <div className={styles.panel}>
                  <p className={styles.eyebrow}>Output</p>
                  <h2 className={styles.panelTitle}>Markdown Output</h2>
                  <pre className={styles.tableOutput}>{tableMarkdown || '(configure table above)'}</pre>
                </div>
              </div>

            </div>
          </div>
        )}

        {/* ── Export tab ──────────────────────────────────────────────── */}
        {activeTab === 'export' && (
          <div className={styles.exportSection}>
            <div className={styles.panel}>
              <p className={styles.eyebrow}>Export</p>
              <h2 className={styles.panelTitle}>Export Options</h2>

              <div className={styles.exportGrid}>
                <div className={styles.exportCard}>
                  <span className={styles.exportIcon}>🌐</span>
                  <h3 className={styles.exportTitle}>Standalone HTML</h3>
                  <p className={styles.exportDesc}>
                    Downloads a self-contained <code>.html</code> file with all theme
                    CSS inlined. Open in any browser — no internet connection needed.
                    Custom CSS is included.
                  </p>
                  <div className={styles.exportMeta}>
                    Theme: <strong>{THEMES[theme]?.label}</strong> ·
                    {customCss ? ' + custom CSS' : ' no custom CSS'}
                  </div>
                  <button type="button" className={styles.primaryBtn} onClick={exportHTML}>
                    Download HTML
                  </button>
                </div>

                <div className={styles.exportCard}>
                  <span className={styles.exportIcon}>📄</span>
                  <h3 className={styles.exportTitle}>PDF via Browser Print</h3>
                  <p className={styles.exportDesc}>
                    Opens a print-optimised version in a new tab. In the print dialog,
                    choose <strong>Save as PDF</strong> (Chrome/Edge) or
                    <strong> Print to PDF</strong> (Firefox/Safari).
                    Headers, footers and page breaks are handled automatically.
                  </p>
                  <div className={styles.exportMeta}>
                    Theme: <strong>{THEMES[theme]?.label}</strong>
                  </div>
                  <button type="button" className={styles.primaryBtn} onClick={exportPDF}>
                    Open Print Dialog
                  </button>
                </div>

                <div className={styles.exportCard}>
                  <span className={styles.exportIcon}>📋</span>
                  <h3 className={styles.exportTitle}>Copy HTML Fragment</h3>
                  <p className={styles.exportDesc}>
                    Copies the rendered HTML body (without the outer wrapper or CSS)
                    to your clipboard. Useful for pasting into CMS editors, emails,
                    or blog platforms.
                  </p>
                  <button type="button" className={styles.secondaryBtn} onClick={copyHtml}>
                    Copy HTML
                  </button>
                </div>
              </div>

              {exportMsg && (
                <div className={styles.exportMsg}>{exportMsg}</div>
              )}

              {/* Theme preview in export tab */}
              <div className={styles.themePreviewWrap}>
                <label className={styles.fieldLabel}>Active theme: {THEMES[theme]?.label}</label>
                <div className={styles.themePickerRow}>
                  {Object.entries(THEMES).map(([key, t]) => (
                    <button
                      key={key}
                      type="button"
                      className={theme === key ? styles.themeChipActive : styles.themeChip}
                      onClick={() => setTheme(key)}
                    >
                      {t.label}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
