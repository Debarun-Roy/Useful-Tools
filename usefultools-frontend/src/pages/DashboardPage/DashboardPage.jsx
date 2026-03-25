import { useNavigate } from 'react-router-dom'
import { logoutUser } from '../../api/apiClient'
import { useAuth } from '../../auth/AuthContext'
import AppHeader from '../../components/AppHeader/AppHeader'
import ToolHero from '../../components/ToolHero/ToolHero'
import styles from './DashboardPage.module.css'

const FEATURES = [
  {
    label: 'Calculator',
    path: '/calculator',
    sprint: 2,
    ready: true,
    icon: 'fx',
    desc: 'Arithmetic, boolean, trig, combined, and complex computation.',
  },
  {
    label: 'Number Analyser',
    path: '/analyser',
    sprint: 3,
    ready: true,
    icon: '#',
    desc: 'Classification, base conversion, and bounded series exploration.',
  },
  {
    label: 'Password Vault',
    path: '/vault',
    sprint: 4,
    ready: false,
    icon: '[]',
    desc: 'Generate, save, and retrieve credentials in a dedicated vault.',
  },
  {
    label: 'Update Password',
    path: '/update-password',
    sprint: 1,
    ready: true,
    icon: 'key',
    desc: 'Rotate your account password and re-authenticate cleanly.',
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
      <AppHeader
        username={username}
        onLogout={handleLogout}
        glyph="*"
      />

      <div className={styles.chrome}>
        <ToolHero
          badge="Control Deck"
          title="Utility"
          accent="Command Center"
          description={`Welcome back, ${username}. Launch the available tools below or jump into account maintenance.`}
          stats={[
            { value: FEATURES.filter(f => f.ready).length, label: 'ready tools' },
            { value: FEATURES.length, label: 'modules' },
            { value: 'main', label: 'branch' },
          ]}
        />

        <main className={styles.main}>
          <section className={styles.grid}>
            {FEATURES.map(feature => (
              <button
                key={feature.path}
                className={feature.ready ? styles.card : styles.cardDisabled}
                onClick={() => feature.ready && navigate(feature.path)}
                disabled={!feature.ready}
              >
                <div className={styles.cardTop}>
                  <span className={styles.cardIcon} aria-hidden="true">{feature.icon}</span>
                  <span className={styles.cardSprint}>Sprint {feature.sprint}</span>
                </div>
                <span className={styles.cardLabel}>{feature.label}</span>
                <span className={styles.cardDesc}>{feature.desc}</span>
                <span className={feature.ready ? styles.cardStatus : styles.cardStatusPending}>
                  {feature.ready ? 'Launch module' : 'Pending release'}
                </span>
              </button>
            ))}
          </section>
        </main>
      </div>
    </div>
  )
}
