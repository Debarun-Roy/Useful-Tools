/*
 * hashIdentifier.js
 * ─────────────────
 * Pure-JS pattern detection for hash and credential formats. Returns a
 * ranked list of candidate algorithms with confidence tiers and short
 * explanations the UI can show inline.
 *
 * ── Why pattern matching (and not magic sniffing) ─────────────────────────
 * Most published hashes carry only their value, not an algorithm tag, so
 * identification is a regex job: length + charset + optional prefix. Several
 * algorithms collide at the same shape (32-hex is both MD5 and NTLM, for
 * instance), so we return ALL plausible candidates and let the UI show them
 * ordered by how much evidence we have.
 *
 * ── Confidence tiers ──────────────────────────────────────────────────────
 *   'certain'  — the input carries an unambiguous self-identifying prefix
 *                (e.g. "$2b$" → BCrypt, "eyJ…" → JWT). No other algorithm
 *                produces this shape.
 *   'likely'   — a common, widely-used format that matches the observed
 *                length and charset (MD5 at 32 hex, SHA-256 at 64 hex).
 *   'possible' — shares shape with a more common format but is still a
 *                realistic guess (NTLM / MD4 at 32 hex).
 *   'rare'     — matches shape but is uncommon in the wild (RIPEMD-160 at
 *                40 hex).
 *
 * ── Design notes ──────────────────────────────────────────────────────────
 * Pure client-side; no network round-trip. Exported for unit-testing.
 * Runs on every keystroke in the Dev Utilities page — keep it cheap.
 */

// ── Algorithm registry ──────────────────────────────────────────────────────
//
// Static metadata consumed both by identifyHash() and by the reference panel
// the UI renders beside the input. Keeping it in one place avoids drift
// between "what the detector reports" and "what the docs say".
export const HASH_ALGORITHMS = {
  md5:        { name: 'MD5',             length: 32,  charset: 'hex',    note: 'Broken for security use. Still common for file checksums.' },
  ntlm:       { name: 'NTLM',            length: 32,  charset: 'hex',    note: 'Windows password hash. Same shape as MD5 — context decides.' },
  md4:        { name: 'MD4',             length: 32,  charset: 'hex',    note: 'Obsolete. Same shape as MD5.' },
  sha1:       { name: 'SHA-1',           length: 40,  charset: 'hex',    note: 'Deprecated for security. Still used in Git, legacy systems.' },
  ripemd160:  { name: 'RIPEMD-160',      length: 40,  charset: 'hex',    note: 'Uncommon. Bitcoin addresses use it internally.' },
  sha224:     { name: 'SHA-224',         length: 56,  charset: 'hex',    note: 'Truncated SHA-256. Rare.' },
  sha256:     { name: 'SHA-256',         length: 64,  charset: 'hex',    note: 'Modern default. Ubiquitous.' },
  sha3_256:   { name: 'SHA3-256',        length: 64,  charset: 'hex',    note: 'Different algorithm from SHA-256 despite identical shape.' },
  sha384:     { name: 'SHA-384',         length: 96,  charset: 'hex',    note: 'Truncated SHA-512.' },
  sha512:     { name: 'SHA-512',         length: 128, charset: 'hex',    note: 'Longer SHA-2 variant.' },
  sha3_512:   { name: 'SHA3-512',        length: 128, charset: 'hex',    note: 'Different algorithm from SHA-512 despite identical shape.' },
  crc32:      { name: 'CRC32',           length: 8,   charset: 'hex',    note: 'Checksum, NOT a cryptographic hash.' },
  bcrypt:     { name: 'BCrypt',          length: 60,  charset: 'mixed',  note: 'Self-identifying via $2a$ / $2b$ / $2y$ prefix.' },
  md5crypt:   { name: 'MD5 crypt',       length: null,charset: 'mixed',  note: 'Unix MD5-based password hash. Prefix $1$.' },
  sha256crypt:{ name: 'SHA-256 crypt',   length: null,charset: 'mixed',  note: 'Unix SHA-256-based password hash. Prefix $5$.' },
  sha512crypt:{ name: 'SHA-512 crypt',   length: null,charset: 'mixed',  note: 'Unix SHA-512-based password hash. Prefix $6$.' },
  argon2:     { name: 'Argon2',          length: null,charset: 'mixed',  note: 'Modern password hash (PHC). Prefix $argon2id$ / $argon2i$ / $argon2d$.' },
  scrypt:     { name: 'scrypt',          length: null,charset: 'mixed',  note: 'Memory-hard password hash. Prefix $scrypt$.' },
  ldap_ssha:  { name: 'LDAP SSHA',       length: null,charset: 'mixed',  note: 'Salted SHA-1, base64-encoded. Prefix {SSHA}.' },
  ldap_sha:   { name: 'LDAP SHA',        length: null,charset: 'mixed',  note: 'SHA-1, base64-encoded. Prefix {SHA}.' },
  jwt:        { name: 'JWT',             length: null,charset: 'mixed',  note: 'JSON Web Token. Three base64url parts separated by dots.' },
}

// ── Helpers ─────────────────────────────────────────────────────────────────

function charsetOf(str) {
  if (/^[0-9a-fA-F]+$/.test(str)) return 'hex'
  if (/^[A-Za-z0-9+/]+=*$/.test(str)) return 'base64'
  if (/^[A-Za-z0-9_-]+$/.test(str)) return 'base64url'
  return 'mixed'
}

function candidate(name, confidence, note) {
  return { name, confidence, note }
}

// ── Main detector ────────────────────────────────────────────────────────────

