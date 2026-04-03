import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logoutUser } from '../../api/apiClient'
import styles from './UnitConverterPage.module.css'

// ── Conversion data ───────────────────────────────────────────────────────────
// factor = "how many SI base units in 1 unit of this type"
// Base units: m (length), kg (mass), s (time), bits (data), m/s (speed), m² (area)

const CATEGORIES = {
  length: {
    label: 'Length', icon: '📏', baseLabel: 'Meters',
    units: {
      m:   { label: 'Meters',            symbol: 'm',   factor: 1 },
      km:  { label: 'Kilometers',        symbol: 'km',  factor: 1000 },
      cm:  { label: 'Centimeters',       symbol: 'cm',  factor: 0.01 },
      mm:  { label: 'Millimeters',       symbol: 'mm',  factor: 0.001 },
      ft:  { label: 'Feet',              symbol: 'ft',  factor: 0.3048 },
      in:  { label: 'Inches',            symbol: 'in',  factor: 0.0254 },
      yd:  { label: 'Yards',             symbol: 'yd',  factor: 0.9144 },
      mi:  { label: 'Miles',             symbol: 'mi',  factor: 1609.344 },
      nmi: { label: 'Nautical Miles',    symbol: 'nmi', factor: 1852 },
    }
  },
  mass: {
    label: 'Mass', icon: '⚖️', baseLabel: 'Kilograms',
    units: {
      kg:  { label: 'Kilograms',         symbol: 'kg',  factor: 1 },
      g:   { label: 'Grams',             symbol: 'g',   factor: 0.001 },
      mg:  { label: 'Milligrams',        symbol: 'mg',  factor: 1e-6 },
      t:   { label: 'Metric Tons',       symbol: 't',   factor: 1000 },
      lb:  { label: 'Pounds',            symbol: 'lb',  factor: 0.453592 },
      oz:  { label: 'Ounces',            symbol: 'oz',  factor: 0.0283495 },
      st:  { label: 'Stone',             symbol: 'st',  factor: 6.35029 },
    }
  },
  temperature: {
    label: 'Temperature', icon: '🌡️',
    units: {
      C: { label: 'Celsius',    symbol: '°C' },
      F: { label: 'Fahrenheit', symbol: '°F' },
      K: { label: 'Kelvin',     symbol: 'K'  },
    }
  },
  time: {
    label: 'Time', icon: '⏱️', baseLabel: 'Seconds',
    units: {
      ms:   { label: 'Milliseconds',     symbol: 'ms',   factor: 0.001 },
      s:    { label: 'Seconds',          symbol: 's',    factor: 1 },
      min:  { label: 'Minutes',          symbol: 'min',  factor: 60 },
      h:    { label: 'Hours',            symbol: 'h',    factor: 3600 },
      d:    { label: 'Days',             symbol: 'd',    factor: 86400 },
      week: { label: 'Weeks',            symbol: 'wk',   factor: 604800 },
      year: { label: 'Years (365.25d)',  symbol: 'yr',   factor: 31557600 },
    }
  },
  data: {
    label: 'Data Size', icon: '💾', baseLabel: 'Bits',
    units: {
      b:   { label: 'Bits',              symbol: 'b',  factor: 1 },
      B:   { label: 'Bytes',             symbol: 'B',  factor: 8 },
      KB:  { label: 'Kilobytes',         symbol: 'KB', factor: 8192 },
      MB:  { label: 'Megabytes',         symbol: 'MB', factor: 8388608 },
      GB:  { label: 'Gigabytes',         symbol: 'GB', factor: 8589934592 },
      TB:  { label: 'Terabytes',         symbol: 'TB', factor: 8796093022208 },
    }
  },
  speed: {
    label: 'Speed', icon: '💨', baseLabel: 'm/s',
    units: {
      ms:   { label: 'Metres/second',    symbol: 'm/s',   factor: 1 },
      kmh:  { label: 'Kilometres/hour',  symbol: 'km/h',  factor: 1 / 3.6 },
      mph:  { label: 'Miles/hour',       symbol: 'mph',   factor: 0.44704 },
      knot: { label: 'Knots',            symbol: 'kn',    factor: 0.514444 },
      fts:  { label: 'Feet/second',      symbol: 'ft/s',  factor: 0.3048 },
    }
  },
  area: {
    label: 'Area', icon: '📐', baseLabel: 'Square meters',
    units: {
      m2:   { label: 'Sq. Meters',       symbol: 'm²',     factor: 1 },
      km2:  { label: 'Sq. Kilometers',   symbol: 'km²',    factor: 1e6 },
      cm2:  { label: 'Sq. Centimeters',  symbol: 'cm²',    factor: 1e-4 },
      ft2:  { label: 'Sq. Feet',         symbol: 'ft²',    factor: 0.092903 },
      in2:  { label: 'Sq. Inches',       symbol: 'in²',    factor: 0.000645 },
      acre: { label: 'Acres',            symbol: 'ac',     factor: 4046.86 },
      ha:   { label: 'Hectares',         symbol: 'ha',     factor: 10000 },
    }
  },
}

// ── Conversion logic ──────────────────────────────────────────────────────────

