import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  registerUnauthorizedHandler,
  setCsrfToken,
  clearCsrfToken,
  fetchCsrfToken,
  validateSession,
} from '../api/apiClient'
import { AuthContext } from './AuthContextStore'

const AUTH_NOTICE_KEY  = 'usefultools.auth_notice'
const CSRF_STORAGE_KEY = '_ut_xsrf'

export function AuthProvider({ children }) {
  const navigate = useNavigate()
  const [username, setUsername] = useState(
    () => localStorage.getItem('username') || null
  )
  const [authNotice, setAuthNotice] = useState(
    () => sessionStorage.getItem(AUTH_NOTICE_KEY) || ''
  )

  function setNotice(message) {
    setAuthNotice(message)
    if (message) sessionStorage.setItem(AUTH_NOTICE_KEY, message)
    else         sessionStorage.removeItem(AUTH_NOTICE_KEY)
  }

  function clearAuthNotice() {
    setNotice('')
  }

  // ── Register the 401 → redirect-to-login handler ──────────────────────────
  useEffect(() => (
    registerUnauthorizedHandler(({ data }) => {
      const message = data?.errorCode === 'UNAUTHENTICATED'
        ? 'Your session expired. Please sign in again.'
        : (data?.error || 'Your session expired. Please sign in again.')

      setUsername(null)
      localStorage.removeItem('username')
      clearCsrfToken()
      setNotice(message)
      navigate('/login', { replace: true })
    })
  ), [navigate])

  // ── Restore CSRF token on page load / reload ──────────────────────────────
  // When the user is "logged in" (username persists in localStorage) but the
  // SPA has been hard-refreshed, the in-memory _csrfToken is lost.
  //
  // Strategy:
  //   1. Check sessionStorage first — survives same-tab page refreshes.
  //   2. If sessionStorage is empty, validate session with backend first.
  //   3. If session is valid, fetch the CSRF token.  The server session
  //      (JSESSIONID) is still valid (now that the session cookie has
  //      SameSite=None and 60-minute timeout is configured), so
  //      GET /api/auth/session-status confirms it, and
  //      GET /api/auth/csrf-token returns it.
  //   4. If the backend returns 401 (session truly expired), the
  //      unauthorized handler above fires and redirects to login.
  useEffect(() => {
    const storedUsername = localStorage.getItem('username')
    if (!storedUsername) return // not logged in — nothing to restore

    // Tier 1: sessionStorage (fast, no network)
    try {
      const stored = sessionStorage.getItem(CSRF_STORAGE_KEY)
      if (stored) {
        setCsrfToken(stored)
        return
      }
    } catch { /* storage blocked */ }

    // Tier 2: Validate session with backend (detects JSESSIONID transmission issues)
    validateSession()
      .then(({ status, data }) => {
        if (status === 401) {
          // Session is expired/invalid - unauthorized handler will redirect
          return
        }
        
        if (data?.success && data.data?.authenticated) {
          // Session is valid, now fetch the CSRF token
          return fetchCsrfToken()
        }
      })
      .then(({ data } = {}) => {
        // Handle the result from fetchCsrfToken() if it was called
        if (data?.success && data.data?.csrfToken) {
          setCsrfToken(data.data.csrfToken)
        }
        // If 401: the unauthorized handler above will redirect to login.
        // If 404 (pre-Sprint-6 session): the user will get a CSRF error on
        //   the next POST and should log in again — acceptable edge case.
      })
      .catch(() => {
        // Network error — user will see API errors on next action.
        // But don't block the app - the user might have a valid session.
      })
  }, []) // intentionally empty — run once on mount only

  // ── Login / logout ────────────────────────────────────────────────────────

  /**
   * @param {string} name     The authenticated username.
   * @param {string} [token]  The CSRF token from the login response body.
   *                          Provided in cross-origin deployments where the
   *                          XSRF-TOKEN cookie cannot be read by document.cookie.
   */
  function login(name, token) {
    setUsername(name)
    localStorage.setItem('username', name)
    if (token) setCsrfToken(token)
    clearAuthNotice()
  }

  function logout() {
    setUsername(null)
    localStorage.removeItem('username')
    clearCsrfToken()
    clearAuthNotice()
  }

  return (
    <AuthContext.Provider value={{
      username,
      isLoggedIn: username !== null,
      authNotice,
      clearAuthNotice,
      login,
      logout,
    }}>
      {children}
    </AuthContext.Provider>
  )
}
