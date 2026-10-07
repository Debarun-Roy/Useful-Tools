/**
 * UnitConverterPage — Sprint 17 tool, migrated in Sprint 26 off its static
 * CATEGORIES object onto the `units` database table.
 *
 * ── Data source (Sprint 26) ──────────────────────────────────────────────
 * Previously this file hardcoded a ~200-line CATEGORIES object containing
 * every unit's label/symbol/factor. That's now server data: GET
 * /api/units/list returns the full reference table once on mount, and
 * everything below (tabs, filters, the combobox, the browse table) is
 * derived from that fetched array. Adding, correcting, or recategorizing a
 * unit is now a database edit, not a frontend redeploy.
 *
 * The one thing that DIDN'T move to the database: unit-type icons (📏, ⚖️,
 * etc.) and the preferred display ordering for tabs/category chips. Those
 * are presentation concerns, not data, so they stay here as lookup tables
 * (UNIT_TYPE_ICONS, UNIT_TYPE_ORDER, CATEGORY_ORDER) with sane fallbacks —
 * a brand-new unit_type added straight to the database still renders
 * correctly, just with a generic icon and alphabetical placement until
 * this file is updated to recognize it by name.
 *
 * ── Conversion formula (Sprint 26) ───────────────────────────────────────
 * Every unit row now carries both `factor` and `offset`. The generic
 * formula handles every unit_type, including Temperature, with no
 * special-casing:
 *
 *     base   = value * fromUnit.factor + fromUnit.offset
 *     result = (base - toUnit.offset) / toUnit.factor
 *
 * A plain multiplicative factor can't represent Celsius/Fahrenheit/
 * Kelvin/Rankine converting correctly, since those scales have different
 * zero points — offset defaults to 0 for every non-temperature unit, so
 * for them this reduces exactly to the old value*factorA/factorB formula.
 * The previous convertTemperature() special case has been removed
 * entirely; Kelvin is the anchor for Temperature (factor=1, offset=0),
 * matching the convention that every unit_type's SI unit is its factor=1
 * row.
 *
 * ── Three modes of navigating ~164 units (Sprint 26) ─────────────────────
 *   1. Unit-type tabs (Length, Mass, ...) — same as before, still the
 *      PRIMARY navigation for the converter, since unit_type is what makes
 *      two units convertible at all (dimensional compatibility).
 *   2. An optional category filter chip row WITHIN the active unit-type
 *      tab (All / SI / Imperial / Scientific / ...) — narrows the from/to
 *      combobox options without leaving the converter.
 *   3. A separate "Browse Units" mode — a flat, independently-filterable
 *      (unit_type AND category, no pairing required) searchable reference
 *      table across all units. This is where "show me everything in one
 *      category, regardless of type" lives, since that view doesn't try to
 *      pair a from/to conversion — mixing unit_types there would produce
 *      dimensionally meaningless pairs (e.g. Å and Planck mass share the
 *      "Scientific" category but obviously can't convert to each other).
 */

import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logoutUser, fetchUnits } from '../../api/apiClient'
import UserMenu from '../../components/UserMenu/UserMenu'
import styles from './UnitConverterPage.module.css'
import { trackTool } from '../../utils/logMetric'
import { logActivity } from '../../utils/logActivity'

// ── Presentation-only lookups (not data — see file docblock) ───────────────

const UNIT_TYPE_ICONS = {
  Length: '📏',
  Mass: '⚖️',
  Temperature: '🌡️',
  Time: '⏱️',
  'Data Size': '💾',
  Speed: '💨',
  Acceleration: '🚀',
  Area: '📐',
  Volume: '🧊',
  Force: '💪',
}
const DEFAULT_UNIT_TYPE_ICON = '🔢'

const UNIT_TYPE_ORDER = [
  'Length', 'Mass', 'Temperature', 'Time', 'Data Size',
  'Speed', 'Acceleration', 'Area', 'Volume', 'Force',
]
const CATEGORY_ORDER = [
  'SI', 'Imperial', 'Scientific', 'Common', 'Ocean Navigation', 'Binary', 'Decimal',
]

/** Sorts `values` by `order`; anything not in `order` is appended alphabetically. */
function sortByPreferredOrder(values, order) {
  return [...values].sort((a, b) => {
    const ia = order.indexOf(a)
    const ib = order.indexOf(b)
    if (ia === -1 && ib === -1) return a.localeCompare(b)
    if (ia === -1) return 1
    if (ib === -1) return -1
    return ia - ib
  })
}