/**
 * Identify the most likely hash/credential format(s) of the given input.
 *
 * @param {string} raw  The user input. Leading/trailing whitespace is stripped.
 * @returns {{
 *   input:      string,
 *   normalized: string,
 *   length:     number,
 *   charset:    'hex' | 'base64' | 'base64url' | 'mixed' | 'empty',
 *   candidates: { name: string, confidence: 'certain'|'likely'|'possible'|'rare', note: string }[],
 *   error:      string | null
 * }}
 */
export function identifyHash(raw) {
  const input = typeof raw === 'string' ? raw : ''
  const normalized = input.trim()

  // Empty input — return a clean "nothing to see" shape.
  if (!normalized) {
    return {
      input, normalized,
      length: 0, charset: 'empty',
      candidates: [],
      error: null,
    }
  }

  const length  = normalized.length
  const charset = charsetOf(normalized)
  const candidates = []

  // ── Self-identifying prefixes (certain) ─────────────────────────────────

  // BCrypt — $2a$, $2b$, $2y$ followed by cost and 53-char base64 blob.
  // BCrypt hashes are almost always exactly 60 chars total.
  if (/^\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}$/.test(normalized)) {
    candidates.push(candidate('BCrypt', 'certain', HASH_ALGORITHMS.bcrypt.note))
  }

  // Unix crypt(3) family — modular crypt format, $id$salt$hash
  if (/^\$1\$/.test(normalized))  candidates.push(candidate('MD5 crypt',    'certain', HASH_ALGORITHMS.md5crypt.note))
  if (/^\$5\$/.test(normalized))  candidates.push(candidate('SHA-256 crypt','certain', HASH_ALGORITHMS.sha256crypt.note))
  if (/^\$6\$/.test(normalized))  candidates.push(candidate('SHA-512 crypt','certain', HASH_ALGORITHMS.sha512crypt.note))

  // Argon2 — $argon2{i|d|id}$v=<n>$m=<n>,t=<n>,p=<n>$salt$hash
  if (/^\$argon2(id|i|d)\$/.test(normalized)) {
    candidates.push(candidate('Argon2', 'certain', HASH_ALGORITHMS.argon2.note))
  }

  // scrypt — $scrypt$ln=...$salt$hash (not as strictly standardised as argon2,
  // but the $scrypt$ prefix is the universal signal.)
  if (/^\$scrypt\$/.test(normalized)) {
    candidates.push(candidate('scrypt', 'certain', HASH_ALGORITHMS.scrypt.note))
  }

  // LDAP — {SSHA} / {SHA} prefix, base64 body
  if (/^\{SSHA\}[A-Za-z0-9+/]+=*$/.test(normalized)) {
    candidates.push(candidate('LDAP SSHA', 'certain', HASH_ALGORITHMS.ldap_ssha.note))
  }
  if (/^\{SHA\}[A-Za-z0-9+/]+=*$/.test(normalized)) {
    candidates.push(candidate('LDAP SHA', 'certain', HASH_ALGORITHMS.ldap_sha.note))
  }

  // JWT — three base64url segments separated by dots. We check that the
  // header decodes to JSON containing "alg" before declaring it a JWT,
  // because a random three-dot base64url string would otherwise look like one.
  if (/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*$/.test(normalized)) {
    const headerPart = normalized.split('.')[0]
    try {
      // atob uses standard base64; convert base64url → base64 first.
      const b64 = headerPart.replace(/-/g, '+').replace(/_/g, '/')
      const padded = b64 + '==='.slice((b64.length + 3) % 4)
      const headerJson = JSON.parse(
        typeof atob === 'function'
          ? atob(padded)
          : Buffer.from(padded, 'base64').toString('utf-8')
      )
      if (headerJson && typeof headerJson.alg === 'string') {
        candidates.push(candidate('JWT', 'certain', HASH_ALGORITHMS.jwt.note))
      }
    } catch {
      // Not a JWT — header wasn't valid base64-encoded JSON. Silent.
    }
  }

  // ── Length + charset based detection (for hex-only inputs) ──────────────
  //
  // If we've already matched a self-identifying prefix above, DON'T also
  // add hex-length candidates — a BCrypt hash shouldn't get an "MD5" label
  // stapled on. The `certain` matches stand alone.
  if (candidates.length === 0 && charset === 'hex') {
    switch (length) {
      case 8:
        candidates.push(candidate('CRC32', 'likely', HASH_ALGORITHMS.crc32.note))
        break
      case 32:
        // MD5 dominates this shape in practice.
        candidates.push(candidate('MD5',  'likely',   HASH_ALGORITHMS.md5.note))
        candidates.push(candidate('NTLM', 'possible', HASH_ALGORITHMS.ntlm.note))
        candidates.push(candidate('MD4',  'rare',     HASH_ALGORITHMS.md4.note))
        break
      case 40:
        candidates.push(candidate('SHA-1',      'likely', HASH_ALGORITHMS.sha1.note))
        candidates.push(candidate('RIPEMD-160', 'rare',   HASH_ALGORITHMS.ripemd160.note))
        break
      case 56:
        candidates.push(candidate('SHA-224', 'likely', HASH_ALGORITHMS.sha224.note))
        break
      case 64:
        candidates.push(candidate('SHA-256',  'likely',   HASH_ALGORITHMS.sha256.note))
        candidates.push(candidate('SHA3-256', 'possible', HASH_ALGORITHMS.sha3_256.note))
        break
      case 96:
        candidates.push(candidate('SHA-384', 'likely', HASH_ALGORITHMS.sha384.note))
        break
      case 128:
        candidates.push(candidate('SHA-512',  'likely',   HASH_ALGORITHMS.sha512.note))
        candidates.push(candidate('SHA3-512', 'possible', HASH_ALGORITHMS.sha3_512.note))
        break
      default:
        // Hex-only but at an unrecognised length — no strong guess.
        break
    }
  }

  return {
    input, normalized,
    length, charset,
    candidates,
    error: null,
  }
}
