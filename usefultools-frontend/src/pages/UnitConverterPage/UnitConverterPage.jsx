import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logoutUser } from '../../api/apiClient'
import UserMenu from '../../components/UserMenu/UserMenu'
import styles from './UnitConverterPage.module.css'
import { trackTool } from '../../utils/logMetric'
import { logActivity } from '../../utils/logActivity'

// ── Conversion data ───────────────────────────────────────────────────────────
// factor = "how many SI base units in 1 unit of this type"
// Base units: m (length), kg (mass), s (time), bits (data), m/s (speed), m² (area)

const CATEGORIES = {
  length: {
    label: 'Length', icon: '📏', baseLabel: 'Meters',
    units: {
      m:   { label: 'Meters',            symbol: 'm',   factor: 1 },
      dam: { label: 'Decameters',        symbol: 'dam', factor: 10 },
      hm:  { label: 'Hectometers',       symbol: 'hm',  factor: 100},
      km:  { label: 'Kilometers',        symbol: 'km',  factor: 1000 },
      Mm:  { label: 'Megameters',        symbol: 'Mm',  factor: 1e6 },
      Gm:  { label: 'Gigameters',        symbol: 'Gm',  factor: 1e9 },
      dm:  { label: 'Decimeters',        symbol: 'dm',  factor: 0.1 },
      cm:  { label: 'Centimeters',       symbol: 'cm',  factor: 0.01 },
      mm:  { label: 'Millimeters',       symbol: 'mm',  factor: 0.001 },
      µm:  { label: 'Micrometers (Microns)', symbol: 'µm', factor: 1e-6 },
      nm:  { label: 'Nanometers',        symbol: 'nm',  factor: 1e-9 },
      pm:  { label: 'Picometers',         symbol: 'pm',  factor: 1e-12 },
      Å:   { label: 'Angstrom',          symbol: 'Å',   factor: 1e-10 },
      fm:  { label: 'Fermi',             symbol: 'fm',  factor: 1e-15 },
      ft:  { label: 'Feet',              symbol: 'ft',  factor: 0.3048 },
      in:  { label: 'Inches',            symbol: 'in',  factor: 0.0254 },
      th:  { label: 'Thou / Mil',        symbol: 'th',  factor: 0.0000254 },
      yd:  { label: 'Yards',             symbol: 'yd',  factor: 0.9144 },
      mi:  { label: 'Miles',             symbol: 'mi',  factor: 1609.344 },
      nmi: { label: 'Nautical Miles',    symbol: 'nmi', factor: 1852 },
      ftm: { label: 'Fathom',            symbol: 'ftm', factor: 1.8288 },
      LD:  { label: 'Lunar Distance',    symbol: 'LD',  factor: 3.844e8 },
      AU:  { label: 'Astronomical Units',symbol: 'AU',  factor: 1.496e11 },
      ly:  { label: 'Light years',       symbol: 'ly',  factor: 9.46e15 },
      pc:  { label: 'Parsec',            symbol: 'pc',  factor: 3.086e16 },
      a0:  { label: 'Bohr Radius',       symbol: 'a₀',  factor: 5.291772109e-11 },
      lp:  { label: 'Planck Length',     symbol: '\(\ell _{p}\)', factor: 1.6e-35 },
    }
  },
  mass: {
    label: 'Mass', icon: '⚖️', baseLabel: 'Kilograms',
    units: {
      kg:  { label: 'Kilograms',         symbol: 'kg',  factor: 1 },
      g:   { label: 'Grams',             symbol: 'g',   factor: 0.001 },
      mg:  { label: 'Milligrams',        symbol: 'mg',  factor: 1e-6 },
      t:   { label: 'Metric Tons',       symbol: 't',   factor: 1000 },
      dag: { label: 'Decagrams',         symbol: 'dag', factor: 0.01 },
      Gg:  { label: 'Gigagrams',         symbol: 'Gg',  factor: 1e6 },
      Tg:  { label: 'Teragrams',         symbol: 'Tg',  factor: 1e9 },
      µg:  { label: 'Micrograms',        symbol: 'µg',  factor: 1e-9 },
      ng:  { label: 'Nanograms',         symbol: 'ng',  factor: 1e-12 },
      pg:  { label: 'Picograms',         symbol: 'pg',  factor: 1e-15 },
      fg:  { label: 'Femtograms',        symbol: 'fg',  factor: 1e-18 },
      lb:  { label: 'Pounds',            symbol: 'lb',  factor: 0.453592 },
      oz:  { label: 'Ounces',            symbol: 'oz',  factor: 0.0283495 },
      st:  { label: 'Stone',             symbol: 'st',  factor: 6.35029 },
      ozt: { label: 'Troy Ounces',       symbol: 'ozt', factor: 0.031103 },
      dwt: { label: 'Pennyweight',       symbol: 'dwt', factor: 0.00155515 },
      mp:  { label: 'Planck Mass',       symbol: '\(m_{P}\)', factor: 2.176e-8 },
      mSun:{ label: 'Solar Mass',        symbol: '\(M_{\odot }\)', factor: 1.989e30 },
      mEarth:{ label: 'Earth Mass',      symbol: '\(M_{\oplus }\)', factor: 5.972e24 },
      q:   { label: 'Quintals',          symbol: 'q',   factor: 100 },
    }
  },
  temperature: {
    label: 'Temperature', icon: '🌡️',
    units: {
      C: { label: 'Celsius',    symbol: '°C' },
      F: { label: 'Fahrenheit', symbol: '°F' },
      K: { label: 'Kelvin',     symbol: 'K'  },
      R: { label: 'Rankine',    symbol: '°R' },

    }
  },
  time: {
    label: 'Time', icon: '⏱️', baseLabel: 'Seconds',
    units: {
      ms:   { label: 'Milliseconds',     symbol: 'ms',   factor: 0.001 },
      μs:   { label: 'Microseconds',     symbol: 'μs',   factor: 1e-6 },
      ns:   { label: 'Nanoseconds',      symbol: 'ns',   factor: 1e-9 },
      ps:   { label: 'Picoseconds',      symbol: 'ps',   factor: 1e-12 },
      s:    { label: 'Seconds',          symbol: 's',    factor: 1 },
      min:  { label: 'Minutes',          symbol: 'min',  factor: 60 },
      h:    { label: 'Hours',            symbol: 'h',    factor: 3600 },
      d:    { label: 'Days',             symbol: 'd',    factor: 86400 },
      week: { label: 'Weeks',            symbol: 'wk',   factor: 604800 },
      year: { label: 'Years (365.25d)',  symbol: 'yr',   factor: 31557600 },
      pt:   { label: 'Planck Time',      symbol: '\(t_{P}\)', factor: 5.39e-44 },
      c:    { label: 'Century',          symbol: 'c',    factor: 3.1556926e9 },
      m:    { label: 'Millenium',        symbol: 'm',    factor: 3.1556926e10 },
      Ma:   { label: 'Mega annum',       symbol: 'Ma',   factor: 3.1556926e13 },
      Ga:   { label: 'Giga annum',       symbol: 'Ga',   factor: 3.1556926e16 },
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
  volume: {
    label: 'Volume', icon: '🧊', baseLabel: 'Cubic meters',
    units: {
      m3:   { label: 'Cu. Meters',       symbol: 'm³',     factor: 1 },
      km3:  { label: 'Cu. Kimometers',   symbol: 'km³',    factor: 1e9 },
      cm3:  { label: 'Cu. Centimeters',  symbol: 'cm³',    factor: 1e-6 },
      ft3:  { label: 'Cu. Feet',         symbol: 'ft³',    factor: 0.028316846592000004 },
      in3:  { label: 'Cu. Inches',       symbol: 'in³',    factor: 0.000016387064 },
    }
  }
}

// ── Conversion logic ──────────────────────────────────────────────────────────

function convertTemperature(value, from, to) {
  if (from === to) return value;
  
  // Step 1: Convert source unit to Celsius
  let c;
  if (from === 'C') {
    c = value;
  } else if (from === 'F') {
    c = (value - 32) * 5 / 9;
  } else if (from === 'K') {
    c = value - 273.15;
  } else if (from === 'R') {
    c = (value - 491.67) * 5 / 9;
  } else {
    throw new Error(`Unsupported input unit: ${from}`);
  }
  
  // Step 2: Convert Celsius to the target unit
  if (to === 'C') return c;
  if (to === 'F') return (c * 9 / 5) + 32;
  if (to === 'K') return c + 273.15;
  if (to === 'R') return (c + 273.15) * 9 / 5;
  
  throw new Error(`Unsupported output unit: ${to}`);
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
  const isGuest = username === 'Guest User'

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
  const resultValue  = trackTool(
    'converter.convert',
    () => convert(numericInput, fromUnit, toUnit, category)
  )
  const resultStr    = formatResult(resultValue)
  const fromSymbol   = catData.units[fromUnit]?.symbol ?? fromUnit
  const toSymbol     = catData.units[toUnit]?.symbol   ?? toUnit

  // Log activity once the user has settled on a real conversion. logActivity
  // is debounced per-tool (1500 ms) so rapid input changes coalesce into one
  // entry capturing the LAST settled state. Skip the initial mount so just
  // opening the page doesn't record an entry.
  const didMount = useRef(false)
  useEffect(() => {
    if (!didMount.current) { didMount.current = true; return }
    if (!isFinite(resultValue) || isNaN(resultValue)) return
    if (!isFinite(numericInput)) return
    logActivity(
      'converter.convert',
      `Converted ${inputVal} ${fromSymbol} → ${resultStr} ${toSymbol} (${catData.label})`,
      { category, fromUnit, toUnit }
    )
  }, [inputVal, fromUnit, toUnit, category, resultValue, resultStr, fromSymbol, toSymbol, catData.label, numericInput])

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
