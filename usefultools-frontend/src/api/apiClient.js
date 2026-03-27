/**
 * apiClient.js — the single place where all HTTP calls to the backend are made.
 *
 * BASE is a relative path ('/api'). During development, Vite's proxy in
 * vite.config.js forwards it to http://localhost:8080/UsefulTools/api.
 * In production, the backend and frontend are served from the same origin
 * so no rewriting is needed.
 *
 * Every exported function returns a Promise that resolves to { status, data }
 * where data is the parsed ApiResponse JSON:
 *   { success: bool, data: any, error: string, errorCode: string }
 *
 * credentials: 'include' on every request sends the JSESSIONID session cookie
 * so AuthFilter recognises the request as authenticated.
 */

const BASE = 'http://localhost:8080/UsefulTools/api'

async function request(path, { method = 'GET', body, isForm = false, isJson = false } = {}) {
  const headers = {}
  let encodedBody

  if (isForm && body) {
    headers['Content-Type'] = 'application/x-www-form-urlencoded'
    encodedBody = new URLSearchParams(body).toString()
  } else if (isJson && body) {
    headers['Content-Type'] = 'application/json'
    encodedBody = JSON.stringify(body)
  }

  const response = await fetch(`${BASE}${path}`, {
    method,
    credentials: 'include',
    headers,
    body: encodedBody,
  })

  const data = await response.json()
  return { status: response.status, data }
}

// ── Authentication ────────────────────────────────────────────────────────────

export const loginUser = (username, password) =>
  request('/auth/login', { method: 'POST', isForm: true, body: { username, password } })

export const registerUser = (username, password) =>
  request('/auth/register', { method: 'POST', isForm: true, body: { username, password } })

export const updatePassword = (username, updatedPassword) =>
  request('/auth/update-password', {
    method: 'POST',
    isForm: true,
    body: { username, updated_password: updatedPassword },
  })

export const logoutUser = () =>
  request('/auth/logout', { method: 'POST' })

// ── Calculator — standard ─────────────────────────────────────────────────────

function evaluateStandard(path, expression) {
  return request(path, { method: 'POST', isJson: true, body: { expression } })
}

export const evaluateSimple       = (expr) => evaluateStandard('/calculator/simple',       expr)
export const evaluateIntermediate = (expr) => evaluateStandard('/calculator/intermediate',  expr)
export const evaluateBoolean      = (expr) => evaluateStandard('/calculator/boolean',       expr)
export const evaluateTrig         = (expr) => evaluateStandard('/calculator/trig',          expr)
export const evaluateCombined     = (expr) => evaluateStandard('/calculator/combined',      expr)

// ── Calculator — complex ──────────────────────────────────────────────────────

export const evaluateComplex = (operation) =>
  request('/calculator/complex', { method: 'POST', isJson: true, body: { operation } })

// ── Calculator — financial ─────────────────────────────────────────

/**
 * Calculate EMI (Equated Monthly Installment)
 * Formula: EMI = P * r * (1 + r)^n / ((1 + r)^n - 1)
 *
 * @param principal  Loan amount
 * @param annualRate Annual interest rate (%)
 * @param tenure     Loan duration
 * @param tenureUnit 'years' or 'months'
 */
export const calculateEMI = (principal, annualRate, tenure, tenureUnit = 'years') =>
  request('/calculator/emi', {
    method: 'POST',
    isJson: true,
    body: { principal, annualRate, tenure, tenureUnit },
  })

/**
 * Calculate income tax based on tax regimes
 *
 * @param income     Annual income
 * @param regime     'new' or 'old' (2024-25 tax regimes)
 * @param deductions Deductions for old regime
 */
export const calculateTax = (income, regime = 'new', deductions = 0) =>
  request('/calculator/tax', {
    method: 'POST',
    isJson: true,
    body: { income, regime, deductions },
  })

/**
 * Calculate compound interest
 *
 * @param principal  Initial investment amount
 * @param rate       Annual interest rate (%)
 * @param time       Time in years
 * @param frequency  Compounding frequency ('annually', 'semiannually', 'quarterly', 'monthly', 'daily')
 */
export const calculateCompoundInterest = (principal, rate, time, frequency = 'annually') =>
  request('/calculator/compound-interest', {
    method: 'POST',
    isJson: true,
    body: { principal, rate, time, frequency },
  })

