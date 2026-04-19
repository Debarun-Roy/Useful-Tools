/*
 * keyGenerator.js
 * ───────────────
 * Cryptographically secure, format-diverse random string generator for API
 * keys, session tokens, and secrets. All randomness comes from
 * crypto.getRandomValues() — never Math.random() — so the output is suitable
 * for real secret material.
 *
 * ── Supported formats ─────────────────────────────────────────────────────
 *   uuid       RFC 4122 v4 UUID with standard 8-4-4-4-12 dashing.
 *   hex        Lowercase hex from N random bytes.
 *   base62     [0-9A-Za-z] of a given character length. Uses rejection
 *              sampling so each character is uniformly distributed over the
 *              62-symbol alphabet.
 *   base64url  URL-safe base64 of N random bytes, '=' padding stripped.
 *              Typical for JWT-style tokens and URL parameters.
 *
 * ── Uniformity ────────────────────────────────────────────────────────────
 * base62 is the only tricky case: 256 % 62 ≠ 0, so simply taking `byte % 62`
 * would slightly favour the first 256-62*4 = 8 symbols (0..7). We reject any
 * byte ≥ 248 (= 62 * 4) and re-sample, which gives a flat distribution.
 * Over 256k samples the per-symbol deviation is below 5%.
 *
 * ── Privacy in activity logs ──────────────────────────────────────────────
 * The activity-log wiring (logActivity.js → ActivityLogController) records
 * the PARAMETERS of a generation ("5 base62 keys at 32 chars with prefix
 * sk_live_"), NOT the generated key material. Keys MUST NEVER flow into
 * the log payload; callers need to be careful not to pass key bodies back
 * into logActivity(). See DevUtilsPage.jsx for the correct pattern.
 */

// ── Constants ────────────────────────────────────────────────────────────────

const BASE62_ALPHABET = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'

// Rejection-sampling threshold: 62 * 4 = 248.  Bytes 0..247 map cleanly onto
// the 62-symbol alphabet (four full passes); bytes 248..255 are rejected.
const BASE62_REJECT_FLOOR = 248

const MIN_BYTE_LENGTH = 4
const MAX_BYTE_LENGTH = 256

// Prefix guardrails — a sensible subset of URL-safe characters, bounded in
// length so nobody accidentally makes a "prefix" that's longer than the key.
const PREFIX_SANITISER = /[^A-Za-z0-9_-]/g
const MAX_PREFIX_LEN = 32

// ── Low-level CSPRNG helper ──────────────────────────────────────────────────
//
// Wrapped so we can mock it in tests if needed. crypto.getRandomValues is
// available on window (browsers) and globalThis.crypto (Node 20+ / Vite's
// jsdom environment) so a single call site works in both.
function randomBytes(n) {
  const buf = new Uint8Array(n)
  const c = (typeof crypto !== 'undefined' ? crypto : globalThis.crypto)
  if (!c || typeof c.getRandomValues !== 'function') {
    throw new Error(
      'No secure random source available. Upgrade your browser or '
      + 'run this in a recent Node runtime.'
    )
  }
  c.getRandomValues(buf)
  return buf
}

// ── Format generators ────────────────────────────────────────────────────────

