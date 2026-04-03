import { useState, useEffect } from 'react'
import { useNavigate }         from 'react-router-dom'
import { fetchUserProfile, logoutUser } from '../../api/apiClient'
import { useAuth }             from '../../auth/useAuth'
import styles                  from './ProfilePage.module.css'

export default function ProfilePage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const [profile, setProfile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error,   setError]   = useState('')

  useEffect(() => {
    fetchUserProfile()
      .then(({ data }) => {
        if (data.success) setProfile(data.data)
        else setError(data.error || 'Failed to load profile.')
      })
      .catch(() => setError('Could not reach the server.'))
      .finally(() => setLoading(false))
  }, [])

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  const stats = profile
    ? [
        { label: 'Standard Calculations', value: profile.totalStandardCalculations,  icon: '🧮' },
        { label: 'EMI Calculations',      value: profile.totalEMICalculations,        icon: '📊' },
        { label: 'Tax Calculations',      value: profile.totalTaxCalculations,        icon: '💰' },
        { label: 'CI Calculations',       value: profile.totalCICalculations,         icon: '📈' },
        { label: 'Salary Breakdowns',     value: profile.totalSalaryCalculations,     icon: '💵' },
        { label: 'Passwords Stored',      value: profile.totalPasswordsStored,        icon: '🔐' },
      ]
    : []

  return (
    <div className={styles.page}>

      {/* Header */}
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.brand}>
            <span className={styles.brandMark} aria-hidden="true">#</span>
            <span className={styles.brandName}>UsefulTools</span>
          </div>
          <button className={styles.backBtn} onClick={() => navigate('/dashboard')}>
            ← Dashboard
          </button>
        </div>
        <div className={styles.headerRight}>
          <span className={styles.userBadge}>{username}</span>
          <button className={styles.logoutBtn} onClick={handleLogout}>Sign out</button>
        </div>
      </header>

      {/* Hero */}
      <section className={styles.hero}>
        <div className={styles.heroGrid} aria-hidden="true" />
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>Sprint 5 · User Profile</div>
          <h1 className={styles.heroTitle}>
            Your<br />
            <span className={styles.heroAccent}>Profile</span>
          </h1>
          <p className={styles.heroSub}>
            A summary of your activity across all UsefulTools features.
          </p>
        </div>
        {profile && (
          <div className={styles.heroStats}>
            <div className={styles.statCard}>
              <span className={styles.statValue}>{profile.totalCalculations}</span>
              <span className={styles.statLabel}>total calculations</span>
            </div>
            <div className={styles.statCard}>
              <span className={styles.statValue}>{profile.totalPasswordsStored}</span>
              <span className={styles.statLabel}>passwords stored</span>
            </div>
          </div>
        )}
      </section>

      {/* Content */}
      <main className={styles.main}>

        {loading && (
          <div className={styles.loading}>Loading your profile…</div>
        )}

        {error && (
          <div className={styles.errorBanner} role="alert">{error}</div>
        )}

        {profile && (
          <>
            {/* Identity card */}
            <div className={styles.identityCard}>
              <div className={styles.avatar} aria-hidden="true">
                {username[0].toUpperCase()}
              </div>
              <div className={styles.identityInfo}>
                <div className={styles.identityUsername}>{profile.username}</div>
                <div className={styles.identityMeta}>
                  {profile.totalCalculations} calculations · {profile.totalPasswordsStored} vault entries
                </div>
                <div className={styles.identityMeta}>
                  Member since {profile.accountCreatedDate}
                </div>
              </div>
              <button
                className={styles.changePassBtn}
                onClick={() => navigate('/update-password')}
              >
                Change password
              </button>
            </div>

            {/* Stats grid */}
            <div className={styles.sectionLabel}>Activity breakdown</div>
            <div className={styles.statsGrid}>
              {stats.map(s => (
                <div key={s.label} className={styles.statTile}>
                  <span className={styles.tileIcon} aria-hidden="true">{s.icon}</span>
                  <span className={styles.tileValue}>{s.value}</span>
                  <span className={styles.tileLabel}>{s.label}</span>
                </div>
              ))}
            </div>
          </>
        )}
      </main>
    </div>
  )
}
