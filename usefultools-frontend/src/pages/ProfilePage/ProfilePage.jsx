import { useState, useEffect, useRef } from 'react'
import { useNavigate }         from 'react-router-dom'
import { fetchUserProfile, logoutUser, deleteUserData, removeUserAccount } from '../../api/apiClient'
import { useAuth }             from '../../auth/useAuth'
import LockedResourceOverlay from '../../components/LockedResourceOverlay/LockedResourceOverlay'
import UserMenu from '../../components/UserMenu/UserMenu'
import styles                  from './ProfilePage.module.css'

// ── Confirmation copy for the two destructive Settings actions ─────────────
const CONFIRM_COPY = {
  deleteData: {
    title: 'Delete my data?',
    body: "This permanently deletes your calculation history, vault entries, "
        + "activity log, favorites, and other usage data. Your account and "
        + "login stay active — you can keep using UsefulTools with a clean "
        + "slate. This cannot be undone.",
    confirmLabel: 'Delete my data',
  },
  removeAccount: {
    title: 'Remove my account?',
    body: "This permanently deletes your account and every piece of data "
        + "tied to it. You'll be signed out immediately and this cannot be "
        + "undone.",
    confirmLabel: 'Remove my account',
  },
}

export default function ProfilePage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const [profile, setProfile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error,   setError]   = useState('')
  const isGuest = username === 'Guest User'

  // ── Settings dropdown ───────────────────────────────────────────────────
  const [settingsOpen, setSettingsOpen] = useState(false)
  const settingsRef = useRef(null)

  // ── Confirm dialog: null | 'deleteData' | 'removeAccount' ──────────────
  const [confirmAction, setConfirmAction] = useState(null)
  const [actionBusy, setActionBusy] = useState(false)

  // ── Inline toast: { message, type } | null ──────────────────────────────
  const [toast, setToast] = useState(null)

  useEffect(() => {
    if (isGuest) {
      setLoading(false)
      return
    }

    loadProfile()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isGuest])

  function loadProfile() {
    setLoading(true)
    fetchUserProfile()
      .then(({ data }) => {
        if (data.success) setProfile(data.data)
        else setError(data.error || 'Failed to load profile.')
      })
      .catch(() => setError('Could not reach the server.'))
      .finally(() => setLoading(false))
  }

  // Close the Settings dropdown on outside click or Escape.
  useEffect(() => {
    if (!settingsOpen) return
    function handleOutside(e) {
      if (settingsRef.current && !settingsRef.current.contains(e.target)) {
        setSettingsOpen(false)
      }
    }
    function handleKey(e) {
      if (e.key === 'Escape') setSettingsOpen(false)
    }
    document.addEventListener('mousedown', handleOutside)
    document.addEventListener('keydown', handleKey)
    return () => {
      document.removeEventListener('mousedown', handleOutside)
      document.removeEventListener('keydown', handleKey)
    }
  }, [settingsOpen])

  function showToast(message, type = 'success') {
    setToast({ message, type })
  }

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  function goToChangePassword() {
    setSettingsOpen(false)
    navigate('/update-password')
  }

  function openConfirm(action) {
    setSettingsOpen(false)
    setConfirmAction(action)
  }

  async function handleConfirmedAction() {
    const action = confirmAction
    setActionBusy(true)

    try {
      if (action === 'deleteData') {
        const { data } = await deleteUserData()
        if (data.success) {
          showToast('Your platform data has been deleted.')
          loadProfile()
        } else {
          showToast(data.error || 'Could not delete your data.', 'error')
        }
      } else if (action === 'removeAccount') {
        const { data } = await removeUserAccount()
        if (data.success) {
          logout()
          navigate('/login')
          return // component is unmounting — skip the finally-block state updates
        } else {
          showToast(data.error || 'Could not remove your account.', 'error')
        }
      }
    } catch {
      showToast('Could not reach the server.', 'error')
    } finally {
      setActionBusy(false)
      setConfirmAction(null)
    }
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

  const confirmCopy = confirmAction ? CONFIRM_COPY[confirmAction] : null

  return (
    <div className={styles.page}>

      {/* Header */}
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.brand}>
            <span className={styles.brandMark} aria-hidden="true">👤</span>
            <span className={styles.brandName}>UsefulTools</span>
          </div>
          <button className={styles.backBtn} onClick={() => navigate('/dashboard')}>
            Dashboard
          </button>
        </div>
        <div className={styles.headerRight}>
          <UserMenu username={username} isGuest={isGuest} variant="light" onLogout={handleLogout} />
        </div>
      </header>

      {/* Hero */}
      <section className={styles.hero}>
        <div className={styles.heroGrid} aria-hidden="true" />
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>User Profile</div>
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

      {/* Toast */}
      {toast && (
        <div className={styles.toastWrap}>
          <div className={toast.type === 'error' ? styles.toastError : styles.toastSuccess}>
            {toast.message}
            <button
              className={styles.toastClose}
              onClick={() => setToast(null)}
              aria-label="Dismiss"
            >
              ×
            </button>
          </div>
        </div>
      )}

      {/* Content */}
      <main className={styles.main}>

        {isGuest && (
          <div style={{ position: 'relative', minHeight: '400px' }}>
            <div style={{ opacity: 0.5, pointerEvents: 'none', userSelect: 'none' }}>
              <div className={styles.loading}>Loading your profile…</div>
            </div>
            <LockedResourceOverlay />
          </div>
        )}

        {!isGuest && loading && (
          <div className={styles.loading}>Loading your profile…</div>
        )}

        {!isGuest && error && (
          <div className={styles.errorBanner} role="alert">{error}</div>
        )}

        {!isGuest && profile && (
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

              <div className={styles.settingsWrap} ref={settingsRef}>
                <button
                  type="button"
                  className={styles.settingsBtn}
                  onClick={() => setSettingsOpen(o => !o)}
                  aria-haspopup="menu"
                  aria-expanded={settingsOpen}
                >
                  Settings
                  <span
                    className={`${styles.chevron} ${settingsOpen ? styles.chevronOpen : ''}`}
                    aria-hidden="true"
                  >
                    ▾
                  </span>
                </button>

                {settingsOpen && (
                  <div className={styles.settingsDropdown} role="menu">
                    <button
                      type="button"
                      className={styles.menuItem}
                      role="menuitem"
                      onClick={goToChangePassword}
                    >
                      <span className={styles.menuIcon} aria-hidden="true">🔑</span>
                      <span className={styles.menuLabel}>Change password</span>
                    </button>

                    <div className={styles.menuDivider} aria-hidden="true" />

                    <button
                      type="button"
                      className={styles.menuItem}
                      role="menuitem"
                      onClick={() => openConfirm('deleteData')}
                    >
                      <span className={styles.menuIcon} aria-hidden="true">🧹</span>
                      <span className={styles.menuLabel}>Delete my data</span>
                    </button>

                    <button
                      type="button"
                      className={`${styles.menuItem} ${styles.menuItemDanger}`}
                      role="menuitem"
                      onClick={() => openConfirm('removeAccount')}
                    >
                      <span className={styles.menuIcon} aria-hidden="true">🗑</span>
                      <span className={styles.menuLabel}>Remove my account</span>
                    </button>
                  </div>
                )}
              </div>
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

      {/* Confirm destructive-action dialog */}
      {confirmAction && confirmCopy && (
        <div className={styles.confirmOverlay}>
          <div className={styles.confirmDialog}>
            <h3 className={styles.confirmTitle}>{confirmCopy.title}</h3>
            <p className={styles.confirmBody}>{confirmCopy.body}</p>
            <div className={styles.confirmActions}>
              <button
                className={styles.cancelBtn}
                onClick={() => setConfirmAction(null)}
                disabled={actionBusy}
              >
                Cancel
              </button>
              <button
                className={styles.dangerBtn}
                onClick={handleConfirmedAction}
                disabled={actionBusy}
              >
                {actionBusy ? 'Working…' : confirmCopy.confirmLabel}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
