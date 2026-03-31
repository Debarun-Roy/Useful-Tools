import { useState }          from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { registerUser }      from '../../api/apiClient'
import AuthLayout            from '../../components/AuthLayout/AuthLayout'
import styles                from './RegisterPage.module.css'

const ERROR_MESSAGES = {
  MISSING_CREDENTIALS: 'Please enter both username and password.',
  INVALID_USERNAME:    'Username must be at least 3 characters long.',
  PASSWORD_TOO_SHORT:  'Password must be at least 8 characters long.',
  USERNAME_TAKEN:      'That username is already taken. Please choose another.',
  // Sprint 6: rate limiting
  RATE_LIMITED:        'Too many registration attempts. Please wait a moment before trying again.',
  INTERNAL_ERROR:      'Something went wrong on our end. Please try again.',
}

function getErrorMessage(errorCode) {
  return ERROR_MESSAGES[errorCode] ?? 'An unexpected error occurred.'
}

export default function RegisterPage() {

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error,    setError]    = useState('')
  const [success,  setSuccess]  = useState(false)
  const [loading,  setLoading]  = useState(false)

  const navigate = useNavigate()

  async function handleSubmit(e) {
    e.preventDefault()

    if (!username.trim() || !password) {
      setError('Please enter both username and password.')
      return
    }

    setLoading(true)
    setError('')

    try {
      const { data } = await registerUser(username.trim(), password)

      if (data.success) {
        setSuccess(true)
        setTimeout(() => navigate('/login'), 2000)
      } else {
        setError(getErrorMessage(data.errorCode))
      }
    } catch {
      setError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setLoading(false)
    }
  }

  if (success) {
    return (
      <AuthLayout title="Account created">
        <div className={styles.success}>
          ✓ Registration successful. Redirecting to sign in…
        </div>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Create account">

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
            placeholder="At least 3 characters"
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
            autoComplete="new-password"
            disabled={loading}
            placeholder="At least 8 characters"
          />
        </div>

        <button
          type="submit"
          className={styles.button}
          disabled={loading}
        >
          {loading ? 'Creating account…' : 'Create account'}
        </button>

      </form>

      <p className={styles.footer}>
        Already have an account?{' '}
        <Link to="/login">Sign in</Link>
      </p>

    </AuthLayout>
  )
}