function convertTemperature(value, from, to) {
  if (from === to) return value
  // Step 1: to Celsius
  let c
  if (from === 'C') c = value
  else if (from === 'F') c = (value - 32) * 5 / 9
  else c = value - 273.15 // K
  // Step 2: from Celsius to target
  if (to === 'C') return c
  if (to === 'F') return c * 9 / 5 + 32
  return c + 273.15 // K
}

function convert(value, fromKey, toKey, categoryKey) {
  if (isNaN(value)) return NaN
  if (categoryKey === 'temperature') {
    return convertTemperature(value, fromKey, toKey)
  }
  const units = CATEGORIES[categoryKey].units
  return value * units[fromKey].factor / units[toKey].factor
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

// ── Main component ────────────────────────────────────────────────────────────

export default function UnitConverterPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()

  const [category, setCategory] = useState('length')
  const [fromUnit, setFromUnit] = useState('m')
  const [toUnit,   setToUnit]   = useState('km')
  const [inputVal, setInputVal] = useState('1')

  const catData  = CATEGORIES[category]
  const unitKeys = Object.keys(catData.units)

  // When category changes, reset units to first two available.
  function handleCategoryChange(newCat) {
    const keys = Object.keys(CATEGORIES[newCat].units)
    setCategory(newCat)
    setFromUnit(keys[0])
    setToUnit(keys[1] ?? keys[0])
    setInputVal('1')
  }

  function handleSwap() {
    setFromUnit(toUnit)
    setToUnit(fromUnit)
  }

  const numericInput = parseFloat(inputVal)
  const resultValue  = convert(numericInput, fromUnit, toUnit, category)
  const resultStr    = formatResult(resultValue)
  const fromSymbol   = catData.units[fromUnit]?.symbol ?? fromUnit
  const toSymbol     = catData.units[toUnit]?.symbol   ?? toUnit

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
            <span className={styles.brandMark} aria-hidden="true">#</span>
            <span className={styles.brandName}>UsefulTools</span>
          </div>
          <button className={styles.backBtn} onClick={() => navigate('/dashboard')}>
            ← Dashboard
          </button>
        </div>
        <div className={styles.headerRight}>
          <span className={styles.userBadge}>{username}</span>
          <button className={styles.logoutBtn} onClick={handleLogout}>Sign out</button>
        </div>
      </header>

      {/* ── Hero ───────────────────────────────────────────────────── */}
      <section className={styles.hero}>
        <div className={styles.heroGrid} aria-hidden="true" />
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>Sprint 7 · Unit Converter</div>
          <h1 className={styles.heroTitle}>
            Unit<br />
            <span className={styles.heroAccent}>Converter</span>
          </h1>
          <p className={styles.heroSub}>
            Instant conversion across 7 categories: length, mass, temperature,
            time, data size, speed, and area — all calculated client-side.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div className={styles.statCard}>
            <span className={styles.statValue}>7</span>
            <span className={styles.statLabel}>categories</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>50+</span>
            <span className={styles.statLabel}>units</span>
          </div>
        </div>
      </section>

      <main className={styles.main}>

        {/* ── Category tabs ───────────────────────────────────────── */}
        <nav className={styles.categoryTabs} aria-label="Unit categories">
          {Object.entries(CATEGORIES).map(([key, cat]) => (
            <button
              key={key}
              className={category === key ? styles.catTabActive : styles.catTab}
              onClick={() => handleCategoryChange(key)}
            >
              <span aria-hidden="true">{cat.icon}</span>
              {cat.label}
            </button>
          ))}
        </nav>

        {/* ── Converter card ──────────────────────────────────────── */}
        <div className={styles.converterCard}>

          {/* From */}
          <div className={styles.converterRow}>
            <div className={styles.converterField}>
              <label className={styles.fieldLabel}>From</label>
              <select
                className={styles.unitSelect}
                value={fromUnit}
                onChange={e => setFromUnit(e.target.value)}
              >
                {unitKeys.map(k => (
                  <option key={k} value={k}>
                    {catData.units[k].label} ({catData.units[k].symbol})
                  </option>
                ))}
              </select>
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
              <label className={styles.fieldLabel}>To</label>
              <select
                className={styles.unitSelect}
                value={toUnit}
                onChange={e => setToUnit(e.target.value)}
              >
                {unitKeys.map(k => (
                  <option key={k} value={k}>
                    {catData.units[k].label} ({catData.units[k].symbol})
                  </option>
                ))}
              </select>
            </div>
            <div className={styles.resultWrap}>
              <span className={styles.resultNum}>{resultStr}</span>
              <span className={styles.unitTag}>{toSymbol}</span>
            </div>
          </div>

          {/* Formula line */}
          {isFinite(resultValue) && !isNaN(numericInput) && (
            <div className={styles.formulaLine}>
              {inputVal} {fromSymbol} = {resultStr} {toSymbol}
            </div>
          )}
        </div>

        {/* ── Quick reference table ─────────────────────────────────── */}
        <div className={styles.referenceCard}>
          <h3 className={styles.refTitle}>Quick reference — common values</h3>
          <div className={styles.refTable}>
            {[1, 10, 100, 1000].map(v => {
              const res = convert(v, fromUnit, toUnit, category)
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

      </main>
    </div>
  )
}
