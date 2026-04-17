import styles from './PageTabs.module.css'

export default function PageTabs({ tabs, activeTab, onChange, ariaLabel }) {
  return (
    <nav className={styles.tabBar} aria-label={ariaLabel}>
      {tabs.map(tab => (
        <button
          key={tab.id}
          className={
            tab.disabled
              ? styles.tabDisabled
              : activeTab === tab.id
              ? styles.tabActive
              : styles.tab
          }
          onClick={() => !tab.disabled && onChange(tab.id)}
          disabled={tab.disabled}
          title={tab.disabled ? 'Please login to access this resource' : undefined}
        >
          {tab.icon && <span className={styles.icon} aria-hidden="true">{tab.icon}</span>}
          {tab.label}
          {tab.disabled && <span className={styles.lockBadge} aria-hidden="true">🔒</span>}
        </button>
      ))}
    </nav>
  )
}
