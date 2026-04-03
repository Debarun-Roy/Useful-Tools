import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { registerUnauthorizedHandler } from '../api/apiClient'
import { AuthContext } from './AuthContextStore'
const AUTH_NOTICE_KEY = 'usefultools.auth_notice'

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
    else sessionStorage.removeItem(AUTH_NOTICE_KEY)
  }

  function clearAuthNotice() {
    setNotice('')
  }

  useEffect(() => (
    registerUnauthorizedHandler(({ data }) => {
      const message = data?.errorCode === 'UNAUTHENTICATED'
        ? 'Your session expired. Please sign in again.'
        : (data?.error || 'Your session expired. Please sign in again.')

      setUsername(null)
      localStorage.removeItem('username')
      setNotice(message)
      navigate('/login', { replace: true })
    })
  ), [navigate])

  function login(name) {
    setUsername(name)
    localStorage.setItem('username', name)
    clearAuthNotice()
  }

  function logout() {
    setUsername(null)
    localStorage.removeItem('username')
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
    }}
    >
      {children}
    </AuthContext.Provider>
  )
}
