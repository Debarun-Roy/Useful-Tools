import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import styles from './UserMenu.module.css'

/**
 * UserMenu — identity button + dropdown.
 *
 * Props:
 *   username  — display name
 *   isGuest   — shows Guest badge; dropdown shows Log in + optional Sign out
 *   variant   — 'light' (default, tool pages) | 'dark' (Dashboard/Calculator)
 *   onLogout  — called when the user clicks "Sign out" inside the dropdown.
 *               When omitted, the Sign out item is not rendered.
 */
export default function UserMenu({ username, isGuest = false, variant = 'light', onLogout }) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef(null)
  const navigate = useNavigate()

  const initial = (username?.[0] || '?').toUpperCase()

  // Close dropdown on outside click or Escape.
  useEffect(() => {
    if (!open) return
    function handleOutside(e) {
      if (containerRef.current && !containerRef.current.contains(e.target)) setOpen(false)
    }
    function handleKey(e) {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', handleOutside)
    document.addEventListener('keydown', handleKey)
    return () => {
      document.removeEventListener('mousedown', handleOutside)
      document.removeEventListener('keydown', handleKey)
    }
  }, [open])

  function goTo(path) {
    setOpen(false)
    navigate(path)
  }

  function handleSignOut() {
    setOpen(false)
    onLogout?.()
  }

  const variantClass = variant === 'dark' ? styles.dark : styles.light
  const containerCls = `${styles.container} ${variantClass}`

  // ── Guest variant ─────────────────────────────────────────────────────────
  if (isGuest) {
    return (
      <div className={containerCls} ref={containerRef}>
        <button
          type="button"
          className={styles.button}
          onClick={() => setOpen(o => !o)}
          aria-haspopup="menu"
          aria-expanded={open}
          aria-label="Account menu"
        >
          <span className={styles.avatar} aria-hidden="true">{initial}</span>
          <span className={styles.name}>{username}</span>
          <span className={styles.guestBadge}>Guest</span>
          <span className={`${styles.chevron} ${open ? styles.chevronOpen : ''}`} aria-hidden="true">▾</span>
        </button>
        {open && (
          <div className={styles.dropdown} role="menu">
            <div className={styles.dropdownHeader} aria-hidden="true">
              <span className={styles.dropdownHeaderAvatar}>{initial}</span>
              <span className={styles.dropdownHeaderName}>{username}</span>
            </div>
            <div className={styles.menuDivider} aria-hidden="true" />
            <button
              type="button"
              className={styles.menuItem}
              role="menuitem"
              onClick={() => goTo('/login')}
            >
              <span className={styles.menuIcon} aria-hidden="true">🔓</span>
              <span className={styles.menuLabel}>Log in</span>
            </button>
            {onLogout && (
              <>
                <div className={styles.menuDivider} aria-hidden="true" />
                <button
                  type="button"
                  className={`${styles.menuItem} ${styles.menuItemDanger}`}
                  role="menuitem"
                  onClick={handleSignOut}
                >
                  <span className={styles.menuIcon} aria-hidden="true">🚪</span>
                  <span className={styles.menuLabel}>Sign out</span>
                </button>
              </>
            )}
          </div>
        )}
      </div>
    )
  }

  // ── Non-guest variant ─────────────────────────────────────────────────────
  return (
    <div className={containerCls} ref={containerRef}>
      <button
        type="button"
        className={styles.button}
        onClick={() => setOpen(o => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Account menu for ${username}`}
      >
        <span className={styles.avatar} aria-hidden="true">{initial}</span>
        <span className={styles.name}>{username}</span>
        <span className={`${styles.chevron} ${open ? styles.chevronOpen : ''}`} aria-hidden="true">▾</span>
      </button>

      {open && (
        <div className={styles.dropdown} role="menu">
          <div className={styles.dropdownHeader} aria-hidden="true">
            <span className={styles.dropdownHeaderAvatar}>{initial}</span>
            <span className={styles.dropdownHeaderName}>{username}</span>
          </div>
          <div className={styles.menuDivider} aria-hidden="true" />
          <button
            type="button"
            className={styles.menuItem}
            role="menuitem"
            onClick={() => goTo('/profile')}
          >
            <span className={styles.menuIcon} aria-hidden="true">👤</span>
            <span className={styles.menuLabel}>Profile</span>
          </button>
          <button
            type="button"
            className={styles.menuItem}
            role="menuitem"
            onClick={() => goTo('/update-password')}
          >
            <span className={styles.menuIcon} aria-hidden="true">🔑</span>
            <span className={styles.menuLabel}>Update password</span>
          </button>
          {onLogout && (
            <>
              <div className={styles.menuDivider} aria-hidden="true" />
              <button
                type="button"
                className={`${styles.menuItem} ${styles.menuItemDanger}`}
                role="menuitem"
                onClick={handleSignOut}
              >
                <span className={styles.menuIcon} aria-hidden="true">🚪</span>
                <span className={styles.menuLabel}>Sign out</span>
              </button>
            </>
          )}
        </div>
      )}
    </div>
  )
}
