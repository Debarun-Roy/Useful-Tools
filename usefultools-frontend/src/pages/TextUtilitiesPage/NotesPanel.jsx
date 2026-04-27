import { useMemo, useState } from 'react'
import { useNotes } from './NotesContext'
import styles from './TextUtilitiesPage.module.css'

/*
 * NotesPanel — collapsible drawer that lists every saved Text Utilities note,
 * filterable by sub-tool. Lives between the hero and tab bar on the page.
 */

const TOOL_LABELS = {
  counter: 'Counter',
  case:    'Case',
  diff:    'Diff',
  regex:   'Regex',
  slug:    'Slug',
  lorem:   'Lorem',
  unknown: 'Other',
}

function fmtDate(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleString('en-GB', {
    day: 'numeric', month: 'short',
    hour: '2-digit', minute: '2-digit',
  })
}

function copyToClipboard(text) {
  try { navigator.clipboard.writeText(text) } catch { /* ignore */ }
}

export default function NotesPanel({ defaultOpen = false }) {
  const { notes, deleteNote, clearAll } = useNotes()
  const [open,    setOpen]    = useState(defaultOpen)
  const [filter,  setFilter]  = useState('all')
  const [confirm, setConfirm] = useState(false)
  const [expanded, setExpanded] = useState({})

  const filtered = useMemo(() => {
    if (filter === 'all') return notes
    return notes.filter(n => (n.tool || 'unknown') === filter)
  }, [notes, filter])

  const counts = useMemo(() => {
    const out = { all: notes.length }
    for (const n of notes) {
      const k = n.tool || 'unknown'
      out[k] = (out[k] || 0) + 1
    }
    return out
  }, [notes])

  return (
    <section className={styles.notesPanel} aria-label="Saved notes">
      <button
        type="button"
        className={styles.notesPanelToggle}
        onClick={() => setOpen(o => !o)}
        aria-expanded={open}
      >
        <span className={styles.notesPanelToggleIcon} aria-hidden="true">
          {open ? '▾' : '▸'}
        </span>
        <span className={styles.notesPanelToggleLabel}>
          Saved notes
        </span>
        <span className={styles.notesPanelCount}>{notes.length}</span>
      </button>

      {open && (
        <div className={styles.notesPanelBody}>

          <div className={styles.notesPanelControls}>
            <div className={styles.notesFilterRow}>
              {[
                { id: 'all',     label: 'All'      },
                { id: 'counter', label: TOOL_LABELS.counter },
                { id: 'case',    label: TOOL_LABELS.case    },
                { id: 'diff',    label: TOOL_LABELS.diff    },
                { id: 'regex',   label: TOOL_LABELS.regex   },
                { id: 'slug',    label: TOOL_LABELS.slug    },
                { id: 'lorem',   label: TOOL_LABELS.lorem   },
              ].map(opt => (
                <button
                  key={opt.id}
                  type="button"
                  className={filter === opt.id ? styles.notesPillActive : styles.notesPill}
                  onClick={() => setFilter(opt.id)}
                  disabled={(counts[opt.id] || (opt.id === 'all' ? counts.all : 0)) === 0}
                >
                  {opt.label}
                  <span className={styles.notesPillCount}>
                    {opt.id === 'all' ? (counts.all || 0) : (counts[opt.id] || 0)}
                  </span>
                </button>
              ))}
            </div>

            {notes.length > 0 && (
              <button
                type="button"
                className={confirm ? styles.notesClearArmed : styles.notesClearBtn}
                onClick={() => {
                  if (confirm) { clearAll(); setConfirm(false) }
                  else { setConfirm(true); setTimeout(() => setConfirm(false), 4000) }
                }}
              >
                {confirm ? 'Click again to confirm' : 'Clear all'}
              </button>
            )}
          </div>

          {filtered.length === 0 && (
            <p className={styles.notesEmpty}>
              {notes.length === 0
                ? 'Nothing saved yet — use the “💾 Save to notes” button on any tool.'
                : 'No notes match the current filter.'}
            </p>
          )}

          {filtered.length > 0 && (
            <ul className={styles.notesList}>
              {filtered.map(n => {
                const open = !!expanded[n.id]
                const preview = n.content.length > 160
                  ? n.content.slice(0, 160) + '…'
                  : n.content
                return (
                  <li key={n.id} className={styles.noteItem}>
                    <div className={styles.noteHead}>
                      <span className={styles.noteToolBadge}>
                        {TOOL_LABELS[n.tool] || TOOL_LABELS.unknown}
                      </span>
                      <span className={styles.noteTitle}>{n.title}</span>
                      <span className={styles.noteDate}>{fmtDate(n.createdAt)}</span>
                    </div>

                    <pre className={styles.noteBody}>
                      {open ? n.content : preview}
                    </pre>

                    <div className={styles.noteActions}>
                      {n.content.length > 160 && (
                        <button
                          type="button"
                          className={styles.noteActionBtn}
                          onClick={() => setExpanded(s => ({ ...s, [n.id]: !s[n.id] }))}
                        >
                          {open ? 'Collapse' : 'Expand'}
                        </button>
                      )}
                      <button
                        type="button"
                        className={styles.noteActionBtn}
                        onClick={() => copyToClipboard(n.content)}
                      >
                        Copy
                      </button>
                      <button
                        type="button"
                        className={styles.noteDeleteBtn}
                        onClick={() => deleteNote(n.id)}
                        aria-label="Delete this note"
                      >
                        Delete
                      </button>
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      )}
    </section>
  )
}
