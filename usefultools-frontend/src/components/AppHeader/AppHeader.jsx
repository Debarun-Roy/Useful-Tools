import BrandMark from '../BrandMark/BrandMark'
import UserMenu from '../UserMenu/UserMenu'
import styles from './AppHeader.module.css'

/**
 * AppHeader — shared page chrome used by tool pages that adopted the
 * design-system shell (currently CalculatorPage; other pages still inline
 * their own headers).
 *
 * Sprint 14 change:
 *   The inline <span className={styles.userBadge}>{username}</span> was
 *   replaced with <UserMenu ... />, giving every page that uses this
 *   component the same avatar + dropdown identity affordance as the
 *   Dashboard. No consumer needs to change — `username` and `onLogout`
 *   are still the only identity-related props.
 */
export default function AppHeader({
  username,
  onLogout,
  onBack,
  backLabel = 'Dashboard',
  glyph = '#',
}) {
  const isGuest = username === 'Guest User'

  return (
    <header className={styles.header}>
      <div className={styles.left}>
        <BrandMark glyph={glyph} compact />
        {onBack && (
          <button className={styles.backBtn} onClick={onBack}>
            {backLabel}
          </button>
        )}
      </div>

      <div className={styles.right}>
        {username && (
          <UserMenu username={username} isGuest={isGuest} variant="light" />
        )}
        {onLogout && (
          <button className={styles.logoutBtn} onClick={onLogout}>
            Sign out
          </button>
        )}
      </div>
    </header>
  )
}