// ── Conversion logic ──────────────────────────────────────────────────────────

/**
 * Generic conversion for any two units of the same unit_type. See the file
 * docblock for why this needs both factor and offset.
 */
function convertGeneric(value, fromUnit, toUnit) {
  if (!fromUnit || !toUnit || !isFinite(value)) return NaN
  const base = value * fromUnit.factor + fromUnit.offset
  return (base - toUnit.offset) / toUnit.factor
}

function formatResult(value) {
  if (!isFinite(value)) return '∞'
  if (isNaN(value)) return '—'
  // Use significant figures to avoid floating-point noise
  const abs = Math.abs(value)
  if (abs === 0) return '0'
  if (abs >= 1e15 || (abs < 1e-6 && abs > 0)) {
    return value.toExponential(8).replace(/\.?0+e/, 'e')
  }
  return parseFloat(value.toPrecision(12)).toString()
}

// ── Searchable unit combobox ────────────────────────────────────────────────
//
// Replaces the old plain <select>. Shows the selected unit's label+symbol
// when idle; typing filters the candidate list (against label, symbol, and
// unitId) live. `units` is whatever subset the caller has already narrowed
// down (current unit-type tab + active category filter chip).

function UnitCombobox({ id, units, value, onChange, placeholder }) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const wrapRef = useRef(null)

  const selected = units.find(u => u.unitId === value) || null
  const displayValue = open ? query : (selected ? `${selected.label} (${selected.symbol})` : '')

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return units
    return units.filter(u =>
      u.label.toLowerCase().includes(q) ||
      u.symbol.toLowerCase().includes(q) ||
      u.unitId.toLowerCase().includes(q)
    )
  }, [units, query])

  useEffect(() => {
    if (!open) return
    function handleOutside(e) {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) {
        setOpen(false)
        setQuery('')
      }
    }
    function handleKey(e) {
      if (e.key === 'Escape') { setOpen(false); setQuery('') }
    }
    document.addEventListener('mousedown', handleOutside)
    document.addEventListener('keydown', handleKey)
    return () => {
      document.removeEventListener('mousedown', handleOutside)
      document.removeEventListener('keydown', handleKey)
    }
  }, [open])

  function selectUnit(u) {
    onChange(u.unitId)
    setOpen(false)
    setQuery('')
  }

  return (
    <div className={styles.comboWrap} ref={wrapRef}>
      <input
        id={id}
        type="text"
        className={styles.unitSelect}
        role="combobox"
        aria-expanded={open}
        aria-autocomplete="list"
        value={displayValue}
        onFocus={e => { setOpen(true); setQuery(''); e.target.select() }}
        onChange={e => { setOpen(true); setQuery(e.target.value) }}
        onKeyDown={e => {
          if (e.key === 'Enter' && filtered.length === 1) {
            selectUnit(filtered[0])
            e.target.blur()
          }
        }}
        placeholder={placeholder}
        autoComplete="off"
      />
      {open && (
        <div className={styles.comboList} role="listbox">
          {filtered.length === 0 && (
            <div className={styles.comboEmpty}>No matching units</div>
          )}
          {filtered.map(u => (
            <button
              type="button"
              key={u.unitId}
              role="option"
              aria-selected={u.unitId === value}
              className={u.unitId === value ? styles.comboOptionActive : styles.comboOption}
              onMouseDown={e => e.preventDefault()}
              onClick={() => selectUnit(u)}
            >
              <span className={styles.comboLabel}>{u.label}</span>
              <span className={styles.comboMeta}>{u.symbol} · {u.category}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

// ── Main component ────────────────────────────────────────────────────────────

export default function UnitConverterPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const isGuest = username === 'Guest User'

  // ── Fetch the unit reference table once ─────────────────────────────────
  const [units, setUnits] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  useEffect(() => {
    fetchUnits()
      .then(({ data }) => {
        if (data.success) setUnits(data.data)
        else setLoadError(data.error || 'Failed to load unit data.')
      })
      .catch(() => setLoadError('Could not reach the server.'))
      .finally(() => setLoading(false))
  }, [])

  const unitTypes = useMemo(
    () => sortByPreferredOrder([...new Set(units.map(u => u.unitType))], UNIT_TYPE_ORDER),
    [units]
  )
  const allCategories = useMemo(
    () => sortByPreferredOrder([...new Set(units.map(u => u.category))], CATEGORY_ORDER),
    [units]
  )

  // ── Mode: convert vs browse ──────────────────────────────────────────────
  const [mode, setMode] = useState('convert')

  // ── Convert mode state ───────────────────────────────────────────────────
  const [unitType, setUnitType] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [fromUnitId, setFromUnitId] = useState('')
  const [toUnitId, setToUnitId] = useState('')
  const [inputVal, setInputVal] = useState('1')

  // Once the fetch resolves, default to the first unit type + first two units.
  const didInit = useRef(false)
  useEffect(() => {
    if (didInit.current || unitTypes.length === 0) return
    didInit.current = true
    const firstType = unitTypes[0]
    const inType = units.filter(u => u.unitType === firstType)
    setUnitType(firstType)
    setFromUnitId(inType[0]?.unitId ?? '')
    setToUnitId(inType[1]?.unitId ?? inType[0]?.unitId ?? '')
  }, [units, unitTypes])

  const unitsInType = useMemo(
    () => units.filter(u => u.unitType === unitType),
    [units, unitType]
  )
  const categoriesInType = useMemo(
    () => sortByPreferredOrder([...new Set(unitsInType.map(u => u.category))], CATEGORY_ORDER),
    [unitsInType]
  )
  const unitsForConverter = useMemo(
    () => (categoryFilter ? unitsInType.filter(u => u.category === categoryFilter) : unitsInType),
    [unitsInType, categoryFilter]
  )

  function handleUnitTypeChange(newType) {
    setUnitType(newType)
    setCategoryFilter('')
    const inType = units.filter(u => u.unitType === newType)
    setFromUnitId(inType[0]?.unitId ?? '')
    setToUnitId(inType[1]?.unitId ?? inType[0]?.unitId ?? '')
    setInputVal('1')
  }

  function handleCategoryFilterChange(newFilter) {
    setCategoryFilter(newFilter)
    const filtered = newFilter ? unitsInType.filter(u => u.category === newFilter) : unitsInType
    if (!filtered.some(u => u.unitId === fromUnitId)) {
      setFromUnitId(filtered[0]?.unitId ?? '')
    }
    if (!filtered.some(u => u.unitId === toUnitId)) {
      setToUnitId(filtered[1]?.unitId ?? filtered[0]?.unitId ?? '')
    }
  }

  function handleSwap() {
    setFromUnitId(toUnitId)
    setToUnitId(fromUnitId)
  }

  const fromUnit = unitsInType.find(u => u.unitId === fromUnitId) || null
  const toUnit   = unitsInType.find(u => u.unitId === toUnitId) || null

  const numericInput = parseFloat(inputVal)
  const resultValue  = (fromUnit && toUnit)
    ? trackTool('converter.convert', () => convertGeneric(numericInput, fromUnit, toUnit))
    : NaN
  const resultStr  = formatResult(resultValue)
  const fromSymbol = fromUnit?.symbol ?? ''
  const toSymbol   = toUnit?.symbol ?? ''

  // Log activity once the user has settled on a real conversion. logActivity
  // is debounced per-tool (1500 ms) so rapid input changes coalesce into one
  // entry capturing the LAST settled state. Skip the initial mount so just
  // opening the page doesn't record an entry.
  const didMount = useRef(false)
  useEffect(() => {
    if (!didMount.current) { didMount.current = true; return }
    if (!fromUnit || !toUnit) return
    if (!isFinite(resultValue) || isNaN(resultValue)) return
    if (!isFinite(numericInput)) return
    logActivity(
      'converter.convert',
      `Converted ${inputVal} ${fromSymbol} → ${resultStr} ${toSymbol} (${unitType})`,
      { unitType, fromUnit: fromUnitId, toUnit: toUnitId }
    )
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inputVal, fromUnitId, toUnitId, unitType, resultValue, resultStr, fromSymbol, toSymbol, numericInput])

  // ── Browse mode state ────────────────────────────────────────────────────
  const [browseType, setBrowseType] = useState('')
  const [browseCategory, setBrowseCategory] = useState('')
  const [browseSearch, setBrowseSearch] = useState('')

  const browseFiltered = useMemo(() => {
    let list = units
    if (browseType) list = list.filter(u => u.unitType === browseType)
    if (browseCategory) list = list.filter(u => u.category === browseCategory)
    const q = browseSearch.trim().toLowerCase()
    if (q) {
      list = list.filter(u =>
        u.label.toLowerCase().includes(q) ||
        u.symbol.toLowerCase().includes(q) ||
        u.unitId.toLowerCase().includes(q)
      )
    }
    return list
  }, [units, browseType, browseCategory, browseSearch])

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  return (
    <div className={styles.page}>

      {/* ── Header ─────────────────────────────────────────────────── */}
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.brand}>
            <span className={styles.brandMark} aria-hidden="true">🔄</span>
            <span className={styles.brandName}>UsefulTools</span>
          </div>
          <button className={styles.backBtn} onClick={() => navigate('/dashboard')}>
            Dashboard
          </button>
        </div>
        <div className={styles.headerRight}>
          <UserMenu username={username} isGuest={isGuest} variant="light" onLogout={handleLogout} />
        </div>
      </header>

      {/* ── Hero ───────────────────────────────────────────────────── */}
      <section className={styles.hero}>
        <div className={styles.heroGrid} aria-hidden="true" />
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>Unit Converter</div>
          <h1 className={styles.heroTitle}>
            Unit<br />
            <span className={styles.heroAccent}>Converter</span>
          </h1>
          <p className={styles.heroSub}>
            Instant conversion across {unitTypes.length || '—'} categories —
            all calculated client-side.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div className={styles.statCard}>
            <span className={styles.statValue}>{unitTypes.length || '—'}</span>
            <span className={styles.statLabel}>categories</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>{units.length || '—'}</span>
            <span className={styles.statLabel}>units</span>
          </div>
        </div>
      </section>

      <main className={styles.main}>

        {loading && (
          <div className={styles.loading}>Loading unit data…</div>
        )}

        {!loading && loadError && (
          <div className={styles.errorBanner} role="alert">{loadError}</div>
        )}

        {!loading && !loadError && (
          <>
            {/* ── Mode toggle ──────────────────────────────────────── */}
            <div className={styles.modeToggle} role="tablist" aria-label="Unit converter mode">
              <button
                role="tab"
                aria-selected={mode === 'convert'}
                className={mode === 'convert' ? styles.modeBtnActive : styles.modeBtn}
                onClick={() => setMode('convert')}
              >
                🔄 Convert
              </button>
              <button
                role="tab"
                aria-selected={mode === 'browse'}
                className={mode === 'browse' ? styles.modeBtnActive : styles.modeBtn}
                onClick={() => setMode('browse')}
              >
                📋 Browse Units
              </button>
            </div>

            {mode === 'convert' && (
              <>
                {/* ── Unit-type tabs ─────────────────────────────── */}
                <nav className={styles.categoryTabs} aria-label="Unit categories">
                  {unitTypes.map(t => (
                    <button
                      key={t}
                      className={unitType === t ? styles.catTabActive : styles.catTab}
                      onClick={() => handleUnitTypeChange(t)}
                    >
                      <span aria-hidden="true">{UNIT_TYPE_ICONS[t] ?? DEFAULT_UNIT_TYPE_ICON}</span>
                      {t}
                    </button>
                  ))}
                </nav>

                {/* ── Category filter chips (within the active tab) ─ */}
                {categoriesInType.length > 1 && (
                  <div className={styles.categoryChips} role="group" aria-label="Filter by category">
                    <button
                      className={categoryFilter === '' ? styles.chipActive : styles.chip}
                      onClick={() => handleCategoryFilterChange('')}
                    >
                      All
                    </button>
                    {categoriesInType.map(c => (
                      <button
                        key={c}
                        className={categoryFilter === c ? styles.chipActive : styles.chip}
                        onClick={() => handleCategoryFilterChange(c)}
                      >
                        {c}
                      </button>
                    ))}
                  </div>
                )}

                {/* ── Converter card ─────────────────────────────── */}
                <div className={styles.converterCard}>

                  {/* From */}
                  <div className={styles.converterRow}>
                    <div className={styles.converterField}>
                      <label className={styles.fieldLabel} htmlFor="from-unit">From</label>
                      <UnitCombobox
                        id="from-unit"
                        units={unitsForConverter}
                        value={fromUnitId}
                        onChange={setFromUnitId}
                        placeholder="Search units…"
                      />
                    </div>
                    <div className={styles.inputWrap}>
                      <input
                        type="number"
                        className={styles.numInput}
                        value={inputVal}
                        onChange={e => setInputVal(e.target.value)}
                        placeholder="Enter value"
                        aria-label="Value to convert"
                      />
                      <span className={styles.unitTag}>{fromSymbol}</span>
                    </div>
                  </div>

                  {/* Swap button */}
                  <div className={styles.swapRow}>
                    <button
                      className={styles.swapBtn}
                      onClick={handleSwap}
                      aria-label="Swap units"
                      title="Swap from and to units"
                    >
                      ⇅ Swap
                    </button>
                  </div>

                  {/* To */}
                  <div className={styles.converterRow}>
                    <div className={styles.converterField}>
                      <label className={styles.fieldLabel} htmlFor="to-unit">To</label>
                      <UnitCombobox
                        id="to-unit"
                        units={unitsForConverter}
                        value={toUnitId}
                        onChange={setToUnitId}
                        placeholder="Search units…"
                      />
                    </div>
                    <div className={styles.resultWrap}>
                      <span className={styles.resultNum}>{resultStr}</span>
                      <span className={styles.unitTag}>{toSymbol}</span>
                    </div>
                  </div>

                  {/* Formula line */}
                  {fromUnit && toUnit && isFinite(resultValue) && !isNaN(numericInput) && (
                    <div className={styles.formulaLine}>
                      {inputVal} {fromSymbol} = {resultStr} {toSymbol}
                    </div>
                  )}
                </div>

                {/* ── Quick reference table ───────────────────────── */}
                {fromUnit && toUnit && (
                  <div className={styles.referenceCard}>
                    <h3 className={styles.refTitle}>Quick reference — common values</h3>
                    <div className={styles.refTable}>
                      {[1, 10, 100, 1000].map(v => {
                        const res = convertGeneric(v, fromUnit, toUnit)
                        return (
                          <div key={v} className={styles.refRow}
                               onClick={() => setInputVal(String(v))}
                               title="Click to use this value">
                            <span className={styles.refFrom}>
                              {v} {fromSymbol}
                            </span>
                            <span className={styles.refEq}>=</span>
                            <span className={styles.refTo}>
                              {formatResult(res)} {toSymbol}
                            </span>
                          </div>
                        )
                      })}
                    </div>
                  </div>
                )}
              </>
            )}

            {mode === 'browse' && (
              <div className={styles.browsePanel}>
                <div className={styles.browseFilters}>
                  <select
                    className={styles.unitSelect}
                    value={browseType}
                    onChange={e => setBrowseType(e.target.value)}
                    aria-label="Filter by unit type"
                  >
                    <option value="">All types</option>
                    {unitTypes.map(t => <option key={t} value={t}>{t}</option>)}
                  </select>
                  <select
                    className={styles.unitSelect}
                    value={browseCategory}
                    onChange={e => setBrowseCategory(e.target.value)}
                    aria-label="Filter by category"
                  >
                    <option value="">All categories</option>
                    {allCategories.map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                  <input
                    type="text"
                    className={styles.unitSelect}
                    value={browseSearch}
                    onChange={e => setBrowseSearch(e.target.value)}
                    placeholder="Search by name or symbol…"
                    aria-label="Search units"
                  />
                </div>

                <div className={styles.browseTableWrap}>
                  <table className={styles.browseTable}>
                    <thead>
                      <tr>
                        <th>Unit</th>
                        <th>Symbol</th>
                        <th>Type</th>
                        <th>Category</th>
                        <th>Factor</th>
                      </tr>
                    </thead>
                    <tbody>
                      {browseFiltered.map(u => (
                        <tr key={`${u.unitType}-${u.unitId}`}>
                          <td>{u.label}</td>
                          <td className={styles.browseSymbol}>{u.symbol}</td>
                          <td>{u.unitType}</td>
                          <td>{u.category}</td>
                          <td className={styles.browseFactor}>{formatResult(u.factor)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {browseFiltered.length === 0 && (
                    <div className={styles.browseEmpty}>No units match your filters.</div>
                  )}
                </div>
              </div>
            )}
          </>
        )}

      </main>
    </div>
  )
}
