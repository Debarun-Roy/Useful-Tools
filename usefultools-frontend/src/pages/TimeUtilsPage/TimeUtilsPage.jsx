import { useState, useMemo, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logoutUser } from '../../api/apiClient'
import UserMenu from '../../components/UserMenu/UserMenu'
import { logActivity } from '../../utils/logActivity'
import styles from './TimeUtilsPage.module.css'
import { trackTool } from '../../utils/logMetric'

/*
 * TimeUtilsPage — Sprint 16
 *
 * Two time-related tools on one page:
 *
 *   1. Timezone Converter
 *      Convert a specific datetime from any IANA timezone to one or more
 *      target timezones. Uses the native Intl API — zero extra packages.
 *
 *   2. Timestamp ↔ Date
 *      • Unix timestamp → human-readable date in any timezone
 *      • Human date/time → Unix timestamp (seconds + milliseconds)
 *
 * Everything is client-side; no data leaves the browser.
 */

// ─── Timezone list ────────────────────────────────────────────────────────

/**
 * Returns the list of IANA timezone strings supported by the runtime.
 * Falls back to a curated list of the ~40 most commonly used zones on
 * older browsers / Safari that don't support Intl.supportedValuesOf yet.
 */
function getTimezones() {
  try {
    if (typeof Intl.supportedValuesOf === 'function') {
      return Intl.supportedValuesOf('timeZone')
    }
  } catch { /* ignore */ }

  // Fallback: a representative set covering most use cases
  return [
    'UTC',
    'America/New_York', 'America/Chicago', 'America/Denver',
    'America/Los_Angeles', 'America/Anchorage', 'America/Phoenix',
    'America/Toronto', 'America/Vancouver', 'America/Mexico_City',
    'America/Sao_Paulo', 'America/Argentina/Buenos_Aires',
    'America/Bogota', 'America/Lima',
    'Europe/London', 'Europe/Paris', 'Europe/Berlin', 'Europe/Rome',
    'Europe/Madrid', 'Europe/Amsterdam', 'Europe/Stockholm',
    'Europe/Warsaw', 'Europe/Istanbul', 'Europe/Moscow',
    'Africa/Cairo', 'Africa/Nairobi', 'Africa/Lagos',
    'Africa/Johannesburg',
    'Asia/Kolkata', 'Asia/Dubai', 'Asia/Karachi', 'Asia/Dhaka',
    'Asia/Bangkok', 'Asia/Singapore', 'Asia/Shanghai', 'Asia/Tokyo',
    'Asia/Seoul', 'Asia/Jakarta', 'Asia/Riyadh', 'Asia/Tehran',
    'Australia/Sydney', 'Australia/Melbourne', 'Australia/Perth',
    'Pacific/Auckland', 'Pacific/Honolulu', 'Pacific/Fiji',
  ]
}

const ALL_TIMEZONES = getTimezones()

// Helper: format a Date in a given timezone
function formatInTZ(date, tz, opts = {}) {
  try {
    return new Intl.DateTimeFormat('en-GB', {
      timeZone: tz,
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
      hour12: false,
      ...opts,
    }).format(date)
  } catch {
    return 'Invalid timezone'
  }
}

// Helper: get UTC offset string for a timezone at a specific date
function getUtcOffset(tz, date = new Date()) {
  try {
    const utcDate = new Date(date.toLocaleString('en-US', { timeZone: 'UTC' }))
    const tzDate  = new Date(date.toLocaleString('en-US', { timeZone: tz }))
    const diffMin = (tzDate - utcDate) / 60000
    const sign    = diffMin >= 0 ? '+' : '-'
    const abs     = Math.abs(diffMin)
    const h       = String(Math.floor(abs / 60)).padStart(2, '0')
    const m       = String(abs % 60).padStart(2, '0')
    return `UTC${sign}${h}:${m}`
  } catch {
    return ''
  }
}

// ─── Timezone search select ─────────────────────────────────────────────────

