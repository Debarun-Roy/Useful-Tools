/**
 * ColorToolsPage.jsx — Sprint 22
 *
 * Full color-tools suite. All computation is client-side (backend frozen).
 *
 * Tabs
 *   1. Converter   — HEX ↔ RGB ↔ HSL ↔ HSV ↔ CMYK with live color swatch
 *   2. Palette     — Complementary, Analogous, Triadic, Split-comp, Tetradic,
 *                    Monochromatic palette generator
 *   3. Gradient    — Visual CSS linear/radial gradient editor with code export
 *   4. Accessibility — WCAG AA/AAA contrast checker + delta-E color difference
 *
 * Activity logging : 'color.convert', 'color.palette', 'color.gradient', 'color.accessibility'
 * Metrics          : trackTool wraps the active computation per tab
 *
 * Privacy rule: log only operation type and counts — never raw hex/RGB values
 */

import { useState, useEffect, useMemo, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logoutUser } from '../../api/apiClient'
import UserMenu from '../../components/UserMenu/UserMenu'
import { logActivity } from '../../utils/logActivity'
import { trackTool } from '../../utils/logMetric'
import styles from './ColorToolsPage.module.css'

// ─── Color math ───────────────────────────────────────────────────────────────

function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)) }
function round2(n) { return Math.round(n * 100) / 100 }

/** Parse any of: "#RGB" "#RRGGBB" "rgb(r,g,b)" "hsl(h,s%,l%)" → {r,g,b} or null */
function parseColor(str) {
  if (!str) return null
  str = str.trim()
  // HEX
  const hex6 = str.match(/^#?([0-9a-f]{6})$/i)
  if (hex6) {
    const v = parseInt(hex6[1], 16)
    return { r: (v >> 16) & 255, g: (v >> 8) & 255, b: v & 255 }
  }
  const hex3 = str.match(/^#?([0-9a-f]{3})$/i)
  if (hex3) {
    const [, s] = hex3
    return { r: parseInt(s[0]+s[0], 16), g: parseInt(s[1]+s[1], 16), b: parseInt(s[2]+s[2], 16) }
  }
  // rgb(r, g, b)
  const rgb = str.match(/rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)/i)
  if (rgb) return { r: +rgb[1], g: +rgb[2], b: +rgb[3] }
  // hsl(h, s%, l%)
  const hsl = str.match(/hsl\(\s*(\d+)\s*,\s*(\d+)%?\s*,\s*(\d+)%?\s*\)/i)
  if (hsl) return hslToRgb(+hsl[1], +hsl[2], +hsl[3])
  return null
}

function rgbToHex({ r, g, b }) {
  return '#' + [r, g, b].map(v => clamp(Math.round(v), 0, 255).toString(16).padStart(2, '0')).join('')
}

function rgbToHsl({ r, g, b }) {
  r /= 255; g /= 255; b /= 255
  const max = Math.max(r, g, b), min = Math.min(r, g, b)
  let h, s
  const l = (max + min) / 2
  if (max === min) { h = s = 0 }
  else {
    const d = max - min
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min)
    switch (max) {
      case r: h = ((g - b) / d + (g < b ? 6 : 0)) / 6; break
      case g: h = ((b - r) / d + 2) / 6; break
      default: h = ((r - g) / d + 4) / 6
    }
  }
  return { h: round2(h * 360), s: round2(s * 100), l: round2(l * 100) }
}

function hslToRgb(h, s, l) {
  h /= 360; s /= 100; l /= 100
  let r, g, b
  if (s === 0) { r = g = b = l }
  else {
    const hue2 = (p, q, t) => {
      if (t < 0) t += 1; if (t > 1) t -= 1
      if (t < 1/6) return p + (q - p) * 6 * t
      if (t < 1/2) return q
      if (t < 2/3) return p + (q - p) * (2/3 - t) * 6
      return p
    }
    const q = l < 0.5 ? l * (1 + s) : l + s - l * s
    const p = 2 * l - q
    r = hue2(p, q, h + 1/3)
    g = hue2(p, q, h)
    b = hue2(p, q, h - 1/3)
  }
  return { r: Math.round(r * 255), g: Math.round(g * 255), b: Math.round(b * 255) }
}

function rgbToHsv({ r, g, b }) {
  r /= 255; g /= 255; b /= 255
  const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min
  let h = 0, s = max === 0 ? 0 : d / max, v = max
  if (d !== 0) {
    switch (max) {
      case r: h = ((g - b) / d + (g < b ? 6 : 0)) / 6; break
      case g: h = ((b - r) / d + 2) / 6; break
      default: h = ((r - g) / d + 4) / 6
    }
  }
  return { h: round2(h * 360), s: round2(s * 100), v: round2(v * 100) }
}

function hsvToRgb(h, s, v) {
  h /= 360; s /= 100; v /= 100
  const i = Math.floor(h * 6), f = h * 6 - i
  const p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s)
  let r, g, b
  switch (i % 6) {
    case 0: r=v; g=t; b=p; break; case 1: r=q; g=v; b=p; break
    case 2: r=p; g=v; b=t; break; case 3: r=p; g=q; b=v; break
    case 4: r=t; g=p; b=v; break; default: r=v; g=p; b=q
  }
  return { r: Math.round(r*255), g: Math.round(g*255), b: Math.round(b*255) }
}

