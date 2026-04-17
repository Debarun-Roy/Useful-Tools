import styles from './PageTabs.module.css'

export default function PageTabs({ tabs, activeTab, onChange, ariaLabel }) {
  return (
    <nav className={styles.tabBar} aria-label={ariaLabel}>
      {tabs.map(tab =>
        tab.disabled ? (
          /* Wrap in a span so the title tooltip fires on hover even though
             the inner button is disabled (disabled elements don't emit
             pointer events, so title never fires on them directly). */
          <span
            key={tab.id}
            className={styles.disabledWrapper}
            title="Please login to access this resource."
            aria-label={`${tab.label} — login required`}
          >
            <button
              className={styles.tabDisabled}
              disabled
              tabIndex={-1}
              aria-disabled="true"
            >
              {tab.icon && <span className={styles.icon} aria-hidden="true">{tab.icon}</span>}
              {tab.label}
              <span className={styles.lockBadge} aria-hidden="true">🔒</span>
            </button>
          </span>
        ) : (
          <button
            key={tab.id}
            className={activeTab === tab.id ? styles.tabActive : styles.tab}
            onClick={() => onChange(tab.id)}
          >
            {tab.icon && <span className={styles.icon} aria-hidden="true">{tab.icon}</span>}
            {tab.label}
          </button>
        )
      )}
    </nav>
  )
}
