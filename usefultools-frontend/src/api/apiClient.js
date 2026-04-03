/**
 * apiClient.js — the single place where all HTTP calls to the backend are made.
 *
 * Sprint 6 addition: CSRF double-submit cookie pattern.
 *   getCsrfToken() reads the XSRF-TOKEN cookie set by LoginController.
 *   The token is sent as the X-XSRF-TOKEN header on every POST/PUT/DELETE
 *   request. CsrfFilter on the server validates header === session token.
 *
 * Sprint 8 addition:
 *   performBaseArithmetic() — POST /api/analyzer/base-arithmetic
 */

const BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080/UsefulTools/api'
let unauthorizedHandler = null
let unauthorizedHandled = false

export function registerUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
  return () => {
    if (unauthorizedHandler === handler) {
      unauthorizedHandler = null
      unauthorizedHandled = false
    }
  }
}

async function parseResponseBody(response) {
  const raw = await response.text()
  if (!raw) return {}

  try {
    return JSON.parse(raw)
  } catch {
    return {
      success: false,
      error: raw,
    }
  }
}

/**
 * Reads the XSRF-TOKEN cookie value set by the server on login.
 * Returns an empty string if the cookie is absent (e.g. before login).
 */
function getCsrfToken() {
  try {
    const match = document.cookie
      .split('; ')
      .find(row => row.startsWith('XSRF-TOKEN='))
    return match ? decodeURIComponent(match.split('=')[1]) : ''
  } catch {
    return ''
  }
}

