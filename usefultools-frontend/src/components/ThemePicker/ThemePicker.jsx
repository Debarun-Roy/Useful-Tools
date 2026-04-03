import { useTheme } from '../../theme/useTheme'
import styles from './ThemePicker.module.css'

export default function ThemePicker() {
  const { theme, setTheme, themes } = useTheme()

  return (
    <label className={styles.shell}>
      <span className={styles.label}>Theme</span>
      <select
        className={styles.select}
        value={theme}
        onChange={(event) => setTheme(event.target.value)}
        aria-label="Select application theme"
      >
        {themes.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  )
}
