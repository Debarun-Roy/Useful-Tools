import styles from './AuthLayout.module.css'

/**
 * AuthLayout — shared visual shell for all three auth screens.
 * Observatory theme: deep space background with dot-grid, glassy card.
 */
export default function AuthLayout({ title, children }) {
  return (
    <div className={styles.page}>
      {/* Secondary bottom-left glow */}
      <div className={styles.glow2} aria-hidden="true" />

      <div className={styles.card}>

        {/* Brand header */}
        <div className={styles.brand}>
          <span className={styles.brandMark} aria-hidden="true">#</span>
          <span className={styles.brandName}>UsefulTools</span>
          <span className={styles.brandDot} aria-hidden="true" />
        </div>

        <div className={styles.divider} aria-hidden="true" />

        <h1 className={styles.title}>{title}</h1>

        {children}
      </div>
    </div>
  )
}