function rgbToCmyk({ r, g, b }) {
  r /= 255; g /= 255; b /= 255
  const k = 1 - Math.max(r, g, b)
  if (k === 1) return { c: 0, m: 0, y: 0, k: 100 }
  return {
    c: round2((1 - r - k) / (1 - k) * 100),
    m: round2((1 - g - k) / (1 - k) * 100),
    y: round2((1 - b - k) / (1 - k) * 100),
    k: round2(k * 100),
  }
}

function cmykToRgb(c, m, y, k) {
  c /= 100; m /= 100; y /= 100; k /= 100
  return {
    r: Math.round(255 * (1 - c) * (1 - k)),
    g: Math.round(255 * (1 - m) * (1 - k)),
    b: Math.round(255 * (1 - y) * (1 - k)),
  }
}

// Color name lookup — ~140 CSS named colors mapped to hex
const COLOR_NAMES = {
  '#ff0000':'red','#00ff00':'lime','#0000ff':'blue','#ffff00':'yellow',
  '#ff00ff':'fuchsia','#00ffff':'aqua','#ffffff':'white','#000000':'black',
  '#808080':'gray','#c0c0c0':'silver','#800000':'maroon','#808000':'olive',
  '#008000':'green','#800080':'purple','#008080':'teal','#000080':'navy',
  '#ff8c00':'darkorange','#ffa500':'orange','#ffd700':'gold',
  '#ff69b4':'hotpink','#dc143c':'crimson','#ff6347':'tomato',
  '#ff4500':'orangered','#da70d6':'orchid','#ee82ee':'violet',
  '#9400d3':'darkviolet','#4b0082':'indigo','#6a5acd':'slateblue',
  '#483d8b':'darkslateblue','#7b68ee':'mediumslateblue','#00bfff':'deepskyblue',
  '#1e90ff':'dodgerblue','#4169e1':'royalblue','#191970':'midnightblue',
  '#00ced1':'darkturquoise','#20b2aa':'lightseagreen','#2e8b57':'seagreen',
  '#3cb371':'mediumseagreen','#90ee90':'lightgreen','#adff2f':'greenyellow',
  '#7cfc00':'lawngreen','#32cd32':'limegreen','#228b22':'forestgreen',
  '#006400':'darkgreen','#8fbc8f':'darkseagreen','#f5deb3':'wheat',
  '#deb887':'burlywood','#d2691e':'chocolate','#a0522d':'sienna',
  '#8b4513':'saddlebrown','#ffe4c4':'bisque','#ffdead':'navajowhite',
  '#f4a460':'sandybrown','#bc8f8f':'rosybrown','#f08080':'lightcoral',
}

function findColorName(hex) {
  const key = hex.toLowerCase()
  if (COLOR_NAMES[key]) return COLOR_NAMES[key]
  // Find closest by Euclidean distance in RGB
  const { r, g, b } = parseColor(hex) || { r: 0, g: 0, b: 0 }
  let best = 'unknown', bestDist = Infinity
  for (const [h, name] of Object.entries(COLOR_NAMES)) {
    const c = parseColor(h)
    if (!c) continue
    const d = (c.r-r)**2 + (c.g-g)**2 + (c.b-b)**2
    if (d < bestDist) { bestDist = d; best = name }
  }
  return bestDist < 2000 ? best : `~${best}`
}

// ── WCAG contrast ─────────────────────────────────────────────────────────────

function relativeLuminance({ r, g, b }) {
  const lin = v => {
    v /= 255
    return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4
  }
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
}

function contrastRatio(rgb1, rgb2) {
  const l1 = relativeLuminance(rgb1), l2 = relativeLuminance(rgb2)
  const lighter = Math.max(l1, l2), darker = Math.min(l1, l2)
  return round2((lighter + 0.05) / (darker + 0.05))
}

function wcagLevel(ratio) {
  if (ratio >= 7) return { aa: true, aaa: true, aaLarge: true, aaaLarge: true }
  if (ratio >= 4.5) return { aa: true, aaa: false, aaLarge: true, aaaLarge: true }
  if (ratio >= 3) return { aa: false, aaa: false, aaLarge: true, aaaLarge: false }
  return { aa: false, aaa: false, aaLarge: false, aaaLarge: false }
}

// ── Delta-E (CIE76) ───────────────────────────────────────────────────────────

function rgbToLab({ r, g, b }) {
  // sRGB → XYZ (D65)
  const lin = v => { v /= 255; return v > 0.04045 ? ((v + 0.055) / 1.055) ** 2.4 : v / 12.92 }
  const R = lin(r), G = lin(g), B = lin(b)
  const X = (R*0.4124564 + G*0.3575761 + B*0.1804375) / 0.95047
  const Y = (R*0.2126729 + G*0.7151522 + B*0.0721750) / 1.00000
  const Z = (R*0.0193339 + G*0.1191920 + B*0.9503041) / 1.08883
  const f = t => t > 0.008856 ? t ** (1/3) : 7.787 * t + 16/116
  return { L: 116 * f(Y) - 16, a: 500 * (f(X) - f(Y)), b: 200 * (f(Y) - f(Z)) }
}

