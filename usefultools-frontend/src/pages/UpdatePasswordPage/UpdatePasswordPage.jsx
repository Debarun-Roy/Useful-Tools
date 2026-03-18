import { useState }        from 'react'
import { useNavigate }     from 'react-router-dom'
import { updatePassword, logoutUser } from '../../api/apiClient'
import { useAuth }         from '../../auth/AuthContext'
import AuthLayout          from '../../components/AuthLayout/AuthLayout'
import styles              from './UpdatePasswordPage.module.css'

const ERROR_MESSAGES = {
  MISSING_PARAMETERS: 'Please fill in all required fields.',
  PASSWORD_TOO_SHORT: 'New password must be at least 8 characters long.',
  USER_NOT_FOUND:     'Account not found. Please sign out and sign in again.',
  INTERNAL_ERROR:     'Something went wrong on our end. Please try again.',
}

function getErrorMessage(errorCode) {
  return ERROR_MESSAGES[errorCode] ?? 'An unexpected error occurred.'
}

export default function UpdatePasswordPage() {

  // username comes from AuthContext — the user is already logged in
  const { username, logout } = useAuth()
  const navigate = useNavigate()

  const [newPassword,     setNewPassword]     = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error,           setError]           = useState('')
  const [success,         setSuccess]         = useState(false)
  const [loading,         setLoading]         = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()

    if (!newPassword) {
      setError('Please enter a new password.')
      return
    }

    // Client-side confirmation check — no need to hit the server for this
    if (newPassword !== confirmPassword) {
      setError('Passwords do not match. Please re-enter.')
      return
    }

    setLoading(true)
    setError('')

    try {
      const { data } = await updatePassword(username, newPassword)

      if (data.success) {
        setSuccess(true)
        // After a password update, the user must sign in again with the new
        // password. Log out (server + client) and redirect to login.
        setTimeout(async () => {
          try { await logoutUser() } catch { /* ignore network errors */ }
          logout()          // clears AuthContext + localStorage
          navigate('/login')
        }, 2000)
      } else {
        setError(getErrorMessage(data.errorCode))
      }
    } catch {
      setError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setLoading(false)
    }
  }

  // ── Success state ─────────────────────────────────────────────────────────
  if (success) {
    return (
      <AuthLayout title="Password updated">
        <div className={styles.success}>
          ✓ Password updated successfully. Redirecting to sign in…
        </div>
      </AuthLayout>
    )
  }

  // ── Form ──────────────────────────────────────────────────────────────────
  return (
    <AuthLayout title="Update password">

      <form onSubmit={handleSubmit} noValidate>

        {error && (
          <div className={styles.error} role="alert">
            {error}
          </div>
        )}

        {/*
          Show the username as a read-only display field so the user knows
          which account they are updating. We don't use a disabled <input>
          because screen readers handle a plain text element more clearly.
        */}
        <div className={styles.field}>
          <span className={styles.label}>Account</span>
          <div className={styles.usernameDisplay}>{username}</div>
        </div>

        <div className={styles.field}>
          <label htmlFor="new-password" className={styles.label}>
            New password
          </label>
          <input
            id="new-password"
            type="password"
            className={styles.input}
            value={newPassword}
            onChange={e => setNewPassword(e.target.value)}
            autoComplete="new-password"
            autoFocus
            disabled={loading}
            placeholder="At least 8 characters"
          />
        </div>

        <div className={styles.field}>
          <label htmlFor="confirm-password" className={styles.label}>
            Confirm new password
          </label>
          <input
            id="confirm-password"
            type="password"
            className={styles.input}
            value={confirmPassword}
            onChange={e => setConfirmPassword(e.target.value)}
            autoComplete="new-password"
            disabled={loading}
            placeholder="Re-enter new password"
          />
        </div>

        <button
          type="submit"
          className={styles.button}
          disabled={loading}
        >
          {loading ? 'Updating…' : 'Update password'}
        </button>

      </form>

    </AuthLayout>
  )
}
