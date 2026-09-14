import { useState }          from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useGoogleReCaptcha } from 'react-google-recaptcha-v3'
import { registerUser }      from '../../api/apiClient'
import AuthLayout            from '../../components/AuthLayout/AuthLayout'
import styles                from './RegisterPage.module.css'

const ERROR_MESSAGES = {
  MISSING_CREDENTIALS: 'Please enter both username and password.',
  INVALID_USERNAME:    'Username must be at least 3 characters long.',
  PASSWORD_TOO_SHORT:  'Password must be at least 8 characters long.',
  PASSWORD_WEAK:       'Password must include 1 uppercase letter, 1 digit, and 1 special character.',
  USERNAME_TAKEN:      'That username is already taken. Please choose another.',
  CAPTCHA_REQUIRED:       'Captcha verification is required.',
  CAPTCHA_INVALID:        'Captcha verification failed. Please try again.',
  CAPTCHA_NOT_CONFIGURED: 'Captcha verification is unavailable right now. Please try again later.',
  // Sprint 6: rate limiting
  RATE_LIMITED:        'Too many registration attempts. Please wait a moment before trying again.',
  INTERNAL_ERROR:      'Something went wrong on our end. Please try again.',
}

const PASSWORD_REQUIREMENTS = [
  {
    label: 'At least 8 characters',
    test: (value) => value.length >= 8,
  },
  {
    label: 'At least 1 uppercase letter',
    test: (value) => /[A-Z]/.test(value),
  },
  {
    label: 'At least 1 digit',
    test: (value) => /\d/.test(value),
  },
  {
    label: 'At least 1 special character',
    test: (value) => /[^A-Za-z0-9]/.test(value),
  },
]

function getErrorMessage(errorCode) {
  return ERROR_MESSAGES[errorCode] ?? 'An unexpected error occurred.'
}

function meetsPasswordPolicy(password) {
  return PASSWORD_REQUIREMENTS.every((requirement) => requirement.test(password))
}

export default function RegisterPage() {

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error,    setError]    = useState('')
  const [success,  setSuccess]  = useState(false)
  const [loading,  setLoading]  = useState(false)

  // Set once, right after a successful registration. Never fetched again —
  // the server only ever stores a BCrypt hash of this value from this point on.
  const [recoveryCode, setRecoveryCode] = useState('')
  const [copied,        setCopied]      = useState(false)
  const [acknowledged,  setAcknowledged] = useState(false)

  const { executeRecaptcha } = useGoogleReCaptcha()
  const navigate = useNavigate()

  async function handleSubmit(e) {
    e.preventDefault()

    if (!username.trim() || !password) {
      setError('Please enter both username and password.')
      return
    }

    if (!meetsPasswordPolicy(password)) {
      setError('Choose a stronger password before creating your account.')
      return
    }

    if (!executeRecaptcha) {
      setError('Captcha is still loading. Please wait a moment and try again.')
      return
    }

    setLoading(true)
    setError('')

    try {
      const recaptchaToken = await executeRecaptcha('register')
      const { data } = await registerUser(username.trim(), password, recaptchaToken)

      if (data.success) {
        setSuccess(true)
        // FIX: this page previously auto-redirected to /login after 2s
        // unconditionally. Now that registration also hands back a one-time
        // recovery code, an unattended redirect would rush the user past the
        // only chance they get to save it. Navigation now waits for the
        // explicit "I've saved my code" acknowledgement below instead.
        setRecoveryCode(data.data?.recoveryCode || '')
      } else {
        setError(getErrorMessage(data.errorCode))
      }
    } catch {
      setError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setLoading(false)
    }
  }

  function handleCopyCode() {
    navigator.clipboard.writeText(recoveryCode).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }

  if (success) {
    return (
      <AuthLayout title="Account created">
        <div className={styles.success}>
          ✓ Registration successful.
        </div>

        {recoveryCode && (
          <>
            <div className={styles.recoveryWarning}>
              Save your recovery code now. It will not be shown again — you'll
              need it if you ever forget your password, and losing it means an
              administrator has to issue you a new one.
            </div>

            <div className={styles.recoveryCodeBox}>
              <code className={styles.recoveryCodeValue}>{recoveryCode}</code>
              <button
                type="button"
                className={styles.copyButton}
                onClick={handleCopyCode}
              >
                {copied ? 'Copied' : 'Copy'}
              </button>
            </div>

            <label className={styles.acknowledgeRow}>
              <input
                type="checkbox"
                checked={acknowledged}
                onChange={e => setAcknowledged(e.target.checked)}
              />
              I've saved this recovery code somewhere safe.
            </label>

            <button
              type="button"
              className={styles.button}
              disabled={!acknowledged}
              onClick={() => navigate('/login')}
            >
              Continue to sign in
            </button>
          </>
        )}
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
            placeholder="Use uppercase, digits, and symbols"
          />
          <div className={styles.requirements} aria-live="polite">
            {PASSWORD_REQUIREMENTS.map((requirement) => {
              const passed = requirement.test(password)
              return (
                <div
                  key={requirement.label}
                  className={passed ? styles.requirementPassed : styles.requirement}
                >
                  <span className={styles.requirementMark} aria-hidden="true">
                    {passed ? 'OK' : '..'}
                  </span>
                  <span>{requirement.label}</span>
                </div>
              )
            })}
          </div>
        </div>

        <button
          type="submit"
          className={styles.button}
          disabled={loading}
        >
          {loading ? 'Creating account…' : 'Create account'}
        </button>

        <p className={styles.helperText}>
          This site is protected by reCAPTCHA. Verifying will not show a
          checkbox — it runs automatically when you create your account.
        </p>

      </form>

      <p className={styles.footer}>
        Already have an account?{' '}
        <Link to="/login">Sign in</Link>
      </p>

    </AuthLayout>
  )
}
