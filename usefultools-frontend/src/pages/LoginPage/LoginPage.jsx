import { useState }            from 'react'
import { useNavigate, Link }   from 'react-router-dom'
import { loginUser }           from '../../api/apiClient'
import { useAuth }             from '../../auth/AuthContext'
import AuthLayout              from '../../components/AuthLayout/AuthLayout'
import styles                  from './LoginPage.module.css'

const ERROR_MESSAGES = {
  MISSING_CREDENTIALS: 'Please enter both username and password.',
  USER_NOT_FOUND:      'No account found for that username. Try registering.',
  INVALID_CREDENTIALS: 'Incorrect password. Please try again.',
  // Sprint 6: account lockout and rate limiting
  ACCOUNT_LOCKED:      null, // Use the server message directly (contains time remaining).
  RATE_LIMITED:        'Too many login attempts. Please wait a moment before trying again.',
  INTERNAL_ERROR:      'Something went wrong on our end. Please try again.',
}

function getErrorMessage(errorCode, serverMessage) {
  if (errorCode === 'ACCOUNT_LOCKED') {
    // The server message already contains the time remaining — use it directly.
    return serverMessage || 'Account temporarily locked. Please try again later.'
  }
  return ERROR_MESSAGES[errorCode] ?? 'An unexpected error occurred.'
}

export default function LoginPage() {

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error,    setError]    = useState('')
  const [loading,  setLoading]  = useState(false)

  const { login } = useAuth()
  const navigate  = useNavigate()

  async function handleSubmit(e) {
    e.preventDefault()

    if (!username.trim() || !password) {
      setError('Please enter both username and password.')
      return
    }

    setLoading(true)
    setError('')

    try {
      const { data } = await loginUser(username.trim(), password)

      if (data.success) {
        login(data.data.username)
        navigate('/dashboard')
      } else {
        setError(getErrorMessage(data.errorCode, data.error))
      }
    } catch {
      setError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout title="Sign in">

      <form onSubmit={handleSubmit} noValidate>

        {error && (
          <div className={styles.error} role="alert">
            {error}
          </div>
        )}

        <div className={styles.field}>
          <label htmlFor="username" className={styles.label}>Username</label>
          <input
            id="username"
            type="text"
            className={styles.input}
            value={username}
            onChange={e => setUsername(e.target.value)}
            autoComplete="username"
            autoFocus
            disabled={loading}
            placeholder="Enter your username"
          />
        </div>

        <div className={styles.field}>
          <label htmlFor="password" className={styles.label}>Password</label>
          <input
            id="password"
            type="password"
            className={styles.input}
            value={password}
            onChange={e => setPassword(e.target.value)}
            autoComplete="current-password"
            disabled={loading}
            placeholder="Enter your password"
          />
        </div>

        <button
          type="submit"
          className={styles.button}
          disabled={loading}
        >
          {loading ? 'Signing in…' : 'Sign in'}
        </button>

      </form>

      <p className={styles.footer}>
        Don't have an account?{' '}
        <Link to="/register">Create one</Link>
      </p>

    </AuthLayout>
  )
}