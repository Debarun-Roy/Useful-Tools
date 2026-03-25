import styles from './BrandMark.module.css'

export default function BrandMark({ glyph = '#', compact = false }) {
  return (
    <div className={compact ? styles.compact : styles.brand}>
      <span className={styles.mark} aria-hidden="true">{glyph}</span>
      <span className={styles.name}>UsefulTools</span>
    </div>
  )
}