function deltaE(rgb1, rgb2) {
  const lab1 = rgbToLab(rgb1), lab2 = rgbToLab(rgb2)
  return round2(Math.sqrt(
    (lab1.L - lab2.L) ** 2 + (lab1.a - lab2.a) ** 2 + (lab1.b - lab2.b) ** 2
  ))
}

// ── Palette generators ────────────────────────────────────────────────────────

function generatePalette(hex, mode) {
  const rgb = parseColor(hex)
  if (!rgb) return []
  const { h, s, l } = rgbToHsl(rgb)

  const make = (hue, sat = s, light = l) => {
    const r = hslToRgb(((hue % 360) + 360) % 360, clamp(sat, 5, 95), clamp(light, 5, 95))
    return rgbToHex(r)
  }

  switch (mode) {
    case 'complementary':
      return [hex, make(h + 180)]
    case 'analogous':
      return [make(h - 30), hex, make(h + 30)]
    case 'triadic':
      return [hex, make(h + 120), make(h + 240)]
    case 'split':
      return [hex, make(h + 150), make(h + 210)]
    case 'tetradic':
      return [hex, make(h + 90), make(h + 180), make(h + 270)]
    case 'monochromatic':
      return [
        make(h, s, clamp(l - 30, 10, 90)),
        make(h, s, clamp(l - 15, 10, 90)),
        hex,
        make(h, s, clamp(l + 15, 10, 90)),
        make(h, s, clamp(l + 30, 10, 90)),
      ]
    default:
      return [hex]
  }
}

const PALETTE_MODES = [
  { id: 'complementary',  label: 'Complementary',  desc: '2 colors — opposite on the wheel' },
  { id: 'analogous',      label: 'Analogous',      desc: '3 colors — adjacent on the wheel' },
  { id: 'triadic',        label: 'Triadic',         desc: '3 colors — evenly spaced' },
  { id: 'split',          label: 'Split-comp',      desc: '3 colors — split complementary' },
  { id: 'tetradic',       label: 'Tetradic',        desc: '4 colors — rectangle on wheel' },
  { id: 'monochromatic',  label: 'Monochromatic',   desc: '5 tints — same hue, varied lightness' },
]

// ── Tabs ──────────────────────────────────────────────────────────────────────

const TABS = [
  { id: 'converter',     label: 'Converter',     icon: '⇄' },
  { id: 'palette',       label: 'Palette',        icon: '◉' },
  { id: 'gradient',      label: 'Gradient',       icon: '▦' },
  { id: 'accessibility', label: 'Accessibility',  icon: '⊙' },
]

// ─── Sub-components ───────────────────────────────────────────────────────────

function TabBar({ tabs, active, onChange }) {
  return (
    <div className={styles.tabBar} role="tablist">
      {tabs.map(t => (
        <button
          key={t.id}
          role="tab"
          aria-selected={active === t.id}
          className={active === t.id ? styles.tabActive : styles.tab}
          onClick={() => onChange(t.id)}
        >
          <span className={styles.tabIcon}>{t.icon}</span>
          {t.label}
        </button>
      ))}
    </div>
  )
}

function ColorSwatch({ hex, size = 'md', label, onClick, copyable = false }) {
  const [copied, setCopied] = useState(false)
  function handleCopy() {
    navigator.clipboard.writeText(hex).then(() => {
      setCopied(true); setTimeout(() => setCopied(false), 1200)
    })
  }
  return (
    <div
      className={`${styles.swatch} ${styles[`swatch_${size}`]}`}
      style={{ background: hex }}
      onClick={copyable ? handleCopy : onClick}
      title={copyable ? `Click to copy ${hex}` : hex}
      role={copyable ? 'button' : undefined}
      tabIndex={copyable ? 0 : undefined}
      onKeyDown={copyable ? e => e.key === 'Enter' && handleCopy() : undefined}
    >
      {copied && <span className={styles.swatchCopied}>✓</span>}
    </div>
  )
}

function FieldRow({ label, children }) {
  return (
    <div className={styles.fieldRow}>
      <label className={styles.fieldLabel}>{label}</label>
      <div className={styles.fieldInputs}>{children}</div>
    </div>
  )
}

function NumInput({ value, onChange, min = 0, max = 255, step = 1, width }) {
  return (
    <input
      type="number"
      className={styles.numInput}
      value={value}
      min={min}
      max={max}
      step={step}
      style={width ? { width } : undefined}
      onChange={e => {
        const v = parseFloat(e.target.value)
        if (!isNaN(v)) onChange(clamp(v, min, max))
      }}
    />
  )
}

// ─── Converter Tab ─────────────────────────────────────────────────────────────

