/*
 * logActivity.js
 * ──────────────
 * Client-side wrapper around POST /api/activity/log. Called from tools
 * after a successful user-visible operation to record an entry in the
 * unified activity timeline.
 *
 * ── Why a debounce ────────────────────────────────────────────────────────
 * Some tools fire many operations in quick succession — Text Utilities'
 * word counter would otherwise log every keystroke, and the Unit Converter
 * logs every slider drag. Debouncing per-tool to 1500ms coalesces these
 * into a single log entry that captures the LAST state the user settled on,
 * which is what they care about seeing on the Dashboard timeline.
 *
 * Each tool has its own independent debounce slot (keyed on toolName), so
 * a burst of "image.process" logs doesn't hold back a concurrent
 * "hash.identify" log.
 *
 * ── Why fire-and-forget ───────────────────────────────────────────────────
 * The activity log is a secondary concern — the user's tool output is the
 * primary concern. A failed log call must never surface as an error toast
 * or block the UI. All fetch errors are caught and silently swallowed.
 *
 * ── What NEVER goes in the payload ────────────────────────────────────────
 * The activity-log table is persisted server-side. Tool callers must not
 * pass anything privacy-sensitive here:
 *   • Image Tools: NEVER filenames or image bytes. Only parameters
 *     (dimensions, format, quality).
 *   • API Key Generator: NEVER the generated key body. Only the count,
 *     format, and length.
 *   • Hash Identifier: NEVER the raw input. Only length, charset,
 *     top candidate name.
 *
 * Each caller's own doc comment (and the tool-page comments in
 * DevUtilsPage.jsx / ImageToolsPage.jsx) repeats this constraint.
 */

import { logActivity as apiLogActivity } from '../api/apiClient'

// ── Debounce state ──────────────────────────────────────────────────────────
//
// A Map<toolName, { timer, args }> — each tool gets its own pending slot.
// When a new call arrives for a tool that's already pending, the timer is
// reset and the args are overwritten so the most recent call wins.
const pending = new Map()
const DEBOUNCE_MS = 1500

// Allow-list mirrored from ActivityDAO.VALID_TOOL_NAMES on the server. The
// server will reject unknown tool names, but doing the check here too means
// we can skip the network round-trip entirely for obvious typos.
const VALID_TOOL_NAMES = new Set([
  'analyzer.classify',
  'converter.convert',
  'text.transform',
  'encoding.transform',
  'code.format',
  'webdev.generate',
  'image.process',
  'hash.identify',
  'key.generate',
  // Sprint 16 additions (DevUtils QR + Cron, TimeUtils convert/timestamp).
  'qrcode.generate',
  'cron.build',
  'time.convert',
  'time.timestamp',
  // Sprint 21 additions (API Formatter).
  'formatter.format',
])

// ── Internal: the actual send ───────────────────────────────────────────────
//
// Extracted so flushLogs can call it synchronously after cancelling a timer.
function send(toolName, summary, payload) {
  try {
    // apiLogActivity returns a promise resolving to { status, data }. We
    // deliberately don't await it anywhere that matters — the tool is not
    // waiting to hear back.
    apiLogActivity(summary, toolName, payload)
      .catch(() => {
        // Silent. A failed log is not a user-facing problem.
      })
  } catch {
    // Swallow synchronous errors too (e.g. SSR contexts where fetch isn't
    // defined — shouldn't happen in our Vite app but defence in depth).
  }
}

// ── Public API ──────────────────────────────────────────────────────────────

/**
 * Schedule an activity-log write, coalescing rapid successive calls to the
 * same tool into one (the most recent call wins).
 *
 * @param {string} toolName  A value from VALID_TOOL_NAMES.
 * @param {string} summary   One-line human-readable summary. Server enforces
 *                           200-char cap; there's no reason to pre-truncate here.
 * @param {object} [payload] Optional JSON-serialisable blob for future
 *                           drill-down views. MUST NOT contain any
 *                           content-derived data (see file header).
 */
export function logActivity(toolName, summary, payload) {
  if (!VALID_TOOL_NAMES.has(toolName)) {
    // Fast-fail typos without polluting the network tab. This also documents
    // the allow-list in one place — adding a new tool means adding to both
    // this set and the server-side ActivityDAO set.
    if (typeof console !== 'undefined' && console.warn) {
      console.warn(`logActivity: unknown toolName "${toolName}", skipping.`)
    }
    return
  }
  if (!summary || typeof summary !== 'string') return

  // Cancel any pending timer for this tool — the new call supersedes it.
  const existing = pending.get(toolName)
  if (existing?.timer) clearTimeout(existing.timer)

  const timer = setTimeout(() => {
    const slot = pending.get(toolName)
    if (!slot) return
    pending.delete(toolName)
    send(slot.toolName, slot.summary, slot.payload)
  }, DEBOUNCE_MS)

  pending.set(toolName, { toolName, summary, payload, timer })
}

/**
 * Flush pending log entries immediately. Useful in tests, and could be
 * wired to window's `pagehide` event in the future to reduce the window
 * where an in-flight debounce is lost when the user navigates away.
 *
 * @param {string} [toolName] If provided, flush only that tool's pending
 *   entry. If omitted, flush all pending entries across all tools.
 */
export function flushLogs(toolName) {
  if (toolName) {
    const slot = pending.get(toolName)
    if (slot) {
      clearTimeout(slot.timer)
      pending.delete(toolName)
      send(slot.toolName, slot.summary, slot.payload)
    }
    return
  }
  for (const [name, slot] of pending.entries()) {
    clearTimeout(slot.timer)
    pending.delete(name)
    send(slot.toolName, slot.summary, slot.payload)
  }
}

/**
 * Reset internal state. Intended for unit tests only — exported with an
 * underscore prefix to make it obvious it's not part of the supported API.
 */
export function _resetForTests() {
  for (const slot of pending.values()) clearTimeout(slot.timer)
  pending.clear()
}
