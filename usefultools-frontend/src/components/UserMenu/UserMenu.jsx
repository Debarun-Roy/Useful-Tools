import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import styles from './UserMenu.module.css'

/**
 * UserMenu — identity button + dropdown.
 *
 * Replaces the "Signed in as <username>" text and the standalone Profile /
 * Update Password dashboard tiles. Shows an avatar-letter mark beside the
 * username; clicking the button toggles a dropdown that lets logged-in users
 * reach /profile and /update-password.
 *
 * Behaviour:
 *   • Non-guest users      — the button is a real <button>. Clicking opens a
 *                            dropdown with "Profile" and "Update Password".
 *                            The dropdown closes on outside-click, Esc, or
 *                            after an option is chosen.
 *   • Guest users          — the button is a non-interactive display-only
 *                            wrapper with a "Guest" badge; no dropdown. This
 *                            matches the visual treatment previously shown in
 *                            Guest Mode and makes clear that the identity
 *                            menu has no account actions available.
 *
 * Variants (visual only — behaviour is identical):
 *   variant="light" (default)  — used on pages whose header has a light
 *                                surface background (all tool pages).
 *   variant="dark"             — used on the Dashboard and Calculator pages
 *                                whose headers have a dark background.
 */
export default function UserMenu({ username, isGuest = false, variant = 'light' }) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef(null)
  const navigate = useNavigate()

  const initial = (username?.[0] || '?').toUpperCase()

  // Close on outside click (non-guest dropdown).
  useEffect(() => {
    if (!open) return

    function handleClick(event) {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setOpen(false)
      }
    }
    function handleKey(event) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    document.addEventListener('keydown', handleKey)
    return () => {
      document.removeEventListener('mousedown', handleClick)
      document.removeEventListener('keydown', handleKey)
    }
  }, [open])

  function goTo(path) {
    setOpen(false)
    navigate(path)
  }

  const variantClass = variant === 'dark' ? styles.dark : styles.light
  const containerCls = `${styles.container} ${variantClass}`

  // ── Guest variant ─────────────────────────────────────────────────────────
  // Static display; no dropdown, no click handler. Preserves the "badge"
  // identity treatment that was tried out in Guest Mode.
  if (isGuest) {
    return (
      <div
        className={containerCls}
        title="Account actions are only available to registered users"
      >
        <div className={styles.buttonDisplay} aria-disabled="true">
          <span className={styles.avatar} aria-hidden="true">{initial}</span>
          <span className={styles.name}>{username}</span>
          <span className={styles.guestBadge}>Guest</span>
        </div>
      </div>
    )
  }

  // ── Non-guest variant ────────────────────────────────────────────────────
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
        </div>
      )}
    </div>
  )
}
