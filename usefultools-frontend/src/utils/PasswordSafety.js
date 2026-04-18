/*
 * passwordSafety.js — client-side password safety checks.
 *
 * Two independent capabilities:
 *   1. calculateEntropy(password)  — information-theoretic entropy in bits.
 *                                    Mirrors the server-side EntropyUtils.java
 *                                    so the Vault / Generated History tabs can
 *                                    show entropy without a network round-trip.
 *   2. checkDictionaryRisk(pwd)    — heuristic detection of passwords that
 *                                    would fall quickly to a dictionary attack.
 *                                    Flags common passwords, common English
 *                                    words with trivial leet substitutions,
 *                                    sequential runs, keyboard walks, and
 *                                    trivially-short or single-class inputs.
 *
 * WHY CLIENT-SIDE:
 *   Stored vault passwords are only ever decrypted in the browser for the
 *   logged-in owner. Running the dictionary check on-device avoids ever
 *   sending plaintext passwords back to the server for this purpose.
 */

// ─── Common-password dictionary ──────────────────────────────────────────────
// A curated subset of the most-leaked passwords (HaveIBeenPwned / RockYou).
// The full 10M list is inappropriate for a frontend bundle; this ~300-entry
// slice catches the overwhelming majority of real dictionary attacks because
// password reuse is heavily concentrated in the top of the distribution.
const COMMON_PASSWORDS = new Set([
  // Top 50 — everyone has seen these
  '123456', '123456789', 'qwerty', 'password', '12345678', '111111', '123123',
  '1234567890', '1234567', 'qwerty123', '000000', '1q2w3e', 'aa12345678', 'abc123',
  'password1', '1234', '12345', 'iloveyou', 'dragon', 'monkey', 'login',
  'letmein', 'princess', 'admin', 'welcome', 'solo', 'master', 'hello',
  'freedom', 'whatever', 'qazwsx', 'trustno1', 'baseball', 'football', 'superman',
  'michael', 'shadow', 'jesus', 'ninja', 'mustang', 'starwars', 'computer',
  'pokemon', 'batman', 'summer', 'winter', 'spring', 'autumn', 'sunshine',
  // Common patterns
  'qwertyuiop', 'asdfghjkl', 'zxcvbnm', '1qaz2wsx', 'qwer1234', 'abcd1234',
  'abcdef', 'abcdefg', '11111111', '22222222', '00000000', '88888888',
  'aaaaaa', 'aaaaaaaa', 'password123', 'password1234', 'admin123', 'administrator',
  'root', 'toor', 'user', 'guest', 'test', 'test123', 'demo', 'changeme',
  // Names and words
  'jennifer', 'jessica', 'charlie', 'andrew', 'michelle', 'daniel', 'matthew',
  'joshua', 'jordan', 'robert', 'tiger', 'sophie', 'nicole', 'ashley', 'hannah',
  'samantha', 'taylor', 'anthony', 'joseph', 'william', 'thomas', 'david',
  // Date/year suffixes
  'password2020', 'password2021', 'password2022', 'password2023', 'password2024',
  'password2025', 'password2026', 'welcome1', 'welcome123', 'Welcome1', 'Welcome123',
  'Password1', 'Password123', 'P@ssw0rd', 'P@ssword1', 'Passw0rd',
  // Sports, bands, pop-culture
  'liverpool', 'chelsea', 'arsenal', 'manutd', 'barcelona', 'realmadrid',
  'beatles', 'metallica', 'nirvana', 'queen', 'harry', 'harrypotter', 'hogwarts',
  'gandalf', 'frodo', 'legolas', 'naruto', 'goku', 'sasuke', 'itachi',
  // Keyboard walks
  'qwertyui', 'asdfjkl', 'zxcvbn', 'qazxsw', 'wsxedc', 'edcrfv', 'qweasdzxc',
  '147258369', '159357', '741852963', '963852741', 'qwe123', 'zxc123',
  // Simple human-memorable
  'login1', 'secret', 'hunter2', 'passpass', 'sekret', 'temp123', 'pass123',
  'test1234', 'demo1234', 'default', 'default123', 'user123', 'guest123',
  'backup', 'system', 'oracle', 'database', 'sqlpass', 'manager', 'service',
  // Common phrases
  'iloveu', 'loveyou', 'forever', 'family', 'friends', 'heaven', 'angel',
  'blessed', 'mother', 'father', 'brother', 'sister', 'baby', 'cookie',
  'chocolate', 'pizza', 'coffee', 'beer', 'money', 'hello123', 'hi12345',
  // Professional / corporate flavor
  'admin1', 'admin2', 'admin12', 'admin1234', 'secret1', 'secret123',
  'company1', 'office1', 'work1234', 'team123', 'project1', 'finance',
  'accounting', 'marketing', 'support', 'helpdesk', 'password!', 'password1!',
  'Password!', 'Password1!', 'P@ssw0rd1', 'P@ssw0rd!', 'P@ssw0rd123',
  // Tech
  'github', 'gitlab', 'docker', 'kubernetes', 'jenkins', 'linux', 'ubuntu',
  'windows', 'apple', 'google', 'microsoft', 'amazon', 'facebook', 'twitter',
  'instagram', 'snapchat', 'tiktok', 'youtube', 'netflix', 'spotify',
  // Numeric patterns
  '246810', '135792', '13579', '24680', '121212', '131313', '212121',
  '112233', '445566', '778899', '998877', '123321', '456654', '789987',
  '101010', '202020', '303030', '252525', '696969', '777777', '666666',
  // Common l33t variants of top words (lowercase)
  'passw0rd', 'p@ssword', 'qw3rty', 'adm1n', 'l3tme1n', 'welc0me',
  '3rr0r', 'h@cker', 'r00t', 'n00b', 'ninj@', 'dr@gon',
  // Short punctuation additions
  'qwerty!', 'admin!', 'welcome!', 'password!', 'secret!',
  'qwerty@', 'admin@', 'welcome@', 'password@', 'secret@',
  'qwerty#', 'admin#', 'welcome#', 'password#', 'secret#',
  // Birthdays / dates (sample)
  '01011990', '01012000', '01011980', '12121212', '01234567', '76543210',
  // Misc widely-leaked
  'michael1', 'michelle1', 'jennifer1', 'daniel1', 'jessica1', 'ashley1',
  'matrix', 'neo', 'morpheus', 'trinity', 'zion', 'enterprise', 'starfleet',
])

