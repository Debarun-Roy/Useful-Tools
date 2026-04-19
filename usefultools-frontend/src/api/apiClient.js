/**
 * apiClient.js — single place for all HTTP calls to the backend.
 *
 * Cross-origin CSRF fix (Vercel frontend / Railway backend):
 *
 *   Problem: In same-origin deployments the frontend reads the XSRF-TOKEN
 *   cookie from document.cookie and attaches it as X-XSRF-TOKEN.  In
 *   cross-origin deployments the XSRF-TOKEN cookie belongs to the Railway
 *   domain; JavaScript running on the Vercel domain cannot read it via
 *   document.cookie — the browser's same-origin policy forbids this.
 *
 *   Solution: A module-level variable (_csrfToken) is used as the primary
 *   source.  AuthContext sets it via setCsrfToken() immediately after login
 *   (the token is now included in the login response body).  sessionStorage
 *   is used as a persistence layer so page refreshes within the same browser
 *   session do not require a new login.  On first load after a refresh,
 *   AuthContext calls fetchCsrfToken() (GET /api/auth/csrf-token) to
 *   retrieve the token from the still-valid server session.  The original
 *   cookie-based fallback remains for same-origin (local dev) deployments.
 *
 * Sprint 15 additions — 7 new exports at the bottom of this file:
 *   logActivity / fetchActivityList / clearActivity  → unified activity log
 *   fetchFavorites / toggleFavorite / removeFavorite / reorderFavorites
 */

import { resolveApiBase } from './apiBase'

const BASE = resolveApiBase(import.meta.env.VITE_API_BASE)
let unauthorizedHandler = null
let unauthorizedHandled = false

// ── CSRF token state ──────────────────────────────────────────────────────────

/**
 * In-memory CSRF token.  Survives SPA navigation but not page reloads.
 * sessionStorage backs it up for page-reload survival within the same tab.
 */
let _csrfToken = ''
const CSRF_STORAGE_KEY = '_ut_xsrf'

/** Called by AuthContext after login and on mount (restore from storage). */
export function setCsrfToken(token) {
  _csrfToken = token || ''
  if (_csrfToken) {
    try { sessionStorage.setItem(CSRF_STORAGE_KEY, _csrfToken) } catch { /* storage blocked */ }
  }
}

/** Called by AuthContext on logout. */
export function clearCsrfToken() {
  _csrfToken = ''
  try { sessionStorage.removeItem(CSRF_STORAGE_KEY) } catch { /* storage blocked */ }
}

/**
 * Returns the best-available CSRF token using a three-tier fallback:
 *   1. Module-level variable  — fastest, always up-to-date after login.
 *   2. sessionStorage         — survives page refreshes in the same tab.
 *   3. document.cookie        — works in same-origin (local dev) deployments.
 */
function getCsrfToken() {
  // Tier 1: module variable
  if (_csrfToken) return _csrfToken

  // Tier 2: sessionStorage (page-refresh survival)
  try {
    const stored = sessionStorage.getItem(CSRF_STORAGE_KEY)
    if (stored) {
      _csrfToken = stored // warm the in-memory cache
      return _csrfToken
    }
  } catch { /* storage blocked in some iframe contexts */ }

  // Tier 3: cookie (same-origin / local-dev fallback)
  try {
    const match = document.cookie
      .split('; ')
      .find(row => row.startsWith('XSRF-TOKEN='))
    if (match) return decodeURIComponent(match.split('=')[1])
  } catch { /* cookie API unavailable */ }

  return ''
}

// ── Core request helper ───────────────────────────────────────────────────────

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
  try { return JSON.parse(raw) } catch { return { success: false, error: raw } }
}

async function request(path, { method = 'GET', body, isForm = false, isJson = false } = {}) {
  const headers = {}

  // Attach CSRF token header for all state-changing methods.
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase())) {
    const token = getCsrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
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
    unauthorizedHandler({ path, status: response.status, data })
  } else if (response.status !== 401) {
    unauthorizedHandled = false
  }

  return { status: response.status, data }
}

// ── Authentication ────────────────────────────────────────────────────────────

export const loginUser = (username, password) =>
  request('/auth/login', { method: 'POST', isForm: true, body: { username, password } })

export const loginAsGuest = () =>
  request('/auth/login-guest', { method: 'POST' })

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

/**
 * Fetches the CSRF token from the server session.
 * Used by AuthContext on page reload when sessionStorage is empty but
 * the server session (JSESSIONID) is still valid.
 */
export const fetchCsrfToken = () =>
  request('/auth/csrf-token')

/**
 * Validates that the current session is still valid.
 * Returns session status including username and authentication state.
 * 
 * This endpoint helps diagnose cross-origin session cookie transmission issues
 * by explicitly checking if the JSESSIONID cookie is being sent properly
 * on cross-origin requests (Vercel → Railway).
 */
