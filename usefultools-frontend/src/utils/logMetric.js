/*
 * logMetric.js — Sprint 18 client-side metrics reporter (debounced).
 * ──────────────────────────────────────────────────────────────────
 *
 * CHANGE FROM PREVIOUS VERSION (added per-tool debouncing).
 *
 * The original logMetric had no debouncing — the assumption was every metric
 * call represents a discrete user action. In practice, many tool pages
 * compute reactively (useMemo on every keystroke) or via inline expressions,
 * so calling trackTool from those sites would flood tool_metrics with one
 * row per keystroke.
 *
 * Mirroring logActivity.js, each tool now has its own pending-write slot
 * keyed on toolName. Rapid successive calls within DEBOUNCE_MS coalesce
 * into a single write — the LAST call's timing is what gets recorded,
 * which is the most meaningful one (the user's settled input).
 *
 * Debounce window: 1500 ms (matches logActivity.js).
 *
 * Tools that genuinely fire only once per discrete user action (button
 * clicks, completed async operations) still work correctly — their
 * single trackTool call schedules a write that fires 1500 ms later.
 * No accuracy is lost for these; just a small fixed delay.
 *
 * ── Two entry points ──────────────────────────────────────────────────────
 *
 *   logMetric(toolName, durationMs, success, errorCode)
 *       Low-level primitive. Use when you've timed something yourself.
 *
 *   trackTool(toolName, fn)
 *       Ergonomic wrapper. Times fn() (sync or async), captures success
 *       from throws, samples Chromium memory deltas, returns fn's result
 *       (or rethrows). The metric WRITE is debounced; fn() runs immediately.
 *
 * ── What NOT to log ───────────────────────────────────────────────────────
 * Never pass user input, output, or any content-derived data through this
 * path. errorCode must be an OPAQUE LABEL (e.g. "PARSE_ERROR"), never the
 * exception's message — messages can include user text.
 */

import { logMetric as apiLogMetric } from '../api/apiClient'

// ── Allow-list (keep in sync with MetricsDAO.VALID_TOOL_NAMES on the server) ─

const VALID_TOOL_NAMES = new Set([
  // Client-side tools
  'analyzer.classify',
  'analyzer.base',
  'analyzer.series',
  'converter.convert',
  'text.transform',
  'encoding.transform',
  'code.format',
  'webdev.generate',
  'webdev.headers',
  'image.process',
  'hash.identify',
  'key.generate',
  'qrcode.generate',
  'cron.build',
  'time.convert',
  'time.timestamp',
  // Server-side tools — listed for symmetry; client code typically won't
  // call these directly because MetricsFilter handles them server-side.
  'password.generate',
  'password.save',
  'password.fetch',
  'calculator.standard',
  'calculator.financial',
  'calculator.probability',
])

// ── Debounce state ──────────────────────────────────────────────────────────
//
// Map<toolName, { timer, payload }>. Each tool gets its own pending slot —
// a burst of "text.transform" writes doesn't hold back a concurrent
// "encoding.transform" write.
const pending = new Map()
const DEBOUNCE_MS = 1500

// ── Internal: best-effort memory sample (Chromium only) ─────────────────────

function sampleMemoryBytes() {
  try {
    const m = typeof performance !== 'undefined' && performance.memory
      ? performance.memory.usedJSHeapSize
      : null
    return typeof m === 'number' ? m : null
  } catch {
    return null
  }
}

// ── Internal: debounced send ────────────────────────────────────────────────

function scheduleSend(payload) {
  if (!VALID_TOOL_NAMES.has(payload.toolName)) {
    if (typeof console !== 'undefined' && console.warn) {
      console.warn(`logMetric: unknown toolName "${payload.toolName}", skipping.`)
    }
    return
  }
  if (typeof payload.executionTimeMs !== 'number'
      || !Number.isFinite(payload.executionTimeMs)) {
    return
  }

  const existing = pending.get(payload.toolName)
  if (existing?.timer) clearTimeout(existing.timer)

  const timer = setTimeout(() => {
    const slot = pending.get(payload.toolName)
    if (!slot) return
    pending.delete(payload.toolName)
    try {
      apiLogMetric({
        toolName:        slot.payload.toolName,
        executionTimeMs: Math.max(0, Math.round(slot.payload.executionTimeMs)),
        memoryBytes:     typeof slot.payload.memoryBytes === 'number'
                            ? Math.round(slot.payload.memoryBytes) : null,
        latencyMs:       typeof slot.payload.latencyMs === 'number'
                            ? Math.round(slot.payload.latencyMs) : null,
        success:         slot.payload.success !== false,
        errorCode:       slot.payload.errorCode || null,
      }).catch(() => {
        // Silent. A failed metric write is not user-facing.
      })
    } catch {
      // Swallow synchronous errors (SSR contexts, storage blocks, etc.)
    }
  }, DEBOUNCE_MS)

  pending.set(payload.toolName, { timer, payload })
}

