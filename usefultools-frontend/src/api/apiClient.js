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