function ConverterTab() {
  const [hexInput, setHexInput] = useState('#3b6fd4')
  const [error, setError] = useState('')
  const [rgb, setRgb]   = useState({ r: 59, g: 111, b: 212 })
  const [hsl, setHsl]   = useState(() => rgbToHsl({ r: 59, g: 111, b: 212 }))
  const [hsv, setHsv]   = useState(() => rgbToHsv({ r: 59, g: 111, b: 212 }))
  const [cmyk, setCmyk] = useState(() => rgbToCmyk({ r: 59, g: 111, b: 212 }))

  function applyRgb(newRgb) {
    const clamped = { r: clamp(Math.round(newRgb.r), 0, 255), g: clamp(Math.round(newRgb.g), 0, 255), b: clamp(Math.round(newRgb.b), 0, 255) }
    setRgb(clamped)
    setHsl(rgbToHsl(clamped))
    setHsv(rgbToHsv(clamped))
    setCmyk(rgbToCmyk(clamped))
    setHexInput(rgbToHex(clamped))
    setError('')
    trackTool('color.convert', () => clamped)
    logActivity('color.convert', 'Converted color', { format: 'rgb' })
  }

  function applyHex(val) {
    setHexInput(val)
    const parsed = parseColor(val)
    if (parsed) { applyRgb(parsed); setError('') }
    else if (val.length >= 4) setError('Invalid hex — try #RRGGBB or #RGB')
  }

  function applyHsl(newHsl) {
    const clamped = { h: clamp(newHsl.h, 0, 360), s: clamp(newHsl.s, 0, 100), l: clamp(newHsl.l, 0, 100) }
    setHsl(clamped)
    const r = hslToRgb(clamped.h, clamped.s, clamped.l)
    setRgb(r); setHsv(rgbToHsv(r)); setCmyk(rgbToCmyk(r)); setHexInput(rgbToHex(r))
    trackTool('color.convert', () => r)
    logActivity('color.convert', 'Converted color', { format: 'hsl' })
  }

  function applyHsv(newHsv) {
    const clamped = { h: clamp(newHsv.h, 0, 360), s: clamp(newHsv.s, 0, 100), v: clamp(newHsv.v, 0, 100) }
    setHsv(clamped)
    const r = hsvToRgb(clamped.h, clamped.s, clamped.v)
    setRgb(r); setHsl(rgbToHsl(r)); setCmyk(rgbToCmyk(r)); setHexInput(rgbToHex(r))
    trackTool('color.convert', () => r)
    logActivity('color.convert', 'Converted color', { format: 'hsv' })
  }

  function applyCmyk(newCmyk) {
    const clamped = { c: clamp(newCmyk.c, 0, 100), m: clamp(newCmyk.m, 0, 100), y: clamp(newCmyk.y, 0, 100), k: clamp(newCmyk.k, 0, 100) }
    setCmyk(clamped)
    const r = cmykToRgb(clamped.c, clamped.m, clamped.y, clamped.k)
    setRgb(r); setHsl(rgbToHsl(r)); setHsv(rgbToHsv(r)); setHexInput(rgbToHex(r))
    trackTool('color.convert', () => r)
    logActivity('color.convert', 'Converted color', { format: 'cmyk' })
  }

  const colorName = useMemo(() => findColorName(hexInput), [hexInput])
  const hex = rgbToHex(rgb)

  return (
    <div className={styles.converterLayout}>
      {/* Large swatch + color picker */}
      <div className={styles.swatchPanel}>
        <div className={styles.bigSwatch} style={{ background: hex }} />
        <input
          type="color"
          className={styles.nativeColorPicker}
          value={hex}
          onChange={e => applyHex(e.target.value)}
          title="Use native color picker"
          aria-label="Open native colour picker"
        />
        <div className={styles.colorMeta}>
          <span className={styles.colorName}>{colorName}</span>
          <span className={styles.colorHexLarge}>{hex.toUpperCase()}</span>
        </div>
      </div>

      {/* Inputs */}
      <div className={styles.formatsPanel}>
        {/* HEX */}
        <div className={styles.panel}>
          <p className={styles.formatLabel}>HEX</p>
          <div className={styles.hexRow}>
            <input
              className={styles.hexInput}
              value={hexInput}
              onChange={e => applyHex(e.target.value)}
              placeholder="#RRGGBB"
              spellCheck={false}
              maxLength={7}
              aria-label="Hex colour value"
            />
            <button className={styles.copyBtn} onClick={() => navigator.clipboard.writeText(hex)}>Copy</button>
          </div>
          {error && <p className={styles.inputError}>{error}</p>}
        </div>

        {/* RGB */}
        <div className={styles.panel}>
          <p className={styles.formatLabel}>RGB</p>
          <FieldRow label="R">
            <NumInput value={rgb.r} onChange={v => applyRgb({ ...rgb, r: v })} />
            <input type="range" min={0} max={255} value={rgb.r} className={styles.slider} style={{ '--track-color': '#f00' }} onChange={e => applyRgb({ ...rgb, r: +e.target.value })} />
          </FieldRow>
          <FieldRow label="G">
            <NumInput value={rgb.g} onChange={v => applyRgb({ ...rgb, g: v })} />
            <input type="range" min={0} max={255} value={rgb.g} className={styles.slider} style={{ '--track-color': '#0f0' }} onChange={e => applyRgb({ ...rgb, g: +e.target.value })} />
          </FieldRow>
          <FieldRow label="B">
            <NumInput value={rgb.b} onChange={v => applyRgb({ ...rgb, b: v })} />
            <input type="range" min={0} max={255} value={rgb.b} className={styles.slider} style={{ '--track-color': '#00f' }} onChange={e => applyRgb({ ...rgb, b: +e.target.value })} />
          </FieldRow>
          <button className={styles.copyBtn} onClick={() => navigator.clipboard.writeText(`rgb(${rgb.r}, ${rgb.g}, ${rgb.b})`)}>Copy rgb()</button>
        </div>

        {/* HSL */}
        <div className={styles.panel}>
          <p className={styles.formatLabel}>HSL</p>
          <FieldRow label="H°">
            <NumInput value={hsl.h} min={0} max={360} onChange={v => applyHsl({ ...hsl, h: v })} />
            <input type="range" min={0} max={360} value={hsl.h} className={styles.slider} onChange={e => applyHsl({ ...hsl, h: +e.target.value })} />
          </FieldRow>
          <FieldRow label="S%">
            <NumInput value={hsl.s} min={0} max={100} onChange={v => applyHsl({ ...hsl, s: v })} />
            <input type="range" min={0} max={100} value={hsl.s} className={styles.slider} onChange={e => applyHsl({ ...hsl, s: +e.target.value })} />
          </FieldRow>
          <FieldRow label="L%">
            <NumInput value={hsl.l} min={0} max={100} onChange={v => applyHsl({ ...hsl, l: v })} />
            <input type="range" min={0} max={100} value={hsl.l} className={styles.slider} onChange={e => applyHsl({ ...hsl, l: +e.target.value })} />
          </FieldRow>
          <button className={styles.copyBtn} onClick={() => navigator.clipboard.writeText(`hsl(${hsl.h}, ${hsl.s}%, ${hsl.l}%)`)}>Copy hsl()</button>
        </div>

        {/* HSV */}
        <div className={styles.panel}>
          <p className={styles.formatLabel}>HSV</p>
          <FieldRow label="H°">
            <NumInput value={hsv.h} min={0} max={360} onChange={v => applyHsv({ ...hsv, h: v })} />
            <input type="range" min={0} max={360} value={hsv.h} className={styles.slider} onChange={e => applyHsv({ ...hsv, h: +e.target.value })} />
          </FieldRow>
          <FieldRow label="S%">
            <NumInput value={hsv.s} min={0} max={100} onChange={v => applyHsv({ ...hsv, s: v })} />
            <input type="range" min={0} max={100} value={hsv.s} className={styles.slider} onChange={e => applyHsv({ ...hsv, s: +e.target.value })} />
          </FieldRow>
          <FieldRow label="V%">
            <NumInput value={hsv.v} min={0} max={100} onChange={v => applyHsv({ ...hsv, v: v })} />
            <input type="range" min={0} max={100} value={hsv.v} className={styles.slider} onChange={e => applyHsv({ ...hsv, v: +e.target.value })} />
          </FieldRow>
        </div>

        {/* CMYK */}
        <div className={styles.panel}>
          <p className={styles.formatLabel}>CMYK</p>
          <FieldRow label="C%">
            <NumInput value={cmyk.c} min={0} max={100} onChange={v => applyCmyk({ ...cmyk, c: v })} />
            <input type="range" min={0} max={100} value={cmyk.c} className={styles.slider} onChange={e => applyCmyk({ ...cmyk, c: +e.target.value })} />
          </FieldRow>
          <FieldRow label="M%">
            <NumInput value={cmyk.m} min={0} max={100} onChange={v => applyCmyk({ ...cmyk, m: v })} />
            <input type="range" min={0} max={100} value={cmyk.m} className={styles.slider} onChange={e => applyCmyk({ ...cmyk, m: +e.target.value })} />
          </FieldRow>
          <FieldRow label="Y%">
            <NumInput value={cmyk.y} min={0} max={100} onChange={v => applyCmyk({ ...cmyk, y: v })} />
            <input type="range" min={0} max={100} value={cmyk.y} className={styles.slider} onChange={e => applyCmyk({ ...cmyk, y: +e.target.value })} />
          </FieldRow>
          <FieldRow label="K%">
            <NumInput value={cmyk.k} min={0} max={100} onChange={v => applyCmyk({ ...cmyk, k: v })} />
            <input type="range" min={0} max={100} value={cmyk.k} className={styles.slider} onChange={e => applyCmyk({ ...cmyk, k: +e.target.value })} />
          </FieldRow>
        </div>
      </div>
    </div>
  )
}

