import { useNavigate } from 'react-router-dom'
import { logoutUser }  from '../../api/apiClient'
import { useAuth }     from '../../auth/useAuth'
import ThemePicker     from '../../components/ThemePicker/ThemePicker'
import styles          from './DashboardPage.module.css'

const FEATURES = [
  {
    label:  'Calculator',
    path:   '/calculator',
    sprint: 2,
    ready:  true,
    icon:   '🧮',
    desc:   'Arithmetic, boolean, trig, complex, matrix, statistics and more',
  },
  {
    label:  'Number Analyser',
    path:   '/analyser',
    sprint: 3,
    ready:  true,
    icon:   '🔢',
    desc:   'Classify numbers, explore base representations, and generate sequences',
  },
  {
    label:  'Password Vault',
    path:   '/vault',
    sprint: 4,
    ready:  true,
    icon:   '🔐',
    desc:   'Generate, save and retrieve passwords securely with RSA-2048 encryption',
  },
  {
    label:  'Unit Converter',
    path:   '/converter',
    sprint: 7,
    ready:  true,
    icon:   '🔄',
    desc:   'Convert between length, mass, temperature, time, data, speed and area',
  },
  {
    label:  'Text Utilities',
    path:   '/text-utils',
    sprint: 10,
    ready:  true,
    icon:   '📝',
    desc:   'Word counter, case converter, diff checker, regex tester, slug generator and more',
  },
  {
    label:  'Encoding & Decoding',
    path:   '/encoding',
    sprint: 11,
    ready:  true,
    icon:   '🔧',
    desc:   'Base64, URL, HTML entities, JWT decoder, binary/hex converter, ROT cipher',
  },
  {
    label:  'Profile',
    path:   '/profile',
    sprint: 5,
    ready:  true,
    icon:   '👤',
    desc:   'View your activity summary and account details',
  },
  {
    label:  'Update Password',
    path:   '/update-password',
    sprint: 1,
    ready:  true,
    icon:   '🔑',
    desc:   'Change your account password securely',
  },
]

export default function DashboardPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore network errors */ }
    logout()
    navigate('/login')
  }

  return (
    <div className={styles.page}>

      <header className={styles.header}>
        <div className={styles.brand}>
          <span className={styles.brandMark} aria-hidden="true">#</span>
          <span className={styles.brandName}>UsefulTools</span>
        </div>
        <div className={styles.userArea}>
          <ThemePicker />
          <span className={styles.usernameLabel}>
            Signed in as <strong style={{ color: 'var(--clr-dark-text)' }}>{username}</strong>
          </span>
          <button className={styles.logoutButton} onClick={handleLogout}>
            Sign out
          </button>
        </div>
      </header>

      <section className={styles.hero} aria-label="Welcome">
        <div className={styles.heroInner}>
          <div className={styles.heroBadge}>
            <span className={styles.statusDot} aria-hidden="true" />
            Mission Control · {new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' })}
          </div>
          <h1 className={styles.greeting}>
            Welcome back,{' '}
            <span className={styles.greetingAccent}>{username}</span>.
          </h1>
          <p className={styles.subtitle}>
            Select a tool below to get started.
          </p>
        </div>
      </section>

      <main className={styles.main}>
        <p className={styles.sectionTitle}>Available tools</p>
        <div className={styles.grid}>
          {FEATURES.map(feature => (
            <button
              key={feature.path}
              className={feature.ready ? styles.card : styles.cardDisabled}
              onClick={() => feature.ready && navigate(feature.path)}
              disabled={!feature.ready}
            >
              <div className={styles.cardIconWrap}>
                <span className={styles.cardIcon} aria-hidden="true">{feature.icon}</span>
              </div>
              <span className={styles.cardLabel}>{feature.label}</span>
              <span className={styles.cardDesc}>{feature.desc}</span>
              {!feature.ready && (
                <span className={styles.cardBadge}>Sprint {feature.sprint}</span>
              )}
            </button>
          ))}
        </div>
      </main>

    </div>
  )
}
