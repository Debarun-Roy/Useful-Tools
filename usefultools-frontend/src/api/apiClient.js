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

/**
 * apiClient.js — unified HTTP client for UsefulTools frontend
 *
 * Includes:
 *   Sprint 15 → activity log + favorites
 *   Sprint 17 → admin APIs + tool toggles + headers inspector
 *
 * Maintains:
 *   cross-origin CSRF compatibility
 *   sessionStorage persistence
 *   unauthorized handler support
 */

import { resolveApiBase } from './apiBase'

const BASE = resolveApiBase(import.meta.env.VITE_API_BASE)

let unauthorizedHandler = null
let unauthorizedHandled = false


// ─────────────────────────────────────────────────────────────
// CSRF TOKEN STATE
// ─────────────────────────────────────────────────────────────

let _csrfToken = ''
const CSRF_STORAGE_KEY = '_ut_xsrf'

export function setCsrfToken(token) {
  _csrfToken = token || ''
  if (_csrfToken) {
    try {
      sessionStorage.setItem(CSRF_STORAGE_KEY, _csrfToken)
    } catch {}
  }
}

export function clearCsrfToken() {
  _csrfToken = ''
  try {
    sessionStorage.removeItem(CSRF_STORAGE_KEY)
  } catch {}
}

function getCsrfToken() {
  if (_csrfToken) return _csrfToken

  try {
    const stored = sessionStorage.getItem(CSRF_STORAGE_KEY)
    if (stored) {
      _csrfToken = stored
      return stored
    }
  } catch {}

  try {
    const match = document.cookie
      .split('; ')
      .find(row => row.startsWith('XSRF-TOKEN='))

    if (match) return decodeURIComponent(match.split('=')[1])
  } catch {}

  return ''
}


// ─────────────────────────────────────────────────────────────
// UNAUTHORIZED HANDLER
// ─────────────────────────────────────────────────────────────

export function registerUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
  return () => {
    if (unauthorizedHandler === handler) {
      unauthorizedHandler = null
      unauthorizedHandled = false
    }
  }
}


// ─────────────────────────────────────────────────────────────
// CORE REQUEST HELPER
// ─────────────────────────────────────────────────────────────

async function parseResponseBody(response) {
  const raw = await response.text()
  if (!raw) return {}

  try {
    return JSON.parse(raw)
  } catch {
    return { success: false, error: raw }
  }
}