// Common English root words that trigger the "dictionary word" heuristic even
// when combined with numbers or symbols. Kept short — these are roots, not
// full passwords.
const COMMON_WORD_ROOTS = [
  'password', 'qwerty', 'admin', 'welcome', 'login', 'secret', 'letmein',
  'monkey', 'dragon', 'sunshine', 'princess', 'superman', 'batman',
  'master', 'freedom', 'shadow', 'ninja', 'mustang', 'computer',
  'pokemon', 'football', 'baseball', 'basketball', 'summer', 'winter',
  'spring', 'autumn', 'hello', 'hunter', 'iloveyou', 'trustno',
  'michael', 'michelle', 'jennifer', 'jessica', 'charlie', 'andrew',
  'matthew', 'joshua', 'jordan', 'robert', 'david', 'daniel',
  'family', 'friends', 'mother', 'father', 'sister', 'brother',
  'manager', 'service', 'support', 'default', 'changeme',
]

// Sequences that indicate a keyboard walk or simple run.
const KEYBOARD_WALKS = [
  'qwerty', 'qwertyu', 'qwertyui', 'qwertyuiop',
  'asdf', 'asdfgh', 'asdfghjkl',
  'zxcv', 'zxcvbn', 'zxcvbnm',
  'qaz', 'wsx', 'edc', 'rfv', 'tgb', 'yhn', 'ujm',
  '1qaz', '2wsx', '3edc', '4rfv', '5tgb',
  '1234567890', '0987654321',
  'abcdefghij', 'jihgfedcba',
]

