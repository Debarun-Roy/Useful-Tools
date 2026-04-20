/**
 * apiClient.js — single place for all HTTP calls to the backend.
 *
 * Sprint 17 additions (bottom of file):
 *   fetchToolStatus          → GET /api/tools/status  (all auth users)
 *   fetchAdminUsers          → GET /api/admin/users
 *   updateUserRole           → PUT /api/admin/users
 *   deleteAdminUser          → DELETE /api/admin/users
 *   fetchAdminToolToggles    → GET /api/admin/tool-toggles
 *   updateToolToggle         → PUT /api/admin/tool-toggles
 *   fetchRequestHeaders      → GET /api/webdev/request-headers
 */

import { resolveApiBase } from './apiBase'

const BASE = resolveApiBase(import.meta.env.VITE_API_BASE)
let unauthorizedHandler = null
let unauthorizedHandled = false

// ── CSRF token state ──────────────────────────────────────────────────────────

let _csrfToken = ''
const CSRF_STORAGE_KEY = '_ut_xsrf'

export function setCsrfToken(token) {
  _csrfToken = token || ''
  if (_csrfToken) {
    try { sessionStorage.setItem(CSRF_STORAGE_KEY, _csrfToken) } catch { /* storage blocked */ }
  }
}

export function clearCsrfToken() {
  _csrfToken = ''
  try { sessionStorage.removeItem(CSRF_STORAGE_KEY) } catch { /* storage blocked */ }
}

function getCsrfToken() {
  if (_csrfToken) return _csrfToken
  try {
    const stored = sessionStorage.getItem(CSRF_STORAGE_KEY)
    if (stored) { _csrfToken = stored; return stored }
  } catch { /* storage blocked */ }
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : ''
}

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
  unauthorizedHandled = false
}

// ── Core request helper ───────────────────────────────────────────────────────

async function parseResponseBody(response) {
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    try { return await response.json() } catch { return null }
  }
  return null
}

export async function request(path, {
  method   = 'GET',
  body     = null,
  isForm   = false,
  isJson   = false,
} = {}) {
  const headers = {}

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

export const fetchCsrfToken = () =>
  request('/auth/csrf-token')

export const validateSession = () =>
  request('/auth/session-status')

// ── User profile ──────────────────────────────────────────────────────────────

export const fetchUserProfile = () =>
  request('/user/profile')

// ── Calculator ────────────────────────────────────────────────────────────────

export const evaluateSimple       = (expr)       => request('/calc/evaluate',        { method: 'POST', isForm: true, body: { expression: expr } })
export const evaluateIntermediate = (expr)       => request('/calc/intermediate',    { method: 'POST', isForm: true, body: { expression: expr } })
export const evaluateBoolean      = (expr)       => request('/calc/boolean',         { method: 'POST', isForm: true, body: { expression: expr } })
export const evaluateTrig         = (expr)       => request('/calc/trig',            { method: 'POST', isForm: true, body: { expression: expr } })
export const evaluateCombined     = (expr)       => request('/calc/combined',        { method: 'POST', isForm: true, body: { expression: expr } })

// ── Feedback ──────────────────────────────────────────────────────────────────

export const submitFeedback = (feedbackData) =>
  request('/feedback/submit', { method: 'POST', isJson: true, body: feedbackData })

// ── Activity log ──────────────────────────────────────────────────────────────

export const logActivity = (summary, toolName, payload) =>
  request('/activity/log', {
    method: 'POST',
    isJson: true,
    body: { toolName, summary, payload },
  })

export const fetchActivityList = ({ tool, limit = 10, offset = 0 } = {}) => {
  const params = new URLSearchParams()
  if (tool) params.append('tool', tool)
  params.append('limit',  String(limit))
  params.append('offset', String(offset))
  return request(`/activity/list?${params.toString()}`)
}

export const clearActivity = (tool) => {
  const suffix = tool ? `?tool=${encodeURIComponent(tool)}` : ''
  return request(`/activity/clear${suffix}`, { method: 'DELETE' })
}

// ── Favorites ─────────────────────────────────────────────────────────────────

export const fetchFavorites = () =>
  request('/favorites/list')

export const toggleFavorite = (toolPath) =>
  request('/favorites/toggle', { method: 'POST', isJson: true, body: { toolPath } })

export const removeFavorite = (toolPath) =>
  request('/favorites/remove', { method: 'DELETE', isJson: true, body: { toolPath } })

export const reorderFavorites = (orderedPaths) =>
  request('/favorites/reorder', { method: 'PUT', isJson: true, body: { paths: orderedPaths } })

// ═══════════════════════════════════════════════════════════════════════════════
// ── Sprint 17 additions ────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════════

// ── Tool status (read-only, all auth users) ───────────────────────────────────

/**
 * Returns the enabled/disabled state of all tools.
 * Used by the Dashboard to grey-out disabled tools for regular users.
 *
 * GET /api/tools/status
 * Response: { "success": true, "data": { "toggles": { "/calculator": true, ... } } }
 */
export const fetchToolStatus = () =>
  request('/tools/status')

// ── Admin — user management ───────────────────────────────────────────────────

/**
 * Lists all registered users with username, role, and createdDate.
 * Requires role=admin.
 *
 * GET /api/admin/users
 */
export const fetchAdminUsers = () =>
  request('/admin/users')

/**
 * Changes a user's role. Cannot demote self or last admin.
 * Requires role=admin.
 *
 * PUT /api/admin/users
 * Body: { username, role: "admin" | "user" }
 */
export const updateUserRole = (username, role) =>
  request('/admin/users', {
    method: 'PUT',
    isJson: true,
    body: { username, role },
  })

/**
 * Permanently deletes a user and all their data.
 * Cannot delete self or last admin.
 * Requires role=admin.
 *
 * DELETE /api/admin/users
 * Body: { username }
 */
export const deleteAdminUser = (username) =>
  request('/admin/users', {
    method: 'DELETE',
    isJson: true,
    body: { username },
  })

// ── Admin — tool toggles ──────────────────────────────────────────────────────

/**
 * Returns all tool toggle states (admin read).
 * Requires role=admin.
 *
 * GET /api/admin/tool-toggles
 */
export const fetchAdminToolToggles = () =>
  request('/admin/tool-toggles')

/**
 * Enables or disables a tool globally.
 * Requires role=admin.
 *
 * PUT /api/admin/tool-toggles
 * Body: { toolPath: "/calculator", enabled: false }
 */
export const updateToolToggle = (toolPath, enabled) =>
  request('/admin/tool-toggles', {
    method: 'PUT',
    isJson: true,
    body: { toolPath, enabled },
  })

// ── Web Dev — request headers ─────────────────────────────────────────────────

/**
 * Returns the HTTP request headers sent by the browser (all auth users).
 * Cookie and X-XSRF-TOKEN are redacted server-side.
 *
 * GET /api/webdev/request-headers
 */
export const fetchRequestHeaders = () =>
  request('/webdev/request-headers')
