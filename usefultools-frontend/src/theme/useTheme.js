import { useContext } from 'react'
import { ThemeContext } from './ThemeContextStore'

export function useTheme() {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('useTheme must be called inside <ThemeProvider>')
  return ctx
}