export async function request(
  path,
  { method = 'GET', body = null, isForm = false, isJson = false } = {}
) {

  const headers = {}

  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = getCsrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  let encodedBody

  if (isForm && body) {
    headers['Content-Type'] =
      'application/x-www-form-urlencoded'

    encodedBody =
      body instanceof URLSearchParams
        ? body.toString()
        : new URLSearchParams(body).toString()
  }

  else if (isJson && body) {
    headers['Content-Type'] = 'application/json'
    encodedBody = JSON.stringify(body)
  }

  const response = await fetch(`${BASE}${path}`, {
    method,
    credentials: 'include',
    headers,
    body: encodedBody
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
    unauthorizedHandler({ path, status: 401, data })
  }
  else if (response.status !== 401) {
    unauthorizedHandled = false
  }

  return { status: response.status, data }
}


// ─────────────────────────────────────────────────────────────
// AUTHENTICATION
// ─────────────────────────────────────────────────────────────

export const loginUser =
  (username, password) =>
    request('/auth/login', {
      method: 'POST',
      isForm: true,
      body: { username, password }
    })

export const loginAsGuest =
  () => request('/auth/login-guest', { method: 'POST' })

export const registerUser =
  (username, password) =>
    request('/auth/register', {
      method: 'POST',
      isForm: true,
      body: { username, password }
    })

export const updatePassword =
  (username, updatedPassword) =>
    request('/auth/update-password', {
      method: 'POST',
      isForm: true,
      body: {
        username,
        updated_password: updatedPassword
      }
    })

export const logoutUser =
  () => request('/auth/logout', { method: 'POST' })

export const fetchCsrfToken =
  () => request('/auth/csrf-token')

export const validateSession =
  () => request('/auth/session-status')


// ─────────────────────────────────────────────────────────────
// USER PROFILE
// ─────────────────────────────────────────────────────────────

export const fetchUserProfile =
  () => request('/user/profile')


// ─────────────────────────────────────────────────────────────
// CALCULATORS
// ─────────────────────────────────────────────────────────────

function evaluateStandard(path, expression) {
  return request(path, {
    method: 'POST',
    isJson: true,
    body: { expression }
  })
}

export const evaluateSimple =
  expr => evaluateStandard('/calculator/simple', expr)

export const evaluateIntermediate =
  expr => evaluateStandard('/calculator/intermediate', expr)

export const evaluateBoolean =
  expr => evaluateStandard('/calculator/boolean', expr)

export const evaluateTrig =
  expr => evaluateStandard('/calculator/trig', expr)

export const evaluateCombined =
  expr => evaluateStandard('/calculator/combined', expr)

export const evaluateComplex =
  operation =>
    request('/calculator/complex', {
      method: 'POST',
      isJson: true,
      body: { operation }
    })


// ─────────────────────────────────────────────────────────────
// MATRIX / STATS / EQUATION / POLYNOMIAL / PROBABILITY
// ─────────────────────────────────────────────────────────────

export const calculateMatrix =
  (operation, size, matrix1, matrix2 = null) =>
    request('/calculator/matrix', {
      method: 'POST',
      isJson: true,
      body: {
        operation,
        size,
        matrix1,
        ...(matrix2 ? { matrix2 } : {})
      }
    })

export const calculateStats =
  data =>
    request('/calculator/stats', {
      method: 'POST',
      isJson: true,
      body: { data }
    })

export const solveEquation =
  equation =>
    request('/calculator/solve', {
      method: 'POST',
      isJson: true,
      body: { equation }
    })

export const calculateProbability =
  (distribution, params) =>
    request('/calculator/probability', {
      method: 'POST',
      isJson: true,
      body: { distribution, params }
    })

export const calculatePolynomial =
  (operation, coefficients, x = null) =>
    request('/calculator/polynomial', {
      method: 'POST',
      isJson: true,
      body: {
        operation,
        coefficients,
        ...(x !== null ? { x } : {})
      }
    })


// ─────────────────────────────────────────────────────────────
// ACTIVITY LOG
// ─────────────────────────────────────────────────────────────

export const logActivity =
  (summary, toolName, payload) =>
    request('/activity/log', {
      method: 'POST',
      isJson: true,
      body: { toolName, summary, payload }
    })

export const fetchActivityList =
  ({ tool, limit = 10, offset = 0 } = {}) => {

    const params = new URLSearchParams()

    if (tool) params.append('tool', tool)

    params.append('limit', limit)
    params.append('offset', offset)

    return request(`/activity/list?${params}`)
  }

export const clearActivity =
  tool =>
    request(
      `/activity/clear${tool ? `?tool=${tool}` : ''}`,
      { method: 'DELETE' }
    )


// ─────────────────────────────────────────────────────────────
// FAVORITES
// ─────────────────────────────────────────────────────────────

export const fetchFavorites =
  () => request('/favorites/list')

export const toggleFavorite =
  toolPath =>
    request('/favorites/toggle', {
      method: 'POST',
      isJson: true,
      body: { toolPath }
    })

export const removeFavorite =
  toolPath =>
    request('/favorites/toggle', {
      method: 'DELETE',
      isJson: true,
      body: { toolPath }
    })

export const reorderFavorites =
  orderedPaths =>
    request('/favorites/reorder', {
      method: 'PUT',
      isJson: true,
      body: { orderedPaths }
    })


// ─────────────────────────────────────────────────────────────
// ADMIN APIs (SPRINT 17)
// ─────────────────────────────────────────────────────────────

export const fetchToolStatus =
  () => request('/tools/status')

export const fetchAdminUsers =
  () => request('/admin/users')

export const updateUserRole =
  (username, role) =>
    request('/admin/users', {
      method: 'PUT',
      isJson: true,
      body: { username, role }
    })

export const deleteAdminUser =
  username =>
    request('/admin/users', {
      method: 'DELETE',
      isJson: true,
      body: { username }
    })

export const fetchAdminToolToggles =
  () => request('/admin/tool-toggles')

export const updateToolToggle =
  (toolPath, enabled) =>
    request('/admin/tool-toggles', {
      method: 'PUT',
      isJson: true,
      body: { toolPath, enabled }
    })


// ─────────────────────────────────────────────────────────────
// WEB DEV TOOL
// ─────────────────────────────────────────────────────────────

export const fetchRequestHeaders =
  () => request('/webdev/request-headers')


// ─────────────────────────────────────────────────────────────
// FEEDBACK
// ─────────────────────────────────────────────────────────────

export const submitFeedback =
  feedbackData =>
    request('/feedback/submit', {
      method: 'POST',
      isJson: true,
      body: feedbackData
    })
    