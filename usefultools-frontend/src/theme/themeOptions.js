export const STORAGE_KEY = 'usefultools.theme'
export const DEFAULT_THEME = 'observatory'

export const THEME_OPTIONS = [
  { value: 'observatory', label: 'Observatory' },
  { value: 'starry-night', label: 'Starry Night' },
  { value: 'solar-flare', label: 'Solar Flare' },
  { value: 'aurora', label: 'Aurora' },
]

export const VALID_THEMES = new Set(THEME_OPTIONS.map((theme) => theme.value))