function TZSelect({ value, onChange, id, label }) {
  const [q, setQ] = useState('')
  const [open, setOpen] = useState(false)

  const filtered = useMemo(() =>
    q.trim()
      ? ALL_TIMEZONES.filter(tz =>
          tz.toLowerCase().includes(q.toLowerCase())
        ).slice(0, 60)
      : ALL_TIMEZONES,
    [q]
  )

  const choose = useCallback((tz) => {
    onChange(tz)
    setQ('')
    setOpen(false)
  }, [onChange])

  return (
    <div className={styles.tzSelect}>
      {label && <label className={styles.fieldLabel} htmlFor={id}>{label}</label>}
      <div className={styles.tzInputWrap}>
        <input
          id={id}
          type="text"
          className={styles.tzInput}
          value={open ? q : value}
          placeholder={open ? 'Search timezones…' : 'Select timezone'}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          onChange={e => setQ(e.target.value)}
        />
        <span className={styles.tzArrow} aria-hidden="true">▾</span>
      </div>
      {open && (
        <ul className={styles.tzDropdown} role="listbox">
          {filtered.length === 0
            ? <li className={styles.tzNoResult}>No results</li>
            : filtered.map(tz => (
                <li
                  key={tz}
                  className={tz === value ? styles.tzOptionActive : styles.tzOption}
                  role="option"
                  aria-selected={tz === value}
                  onMouseDown={() => choose(tz)}
                >
                  <span className={styles.tzName}>{tz}</span>
                  <span className={styles.tzOffset}>{getUtcOffset(tz)}</span>
                </li>
              ))
          }
        </ul>
      )}
    </div>
  )
}

// ─── Timezone Converter tab ─────────────────────────────────────────────────

// Local browser TZ as default
const LOCAL_TZ = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'

