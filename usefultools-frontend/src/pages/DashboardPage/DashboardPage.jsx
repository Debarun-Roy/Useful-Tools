import { useNavigate } from 'react-router-dom'
import { logoutUser }  from '../../api/apiClient'
import { useAuth }     from '../../auth/AuthContext'
import styles          from './DashboardPage.module.css'

/**
 * Feature registry — update the ready flag as each sprint is completed.
 *
 * Sprint 1 (current):  Update Password
 * Sprint 2 (next):     Calculator
 * Sprint 3:            Number Analyser
 * Sprint 4:            Password Vault
 */
const FEATURES = [
  {
    label:   'Calculator',
    path:    '/calculator',
    sprint:  2,
    ready:   false,
    icon:    '🧮',
    desc:    'Arithmetic, boolean, trig, complex and more',
  },
  {
    label:   'Number Analyser',
    path:    '/analyser',
    sprint:  3,
    ready:   false,
    icon:    '🔢',
    desc:    'Classify numbers and generate sequences',
  },
  {
    label:   'Password Vault',
    path:    '/vault',
    sprint:  4,
    ready:   false,
    icon:    '🔐',
    desc:    'Generate, save and retrieve passwords securely',
  },
  {
    label:   'Update Password',
    path:    '/update-password',
    sprint:  1,
    ready:   true,
    icon:    '🔑',
    desc:    'Change your account password',
  },
]

export default function DashboardPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    try {
      await logoutUser()   // invalidate the server session
    } catch {
      // If the server call fails (e.g. Tomcat restarted), we still clear the
      // client-side auth so the user isn't stuck in a broken logged-in state.
    } finally {
      logout()              // clear AuthContext + localStorage
      navigate('/login')
    }
  }

  return (
    <div className={styles.page}>

      {/* ── Top navigation bar ───────────────────────────────────────── */}
      <header className={styles.header}>
        <div className={styles.brand}>
          <span className={styles.brandIcon} aria-hidden="true">⚙</span>
          <span className={styles.brandName}>UsefulTools</span>
        </div>
        <div className={styles.userArea}>
          <span className={styles.usernameLabel}>
            Signed in as <strong>{username}</strong>
          </span>
          <button
            className={styles.logoutButton}
            onClick={handleLogout}
          >
            Sign out
          </button>
        </div>
      </header>

      {/* ── Main content ─────────────────────────────────────────────── */}
      <main className={styles.main}>

        <div className={styles.welcome}>
          <h1 className={styles.greeting}>Welcome back, {username}.</h1>
          <p className={styles.subtitle}>What would you like to do today?</p>
        </div>

        {/* Feature cards grid */}
        <div className={styles.grid}>
          {FEATURES.map(feature => (
            <button
              key={feature.path}
              className={feature.ready ? styles.card : styles.cardDisabled}
              onClick={() => feature.ready && navigate(feature.path)}
              disabled={!feature.ready}
            >
              <span className={styles.cardIcon} aria-hidden="true">
                {feature.icon}
              </span>
              <span className={styles.cardLabel}>{feature.label}</span>
              <span className={styles.cardDesc}>{feature.desc}</span>
              {!feature.ready && (
                <span className={styles.cardBadge}>
                  Coming in Sprint {feature.sprint}
                </span>
              )}
            </button>
          ))}
        </div>

      </main>
    </div>
  )
}
