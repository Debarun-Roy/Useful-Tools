import styles from './PageTabs.module.css'

export default function PageTabs({ tabs, activeTab, onChange, ariaLabel }) {
  return (
    <nav className={styles.tabBar} aria-label={ariaLabel}>
      {tabs.map(tab => (
        <button
          key={tab.id}
          className={activeTab === tab.id ? styles.tabActive : styles.tab}
          onClick={() => onChange(tab.id)}
        >
          {tab.icon && <span className={styles.icon} aria-hidden="true">{tab.icon}</span>}
          {tab.label}
        </button>
      ))}
    </nav>
  )
}