/**
 * Calculate salary breakdown with deductions
 *
 * @param basicSalary   Monthly basic salary
 * @param hra           House rent allowance
 * @param da            Dearness allowance
 * @param allowances    Other allowances
 * @param pfContribution Provident fund contribution
 * @param professionalTax Professional tax
 * @param otherDeductions Other deductions
 */
export const calculateSalaryBreakdown = (
  basicSalary,
  hra = 0,
  da = 0,
  allowances = 0,
  pfContribution = 0,
  professionalTax = 0,
  otherDeductions = 0
) =>
  request('/calculator/salary-breakdown', {
    method: 'POST',
    isJson: true,
    body: {
      basicSalary,
      hra,
      da,
      allowances,
      pfContribution,
      professionalTax,
      otherDeductions,
    },
  })

// ── Calculator — validation ───────────────────────────────────────────────────

export const validateExpression = (expr, mode = 'simple') =>
  request(`/calculator/validate?expr=${encodeURIComponent(expr)}&mode=${encodeURIComponent(mode)}`)

// ── Number Analyser ───────────────────────────────────────────────────────────

export const classifyNumber = (number) =>
  request('/analyzer/classify', {
    method: 'POST',
    isForm: true,
    body: { number },
  })

export const fetchBaseRepresentation = (number, choice) =>
  request('/analyzer/base-representation', {
    method: 'POST',
    isForm: true,
    body: { number, choice },
  })

export const fetchAllSeries = (terms) =>
  request('/analyzer/series/all', {
    method: 'POST',
    isJson: true,
    body: { terms },
  })

export const fetchSelectedSeries = (terms, choiceMap) =>
  request('/analyzer/series/selected', {
    method: 'POST',
    isJson: true,
    body: { terms, choiceMap },
  })

// ── Password Vault ────────────────────────────────────────────────────────────

/**
 * Generate a secure password for a platform.
 * Backend saves it to generator_table (history log) and returns the plaintext.
 * Call savePassword() afterwards if the user wants it encrypted in the vault.
 *
 * @param username   Logged-in username
 * @param platform   Platform / site name
 * @param length     Total password length (8–128)
 * @param customize  'custom' for manual character-type split, anything else = auto
 * @param customFields  When customize='custom': { Numbers, 'Special Characters',
 *                      'Uppercase Alphabets', 'Lowercase Alphabets' } each an int count
 */
export const generatePassword = (username, platform, length, customize = 'auto', customFields = {}) => {
  const body = { username, platform, length: String(length), customize_password: customize }

  if (customize === 'custom') {
    // Backend expects each category as its own parameter
    Object.entries(customFields).forEach(([key, val]) => {
      body[key] = String(val)
    })
    // Backend also expects a repeated 'customization_checkboxes' parameter.
    // URLSearchParams handles repeated keys correctly when passed as an array of pairs.
    // We handle that via the raw URLSearchParams approach below.
  }

  // Build URLSearchParams manually to support repeated keys for customization_checkboxes
  const params = new URLSearchParams()
  params.append('username', username)
  params.append('platform', platform)
  params.append('length', String(length))
  params.append('customize_password', customize)

  if (customize === 'custom') {
    Object.keys(customFields).forEach(key => {
      params.append('customization_checkboxes', key)
      params.append(key, String(customFields[key]))
    })
  }

  return fetch(`${BASE}/passwords/generate`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: params.toString(),
  }).then(r => r.json().then(data => ({ status: r.status, data })))
}

/**
 * Save a password (plaintext) into the vault.
 * The backend encrypts it with RSA before storing.
 *
 * @param username   Logged-in username
 * @param platform   Platform / site name
 * @param password   Plaintext password to encrypt and store
 */
export const savePassword = (username, platform, password) =>
  request('/passwords/save', {
    method: 'POST',
    isForm: true,
    body: { username, platform, password },
  })

/**
 * Fetch all decrypted passwords for the logged-in user.
 * Username is read from the session server-side — no parameter needed.
 * Returns { "1": { platform, decrypted_password }, "2": ... }
 */
export const fetchAllPasswords = () =>
  request('/passwords/fetch?choice=All+Passwords')

/**
 * Fetch the decrypted password for a single platform.
 * Username is read from the session server-side — no parameter needed.
 *
 * @param platform  Platform to look up
 */
export const fetchPlatformPassword = (platform) =>
  request(`/passwords/fetch?choice=Single&platform=${encodeURIComponent(platform)}`)