import { useCallback, useEffect, useState } from 'react'

/*
 * useTextUtilNotes — small localStorage-backed notes store for Text Utilities.
 *
 * Each note is { id, tool, title, content, createdAt }, where `tool` is the
 * sub-tool id ('counter' | 'case' | 'diff' | 'regex' | 'slug' | 'lorem')
 * so we can filter by tool in the UI.
 *
 * We deliberately keep this client-side only:
 *   • No server round-trip for what is fundamentally a scratch pad.
 *   • Users can clear instantly via browser storage; no PII leaves the client.
 *   • Per-user namespace via the username key prevents cross-account bleed
 *     when multiple users share a browser profile.
 *
 * Storage cap: 200 notes / 1 MB total. We prune oldest first when exceeded so
 * a runaway loop in some future tool can't fill localStorage indefinitely.
 */

const STORAGE_PREFIX = '_ut_textutil_notes_v1::'
const MAX_NOTES      = 200
const MAX_BYTES      = 1024 * 1024 // 1 MB
const MAX_BODY_CHARS = 50_000      // single-note guardrail

function storageKey(username) {
  // Guests share a single bucket — they have no stable identity but the
  // notes are still useful within a session. Falls back to 'anon' when the
  // hook is rendered before auth is ready (extremely brief window).
  return STORAGE_PREFIX + (username || 'anon')
}

function readAll(username) {
  try {
    const raw = localStorage.getItem(storageKey(username))
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    // Storage blocked / quota / corrupt JSON: behave as an empty store.
    return []
  }
}

function writeAll(username, notes) {
  try {
    let serialized = JSON.stringify(notes)
    // Prune newest-first beyond size cap. Iterates a few times only.
    while (serialized.length > MAX_BYTES && notes.length > 1) {
      notes = notes.slice(0, notes.length - 1) // drop oldest (sorted newest-first below)
      serialized = JSON.stringify(notes)
    }
    localStorage.setItem(storageKey(username), serialized)
    return notes
  } catch {
    return notes
  }
}

function makeId() {
  // Cheap, stable-enough id; not cryptographic, just unique per browser.
  return 'n_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 8)
}

export default function useTextUtilNotes(username) {
  const [notes, setNotes] = useState(() => readAll(username))

  useEffect(() => {
    setNotes(readAll(username))
  }, [username])

  /**
   * Save a new note. Returns the new note object (with id + createdAt) or
   * null if persistence failed.
   */
  const addNote = useCallback((tool, title, content) => {
    const safeContent = (content || '').slice(0, MAX_BODY_CHARS)
    if (!safeContent.trim()) return null
    const newNote = {
      id:        makeId(),
      tool:      tool || 'unknown',
      title:     (title || '').trim().slice(0, 80) || 'Untitled note',
      content:   safeContent,
      createdAt: new Date().toISOString(),
    }
    setNotes(prev => {
      const next = [newNote, ...prev].slice(0, MAX_NOTES)
      return writeAll(username, next)
    })
    return newNote
  }, [username])

  const updateNote = useCallback((id, patch) => {
    setNotes(prev => {
      const next = prev.map(n =>
        n.id === id ? { ...n, ...patch, id: n.id, createdAt: n.createdAt } : n
      )
      return writeAll(username, next)
    })
  }, [username])

  const deleteNote = useCallback((id) => {
    setNotes(prev => {
      const next = prev.filter(n => n.id !== id)
      return writeAll(username, next)
    })
  }, [username])

  const clearAll = useCallback(() => {
    setNotes(() => {
      try { localStorage.removeItem(storageKey(username)) } catch { /* ignore */ }
      return []
    })
  }, [username])

  return { notes, addNote, updateNote, deleteNote, clearAll }
}