// Format a Date as a local datetime-local string (YYYY-MM-DDTHH:mm)
function toDatetimeLocal(date) {
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const COMMON_TARGET_TZS = [
  'UTC', 'America/New_York', 'America/Los_Angeles',
  'Europe/London', 'Europe/Paris', 'Europe/Berlin',
  'Asia/Kolkata', 'Asia/Singapore', 'Asia/Tokyo', 'Australia/Sydney',
]

function TimezoneConverterTool() {
  const now = new Date()
  const [datetimeLocal, setDatetimeLocal] = useState(toDatetimeLocal(now))
  const [sourceTZ, setSourceTZ]   = useState(LOCAL_TZ)
  const [targetTZs, setTargetTZs] = useState(['UTC', 'America/New_York', 'Europe/London', 'Asia/Kolkata'])
  const [addTZ, setAddTZ]         = useState('America/Los_Angeles')

  // Parse the datetime-local value as if it's in sourceTZ
  const parsedDate = useMemo(() => {
    if (!datetimeLocal) return null
    try {
      // datetimeLocal is "YYYY-MM-DDTHH:mm" — we interpret it in sourceTZ
      // by formatting a UTC date until it matches in the sourceTZ.
      // Strategy: use Date.parse as if it's local, then adjust for TZ offset.
      const localDate = new Date(datetimeLocal)
      if (isNaN(localDate)) return null

      // Get what the local time looks like in sourceTZ
      const localOffset = localDate.getTimezoneOffset() // browser TZ offset in min
      const tzDate = new Date(localDate.toLocaleString('en-US', { timeZone: sourceTZ }))
      const diff = localDate - tzDate  // ms difference
      const corrected = new Date(localDate.getTime() + diff)
      return corrected
    } catch {
      return null
    }
  }, [datetimeLocal, sourceTZ])

  function addTargetTZ() {
    if (!addTZ || targetTZs.includes(addTZ)) return
    setTargetTZs(prev => [...prev, addTZ])
  }

  function removeTZ(tz) {
    setTargetTZs(prev => prev.filter(t => t !== tz))
  }

  function useNow() {
    setDatetimeLocal(toDatetimeLocal(new Date()))
  }

  function handleConvert() {
    if (!parsedDate) return
    logActivity(
      'time.convert',
      `Converted time from ${sourceTZ} to ${targetTZs.length} timezone${targetTZs.length > 1 ? 's' : ''}`,
      { sourceTZ, targetCount: targetTZs.length }
    )
  }

  const results = useMemo(() => trackTool('time.convert', () => {
    if (!parsedDate) return []
    return targetTZs.map(tz => ({
      tz,
      formatted: formatInTZ(parsedDate, tz),
      offset: getUtcOffset(tz, parsedDate),
    }))
  }), [parsedDate, targetTZs])

  return (
    <div className={styles.tabPanel}>

      {/* Source time input */}
      <div className={styles.sourceBlock}>
        <div className={styles.field}>
          <label className={styles.fieldLabel} htmlFor="datetime-input">
            Date &amp; time
          </label>
          <div className={styles.dateInputRow}>
            <input
              id="datetime-input"
              type="datetime-local"
              className={styles.dateInput}
              value={datetimeLocal}
              onChange={e => setDatetimeLocal(e.target.value)}
            />
            <button className={styles.nowBtn} onClick={useNow} type="button">
              Now
            </button>
          </div>
        </div>

        <TZSelect
          id="source-tz"
          label="In timezone"
          value={sourceTZ}
          onChange={setSourceTZ}
        />
      </div>

      {/* Target TZ management */}
      <div className={styles.targetSection}>
        <div className={styles.targetHeader}>
          <span className={styles.fieldLabel}>Convert to</span>
          <div className={styles.addTZRow}>
            <TZSelect
              id="add-tz"
              value={addTZ}
              onChange={setAddTZ}
            />
            <button
              className={styles.addBtn}
              onClick={addTargetTZ}
              type="button"
            >+ Add</button>
          </div>
        </div>

        {/* Quick-add common zones */}
        <div className={styles.quickZones}>
          {COMMON_TARGET_TZS.filter(z => !targetTZs.includes(z)).map(z => (
            <button
              key={z}
              className={styles.quickZoneChip}
              onClick={() => setTargetTZs(prev => [...prev, z])}
            >
              + {z.split('/').pop()?.replace('_', ' ')}
            </button>
          ))}
        </div>
      </div>

      {/* Results table */}
      {results.length > 0 && parsedDate && (
        <div className={styles.resultsBlock} onClick={handleConvert}>
          <table className={styles.tzTable}>
            <thead>
              <tr>
                <th>Timezone</th>
                <th>Offset</th>
                <th>Converted time</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {results.map(({ tz, formatted, offset }) => (
                <tr key={tz} className={styles.tzRow}>
                  <td className={styles.tzNameCell}>{tz}</td>
                  <td className={styles.tzOffsetCell}>
                    <span className={styles.offsetBadge}>{offset}</span>
                  </td>
                  <td className={styles.tzTimeCell}>
                    <code className={styles.tzTime}>{formatted}</code>
                  </td>
                  <td>
                    <button
                      className={styles.removeBtn}
                      onClick={() => removeTZ(tz)}
                      title="Remove"
                    >×</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {!parsedDate && (
        <div className={styles.placeholderCard}>
          <p className={styles.placeholderTitle}>Enter a date and source timezone above</p>
          <p className={styles.placeholderBody}>
            Results will appear instantly. You can add or remove target timezones
            at any time. The browser's Intl API handles all conversion — no network
            calls needed.
          </p>
        </div>
      )}
    </div>
  )
}

// ─── Timestamp ↔ Date tab ──────────────────────────────────────────────────

const UNIX_EPOCH = new Date(0)
const MAX_TIMESTAMP = 32503680000 // year 3000

function TimestampTool() {
  const [tsInput, setTsInput]   = useState('')
  const [dateInput, setDateInput] = useState('')
  const [displayTZ, setDisplayTZ] = useState(LOCAL_TZ)

  // Timestamp → date
  const tsResult = useMemo(() => trackTool('time.timestamp', () => {
    const raw = tsInput.trim()
    if (!raw) return null
    let n = Number(raw)
    if (isNaN(n)) return { error: 'Not a number' }
    // If the user entered milliseconds (13+ digits), convert to seconds
    const isMs = Math.abs(n) > 1e10
    const ts = isMs ? n : n * 1000
    const date = new Date(ts)
    if (isNaN(date)) return { error: 'Invalid timestamp' }
    return {
      date,
      seconds: isMs ? Math.floor(n / 1000) : n,
      millis: isMs ? n : n * 1000,
      iso: date.toISOString(),
      localFormatted: formatInTZ(date, displayTZ),
      utcFormatted: formatInTZ(date, 'UTC'),
      relative: relativeTime(date),
    }
  }), [tsInput, displayTZ])

  // Date → timestamp
  const dateResult = useMemo(() => trackTool('time.timestamp', () => {
    if (!dateInput) return null
    const date = new Date(dateInput)
    if (isNaN(date)) return { error: 'Invalid date string' }
    const s = Math.floor(date.getTime() / 1000)
    const ms = date.getTime()
    return { seconds: s, millis: ms, iso: date.toISOString() }
  }), [dateInput])

  function relativeTime(date) {
    const diffMs = date - new Date()
    const abs = Math.abs(diffMs)
    const past = diffMs < 0
    if (abs < 60000) return past ? 'just now' : 'in a moment'
    if (abs < 3600000) {
      const m = Math.round(abs / 60000)
      return past ? `${m} min ago` : `in ${m} min`
    }
    if (abs < 86400000) {
      const h = Math.round(abs / 3600000)
      return past ? `${h}h ago` : `in ${h}h`
    }
    const d = Math.round(abs / 86400000)
    return past ? `${d} days ago` : `in ${d} days`
  }

  async function copyText(t) {
    await navigator.clipboard.writeText(String(t))
  }

  function useNowTs() {
    setTsInput(String(Math.floor(Date.now() / 1000)))
  }

  function useNowDate() {
    setDateInput(new Date().toISOString())
  }

  return (
    <div className={styles.tabPanel}>

      {/* Timestamp → Date */}
      <div className={styles.converterSection}>
        <h3 className={styles.sectionHeading}>
          <span className={styles.sectionIcon} aria-hidden="true">🔢</span>
          Unix timestamp → Human date
        </h3>

        <div className={styles.tsInputRow}>
          <div className={styles.field} style={{ flex: 1 }}>
            <label className={styles.fieldLabel} htmlFor="ts-input">
              Timestamp <span className={styles.optional}>(seconds or milliseconds)</span>
            </label>
            <input
              id="ts-input"
              type="text"
              className={styles.monoInput}
              placeholder="e.g. 1704067200"
              value={tsInput}
              onChange={e => { setTsInput(e.target.value) }}
              autoComplete="off"
              spellCheck={false}
            />
          </div>
          <button className={styles.nowBtn} onClick={useNowTs} style={{ alignSelf: 'flex-end' }}>
            Now
          </button>
        </div>

        <TZSelect
          id="display-tz"
          label="Display in timezone"
          value={displayTZ}
          onChange={setDisplayTZ}
        />

        {tsResult?.error && (
          <div className={styles.errorBanner}>{tsResult.error}</div>
        )}

        {tsResult && !tsResult.error && (
          <div className={styles.resultGrid}>
            {[
              { label: 'ISO 8601',         value: tsResult.iso },
              { label: `In ${displayTZ}`,  value: tsResult.localFormatted },
              { label: 'In UTC',           value: tsResult.utcFormatted },
              { label: 'Unix (seconds)',   value: String(tsResult.seconds) },
              { label: 'Unix (ms)',        value: String(tsResult.millis) },
              { label: 'Relative',         value: tsResult.relative, noCopy: true },
            ].map(({ label, value, noCopy }) => (
              <div key={label} className={styles.resultRow}>
                <span className={styles.resultLabel}>{label}</span>
                <code className={styles.resultValue}>{value}</code>
                {!noCopy && (
                  <button className={styles.copyBtn} onClick={() => copyText(value)}>
                    Copy
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      <hr className={styles.divider} />

      {/* Date → Timestamp */}
      <div className={styles.converterSection}>
        <h3 className={styles.sectionHeading}>
          <span className={styles.sectionIcon} aria-hidden="true">📅</span>
          Human date → Unix timestamp
        </h3>

        <div className={styles.tsInputRow}>
          <div className={styles.field} style={{ flex: 1 }}>
            <label className={styles.fieldLabel} htmlFor="date-input">
              Date string <span className={styles.optional}>(ISO 8601, datetime-local, or any parseable format)</span>
            </label>
            <input
              id="date-input"
              type="text"
              className={styles.monoInput}
              placeholder="e.g. 2024-01-01T00:00:00Z  or  2024-01-01 09:30"
              value={dateInput}
              onChange={e => setDateInput(e.target.value)}
              autoComplete="off"
              spellCheck={false}
            />
          </div>
          <button className={styles.nowBtn} onClick={useNowDate} style={{ alignSelf: 'flex-end' }}>
            Now
          </button>
        </div>

        {dateResult?.error && (
          <div className={styles.errorBanner}>{dateResult.error}</div>
        )}

        {dateResult && !dateResult.error && (
          <div className={styles.resultGrid}>
            {[
              { label: 'ISO 8601',       value: dateResult.iso },
              { label: 'Unix (seconds)', value: String(dateResult.seconds) },
              { label: 'Unix (ms)',      value: String(dateResult.millis) },
            ].map(({ label, value }) => (
              <div key={label} className={styles.resultRow}>
                <span className={styles.resultLabel}>{label}</span>
                <code className={styles.resultValue}>{value}</code>
                <button className={styles.copyBtn} onClick={() => copyText(value)}>
                  Copy
                </button>
              </div>
            ))}
          </div>
        )}

        {!dateInput && (
          <p className={styles.tipText}>
            💡 Tip: Append <code>Z</code> to treat the date as UTC.
            Without it, the browser interprets it as local time.
          </p>
        )}
      </div>
    </div>
  )
}

// ─── Page shell ──────────────────────────────────────────────────────────────

const TABS = [
  { id: 'timezone',  label: 'Timezone Converter', icon: '🌍' },
  { id: 'timestamp', label: 'Timestamp ↔ Date',   icon: '🔢' },
]

export default function TimeUtilsPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState('timezone')
  const isGuest = username === 'Guest User'

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  function renderTab() {
    switch (activeTab) {
      case 'timezone':  return <TimezoneConverterTool />
      case 'timestamp': return <TimestampTool />
      default:          return <TimezoneConverterTool />
    }
  }

  return (
    <div className={styles.page}>

      {/* ── Header ──────────────────────────────────────────────────── */}
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.brand}>
            <span className={styles.brandMark} aria-hidden="true">⏱</span>
            <span className={styles.brandName}>UsefulTools</span>
          </div>
          <button className={styles.backBtn} onClick={() => navigate('/dashboard')}>
            ← Dashboard
          </button>
        </div>
        <div className={styles.headerRight}>
          <UserMenu username={username} isGuest={isGuest} variant="light" />
          <button className={styles.logoutBtn} onClick={handleLogout}>Sign out</button>
        </div>
      </header>

      {/* ── Hero ────────────────────────────────────────────────────── */}
      <section className={styles.hero}>
        <div className={styles.heroGrid} aria-hidden="true" />
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>Time Utilities</div>
          <h1 className={styles.heroTitle}>
            Time<br />
            <span className={styles.heroAccent}>Utilities</span>
          </h1>
          <p className={styles.heroSub}>
            Convert times across any IANA timezone and translate between Unix
            timestamps and human-readable dates. All Intl API — no packages, no server calls.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div className={styles.statCard}>
            <span className={styles.statValue}>{ALL_TIMEZONES.length}+</span>
            <span className={styles.statLabel}>timezones</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>0</span>
            <span className={styles.statLabel}>server calls</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>∞</span>
            <span className={styles.statLabel}>precision</span>
          </div>
        </div>
      </section>

      <main className={styles.main}>
        {/* ── Tab bar ─────────────────────────────────────────────── */}
        <nav className={styles.tabBar} aria-label="Time utilities">
          {TABS.map(tab => (
            <button
              key={tab.id}
              className={activeTab === tab.id ? styles.tabActive : styles.tab}
              onClick={() => setActiveTab(tab.id)}
            >
              <span className={styles.tabIcon} aria-hidden="true">{tab.icon}</span>
              {tab.label}
            </button>
          ))}
        </nav>

        <div className={styles.content}>
          {renderTab()}
        </div>
      </main>
    </div>
  )
}
