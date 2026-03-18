/**
 * apiClient.js — the single place where all HTTP calls to the backend are made.
 *
 * WHY A WRAPPER:
 * Every call to the backend needs the same set of things:
 *   1. The /api base path.
 *   2. credentials: 'include' — this tells the browser to attach the
 *      JSESSIONID session cookie on every request. Without this, AuthFilter
 *      always sees an unauthenticated request and returns 401.
 *   3. Consistent JSON parsing of the ApiResponse envelope.
 *
 * Centralising this means you change it in one place if anything ever shifts.
 *
 * RETURN VALUE:
 * Every exported function returns a Promise that resolves to { status, data }:
 *   status  — the HTTP status code (200, 201, 400, 401, 409, 500, …)
 *   data    — the parsed ApiResponse JSON from the backend:
 *             { success: bool, data: any, error: string, errorCode: string }
 *
 * In your components, the pattern is always:
 *   const { data } = await loginUser(username, password);
 *   if (data.success) { ... use data.data ... }
 *   else              { ... show error message from data.errorCode ... }
 *
 * WHY FORM-ENCODED vs JSON:
 * The auth and password controllers use request.getParameter() — they expect
 * application/x-www-form-urlencoded (the classic HTML form format).
 * The calculator controllers use request.getReader() — they expect a JSON body.
 * Pass isForm: true for the former, isJson: true for the latter.
 */

const BASE = 'http://localhost:8080/UsefulTools/api'

/**
 * Internal core wrapper. Not exported — call the named functions below.
 *
 * @param {string} path      - API path relative to /api, e.g. '/auth/login'
 * @param {object} options
 *   @param {string}  method  - HTTP method, default 'GET'
 *   @param {object}  body    - request payload (key/value pairs)
 *   @param {boolean} isForm  - encode body as application/x-www-form-urlencoded
 *   @param {boolean} isJson  - encode body as application/json
 */
async function request(path, { method = 'GET', body, isForm = false, isJson = false } = {}) {
  const headers = {}
  let encodedBody

  if (isForm && body) {
    headers['Content-Type'] = 'application/x-www-form-urlencoded'
    // URLSearchParams builds the "username=alice&password=secret" format
    encodedBody = new URLSearchParams(body).toString()
  } else if (isJson && body) {
    headers['Content-Type'] = 'application/json'
    encodedBody = JSON.stringify(body)
  }

  const response = await fetch(`${BASE}${path}`, {
    method,
    credentials: 'include',  // CRITICAL: send JSESSIONID cookie on every request
    headers,
    body: encodedBody,
  })

  const data = await response.json()
  return { status: response.status, data }
}

// ── Authentication ────────────────────────────────────────────────────────────
// These all use isForm: true because the controllers call request.getParameter()

export const loginUser = (username, password) =>
  request('/auth/login', {
    method: 'POST',
    isForm: true,
    body: { username, password },
  })

export const registerUser = (username, password) =>
  request('/auth/register', {
    method: 'POST',
    isForm: true,
    body: { username, password },
  })

export const updatePassword = (username, updatedPassword) =>
  request('/auth/update-password', {
    method: 'POST',
    isForm: true,
    // The backend reads the parameter named "updated_password" exactly
    body: { username, updated_password: updatedPassword },
  })

export const logoutUser = () =>
  request('/auth/logout', { method: 'POST' })
