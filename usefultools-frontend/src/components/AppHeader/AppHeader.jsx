import BrandMark from '../BrandMark/BrandMark'
import styles from './AppHeader.module.css'

export default function AppHeader({
  username,
  onLogout,
  onBack,
  backLabel = 'Dashboard',
  glyph = '#',
}) {
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
        {username && <span className={styles.userBadge}>{username}</span>}
        {onLogout && (
          <button className={styles.logoutBtn} onClick={onLogout}>
            Sign out
          </button>
        )}
      </div>
    </header>
  )
}
