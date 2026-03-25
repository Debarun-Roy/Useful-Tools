import BrandMark from '../BrandMark/BrandMark'
import styles from './AuthLayout.module.css'

export default function AuthLayout({ title, children }) {
  return (
    <div className={styles.page}>
      <div className={styles.orbit} aria-hidden="true" />
      <div className={styles.card}>
        <div className={styles.header}>
          <BrandMark glyph="@" compact />
          <span className={styles.kicker}>Secure access channel</span>
        </div>

        <h1 className={styles.title}>{title}</h1>
        <p className={styles.subtitle}>
          Continue inside the same observatory theme used by the utility tools.
        </p>

        {children}
      </div>
    </div>
  )
}
