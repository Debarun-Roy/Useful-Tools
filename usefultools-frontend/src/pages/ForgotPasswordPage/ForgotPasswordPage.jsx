import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useGoogleReCaptcha } from 'react-google-recaptcha-v3'
import {
  verifyForgotPasswordCaptcha,
  verifyRecoveryCode,
  requestPasswordReset,
} from '../../api/apiClient'
import AuthLayout from '../../components/AuthLayout/AuthLayout'
import styles      from './ForgotPasswordPage.module.css'

// ── Steps ────────────────────────────────────────────────────────────────
const STEP_CAPTCHA       = 'CAPTCHA'
const STEP_RECOVERY_CODE = 'RECOVERY_CODE'
const STEP_NEW_PASSWORD  = 'NEW_PASSWORD'

const ERROR_MESSAGES = {
  USERNAME_REQUIRED:      'Please enter your username.',
  CAPTCHA_REQUIRED:       'Captcha verification is required.',
  CAPTCHA_INVALID:        'Invalid captcha or username. Please try again.',
  CAPTCHA_NOT_CONFIGURED: 'Captcha verification is unavailable right now. Please try again later.',
  USER_NOT_FOUND:         'Invalid captcha or username. Please try again.',
  RECOVERY_CODE_REQUIRED: 'Please enter your recovery code.',
  INVALID_RECOVERY_CODE:  'Invalid recovery code. Please try again.',
  NEW_PASSWORD_REQUIRED:  'Please enter a new password.',
  PASSWORD_TOO_SHORT:     'Password must be at least 8 characters long.',
  PASSWORD_WEAK:          'Password must include 1 uppercase letter, 1 digit, and 1 special character.',
  PASSWORD_RECENTLY_USED: 'This password was recently used. Please choose a different password.',
  RATE_LIMITED:           'Too many attempts. Please wait a moment before trying again.',
  INTERNAL_ERROR:         'Something went wrong on our end. Please try again.',
}

function getErrorMessage(errorCode, serverMessage) {
  return ERROR_MESSAGES[errorCode] || serverMessage || 'An unknown error occurred.'
}