// ─── Palette Tab ───────────────────────────────────────────────────────────────

function PaletteTab() {
  const [baseHex, setBaseHex]   = useState('#3b6fd4')
  const [mode, setMode]         = useState('complementary')
  const [copiedIdx, setCopiedIdx] = useState(null)

  const palette = useMemo(() => {
    const parsed = parseColor(baseHex)
    if (!parsed) return []
    return generatePalette(rgbToHex(parsed), mode)
  }, [baseHex, mode])

  function handleBaseChange(val) {
    setBaseHex(val)
    if (parseColor(val)) {
      trackTool('color.palette', () => val)
      logActivity('color.palette', 'Generated palette', { mode, swatches: palette.length })
    }
  }

  function copyHex(hex, idx) {
    navigator.clipboard.writeText(hex)
    setCopiedIdx(idx)
    setTimeout(() => setCopiedIdx(null), 1200)
  }

  function exportPaletteCSS() {
    const css = palette.map((h, i) => `  --color-${i + 1}: ${h};`).join('\n')
    const blob = new Blob([`:root {\n${css}\n}`], { type: 'text/css' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = `palette-${mode}.css`; a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className={styles.paletteLayout}>
      <div className={styles.panel}>
        <p className={styles.eyebrow}>Configuration</p>
        <h2 className={styles.panelTitle}>Palette Generator</h2>

        <label className={styles.fieldLabel}>Base Color</label>
        <div className={styles.baseColorRow}>
          <input
            className={styles.hexInput}
            value={baseHex}
            onChange={e => handleBaseChange(e.target.value)}
            placeholder="#RRGGBB"
          />
          <input
            type="color"
            className={styles.nativeColorPickerSm}
            value={parseColor(baseHex) ? rgbToHex(parseColor(baseHex)) : '#3b6fd4'}
            onChange={e => handleBaseChange(e.target.value)}
          />
        </div>

        <label className={styles.fieldLabel}>Harmony Mode</label>
        <div className={styles.modeGrid}>
          {PALETTE_MODES.map(m => (
            <button
              key={m.id}
              type="button"
              className={mode === m.id ? styles.modeActive : styles.modeBtn}
              onClick={() => setMode(m.id)}
            >
              <span className={styles.modeName}>{m.label}</span>
              <span className={styles.modeDesc}>{m.desc}</span>
            </button>
          ))}
        </div>

        <button type="button" className={styles.secondaryBtn} onClick={exportPaletteCSS}
          disabled={palette.length === 0}>
          Export as CSS Variables
        </button>
      </div>

      <div className={styles.paletteResult}>
        <div className={styles.panel}>
          <p className={styles.eyebrow}>Result</p>
          <h2 className={styles.panelTitle}>{PALETTE_MODES.find(m => m.id === mode)?.label}</h2>

          {palette.length > 0 ? (
            <>
              {/* Large swatches strip */}
              <div className={styles.paletteStrip}>
                {palette.map((hex, i) => (
                  <div key={i} className={styles.paletteSwatchWrap}>
                    <div
                      className={styles.paletteSwatch}
                      style={{ background: hex }}
                      onClick={() => copyHex(hex, i)}
                      role="button"
                      title={`Click to copy ${hex}`}
                      tabIndex={0}
                      onKeyDown={e => e.key === 'Enter' && copyHex(hex, i)}
                    />
                    <div className={styles.swatchInfo}>
                      <span className={styles.swatchHex}>{hex.toUpperCase()}</span>
                      <span className={styles.swatchColorName}>{findColorName(hex)}</span>
                      {copiedIdx === i && <span className={styles.swatchCopiedTag}>✓ Copied</span>}
                    </div>
                  </div>
                ))}
              </div>

              {/* Hex codes row for quick reference */}
              <div className={styles.paletteHexRow}>
                {palette.map((hex, i) => (
                  <code key={i} className={styles.paletteHexCode}>{hex.toUpperCase()}</code>
                ))}
              </div>
            </>
          ) : (
            <p className={styles.emptyMsg}>Enter a valid hex color above to generate a palette.</p>
          )}
        </div>
      </div>
    </div>
  )
}

// ─── Gradient Tab ──────────────────────────────────────────────────────────────

function GradientTab() {
  const [gradType, setGradType] = useState('linear')
  const [angle, setAngle]       = useState(135)
  const [stops, setStops]       = useState([
    { color: '#3b6fd4', pos: 0 },
    { color: '#7ec8e3', pos: 100 },
  ])
  const [copiedCSS, setCopiedCSS] = useState(false)

  const cssGradient = useMemo(() => {
    const stopsStr = stops
      .slice()
      .sort((a, b) => a.pos - b.pos)
      .map(s => `${s.color} ${s.pos}%`)
      .join(', ')
    return gradType === 'linear'
      ? `linear-gradient(${angle}deg, ${stopsStr})`
      : `radial-gradient(circle, ${stopsStr})`
  }, [stops, gradType, angle])

  const cssRule = `background: ${cssGradient};`

  function addStop() {
    if (stops.length >= 8) return
    setStops(prev => [...prev, { color: '#ffffff', pos: 50 }])
    logActivity('color.gradient', 'Added gradient stop', { stops: stops.length + 1 })
  }

  function removeStop(i) {
    if (stops.length <= 2) return
    setStops(prev => prev.filter((_, idx) => idx !== i))
  }

  function updateStop(i, field, val) {
    setStops(prev => prev.map((s, idx) => idx === i ? { ...s, [field]: val } : s))
  }

  function copyCSS() {
    navigator.clipboard.writeText(cssRule)
    setCopiedCSS(true)
    setTimeout(() => setCopiedCSS(false), 1200)
    trackTool('color.gradient', () => cssGradient)
    logActivity('color.gradient', 'Copied gradient CSS', { type: gradType, stops: stops.length })
  }

  function exportCSS() {
    const blob = new Blob([`.gradient {\n  ${cssRule}\n}`], { type: 'text/css' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = `gradient-${Date.now()}.css`; a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className={styles.gradientLayout}>
      {/* Preview strip */}
      <div className={styles.gradientPreview} style={{ background: cssGradient }} />

      <div className={styles.gradientControls}>
        <div className={styles.panel}>
          <p className={styles.eyebrow}>Configuration</p>
          <h2 className={styles.panelTitle}>Gradient Builder</h2>

          {/* Type */}
          <label className={styles.fieldLabel}>Type</label>
          <div className={styles.gradTypeRow}>
            {['linear', 'radial'].map(t => (
              <button
                key={t}
                type="button"
                className={gradType === t ? styles.modeActive : styles.modeBtn}
                onClick={() => setGradType(t)}
                style={{ flex: 1 }}
              >
                {t.charAt(0).toUpperCase() + t.slice(1)}
              </button>
            ))}
          </div>

          {/* Angle — only for linear */}
          {gradType === 'linear' && (
            <>
              <label className={styles.fieldLabel}>Angle: {angle}°</label>
              <input
                type="range" min={0} max={360} value={angle}
                className={styles.slider}
                onChange={e => setAngle(+e.target.value)}
              />
            </>
          )}

          {/* Color stops */}
          <label className={styles.fieldLabel}>Color Stops ({stops.length})</label>
          {stops.map((stop, i) => (
            <div key={i} className={styles.stopRow}>
              <input
                type="color"
                className={styles.nativeColorPickerSm}
                value={stop.color}
                onChange={e => updateStop(i, 'color', e.target.value)}
              />
              <input
                className={styles.hexInput}
                value={stop.color}
                onChange={e => updateStop(i, 'color', e.target.value)}
                style={{ width: 90 }}
              />
              <input
                type="range" min={0} max={100} value={stop.pos}
                className={styles.slider}
                onChange={e => updateStop(i, 'pos', +e.target.value)}
              />
              <span className={styles.stopPos}>{stop.pos}%</span>
              <button
                type="button"
                className={styles.removeStopBtn}
                onClick={() => removeStop(i)}
                disabled={stops.length <= 2}
                title="Remove stop"
                aria-label={`Remove colour stop ${i + 1}`}
              >×</button>
            </div>
          ))}
          <button type="button" className={styles.ghostBtn} onClick={addStop} disabled={stops.length >= 8}>
            + Add Stop
          </button>
        </div>

        <div className={styles.panel}>
          <p className={styles.eyebrow}>CSS Output</p>
          <h2 className={styles.panelTitle}>Generated Code</h2>
          <pre className={styles.codeBlock}>{cssRule}</pre>
          <div className={styles.gradientBtnRow}>
            <button type="button" className={styles.primaryBtn} onClick={copyCSS}>
              {copiedCSS ? '✓ Copied' : 'Copy CSS'}
            </button>
            <button type="button" className={styles.secondaryBtn} onClick={exportCSS}>
              Download .css
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── Accessibility Tab ─────────────────────────────────────────────────────────

function AccessibilityTab() {
  const [fg, setFg] = useState('#ffffff')
  const [bg, setBg] = useState('#3b6fd4')
  const [de1, setDe1] = useState('#ff0000')
  const [de2, setDe2] = useState('#ff8800')

  const fgRgb = parseColor(fg) || { r: 255, g: 255, b: 255 }
  const bgRgb = parseColor(bg) || { r: 59, g: 111, b: 212 }

  const ratio = contrastRatio(fgRgb, bgRgb)
  const levels = wcagLevel(ratio)

  const de1Rgb = parseColor(de1) || { r: 255, g: 0, b: 0 }
  const de2Rgb = parseColor(de2) || { r: 255, g: 136, b: 0 }
  const dE = deltaE(de1Rgb, de2Rgb)

  function check() {
    trackTool('color.accessibility', () => ratio)
    logActivity('color.accessibility', 'Checked contrast', { ratio, passed: levels.aa })
  }

  const PassBadge = ({ pass, label }) => (
    <span className={pass ? styles.passBadge : styles.failBadge}>
      {pass ? '✓' : '✗'} {label}
    </span>
  )

  return (
    <div className={styles.a11yLayout}>
      {/* Contrast checker */}
      <div className={styles.panel}>
        <p className={styles.eyebrow}>WCAG Contrast</p>
        <h2 className={styles.panelTitle}>Contrast Ratio Checker</h2>

        <div className={styles.a11yInputRow}>
          <div className={styles.a11yColorPicker}>
            <label className={styles.fieldLabel}>Foreground</label>
            <div className={styles.baseColorRow}>
              <input className={styles.hexInput} value={fg} onChange={e => { setFg(e.target.value); check() }} placeholder="#RRGGBB" maxLength={7} />
              <input type="color" className={styles.nativeColorPickerSm} value={parseColor(fg) ? rgbToHex(parseColor(fg)) : '#ffffff'} onChange={e => { setFg(e.target.value); check() }} />
            </div>
          </div>
          <div className={styles.a11yColorPicker}>
            <label className={styles.fieldLabel}>Background</label>
            <div className={styles.baseColorRow}>
              <input className={styles.hexInput} value={bg} onChange={e => { setBg(e.target.value); check() }} placeholder="#RRGGBB" maxLength={7} />
              <input type="color" className={styles.nativeColorPickerSm} value={parseColor(bg) ? rgbToHex(parseColor(bg)) : '#3b6fd4'} onChange={e => { setBg(e.target.value); check() }} />
            </div>
          </div>
        </div>

        {/* Live preview */}
        <div className={styles.a11yPreview} style={{ background: bgRgb ? rgbToHex(bgRgb) : bg, color: fgRgb ? rgbToHex(fgRgb) : fg }}>
          <span className={styles.a11yPreviewLarge}>Large Text Sample Aa</span>
          <span className={styles.a11yPreviewSmall}>Normal body text at standard size</span>
        </div>

        {/* Result */}
        <div className={styles.a11yResult}>
          <div className={styles.ratioDisplay}>
            <span className={styles.ratioValue}>{ratio}:1</span>
            <span className={styles.ratioLabel}>Contrast Ratio</span>
          </div>
          <div className={styles.badgeGrid}>
            <PassBadge pass={levels.aa}      label="AA Normal" />
            <PassBadge pass={levels.aaa}     label="AAA Normal" />
            <PassBadge pass={levels.aaLarge} label="AA Large" />
            <PassBadge pass={levels.aaaLarge} label="AAA Large" />
          </div>
          <p className={styles.a11yNote}>
            AA requires ≥4.5:1 (normal) or ≥3:1 (large/bold ≥18pt).
            AAA requires ≥7:1 (normal) or ≥4.5:1 (large/bold).
          </p>
        </div>
      </div>

      {/* Delta-E */}
      <div className={styles.panel}>
        <p className={styles.eyebrow}>Color Difference</p>
        <h2 className={styles.panelTitle}>Delta-E Calculator (CIE76)</h2>

        <div className={styles.a11yInputRow}>
          <div className={styles.a11yColorPicker}>
            <label className={styles.fieldLabel}>Color 1</label>
            <div className={styles.baseColorRow}>
              <input className={styles.hexInput} value={de1} onChange={e => setDe1(e.target.value)} placeholder="#RRGGBB" maxLength={7} />
              <input type="color" className={styles.nativeColorPickerSm} value={parseColor(de1) ? rgbToHex(parseColor(de1)) : '#ff0000'} onChange={e => setDe1(e.target.value)} />
            </div>
          </div>
          <div className={styles.a11yColorPicker}>
            <label className={styles.fieldLabel}>Color 2</label>
            <div className={styles.baseColorRow}>
              <input className={styles.hexInput} value={de2} onChange={e => setDe2(e.target.value)} placeholder="#RRGGBB" maxLength={7} />
              <input type="color" className={styles.nativeColorPickerSm} value={parseColor(de2) ? rgbToHex(parseColor(de2)) : '#ff8800'} onChange={e => setDe2(e.target.value)} />
            </div>
          </div>
        </div>

        <div className={styles.dePreviewRow}>
          <div className={styles.deBlock} style={{ background: parseColor(de1) ? rgbToHex(parseColor(de1)) : de1 }} />
          <div className={styles.deArrow}>→</div>
          <div className={styles.deBlock} style={{ background: parseColor(de2) ? rgbToHex(parseColor(de2)) : de2 }} />
        </div>

        <div className={styles.a11yResult}>
          <div className={styles.ratioDisplay}>
            <span className={styles.ratioValue}>ΔE {dE}</span>
            <span className={styles.ratioLabel}>
              {dE < 1 ? 'Imperceptible difference' : dE < 2 ? 'Just noticeable' : dE < 10 ? 'Perceptible' : dE < 50 ? 'Clearly distinct' : 'Very different colors'}
            </span>
          </div>
          <p className={styles.a11yNote}>
            ΔE &lt; 1 = indistinguishable · ΔE 1–2 = subtle · ΔE &gt; 10 = clearly different · ΔE &gt; 50 = opposite ends of the spectrum.
          </p>
        </div>
      </div>
    </div>
  )
}

// ─── Main component ───────────────────────────────────────────────────────────

export default function ColorToolsPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const isGuest = username === 'Guest User'
  const [activeTab, setActiveTab] = useState('converter')

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.brand}>
            <span className={styles.brandMark} aria-hidden="true">🎨</span>
            <span className={styles.brandName}>UsefulTools</span>
          </div>
          <button className={styles.backBtn} onClick={() => navigate('/dashboard')}>Dashboard</button>
        </div>
        <UserMenu username={username} isGuest={isGuest} variant="light" onLogout={handleLogout} />
      </header>

      <section className={styles.hero}>
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>Sprint 22</div>
          <h1 className={styles.heroTitle}>Color Tools</h1>
          <p className={styles.heroSub}>
            Convert between HEX, RGB, HSL, HSV and CMYK. Generate harmonious palettes,
            build CSS gradients, and check WCAG accessibility contrast ratios.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div><strong>5</strong><span>formats</span></div>
          <div><strong>6</strong><span>palette modes</span></div>
          <div><strong>WCAG</strong><span>AA + AAA</span></div>
        </div>
      </section>

      <main className={styles.main}>
        <TabBar tabs={TABS} active={activeTab} onChange={setActiveTab} />
        {activeTab === 'converter'     && <ConverterTab />}
        {activeTab === 'palette'       && <PaletteTab />}
        {activeTab === 'gradient'      && <GradientTab />}
        {activeTab === 'accessibility' && <AccessibilityTab />}
      </main>
    </div>
  )
}