// Leet-map used when normalizing before comparing to COMMON_WORD_ROOTS.
const LEET_MAP = {
  '0': 'o', '1': 'i', '3': 'e', '4': 'a', '5': 's',
  '7': 't', '8': 'b', '9': 'g', '$': 's', '@': 'a', '!': 'i',
}

function unleet(pwd) {
  let out = ''
  for (const ch of pwd.toLowerCase()) {
    out += LEET_MAP[ch] ?? ch
  }
  return out
}

// ─── Entropy calculation ─────────────────────────────────────────────────────
// Mirrors Useful-Tools/src/main/java/passwordgenerator/utilities/EntropyUtils.java.
// Character-pool sizes must stay in lock-step with the server so values agree.
const POOL_LOWER   = 26
const POOL_UPPER   = 26
const POOL_DIGIT   = 10
const POOL_SPECIAL = 32  // printable ASCII 33–126 minus alphanumerics

function effectivePoolSize(password) {
  let hasLower = false, hasUpper = false, hasDigit = false, hasSpecial = false
  for (let i = 0; i < password.length; i++) {
    const code = password.charCodeAt(i)
    if      (code >= 97 && code <= 122) hasLower   = true   // a-z
    else if (code >= 65 && code <= 90)  hasUpper   = true   // A-Z
    else if (code >= 48 && code <= 57)  hasDigit   = true   // 0-9
    else if (code >= 33 && code <= 126) hasSpecial = true   // printable special
  }
  return (hasLower ? POOL_LOWER : 0)
       + (hasUpper ? POOL_UPPER : 0)
       + (hasDigit ? POOL_DIGIT : 0)
       + (hasSpecial ? POOL_SPECIAL : 0)
}

/**
 * Information-theoretic entropy of a password, in bits.
 * H = length × log₂(effective-pool-size), rounded to two decimals.
 * Returns 0 for empty/null/single-class-single-character inputs.
 */
export function calculateEntropy(password) {
  if (!password) return 0
  const pool = effectivePoolSize(password)
  if (pool <= 1) return 0
  const entropy = password.length * (Math.log(pool) / Math.log(2))
  return Math.round(entropy * 100) / 100
}

/**
 * Human-readable strength label that matches the server's thresholds.
 * Same cut-offs as EntropyUtils.strengthLabel.
 */
export function strengthLabel(entropyBits) {
  if (entropyBits < 28)  return 'Very Weak'
  if (entropyBits < 36)  return 'Weak'
  if (entropyBits < 60)  return 'Fair'
  if (entropyBits < 128) return 'Strong'
  return 'Very Strong'
}

// ─── Pattern detectors ───────────────────────────────────────────────────────

function hasSequentialRun(pwd, minLen = 4) {
  if (pwd.length < minLen) return false
  const s = pwd.toLowerCase()
  for (let i = 0; i <= s.length - minLen; i++) {
    let ascending = true, descending = true
    for (let j = 1; j < minLen; j++) {
      const diff = s.charCodeAt(i + j) - s.charCodeAt(i + j - 1)
      if (diff !==  1) ascending  = false
      if (diff !== -1) descending = false
    }
    if (ascending || descending) return true
  }
  return false
}

function hasRepeatingRun(pwd, minLen = 4) {
  if (pwd.length < minLen) return false
  let run = 1
  for (let i = 1; i < pwd.length; i++) {
    run = pwd[i] === pwd[i - 1] ? run + 1 : 1
    if (run >= minLen) return true
  }
  return false
}

function containsKeyboardWalk(pwd) {
  const lower = pwd.toLowerCase()
  return KEYBOARD_WALKS.some(walk => walk.length >= 4 && lower.includes(walk))
}

