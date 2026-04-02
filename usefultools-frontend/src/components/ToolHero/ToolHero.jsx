import styles from './ToolHero.module.css'

export default function ToolHero({ badge, title, accent, description, stats = [], fullWidth = false }) {
  return (
    <section className={fullWidth ? `${styles.hero} ${styles.heroFull}` : styles.hero}>
      <div className={styles.grid} aria-hidden="true" />
      <div className={styles.content}>
        {badge && <div className={styles.badge}>{badge}</div>}
        <h1 className={styles.title}>
          {title}
          {accent && (
            <>
              <br />
              <span className={styles.accent}>{accent}</span>
            </>
          )}
        </h1>
        {description && <p className={styles.description}>{description}</p>}
      </div>

      {stats.length > 0 && (
        <div className={styles.stats}>
          {stats.map(stat => (
            <div key={`${stat.label}-${stat.value}`} className={styles.statCard}>
              <span className={styles.statValue}>{stat.value}</span>
              <span className={styles.statLabel}>{stat.label}</span>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}