async function request(path, { method = 'GET', body, isForm = false, isJson = false } = {}) {
  const headers = {}

  // Inject CSRF token for all state-changing methods (Sprint 6).
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase())) {
    const csrfToken = getCsrfToken()
    if (csrfToken) {
      headers['X-XSRF-TOKEN'] = csrfToken
    }
  }

  let encodedBody

  if (isForm && body) {
    headers['Content-Type'] = 'application/x-www-form-urlencoded'
    encodedBody = body instanceof URLSearchParams
      ? body.toString()
      : new URLSearchParams(body).toString()
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

  const data = await parseResponseBody(response)

  if (
    response.status === 401 &&
    unauthorizedHandler &&
    !unauthorizedHandled &&
    path !== '/auth/login' &&
    path !== '/auth/register'
  ) {
    unauthorizedHandled = true
    unauthorizedHandler({
      path,
      status: response.status,
      data,
    })
  } else if (response.status !== 401) {
    unauthorizedHandled = false
  }

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

export const evaluateSimple       = (expr) => evaluateStandard('/calculator/simple',      expr)
export const evaluateIntermediate = (expr) => evaluateStandard('/calculator/intermediate', expr)
export const evaluateBoolean      = (expr) => evaluateStandard('/calculator/boolean',      expr)
export const evaluateTrig         = (expr) => evaluateStandard('/calculator/trig',         expr)
export const evaluateCombined     = (expr) => evaluateStandard('/calculator/combined',     expr)

// ── Calculator — complex ──────────────────────────────────────────────────────

export const evaluateComplex = (operation) =>
  request('/calculator/complex', { method: 'POST', isJson: true, body: { operation } })

// ── Calculator — financial ────────────────────────────────────────────────────

export const calculateEMI = (principal, annualRate, tenure, tenureUnit = 'years') =>
  request('/calculator/emi', {
    method: 'POST',
    isJson: true,
    body: { principal, annualRate, tenure, tenureUnit },
  })

export const calculateTax = (income, regime = 'new', deductions = 0) =>
  request('/calculator/tax', {
    method: 'POST',
    isJson: true,
    body: { income, regime, deductions },
  })

export const calculateCompoundInterest = (principal, rate, time, frequency = 'annually') =>
  request('/calculator/compound-interest', {
    method: 'POST',
    isJson: true,
    body: { principal, rate, time, frequency },
  })

export const calculateSalaryBreakdown = (
  basicSalary, hra = 0, da = 0, allowances = 0,
  pfContribution = 0, professionalTax = 0, otherDeductions = 0
) =>
  request('/calculator/salary-breakdown', {
    method: 'POST',
    isJson: true,
    body: { basicSalary, hra, da, allowances, pfContribution, professionalTax, otherDeductions },
  })

// ── Calculator — validation ───────────────────────────────────────────────────

export const validateExpression = (expr, mode = 'simple') =>
  request(`/calculator/validate?expr=${encodeURIComponent(expr)}&mode=${encodeURIComponent(mode)}`)

// ── Number Analyser ───────────────────────────────────────────────────────────

export const classifyNumber = (number) =>
  request('/analyzer/classify', { method: 'POST', isForm: true, body: { number } })

export const fetchBaseRepresentation = (number, choice) =>
  request('/analyzer/base-representation', {
    method: 'POST',
    isForm: true,
    body: { number, choice },
  })

export const fetchAllSeries = (terms) =>
  request('/analyzer/series/all', { method: 'POST', isJson: true, body: { terms } })

export const fetchSelectedSeries = (terms, choiceMap) =>
  request('/analyzer/series/selected', { method: 'POST', isJson: true, body: { terms, choiceMap } })

/**
 * Performs arithmetic on two numbers expressed in an arbitrary base.
 *
 * @param {string} number1   First operand as a string in the given base.
 * @param {string} number2   Second operand as a string in the given base.
 * @param {number} base      The base (2–62).
 * @param {string} operation One of: add | subtract | multiply | divide
 */
export const performBaseArithmetic = (number1, number2, base, operation) =>
  request('/analyzer/base-arithmetic', {
    method: 'POST',
    isJson: true,
    body: { number1, number2, base, operation },
  })

// ── Password Vault ────────────────────────────────────────────────────────────

export const generatePassword = (_username, platform, length, customize = 'auto', customFields = {}) => {
  const params = new URLSearchParams()
  params.append('platform', platform)
  params.append('length', String(length))
  params.append('customize_password', customize)

  if (customize === 'custom') {
    Object.keys(customFields).forEach(key => {
      params.append('customization_checkboxes', key)
      params.append(key, String(customFields[key]))
    })
  }

  return request('/passwords/generate', {
    method: 'POST',
    isForm: true,
    body: params,
  })
}

export const savePassword = (_username, platform, password) =>
  request('/passwords/save', {
    method: 'POST',
    isForm: true,
    body: { platform, password },
  })

export const fetchAllPasswords = () =>
  request('/passwords/fetch?choice=All+Passwords')

export const fetchPlatformPassword = (platform) =>
  request(`/passwords/fetch?choice=Single&platform=${encodeURIComponent(platform)}`)

export const exportVaultEntries = () =>
  request('/passwords/export')

export const fetchGeneratedPasswordHistory = (page = 0, size = 12) =>
  request(`/passwords/generated-history?page=${page}&size=${size}`)

// ── Calculation History ───────────────────────────────────────────────────────

export const fetchCalculationHistory = (page = 0, size = 20) =>
  request(`/calculator/history?page=${page}&size=${size}`)

export const fetchFinancialHistory = (type, page = 0, size = 10) =>
  request(`/calculator/financial-history?type=${encodeURIComponent(type)}&page=${page}&size=${size}`)

// ── User Profile ──────────────────────────────────────────────────────────────

export const fetchUserProfile = () =>
  request('/user/profile')

// ── Vault Management ──────────────────────────────────────────────────────────

export const deleteVaultEntry = (platform) =>
  request(`/passwords/delete?platform=${encodeURIComponent(platform)}`, {
    method: 'DELETE',
  })

export const updateVaultEntry = (platform, password) =>
  request('/passwords/update', {
    method: 'PUT',
    isJson: true,
    body: { platform, password },
  })

// ── Matrix Calculator ─────────────────────────────────────────────────────────

export const calculateMatrix = (operation, size, matrix1, matrix2 = null) =>
  request('/calculator/matrix', {
    method: 'POST',
    isJson: true,
    body: { operation, size, matrix1, ...(matrix2 ? { matrix2 } : {}) },
  })

// ── Statistics Calculator ─────────────────────────────────────────────────────

export const calculateStats = (data) =>
  request('/calculator/stats', {
    method: 'POST',
    isJson: true,
    body: { data },
  })