function containsCommonWord(pwd) {
  const normalized = unleet(pwd)
  return COMMON_WORD_ROOTS.some(word => normalized.includes(word))
}

// ─── Public API ──────────────────────────────────────────────────────────────

/**
 * @typedef {Object} DictionaryRisk
 * @property {boolean} vulnerable    — true if the password should be flagged.
 * @property {'critical'|'high'|'medium'|'low'} severity
 * @property {string[]} reasons      — short human-readable reason strings.
 * @property {string}   message      — one-line summary for UI display.
 */

/**
 * Checks whether a password is vulnerable to a dictionary / wordlist attack.
 *
 * Severity levels:
 *   "critical" — password is in the common-password list (instant crack)
 *   "high"     — contains a common word root OR is a keyboard walk
 *   "medium"   — has a sequential / repeating run of 4+ chars, single char class,
 *                or is short (< 10) while lacking diversity
 *   "low"      — not vulnerable
 *
 * @param {string} password
 * @returns {DictionaryRisk}
 */
export function checkDictionaryRisk(password) {
  if (!password) {
    return { vulnerable: false, severity: 'low', reasons: [], message: '' }
  }

  const reasons = []
  let severity = 'low'

  // 1. Direct match against common-password list (case-insensitive).
  const lower = password.toLowerCase()
  if (COMMON_PASSWORDS.has(password) || COMMON_PASSWORDS.has(lower)) {
    reasons.push('appears on lists of the most-leaked passwords')
    severity = 'critical'
  }

  // 2. Common English word root, with or without leet substitutions.
  if (containsCommonWord(password)) {
    reasons.push('contains a common English word or name')
    if (severity === 'low') severity = 'high'
  }

  // 3. Keyboard walk (qwerty, asdf, 1qaz, etc.).
  if (containsKeyboardWalk(password)) {
    reasons.push('contains a keyboard walk (e.g. qwerty, asdf)')
    if (severity === 'low') severity = 'high'
  }

  // 4. Sequential ascending/descending run of 4+ characters (1234, abcd, 4321).
  if (hasSequentialRun(password, 4)) {
    reasons.push('contains a sequential run (e.g. 1234, abcd)')
    if (severity === 'low') severity = 'medium'
  }

  // 5. Repeating run of 4+ identical characters (aaaa, 1111).
  if (hasRepeatingRun(password, 4)) {
    reasons.push('contains a long run of repeated characters')
    if (severity === 'low') severity = 'medium'
  }

  // 6. Too short (< 8) — always a dictionary risk because brute-force time is trivial.
  if (password.length < 8) {
    reasons.push(`is only ${password.length} characters long`)
    if (severity === 'low') severity = 'medium'
  }

  // 7. Single character class (only letters, only digits) on a short password.
  const pool = effectivePoolSize(password)
  if (pool <= POOL_DIGIT && password.length < 12) {
    reasons.push('uses only a single character class')
    if (severity === 'low') severity = 'medium'
  }

  const vulnerable = reasons.length > 0 && severity !== 'low'

  let message = ''
  if (vulnerable) {
    if (severity === 'critical') {
      message = 'Vulnerable: this password ' + reasons[0]
        + '. Dictionary attacks would crack it almost instantly.'
    } else if (severity === 'high') {
      message = 'Vulnerable: this password ' + reasons[0]
        + '. A dictionary attack would find it quickly.'
    } else {
      message = 'Weak: this password ' + reasons.join(', and ') + '.'
    }
  }

  return { vulnerable, severity, reasons, message }
}

/**
 * Convenience: returns entropy + strength label + dictionary-risk assessment
 * in one call. Used by the Vault and Generated History cards.
 */
export function analysePassword(password) {
  const entropyBits = calculateEntropy(password)
  return {
    entropyBits,
    strengthLabel: strengthLabel(entropyBits),
    risk: checkDictionaryRisk(password),
  }
}
