import { useCallback, useEffect, useState } from 'react'

/*
 * useRestCollection — localStorage-backed collection for the REST tester.
 *
 * Two parallel stores under the same per-user key:
 *
 *   saved    — { id, name, method, url, headers, body, contentType, createdAt }
 *              Manually saved requests. Persistent until the user deletes.
 *
 *   history  — { id, method, url, status, statusText, ok, elapsed, sentAt }
 *              Auto-appended after every send(). Capped at MAX_HISTORY entries
 *              with newest-first; older entries fall off the end.
 *
 * No PII concerns specific to this hook — request URLs and bodies stay in the
 * user's own browser. Response bodies are NOT persisted in history (only the
 * status line + elapsed time + url) to keep the store lean and avoid storing
 * potentially-sensitive response payloads.
 *
 * Storage cap: see MAX_BYTES below. We prune oldest history first if the
 * total bumps the cap, then drop saved if needed (rare).
 */

const STORAGE_PREFIX = '_ut_rest_collection_v1::'
const MAX_SAVED      = 100
const MAX_HISTORY    = 30
const MAX_BYTES      = 512 * 1024 // 512 KB
const MAX_BODY_CHARS = 50_000

function storageKey(username) {
  return STORAGE_PREFIX + (username || 'anon')
}

function readAll(username) {
  try {
    const raw = localStorage.getItem(storageKey(username))
    if (!raw) return { saved: [], history: [] }
    const parsed = JSON.parse(raw)
    return {
      saved:   Array.isArray(parsed.saved)   ? parsed.saved   : [],
      history: Array.isArray(parsed.history) ? parsed.history : [],
    }
  } catch {
    return { saved: [], history: [] }
  }
}

function writeAll(username, state) {
  try {
    let next = {
      saved:   state.saved.slice(0, MAX_SAVED),
      history: state.history.slice(0, MAX_HISTORY),
    }
    let serialized = JSON.stringify(next)

    // If we blow the size cap, prune history first (least-valuable),
    // then saved (most-valuable, last resort).
    while (serialized.length > MAX_BYTES && next.history.length > 0) {
      next = { ...next, history: next.history.slice(0, next.history.length - 1) }
      serialized = JSON.stringify(next)
    }
    while (serialized.length > MAX_BYTES && next.saved.length > 1) {
      next = { ...next, saved: next.saved.slice(0, next.saved.length - 1) }
      serialized = JSON.stringify(next)
    }

    localStorage.setItem(storageKey(username), serialized)
    return next
  } catch {
    return state
  }
}

function makeId(prefix) {
  return prefix + '_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 8)
}

function clipBody(body) {
  if (!body) return ''
  return body.length > MAX_BODY_CHARS ? body.slice(0, MAX_BODY_CHARS) : body
}

export default function useRestCollection(username) {
  const [state, setState] = useState(() => readAll(username))

  useEffect(() => {
    setState(readAll(username))
  }, [username])

  /**
   * Save (or update — by id) a named request.
   *
   * @param req {object}  { id?, name, method, url, headers, body, contentType }
   * @returns the persisted object including id + createdAt.
   */
  const saveRequest = useCallback((req) => {
    const persisted = {
      id:          req.id || makeId('r'),
      name:        (req.name || '').trim() || 'Untitled request',
      method:      req.method || 'GET',
      url:         (req.url || '').trim(),
      headers:     Array.isArray(req.headers) ? req.headers : [],
      body:        clipBody(req.body || ''),
      contentType: req.contentType || 'json',
      createdAt:   req.createdAt || new Date().toISOString(),
    }

    setState(prev => {
      const idx = prev.saved.findIndex(r => r.id === persisted.id)
      const nextSaved = idx >= 0
        ? prev.saved.map((r, i) => i === idx ? persisted : r)
        : [persisted, ...prev.saved]
      return writeAll(username, { ...prev, saved: nextSaved })
    })
    return persisted
  }, [username])

  const deleteRequest = useCallback((id) => {
    setState(prev => writeAll(username, {
      ...prev,
      saved: prev.saved.filter(r => r.id !== id),
    }))
  }, [username])

  /**
   * Append a history entry. Headers/body intentionally omitted to keep size
   * bounded and avoid stashing response payloads.
   */
  const recordHistory = useCallback((entry) => {
    const h = {
      id:         makeId('h'),
      method:     entry.method || 'GET',
      url:        entry.url || '',
      status:     entry.status ?? null,
      statusText: entry.statusText || '',
      ok:         entry.ok === true,
      elapsed:    typeof entry.elapsed === 'number' ? entry.elapsed : null,
      error:      entry.error || null,
      sentAt:     new Date().toISOString(),
    }
    setState(prev => writeAll(username, {
      ...prev,
      history: [h, ...prev.history].slice(0, MAX_HISTORY),
    }))
  }, [username])

  const clearHistory = useCallback(() => {
    setState(prev => writeAll(username, { ...prev, history: [] }))
  }, [username])

  const clearAll = useCallback(() => {
    try { localStorage.removeItem(storageKey(username)) } catch { /* ignore */ }
    setState({ saved: [], history: [] })
  }, [username])

  return {
    saved:   state.saved,
    history: state.history,
    saveRequest,
    deleteRequest,
    recordHistory,
    clearHistory,
    clearAll,
  }
}