export default function ForgotPasswordPage() {

  const [step, setStep] = useState(STEP_CAPTCHA)

  // Step 1 — username + captcha
  const [username, setUsername] = useState('')

  // Step 2 — recovery code
  const [recoveryCode, setRecoveryCode] = useState('')

  // Step 3 — new password
  const [newPassword, setNewPassword]         = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showNewPassword, setShowNewPassword] = useState(false)

  const [error, setError]     = useState('')
  const [loading, setLoading] = useState(false)
  const [successMessage, setSuccessMessage] = useState('')

  const { executeRecaptcha } = useGoogleReCaptcha()
  const navigate = useNavigate()

  // ── Step 1: username + captcha ──────────────────────────────────────────
  async function handleCaptchaSubmit(e) {
    e.preventDefault()
    setError('')

    if (!username.trim()) {
      setError('Please enter your username.')
      return
    }

    if (!executeRecaptcha) {
      setError('Captcha is still loading. Please wait a moment and try again.')
      return
    }

    setLoading(true)

    try {
      const recaptchaToken = await executeRecaptcha('forgot_password')
      const { data } = await verifyForgotPasswordCaptcha(username.trim(), recaptchaToken)

      if (data.success) {
        setStep(STEP_RECOVERY_CODE)
      } else {
        setError(getErrorMessage(data.errorCode, data.error))
      }
    } catch {
      setError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setLoading(false)
    }
  }

  // ── Step 2: recovery code ───────────────────────────────────────────────
  async function handleRecoveryCodeSubmit(e) {
    e.preventDefault()
    setError('')

    if (!recoveryCode.trim()) {
      setError('Please enter your recovery code.')
      return
    }

    setLoading(true)

    try {
      const { data } = await verifyRecoveryCode(username.trim(), recoveryCode.trim())

      if (data.success) {
        setStep(STEP_NEW_PASSWORD)
      } else {
        setError(getErrorMessage(data.errorCode, data.error))
      }
    } catch {
      setError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setLoading(false)
    }
  }

  // ── Step 3: new password + confirm ──────────────────────────────────────
  async function handlePasswordSubmit(e) {
    e.preventDefault()
    setError('')

    if (!newPassword || !confirmPassword) {
      setError('Please enter and confirm your new password.')
      return
    }

    // Passwords-match check is purely client-side — no server round trip
    // needed just to compare two strings the user just typed.
    if (newPassword !== confirmPassword) {
      setError("Passwords don't match. Please try again.")
      return
    }

    setLoading(true)

    try {
      const { data } = await requestPasswordReset(
        username.trim(),
        recoveryCode.trim(),
        newPassword
      )

      if (data.success) {
        setSuccessMessage('Password changed successfully.')
        setTimeout(() => {
          navigate('/login')
        }, 2000)
      } else if (data.errorCode === 'INVALID_RECOVERY_CODE') {
        // Rare race: the code was valid at Step 2 but rejected here (e.g. an
        // admin regenerated it in between). Send the user back to Step 2
        // rather than showing a dead-end error on the password step.
        setStep(STEP_RECOVERY_CODE)
        setError(getErrorMessage(data.errorCode, data.error))
      } else {
        setError(getErrorMessage(data.errorCode, data.error))
      }
    } catch {
      setError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setLoading(false)
    }
  }

  function handleStartOver() {
    setStep(STEP_CAPTCHA)
    setUsername('')
    setRecoveryCode('')
    setNewPassword('')
    setConfirmPassword('')
    setError('')
    setSuccessMessage('')
  }

  if (successMessage) {
    return (
      <AuthLayout title="Forgot Password">
        <div className={styles.success} role="alert">
          {successMessage}
        </div>
        <p className={styles.footer}>Redirecting to sign in…</p>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Forgot Password">

      {error && (
        <div className={styles.error} role="alert">
          {error}
        </div>
      )}

      {/* ── Step 1: username + captcha ──────────────────────────────────── */}
      {step === STEP_CAPTCHA && (
        <form onSubmit={handleCaptchaSubmit} noValidate>

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

          <p className={styles.helperText}>
            This site is protected by reCAPTCHA. Verifying will not show a
            checkbox — it runs automatically when you continue.
          </p>

          <button type="submit" className={styles.button} disabled={loading}>
            {loading ? 'Verifying…' : 'Continue'}
          </button>
        </form>
      )}

      {/* ── Step 2: recovery code ───────────────────────────────────────── */}
      {step === STEP_RECOVERY_CODE && (
        <form onSubmit={handleRecoveryCodeSubmit} noValidate>

          <p className={styles.contextLine}>
            Resetting password for <strong>{username.trim()}</strong>
          </p>

          <div className={styles.field}>
            <label htmlFor="recoveryCode" className={styles.label}>Recovery code</label>
            <input
              id="recoveryCode"
              type="text"
              className={styles.input}
              value={recoveryCode}
              onChange={e => setRecoveryCode(e.target.value)}
              autoComplete="off"
              autoFocus
              disabled={loading}
              placeholder="Paste the code you saved at registration"
            />
          </div>

          <p className={styles.helperText}>
            Lost your recovery code? Contact an administrator for help.
          </p>

          <button type="submit" className={styles.button} disabled={loading}>
            {loading ? 'Verifying…' : 'Verify code'}
          </button>
        </form>
      )}

      {/* ── Step 3: new password + confirm ──────────────────────────────── */}
      {step === STEP_NEW_PASSWORD && (
        <form onSubmit={handlePasswordSubmit} noValidate>

          <p className={styles.contextLine}>
            Resetting password for <strong>{username.trim()}</strong>
          </p>

          <div className={styles.field}>
            <label htmlFor="newPassword" className={styles.label}>New password</label>
            <div className={styles.passwordWrapper}>
              <input
                id="newPassword"
                type={showNewPassword ? 'text' : 'password'}
                className={styles.passwordInput}
                value={newPassword}
                onChange={e => setNewPassword(e.target.value)}
                autoComplete="new-password"
                autoFocus
                disabled={loading}
                placeholder="Use uppercase, digits, and symbols"
              />
              <button
                type="button"
                className={styles.passwordToggle}
                onClick={() => setShowNewPassword(!showNewPassword)}
                disabled={loading}
                aria-label={showNewPassword ? 'Hide password' : 'Show password'}
                aria-pressed={showNewPassword}
              >
                {showNewPassword ? 'Hide' : 'Show'}
              </button>
            </div>
          </div>

          <div className={styles.field}>
            <label htmlFor="confirmPassword" className={styles.label}>Confirm new password</label>
            <input
              id="confirmPassword"
              type={showNewPassword ? 'text' : 'password'}
              className={styles.input}
              value={confirmPassword}
              onChange={e => setConfirmPassword(e.target.value)}
              autoComplete="new-password"
              disabled={loading}
              placeholder="Re-enter your new password"
            />
          </div>

          <button type="submit" className={styles.button} disabled={loading}>
            {loading ? 'Resetting…' : 'Reset password'}
          </button>
        </form>
      )}

      {step !== STEP_CAPTCHA && (
        <button type="button" className={styles.linkButton} onClick={handleStartOver}>
          ‹ Start over
        </button>
      )}

      <p className={styles.footer}>
        Remembered your password? <Link to="/login">Sign in</Link>
      </p>

    </AuthLayout>
  )
}
