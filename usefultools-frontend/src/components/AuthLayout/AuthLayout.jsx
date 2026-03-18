import styles from './AuthLayout.module.css'

/**
 * AuthLayout — the shared visual shell for all three auth screens.
 *
 * WHY THIS EXISTS (the "layout component" pattern):
 * Login, Register, and Update Password all share identical outer structure:
 *   - A full-page gradient background
 *   - A centred white card
 *   - A brand header at the top of the card
 *   - A page-specific title
 *
 * Without this component, that structure would be copy-pasted three times.
 * Extracting it here means:
 *   1. One change here updates all three pages at once.
 *   2. Each page file only contains what is unique to that page (the form).
 *
 * PROPS:
 *   title    — the h1 heading shown inside the card (e.g. "Sign in")
 *   children — the JSX content passed between <AuthLayout> tags in each
 *              page component (the form, footer links, etc.)
 *
 * {children} is React's way of rendering whatever is placed between the
 * opening and closing tags of a component — exactly like HTML's slot concept.
 */
export default function AuthLayout({ title, children }) {
  return (
    <div className={styles.page}>
      <div className={styles.card}>

        {/* Brand header — same on every auth page */}
        <div className={styles.brand}>
          <span className={styles.brandIcon} aria-hidden="true">⚙</span>
          <span className={styles.brandName}>UsefulTools</span>
        </div>

        <h1 className={styles.title}>{title}</h1>

        {/* Page-specific content (form, links, etc.) */}
        {children}

      </div>
    </div>
  )
}