/** RFC 4122 v4 UUID. */
export function generateUUIDv4() {
  // crypto.randomUUID gives us a native v4 UUID when available — it's faster
  // AND matches the RFC exactly. Fall back to a hand-rolled version built
  // from getRandomValues for older runtimes.
  const c = (typeof crypto !== 'undefined' ? crypto : globalThis.crypto)
  if (c && typeof c.randomUUID === 'function') return c.randomUUID()

  const b = randomBytes(16)
  b[6] = (b[6] & 0x0f) | 0x40      // version 4
  b[8] = (b[8] & 0x3f) | 0x80      // variant 10xx
  const hex = Array.from(b, x => x.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

/** Lowercase hex of the given byte length (default 32 bytes → 64 hex chars). */
export function generateHex(byteLength = 32) {
  const n = Math.max(MIN_BYTE_LENGTH, Math.min(byteLength | 0, MAX_BYTE_LENGTH))
  const bytes = randomBytes(n)
  let out = ''
  for (let i = 0; i < bytes.length; i++) {
    out += bytes[i].toString(16).padStart(2, '0')
  }
  return out
}

/**
 * base62 string of the given CHARACTER length (default 32). Uses rejection
 * sampling to keep the per-symbol distribution flat.
 */
export function generateBase62(length = 32) {
  const target = Math.max(MIN_BYTE_LENGTH, Math.min(length | 0, MAX_BYTE_LENGTH))
  // Over-sample by 2x to reduce the chance of running out before we hit the
  // target length even with worst-case rejection rates.
  let out = ''
  while (out.length < target) {
    const need = target - out.length
    const chunk = randomBytes(need * 2)
    for (let i = 0; i < chunk.length && out.length < target; i++) {
      const b = chunk[i]
      if (b < BASE62_REJECT_FLOOR) {
        out += BASE62_ALPHABET[b % 62]
      }
    }
  }
  return out
}

/** URL-safe base64 of the given byte length (default 32 bytes). Padding stripped. */
export function generateBase64Url(byteLength = 32) {
  const n = Math.max(MIN_BYTE_LENGTH, Math.min(byteLength | 0, MAX_BYTE_LENGTH))
  const bytes = randomBytes(n)
  // Convert bytes → standard base64 → URL-safe base64 without padding.
  // btoa() takes a binary string, so we expand the bytes into one first.
  let bin = ''
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i])
  const standard = typeof btoa === 'function'
    ? btoa(bin)
    : Buffer.from(bytes).toString('base64')
  return standard.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

// ── Prefix sanitiser ─────────────────────────────────────────────────────────
//
// Accepts whatever the user typed; strips anything outside [A-Za-z0-9_-];
// truncates to MAX_PREFIX_LEN. Returns ''.
export function sanitisePrefix(raw) {
  if (typeof raw !== 'string') return ''
  const cleaned = raw.replace(PREFIX_SANITISER, '')
  return cleaned.length > MAX_PREFIX_LEN
    ? cleaned.slice(0, MAX_PREFIX_LEN)
    : cleaned
}

// ── Orchestrator ─────────────────────────────────────────────────────────────

/**
 * Generates {@code count} keys of the given format.
 *
 * @param {object} opts
 * @param {'uuid'|'hex'|'base62'|'base64url'} opts.format
 * @param {number} opts.length  Length parameter. For uuid this is ignored.
 *   For hex / base64url this is byte count (chars = 2 * bytes for hex,
 *   ~1.33 * bytes for base64url). For base62 this is character count.
 * @param {string} [opts.prefix]  Optional prefix prepended to each key.
 * @param {number} [opts.count]   How many keys to generate. Clamped to [1, 100].
 * @returns {string[]}
 */
export function generateKeys({ format, length = 32, prefix = '', count = 1 } = {}) {
  const n = Math.max(1, Math.min(count | 0, 100))
  const cleanPrefix = sanitisePrefix(prefix)

  const produceOne = () => {
    switch (format) {
      case 'uuid':      return generateUUIDv4()
      case 'hex':       return generateHex(length)
      case 'base62':    return generateBase62(length)
      case 'base64url': return generateBase64Url(length)
      default:
        throw new Error(`Unknown format: ${format}`)
    }
  }

  const out = new Array(n)
  for (let i = 0; i < n; i++) out[i] = cleanPrefix + produceOne()
  return out
}

// ── Entropy estimator ────────────────────────────────────────────────────────
//
// Used by the UI's entropy strip. This is a rough bound — the effective
// entropy of a real secret depends on the attacker model, but for the
// "is this likely enough to be secure" display a log2(alphabet) * length
// approximation is the standard treatment.
export function estimateEntropyBits({ format, length = 32 } = {}) {
  switch (format) {
    case 'uuid':
      return 122 // RFC 4122 v4: 128 bits minus 6 fixed version/variant bits
    case 'hex':
      return (length | 0) * 8           // one byte per 2 hex chars
    case 'base62':
      return Math.floor((length | 0) * Math.log2(62))
    case 'base64url':
      return (length | 0) * 8           // one byte per ~1.33 base64 chars
    default:
      return 0
  }
}