// ── Public API ──────────────────────────────────────────────────────────────

/**
 * Low-level recorder. The caller has already measured duration.
 *
 * @param {string}  toolName    Must be in VALID_TOOL_NAMES.
 * @param {number}  durationMs  Wall-clock duration of the operation.
 * @param {boolean} [success=true]
 * @param {string}  [errorCode] Optional machine-readable failure label.
 */
export function logMetric(toolName, durationMs, success = true, errorCode = null) {
  scheduleSend({
    toolName,
    executionTimeMs: durationMs,
    memoryBytes: null,
    latencyMs:   null,
    success,
    errorCode: success ? null : errorCode,
  })
}

/**
 * Ergonomic wrapper that times fn(), captures success from whether it
 * throws, and records a memory delta on Chromium. Sync and async safe.
 * Returns whatever fn returns (unwrapped). The metric WRITE is debounced
 * per-tool; fn() runs immediately.
 *
 * Example:
 *
 *   import { trackTool } from '../../utils/logMetric'
 *
 *   function handleGenerate() {
 *     const out = trackTool('text.transform', () => transformText(input))
 *     setOutput(out)
 *   }
 *
 *   async function handleFetch() {
 *     const resp = await trackTool('webdev.generate', () => doAsync())
 *     setResp(resp)
 *   }
 *
 * @param {string} toolName  Must be in VALID_TOOL_NAMES.
 * @param {Function} fn      Zero-arg function. Sync or async. Throws propagate.
 * @returns Whatever fn returns (unwrapped).
 */
export function trackTool(toolName, fn) {
  const start     = (typeof performance !== 'undefined') ? performance.now() : Date.now()
  const memBefore = sampleMemoryBytes()

  const finish = (success, errorCode) => {
    const end      = (typeof performance !== 'undefined') ? performance.now() : Date.now()
    const memAfter = sampleMemoryBytes()
    const memDelta = (memBefore != null && memAfter != null) ? (memAfter - memBefore) : null
    scheduleSend({
      toolName,
      executionTimeMs: end - start,
      memoryBytes: memDelta,
      latencyMs: null,
      success,
      errorCode: success ? null : errorCode,
    })
  }

  let result
  try {
    result = fn()
  } catch (err) {
    finish(false, errorMessageToCode(err))
    throw err
  }

  // Promise-aware path: time the whole chain.
  if (result && typeof result.then === 'function') {
    return result.then(
      (val) => { finish(true, null); return val },
      (err) => { finish(false, errorMessageToCode(err)); throw err }
    )
  }

  // Synchronous success.
  finish(true, null)
  return result
}

/**
 * Flush pending metric writes immediately. Useful in tests and could be
 * wired to window's pagehide event in the future.
 *
 * @param {string} [toolName] If provided, flush only that tool's pending
 *   write. If omitted, flush all pending writes.
 */
export function flushMetrics(toolName) {
  function flushOne(slot) {
    if (!slot) return
    clearTimeout(slot.timer)
    try {
      apiLogMetric({
        toolName:        slot.payload.toolName,
        executionTimeMs: Math.max(0, Math.round(slot.payload.executionTimeMs)),
        memoryBytes:     typeof slot.payload.memoryBytes === 'number'
                            ? Math.round(slot.payload.memoryBytes) : null,
        latencyMs:       typeof slot.payload.latencyMs === 'number'
                            ? Math.round(slot.payload.latencyMs) : null,
        success:         slot.payload.success !== false,
        errorCode:       slot.payload.errorCode || null,
      }).catch(() => {})
    } catch {
      // Swallow.
    }
  }

  if (toolName) {
    const slot = pending.get(toolName)
    if (slot) {
      pending.delete(toolName)
      flushOne(slot)
    }
    return
  }
  for (const [name, slot] of pending.entries()) {
    pending.delete(name)
    flushOne(slot)
  }
}

/**
 * Reset internal state. Tests only.
 */
export function _resetForTests() {
  for (const slot of pending.values()) clearTimeout(slot.timer)
  pending.clear()
}

/**
 * Collapses an arbitrary thrown value into a short, opaque label. We never
 * send the raw message because it could include user content (regex strings,
 * text inputs, etc.).
 */
function errorMessageToCode(err) {
  if (!err) return 'UNKNOWN'
  if (typeof err === 'string') return 'STRING_ERROR'
  if (err.name === 'SyntaxError')       return 'SYNTAX_ERROR'
  if (err.name === 'TypeError')         return 'TYPE_ERROR'
  if (err.name === 'RangeError')        return 'RANGE_ERROR'
  if (err.name === 'NetworkError')      return 'NETWORK_ERROR'
  if (err.name === 'AbortError')        return 'ABORTED'
  return err.name || 'EXCEPTION'
}
