import { createContext, useContext, useState } from 'react'

/**
 * AuthContext.jsx — global authentication state for the entire app.
 *
 * WHAT IT PROVIDES to any component that calls useAuth():
 *   username    — the logged-in username string, or null when not logged in
 *   isLoggedIn  — boolean shortcut: username !== null
 *   login(name) — call this after a successful /api/auth/login response
 *   logout()    — call this after a successful /api/auth/logout response
 *
 * HOW CONTEXT WORKS:
 * React's Context is a way to share data across the entire component tree
 * without passing it down as props through every level.
 *
 *   AuthProvider wraps all your routes in App.jsx.
 *   Any component inside — no matter how deeply nested — can call useAuth()
 *   and immediately get the current username and the login/logout functions.
 *
 * WHY localStorage:
 * React state (useState) is lost on page refresh. Without persistence, the
 * user would be logged out of the React UI every time they refresh, even
 * though their Tomcat session is still valid.
 *
 * Storing the username in localStorage means React knows who is logged in
 * across refreshes. The actual security lives on the server: AuthFilter
 * validates the JSESSIONID cookie on every /api/* request regardless of
 * what React thinks. If the Tomcat session expires, the next API call
 * returns 401 — we handle that in a later sprint by catching 401 responses
 * globally and redirecting to /login.
 *
 * localStorage stores only the username string — never a password, never
 * a token, never anything sensitive.
 */

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  // Read the saved username when the app first loads (e.g. after a refresh).
  // The arrow function form of useState is a "lazy initialiser" — it only
  // runs once on mount, not on every re-render.
  const [username, setUsername] = useState(
    () => localStorage.getItem('username') || null
  )

  function login(name) {
    setUsername(name)
    localStorage.setItem('username', name)
  }

  function logout() {
    setUsername(null)
    localStorage.removeItem('username')
  }

  // The value object is what every useAuth() call receives.
  const value = {
    username,
    isLoggedIn: username !== null,
    login,
    logout,
  }

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

/**
 * useAuth — the hook that gives any component access to auth state.
 *
 * Usage in any component:
 *   import { useAuth } from '../../auth/AuthContext'
 *   const { username, isLoggedIn, login, logout } = useAuth()
 *
 * The error guard ensures a clear message if someone accidentally uses
 * this hook outside of AuthProvider.
 */
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be called inside <AuthProvider>')
  return ctx
}
