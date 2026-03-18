import { useState }            from 'react'
import { useNavigate, Link }   from 'react-router-dom'
import { loginUser }           from '../../api/apiClient'
import { useAuth }             from '../../auth/AuthContext'
import AuthLayout              from '../../components/AuthLayout/AuthLayout'
import styles                  from './LoginPage.module.css'

/**
 * Maps backend errorCode strings to user-facing messages.
 *
 * WHY A MAP INSTEAD OF A SWITCH:
 * An object lookup is cleaner than a switch for this pattern. Adding a new
 * error code means adding one line here — nothing else changes.
 * The fallback at the bottom of getErrorMessage() handles any unknown code.
 */
const ERROR_MESSAGES = {
  MISSING_CREDENTIALS: 'Please enter both username and password.',
  USER_NOT_FOUND:      'No account found for that username. Try registering.',
  INVALID_CREDENTIALS: 'Incorrect password. Please try again.',
  INTERNAL_ERROR:      'Something went wrong on our end. Please try again.',
}

function getErrorMessage(errorCode) {
  return ERROR_MESSAGES[errorCode] ?? 'An unexpected error occurred.'
}

export default function LoginPage() {

  // ── Component state ────────────────────────────────────────────────────────
  //
  // useState(initialValue) returns [currentValue, setterFunction].
  // Calling the setter triggers a re-render with the new value.
  //
  // We use four separate state variables rather than one object because
  // each has a different type and lifecycle:
  //   username / password — controlled input values (update on every keystroke)
  //   error               — a string shown to the user, cleared on new submit
  //   loading             — boolean that disables the form during the API call

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error,    setError]    = useState('')
  const [loading,  setLoading]  = useState(false)

  // ── Hooks ──────────────────────────────────────────────────────────────────
  const { login } = useAuth()        // stores the username in AuthContext on success
  const navigate  = useNavigate()    // programmatic navigation (like a redirect)

  // ── Submit handler ─────────────────────────────────────────────────────────
  async function handleSubmit(e) {
    // Prevent the browser's default behaviour of reloading the page on submit.
    // Without this, the entire React app would be destroyed on every form submit.
    e.preventDefault()

    // Client-side validation — avoid a pointless network call if fields are empty
    if (!username.trim() || !password) {
      setError('Please enter both username and password.')
      return
    }

    setLoading(true)
    setError('')    // clear any previous error before the new attempt

    try {
      const { data } = await loginUser(username.trim(), password)

      if (data.success) {
        // data.data is the { username, message } object returned by LoginController
        login(data.data.username)   // save to AuthContext + localStorage
        navigate('/dashboard')      // go to the main app
      } else {
        setError(getErrorMessage(data.errorCode))
      }
    } catch {
      // This catch block only fires for network errors (Tomcat not running,
      // no internet, etc.) — not for 4xx/5xx responses, which are handled above.
      setError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      // finally always runs, whether the try succeeded or threw.
      // This guarantees the loading spinner is always removed.
      setLoading(false)
    }
  }

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <AuthLayout title="Sign in">

      {/*
        noValidate: disables the browser's built-in validation popups.
        We handle validation ourselves for a better, consistent UX.
      */}
      <form onSubmit={handleSubmit} noValidate>

        {/* Error banner — only mounted in the DOM when there is an error */}
        {error && (
          <div className={styles.error} role="alert">
            {error}
          </div>
        )}

        <div className={styles.field}>
          <label htmlFor="username" className={styles.label}>
            Username
          </label>
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
          <label htmlFor="password" className={styles.label}>
            Password
          </label>
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
