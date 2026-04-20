import { useEffect, useState } from 'react'
import { AuthContext } from './AuthContextStore'
import { setCsrfToken, clearCsrfToken } from '../api/apiClient'
import { fetchCsrfToken, validateSession } from '../api/apiClient'

const CSRF_STORAGE_KEY = '_ut_xsrf'
const ROLE_STORAGE_KEY = '_ut_role'

export function AuthProvider({ children }) {
  const [username, setUsername] = useState(() => localStorage.getItem('username') || null)
  const [role,     setRole]     = useState(() => localStorage.getItem(ROLE_STORAGE_KEY) || null)
  const [authNotice, setAuthNotice] = useState(null)

  // ── Unauthorized handler ─────────────────────────────────────────────────
  useEffect(() => {
    const handler = ({ path }) => {
      setAuthNotice(
        path?.includes('/auth/')
          ? null
          : 'Your session has expired. Please sign in again.'
      )
      setUsername(null)
      setRole(null)
      localStorage.removeItem('username')
      localStorage.removeItem(ROLE_STORAGE_KEY)
      clearCsrfToken()
    }
    import('../api/apiClient').then(({ setUnauthorizedHandler }) => {
      setUnauthorizedHandler(handler)
    })
    return () => {
      import('../api/apiClient').then(({ setUnauthorizedHandler }) => {
        setUnauthorizedHandler(null)
      })
    }
  }, [])

  // ── Restore CSRF token on page reload ───────────────────────────────────
  useEffect(() => {
    const storedUsername = localStorage.getItem('username')
    if (!storedUsername) return

    try {
      const stored = sessionStorage.getItem(CSRF_STORAGE_KEY)
      if (stored) {
        setCsrfToken(stored)
        return
      }
    } catch { /* storage blocked */ }

    validateSession()
      .then(({ status, data }) => {
        if (status === 401) return
        if (data?.success && data.data?.authenticated) {
          return fetchCsrfToken()
        }
      })
      .then(({ data } = {}) => {
        if (data?.success && data.data?.csrfToken) {
          setCsrfToken(data.data.csrfToken)
        }
      })
      .catch(() => { /* network error — tolerated */ })
  }, [])

  // ── Login / logout ────────────────────────────────────────────────────────

  /**
   * @param {string} name   The authenticated username.
   * @param {string} token  The CSRF token from the login response body.
   * @param {string} userRole  The user's role from the login response body.
   *                           Defaults to 'user' if not provided (backward-compat).
   */
  function login(name, token, userRole) {
    const resolvedRole = userRole || 'user'
    setUsername(name)
    setRole(resolvedRole)
    localStorage.setItem('username', name)
    localStorage.setItem(ROLE_STORAGE_KEY, resolvedRole)
    if (token) setCsrfToken(token)
    clearAuthNotice()
  }

  function logout() {
    setUsername(null)
    setRole(null)
    localStorage.removeItem('username')
    localStorage.removeItem(ROLE_STORAGE_KEY)
    clearCsrfToken()
    clearAuthNotice()
  }

  function clearAuthNotice() {
    setAuthNotice(null)
  }

  return (
    <AuthContext.Provider value={{
      username,
      role,
      isLoggedIn: username !== null,
      isAdmin:    role === 'admin',
      isGuest:    username === 'Guest User',
      authNotice,
      clearAuthNotice,
      login,
      logout,
    }}>
      {children}
    </AuthContext.Provider>
  )
}
