import { useEffect, useState } from 'react'
import { ThemeContext } from './ThemeContextStore'
import { DEFAULT_THEME, STORAGE_KEY, THEME_OPTIONS, VALID_THEMES } from './themeOptions'

function getInitialTheme() {
  const storedTheme = localStorage.getItem(STORAGE_KEY)
  return VALID_THEMES.has(storedTheme) ? storedTheme : DEFAULT_THEME
}

export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(getInitialTheme)

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    localStorage.setItem(STORAGE_KEY, theme)
  }, [theme])

  function updateTheme(nextTheme) {
    if (!VALID_THEMES.has(nextTheme)) return
    setTheme(nextTheme)
  }

  return (
    <ThemeContext.Provider value={{ theme, setTheme: updateTheme, themes: THEME_OPTIONS }}>
      {children}
    </ThemeContext.Provider>
  )
}