export const validateSession = () =>
  request('/auth/session-status')

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
  return request('/passwords/generate', { method: 'POST', isForm: true, body: params })
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
  request(`/passwords/delete?platform=${encodeURIComponent(platform)}`, { method: 'DELETE' })

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
  request('/calculator/stats', { method: 'POST', isJson: true, body: { data } })

// ── Equation Solver ───────────────────────────────────────────────────────────

export const solveEquation = (equation) =>
  request('/calculator/solve', { method: 'POST', isJson: true, body: { equation } })

// ── Probability Calculator ────────────────────────────────────────────────────

export const calculateProbability = (distribution, params) =>
  request('/calculator/probability', {
    method: 'POST',
    isJson: true,
    body: { distribution, params },
  })

// ── Polynomial Calculator ─────────────────────────────────────────────────────

export const calculatePolynomial = (operation, coefficients, x = null) =>
  request('/calculator/polynomial', {
    method: 'POST',
    isJson: true,
    body: { operation, coefficients, ...(x !== null ? { x } : {}) },
  })

// ── Feedback ──────────────────────────────────────────────────────────────────

export const submitFeedback = (feedbackData) =>
  request('/feedback/submit', { method: 'POST', isJson: true, body: feedbackData })

// ═══════════════════════════════════════════════════════════════════════════════
// ── Sprint 15 — Activity log & Favorites ──────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════════
//
// These seven exports are the new Sprint 15 endpoints. They wrap the backend
// controllers delivered in Batch 1 (ActivityLogController, ActivityListController,
// ActivityClearController, FavoritesListController, FavoritesToggleController,
// FavoritesReorderController).
//
// The activity-log endpoints are used by the logActivity.js utility (debounced
// fire-and-forget) from inside each client-side tool; the favorites endpoints
// are used by the useFavorites hook on the Dashboard.

// ── Activity log ──────────────────────────────────────────────────────────────

/**
 * Post a single activity entry. Fire-and-forget from the caller's perspective
 * — the promise is returned for debugging only and should not be awaited by
 * business logic.
 *
 * @param {string} summary    One-line human-readable summary (≤200 chars).
 * @param {string} toolName   A value from ActivityDAO.VALID_TOOL_NAMES.
 * @param {object} [payload]  Optional JSON-serialisable blob. MUST NOT
 *                            contain any content-derived data (see
 *                            logActivity.js header for the full rules).
 */
export const logActivity = (summary, toolName, payload) =>
  request('/activity/log', {
    method: 'POST',
    isJson: true,
    body: { toolName, summary, payload },
  })

/**
 * Fetch recent activity entries for the current user.
 *
 * @param {object} [opts]
 * @param {string} [opts.tool]    Optional tool_name filter.
 * @param {number} [opts.limit]   Page size (default 10, server clamps to ≤100).
 * @param {number} [opts.offset]  Row offset for pagination.
 */
export const fetchActivityList = ({ tool, limit = 10, offset = 0 } = {}) => {
  const params = new URLSearchParams()
  if (tool) params.append('tool', tool)
  params.append('limit',  String(limit))
  params.append('offset', String(offset))
  return request(`/activity/list?${params.toString()}`)
}

/**
 * Clear the current user's activity log, optionally restricted to a single tool.
 *
 * @param {string} [tool]  Optional tool_name to restrict the delete.
 */
export const clearActivity = (tool) => {
  const suffix = tool ? `?tool=${encodeURIComponent(tool)}` : ''
  return request(`/activity/clear${suffix}`, { method: 'DELETE' })
}

// ── Favorites ─────────────────────────────────────────────────────────────────

/**
 * Fetch the current user's favorite tools in display order.
 * Response: { favorites: [{ id, toolPath, displayOrder, createdAt }...], max: 20 }
 */
export const fetchFavorites = () =>
  request('/favorites/list')

/**
 * Add a tool path to the current user's favorites.
 * Server returns 409 FAVORITES_FULL if at cap.
 *
 * @param {string} toolPath  A value from FavoritesDAO.VALID_TOOL_PATHS
 *                           ('/calculator', '/dev-utils', ...).
 */
export const toggleFavorite = (toolPath) =>
  request('/favorites/toggle', {
    method: 'POST',
    isJson: true,
    body: { toolPath },
  })

/**
 * Remove a tool path from the current user's favorites.
 * Mirror of toggleFavorite; kept separate so callers can be explicit about
 * intent (add-if-absent vs always-remove).
 */
export const removeFavorite = (toolPath) =>
  request(`/favorites/toggle?toolPath=${encodeURIComponent(toolPath)}`, {
    method: 'DELETE',
  })

/**
 * Replace the display order of the user's favorites with the given list.
 *
 * @param {string[]} orderedPaths  Full list of favorited tool paths in the
 *                                 desired new order.
 */
export const reorderFavorites = (orderedPaths) =>
  request('/favorites/reorder', {
    method: 'PUT',
    isJson: true,
    body: { orderedPaths },
  })
