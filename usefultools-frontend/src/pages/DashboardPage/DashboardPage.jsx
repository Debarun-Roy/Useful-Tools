import { useMemo, useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { logoutUser }  from '../../api/apiClient'
import { fetchToolStatus } from '../../api/apiClient'
import { useAuth }     from '../../auth/useAuth'
import ThemePicker     from '../../components/ThemePicker/ThemePicker'
import UserMenu        from '../../components/UserMenu/UserMenu'
import RecentActivity  from '../../components/RecentActivity/RecentActivity'
import useFavorites    from '../../hooks/useFavorites.js'
import styles          from './DashboardPage.module.css'

/**
 * Dashboard (Sprint 15 refresh):
 *
 *   Section order on the page, top-to-bottom:
 *     1. Header (brand, theme picker, user menu, sign out)          — unchanged
 *     2. Hero / greeting                                             — unchanged
 *     3. Recent Activity widget  (non-guest only)                    — NEW
 *     4. Favorites section       (when the user has pinned any)      — NEW
 *     5. Main tool grid          — "All tools" heading if favorites
 *                                   are present, else the legacy
 *                                   "Available tools" heading.
 *
 *   Sprint 15 tiles: /dev-utils — Hash Identifier + API Key Generator,
 *   wired in as the 10th feature. Guest users see it unlocked (it does
 *   not require auth server-side).
 *
 *   Favorites:
 *     • Each ready feature card gets a star toggle in the top-right.
 *     • The Favorites section above the grid is re-orderable via
 *       native HTML5 drag-and-drop AND a pair of up/down arrow buttons
 *       (accessible + keyboard-friendly).
 *     • For guest users favorites are persisted in localStorage; for
 *       registered users they are persisted server-side via the hook.
 *
 *   Recent Activity:
 *     • Shown above the feature grid for non-guest users only.
 *     • Guests are excluded because there's no meaningful activity log
 *       for a guest session that expires on logout.
 */

/*
 * DASHBOARD PATCH — Sprint 16
 *
 * Replace the FEATURES array in DashboardPage.jsx with this version.
 * Also add /time-utils to the FEATURES_BY_PATH lookup (it is auto-built
 * from the FEATURES array so no separate change is needed).
 *
 * ── What changed ──────────────────────────────────────────────────────────
 *   • New entry: Time Utilities → /time-utils  (sprint 16)
 *   • Dev Utilities description updated to mention QR Code + Cron Builder
 */

const FEATURES = [
  {
    label:  'Calculator',
    path:   '/calculator',
    sprint: 2,
    ready:  true,
    icon:   '🧮',
    desc:   'Arithmetic, boolean, trig, complex, matrix, statistics and more',
  },
  {
    label:  'Number Analyser',
    path:   '/analyser',
    sprint: 3,
    ready:  true,
    icon:   '🔢',
    desc:   'Classify numbers, explore base representations, and generate sequences',
  },
  {
    label:  'Password Vault',
    path:   '/vault',
    sprint: 4,
    ready:  true,
    icon:   '🔐',
    desc:   'Generate, save and retrieve passwords securely with RSA-2048 encryption',
    requiresAuth: true,
  },
  {
    label:  'Unit Converter',
    path:   '/converter',
    sprint: 7,
    ready:  true,
    icon:   '🔄',
    desc:   'Convert between length, mass, temperature, time, data, speed and area',
  },
  {
    label:  'Text Utilities',
    path:   '/text-utils',
    sprint: 10,
    ready:  true,
    icon:   '📝',
    desc:   'Word counter, case converter, diff checker, regex tester, slug generator and more',
  },
  {
    label:  'Encoding & Decoding',
    path:   '/encoding',
    sprint: 11,
    ready:  true,
    icon:   '🔧',
    desc:   'Base64, URL, HTML entities, JWT decoder, binary/hex converter, ROT cipher',
  },
  {
    label:  'Code Utilities',
    path:   '/code-utils',
    sprint: 12,
    ready:  true,
    icon:   '💻',
    desc:   'JSON formatter, YAML↔JSON, CSV↔JSON, Markdown renderer',
  },
  {
    label:  'Web Dev Helpers',
    path:   '/web-dev',
    sprint: 13,
    ready:  true,
    icon:   '🛠️',
    desc:   'CSS gradients, box-shadows, Flexbox, robots.txt, favicons, HTTP header viewer, REST tester',
  },
  {
    label:  'Image Tools',
    path:   '/image-tools',
    sprint: 14,
    ready:  true,
    icon:   '🖼️',
    desc:   'Resize, convert PNG/JPG/WebP, compress, crop, rotate, and filter — all client-side',
  },
  {
    label:  'Dev Utilities',
    path:   '/dev-utils',
    sprint: 15,
    ready:  true,
    icon:   '🧑‍💻',
    desc:   'Hash identifier, API key generator, QR code generator, and cron expression builder',
  },
  {
    label:  'Time Utilities',
    path:   '/time-utils',
    sprint: 16,
    ready:  true,
    icon:   '🕐',
    desc:   'Timezone converter and Unix timestamp ↔ human date — all via the native Intl API',
  },
  {
    label:  'API Formatter',
    path:   '/formatter',
    sprint: 21,
    ready:  true,
    icon:   '⚡',
    desc:   'Format, validate, minify and analyse JSON, XML, and YAML — with JSON Schema support',
  },
]

// Quick lookup by path for rendering a favorite's corresponding feature card.
const FEATURES_BY_PATH = Object.fromEntries(FEATURES.map(f => [f.path, f]))

// ── Small subcomponents ─────────────────────────────────────────────────────

/**
 * StarToggle — small button overlaid on a feature card that toggles the
 * favorite state. stopPropagation() is critical: without it, clicking the
 * star also triggers the card's navigation handler.
 */
function StarToggle({ active, onClick, label }) {
  function handleClick(e) {
    e.preventDefault()
    e.stopPropagation()
    onClick()
  }
  return (
    <button
      type="button"
      className={active ? styles.starButtonActive : styles.starButton}
      onClick={handleClick}
      aria-pressed={active}
      aria-label={label}
      title={label}
    >
      {active ? '★' : '☆'}
    </button>
  )
}

/**
 * ReorderControls — stacked up/down arrow buttons for keyboard-accessible
 * favorite reordering. Hidden on non-favorite cards.
 *
 * Disabling the up arrow for index 0 and the down arrow for the last index
 * follows the standard list-control affordance — users shouldn't have to
 * click to find out they can't move further.
 */
function ReorderControls({ index, lastIndex, onMove }) {
  function handle(e, dir) {
    e.preventDefault()
    e.stopPropagation()
    onMove(dir)
  }
  return (
    <div className={styles.reorderControls} aria-hidden={false}>
      <button
        type="button"
        className={styles.reorderBtn}
        onClick={e => handle(e, -1)}
        disabled={index === 0}
        aria-label="Move up"
        title="Move up"
      >
        ↑
      </button>
      <button
        type="button"
        className={styles.reorderBtn}
        onClick={e => handle(e, +1)}
        disabled={index === lastIndex}
        aria-label="Move down"
        title="Move down"
      >
        ↓
      </button>
    </div>
  )
}

// ── Array helper ───────────────────────────────────────────────────────────
//
// Returns a new array with the element at `from` moved to position `to`.
// Used by the up/down buttons and by the drag-end handler.
function moveInArray(arr, from, to) {
  if (from === to) return arr
  if (from < 0 || from >= arr.length) return arr
  if (to < 0   || to >= arr.length)   return arr
  const next = arr.slice()
  const [item] = next.splice(from, 1)
  next.splice(to, 0, item)
  return next
}

// ── Main component ────────────────────────────────────────────────────────

export default function DashboardPage() {
  const { username, logout, role } = useAuth()   // add `role`
  const isAdmin = role === 'admin'
  const navigate = useNavigate()
  const isGuest  = username === 'Guest User'

  const {
    favorites,
    error: favoritesError,
    isFavorited,
    toggleFavorite,
    reorderFavorites,
    isGuestBacked,
  } = useFavorites(username)

  // ── Drag-and-drop reorder state ──────────────────────────────────────────
  //
  // We track both the source index (which card is being dragged) and the
  // target index (which card it's currently over) so we can highlight the
  // drop target. Native HTML5 DnD gives us these via dragstart / dragover /
  // drop / dragend events — no library needed.
  const [dragFromIndex, setDragFromIndex] = useState(null)
  const [dragOverIndex, setDragOverIndex] = useState(null)
  const [toolStatuses, setToolStatuses] = useState({})  // { "/calculator": true, ... }

  const [localError, setLocalError] = useState('')

  useEffect(() => {
   fetchToolStatus()
     .then(({ data }) => {
       if (data?.success && data.data?.toggles) {
         setToolStatuses(data.data.toggles)
       }
     })
     .catch(() => {}) // fail silently — all tools default to enabled
  }, [])

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore network errors */ }
    logout()
    navigate('/login')
  }

  // ── Split features into Favorite / Other for the two sections ───────────
  const { favoriteFeatures, otherFeatures } = useMemo(() => {
    const favSet = new Set(favorites)
    const favFeats = favorites
      .map(path => FEATURES_BY_PATH[path])
      .filter(Boolean)                   // drop any unknown path defensively
    const otherFeats = FEATURES.filter(f => !favSet.has(f.path))
    return { favoriteFeatures: favFeats, otherFeatures: otherFeats }
  }, [favorites])

  // ── Star click handler with error surfacing ─────────────────────────────
  async function handleToggleFavorite(path) {
    const result = await toggleFavorite(path)
    if (!result.ok && result.error) {
      setLocalError(result.error)
      // Clear the error after 4 s so it doesn't linger indefinitely.
      setTimeout(() => setLocalError(''), 4000)
    } else {
      setLocalError('')
    }
  }

  // ── Up/down reorder ─────────────────────────────────────────────────────
  async function handleMoveFavorite(fromIndex, delta) {
    const toIndex = fromIndex + delta
    const reordered = moveInArray(favorites, fromIndex, toIndex)
    const result = await reorderFavorites(reordered)
    if (!result.ok && result.error) {
      setLocalError(result.error)
      setTimeout(() => setLocalError(''), 4000)
    } else {
      setLocalError('')
    }
  }

  // ── Drag and drop ───────────────────────────────────────────────────────
  function handleDragStart(index) {
    return (e) => {
      setDragFromIndex(index)
      // Required for some browsers (Firefox) to recognise the drag.
      if (e.dataTransfer) {
        e.dataTransfer.effectAllowed = 'move'
        // setData requires a non-empty value or Firefox ignores the drag.
        try { e.dataTransfer.setData('text/plain', String(index)) } catch { /* ignore */ }
      }
    }
  }

  function handleDragOver(index) {
    return (e) => {
      // preventDefault is what allows dropping in the first place.
      e.preventDefault()
      if (dragOverIndex !== index) setDragOverIndex(index)
      if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
    }
  }

  function handleDragLeave() {
    setDragOverIndex(null)
  }

  async function handleDrop(targetIndex) {
    if (dragFromIndex === null || dragFromIndex === targetIndex) {
      setDragFromIndex(null)
      setDragOverIndex(null)
      return
    }
    const reordered = moveInArray(favorites, dragFromIndex, targetIndex)
    setDragFromIndex(null)
    setDragOverIndex(null)
    const result = await reorderFavorites(reordered)
    if (!result.ok && result.error) {
      setLocalError(result.error)
      setTimeout(() => setLocalError(''), 4000)
    } else {
      setLocalError('')
    }
  }

  function handleDragEnd() {
    setDragFromIndex(null)
    setDragOverIndex(null)
  }

  // ── Render a single feature card ─────────────────────────────────────────
  //
  // A single rendering function handles all three states (locked / disabled /
  // ready) and both placements (inside Favorites vs inside the main grid).
  // When rendered as a favorite we wrap the card in a draggable container
  // and inject the StarToggle + ReorderControls; when rendered in the main
  // grid we only inject the StarToggle on ready cards.
  function renderFeatureCard(feature, { asFavorite = false, favoriteIndex = -1 } = {}) {
    const locked = isGuest && feature.requiresAuth

    // Locked (guest + requires auth): no favorite affordance shown at all.
    if (locked) {
      return (
        <span
          key={feature.path}
          className={styles.cardLockedWrapper}
          title="Please login to access this resource."
          aria-label={`${feature.label} — login required`}
        >
          <div className={styles.cardLocked} aria-disabled="true">
            <div className={styles.cardIconWrap}>
              <span className={styles.cardIcon} aria-hidden="true">{feature.icon}</span>
            </div>
            <span className={styles.cardLabel}>{feature.label}</span>
            <span className={styles.cardDesc}>{feature.desc}</span>
            <span className={styles.cardLockBadge} aria-hidden="true">🔒 Login required</span>
          </div>
        </span>
      )
    }

    // Not ready (coming soon): no favorite affordance.
    if (!feature.ready) {
      return (
        <div key={feature.path} className={styles.cardDisabled}>
          <div className={styles.cardIconWrap}>
            <span className={styles.cardIcon} aria-hidden="true">{feature.icon}</span>
          </div>
          <span className={styles.cardLabel}>{feature.label}</span>
          <span className={styles.cardDesc}>{feature.desc}</span>
          <span className={styles.cardBadge}>Sprint {feature.sprint}</span>
          {toolStatuses[feature.path] === false && (
           <span className={styles.cardDisabledBadge}>
             {isAdmin ? '⚡ Disabled (admin bypass)' : '🚫 Disabled'}
           </span>
          )}
        </div>
      )
    }

    // Ready — wrap the card in a relative container so we can absolutely-
    // position the star (and, for favorites, the reorder arrows).
    const fav = isFavorited(feature.path)
    const wrapCls = [
      styles.cardWrap,
      asFavorite && dragFromIndex === favoriteIndex ? styles.cardDragging : '',
      asFavorite && dragOverIndex === favoriteIndex && dragFromIndex !== favoriteIndex
        ? styles.cardDragOver : '',
    ].filter(Boolean).join(' ')

    const lastIndex = favoriteFeatures.length - 1

    // Note on the switch from <button> to <div role="button">:
    // The card contains interactive children (the star toggle and, for
    // favorites, the reorder arrows). Nesting interactive elements inside
    // a <button> is invalid HTML. We keep all the button semantics via
    // role="button" + tabIndex + onKeyDown handling for Enter/Space.
    return (
      <div
        key={feature.path}
        className={wrapCls}
        draggable={asFavorite}
        onDragStart={asFavorite ? handleDragStart(favoriteIndex) : undefined}
        onDragOver={asFavorite ? handleDragOver(favoriteIndex) : undefined}
        onDragLeave={asFavorite ? handleDragLeave : undefined}
        onDrop={asFavorite ? () => handleDrop(favoriteIndex) : undefined}
        onDragEnd={asFavorite ? handleDragEnd : undefined}
      >
        <StarToggle
          active={fav}
          onClick={() => handleToggleFavorite(feature.path)}
          label={fav ? `Remove ${feature.label} from favorites` : `Add ${feature.label} to favorites`}
        />
        {asFavorite && favoriteFeatures.length > 1 && (
          <ReorderControls
            index={favoriteIndex}
            lastIndex={lastIndex}
            onMove={(delta) => handleMoveFavorite(favoriteIndex, delta)}
          />
        )}
        <div
          role="button"
          tabIndex={0}
          className={styles.card}
          onClick={() => {
            const isEnabled = toolStatuses[feature.path] !== false  // default true
            if (!isEnabled && !isAdmin) return   // blocked for regular users
            navigate(feature.path)
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault()
              navigate(feature.path)
            }
          }}
        >
          <div className={styles.cardIconWrap}>
            <span className={styles.cardIcon} aria-hidden="true">{feature.icon}</span>
          </div>
          <span className={styles.cardLabel}>{feature.label}</span>
          <span className={styles.cardDesc}>{feature.desc}</span>
        </div>
      </div>
    )
  }

  // ── Render ───────────────────────────────────────────────────────────────
  return (
    <div className={styles.page}>

      <header className={styles.header}>
        <div className={styles.headerTopRow}>
          <div className={styles.brand}>
            <span className={styles.brandMark} aria-hidden="true">#</span>
            <span className={styles.brandName}>UsefulTools</span>
          </div>
          {isAdmin && (
            <button
              className={styles.adminPanelBtn}
              onClick={() => navigate('/admin')}
            >
              ⚙ Admin
            </button>
          )}
        </div>
        <div className={styles.userArea}>
          <ThemePicker />
          <UserMenu username={username} isGuest={isGuest} variant="dark" onLogout={handleLogout} />
        </div>
      </header>

      <section className={styles.hero} aria-label="Welcome">
        <div className={styles.heroInner}>
          <div className={styles.heroBadge}>
            <span className={styles.statusDot} aria-hidden="true" />
            Mission Control · {new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' })}
          </div>
          <h1 className={styles.greeting}>
            Welcome back,{' '}
            <span className={styles.greetingAccent}>{username}</span>.
          </h1>
          <p className={styles.subtitle}>
            {isGuest
              ? 'You are browsing as a guest. Some features require a full account.'
              : 'Select a tool below to get started.'}
          </p>
        </div>
      </section>

      <main className={styles.main}>

        {/* ── Recent Activity (non-guest only) ─────────────────────────── */}
        {!isGuest && <RecentActivity />}

        {/* ── Favorites section ────────────────────────────────────────── */}
        {favoriteFeatures.length > 0 && (
          <section
            className={styles.favoritesSection}
            aria-label="Favorite tools"
          >
            <div className={styles.sectionHeader}>
              <p className={styles.sectionTitle}>
                <span className={styles.sectionIcon} aria-hidden="true">★</span>
                Favorites
                <span className={styles.favoriteCount}>
                  {favoriteFeatures.length}
                </span>
              </p>
              <span className={styles.favoriteHint}>
                {isGuestBacked
                  ? 'Guest favorites are stored in this browser only.'
                  : 'Drag cards to reorder, or use the ↑↓ buttons.'}
              </span>
            </div>

            {(favoritesError || localError) && (
              <div className={styles.errorBanner} role="alert">
                {localError || favoritesError}
              </div>
            )}

            <div className={styles.grid}>
              {favoriteFeatures.map((f, i) =>
                renderFeatureCard(f, { asFavorite: true, favoriteIndex: i })
              )}
            </div>
          </section>
        )}

        {/* ── Main tool grid ───────────────────────────────────────────── */}
        <p className={styles.sectionTitle}>
          {favoriteFeatures.length > 0 ? 'All tools' : 'Available tools'}
        </p>
        <div className={styles.grid}>
          {(favoriteFeatures.length > 0 ? otherFeatures : FEATURES).map(feature =>
            renderFeatureCard(feature)
          )}
        </div>

      </main>

    </div>
  )
}
