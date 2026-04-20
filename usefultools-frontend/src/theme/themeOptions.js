export const STORAGE_KEY = 'usefultools.theme'
export const DEFAULT_THEME = 'observatory'

export const THEME_OPTIONS = [
  // ── Original 4 ──────────────────────────────────────────────────────────
  { value: 'observatory',  label: 'Observatory'  },
  { value: 'starry-night', label: 'Starry Night' },
  { value: 'solar-flare',  label: 'Solar Flare'  },
  { value: 'aurora',       label: 'Aurora'       },

  // ── Sprint 16 additions ──────────────────────────────────────────────────
  { value: 'nebula',       label: 'Nebula'       },   // deep violet / cosmic purple
  { value: 'midnight',     label: 'Midnight'     },   // near-black navy, silver accents
  { value: 'solstice',     label: 'Solstice'     },   // warm amber & burnt gold, dark
  { value: 'glacier',      label: 'Glacier'      },   // icy white/cyan, light
  { value: 'crimson-dusk', label: 'Crimson Dusk' },   // deep red/burgundy, dark
  { value: 'synthwave',    label: 'Synthwave'    },   // neon pink/cyan on near-black
]

export const VALID_THEMES = new Set(THEME_OPTIONS.map((theme) => theme.value))