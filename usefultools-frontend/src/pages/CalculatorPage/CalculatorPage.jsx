import { useState }    from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth }     from '../../auth/AuthContext'
import { logoutUser,
         evaluateSimple, evaluateIntermediate,
         evaluateBoolean, evaluateTrig,
         evaluateCombined }   from '../../api/apiClient'
import StandardCalc    from './StandardCalc/StandardCalc'
import ComplexCalc     from './ComplexCalc/ComplexCalc'
import styles          from './CalculatorPage.module.css'

// ─── Tab definitions ──────────────────────────────────────────────────────────

const TABS = [
  { id: 'simple',       label: 'Simple'       },
  { id: 'intermediate', label: 'Intermediate' },
  { id: 'boolean',      label: 'Boolean'      },
  { id: 'trig',         label: 'Trig'         },
  { id: 'combined',     label: 'Combined'     },
  { id: 'complex',      label: 'Complex'      },
]

const EVALUATE_FNS = {
  simple:       evaluateSimple,
  intermediate: evaluateIntermediate,
  boolean:      evaluateBoolean,
  trig:         evaluateTrig,
  combined:     evaluateCombined,
}

// ─── Button data ──────────────────────────────────────────────────────────────

const b = (display, insert, type = 'default') => ({ display, insert, type })

const DIGIT_BTNS = [
  b('9','9','digit'), b('8','8','digit'), b('7','7','digit'),
  b('6','6','digit'), b('5','5','digit'), b('4','4','digit'),
  b('3','3','digit'), b('2','2','digit'), b('1','1','digit'),
  b('0','0','digit'), b('.', '.','digit'), b(',',',','op'),
]

const ARITH_BTNS = [
  b('+', '+', 'op'), b('−', '-', 'op'), b('×', '*', 'op'), b('÷', '/', 'op'),
  b('^', '^', 'op'), b('% (mod)', '%', 'op'),
  b('(', '(', 'op'), b(')', ')', 'op'),
]

const MATH_FUNC_BTNS = [
  b('sin(',   'sin(',   'func'), b('cos(',   'cos(',   'func'), b('tan(',   'tan(',   'func'),
  b('asin(',  'asin(',  'func'), b('acos(',  'acos(',  'func'), b('atan(',  'atan(',  'func'),
  b('log(',   'log(',   'func'), b('log2(',  'log2(',  'func'), b('log10(', 'log10(', 'func'),
  b('logn(',  'logn(',  'func'), b('sqrt(',  'sqrt(',  'func'), b('cbrt(',  'cbrt(',  'func'),
  b('abs(',   'abs(',   'func'), b('ceil(',  'ceil(',  'func'), b('floor(', 'floor(', 'func'),
]

const CUSTOM_FUNC_BTNS = [
  b('fact(',    'fact(',    'func'), b('nCr(',     'nCr(',     'func'),
  b('round(',   'round(',   'func'), b('trunc(',   'trunc(',   'func'),
  b('max(',     'max(',     'func'), b('min(',     'min(',     'func'),
  b('mean(',    'mean(',    'func'), b('median(',  'median(',  'func'),
  b('percent(', 'percent(', 'func'),
]

const CONST_BTNS = [
  b('π', '3.141592653589793', 'const'),
  b('e', '2.718281828459045', 'const'),
  b('φ', '1.618033988749895', 'const'),
  b('γ', '0.577215664901532', 'const'),
  b('τ', '6.283185307179586', 'const'),
  b('ln10', '2.30258509299', 'const'),
  b('ln2', '0.69314718056', 'const'),
  b('log2e', '1.44269504089', 'const'),
  b('log10e', '0.43429448190', 'const'),
  b('ln(π)', '1.14472988585', 'const'),
  b('ln(2π)', '1.83787706641', 'const'),
  b('log₂(10)', '3.321928', 'const'),
  b('log₁₀(2)', '0.301029995666', 'const'),
  b('c', '299792458', 'const'),
  b('g', '9.80665', 'const'),
  b('G', '6.67430e-11', 'const'),
  b('h', '6.62607015e-34', 'const'),
  b('TRUE', '1', 'const'),
  b('FALSE', '0', 'const'),
  b('√2', '1.414213562373095', 'const'),
  b('√3', '1.732050807568877', 'const'),
  b('√5', '2.23606797749979', 'const'),
  b('1/√2', '0.707106781186547', 'const'),
  b('π/2', '1.5707963267948966', 'const'),
  b('π/3', '1.0471975511965976', 'const'),
  b('π/4', '0.7853981633974483', 'const'),
  b('2π', '6.283185307179586', 'const'),
  b('k', '1.380649e-23', 'const'),
  b('NA', '6.02214076e23', 'const'),
  b('ε₀', '8854e-12', 'const'),
  b('μ₀', '1.25663706e-6', 'const'),
  b('ζ(3)', '1.202056903159594', 'const'),
  b('K', '0.915965594177219', 'const'),
  b('M', '0.261497212847643', 'const'),
  b('B₂', '0.660161815846869', 'const'),
  b('A', '1.28242712910062', 'const'),
  b('√π', '1.7724538509055159', 'const'),
  b('π²', '9.869604401089358', 'const'),
  b('π³', '31.006276680299816', 'const'),
  b('e²', '7.3890560989306495', 'const'),
  b('e^π', '23.140692632779263', 'const'),
  b('2^√2', '2.665144142690225', 'const'),
]

const TRIG_RAD_BTNS = [
  b('sin(',    'sin(',    'func'), b('cos(',    'cos(',    'func'), b('tan(',    'tan(',    'func'),
  b('asin(',   'asin(',   'func'), b('acos(',   'acos(',   'func'), b('atan(',   'atan(',   'func'),
  b('atan2(',  'atan2(',  'func'), b('cosec(',  'cosec(',  'func'), b('sec(',    'sec(',    'func'),
  b('cot(',    'cot(',    'func'), b('acosec(', 'acosec(', 'func'), b('asec(',   'asec(',   'func'),
  b('acot(',   'acot(',   'func'),
]

const TRIG_DEG_BTNS = [
  b('sind(',   'sind(',   'func'), b('cosd(',   'cosd(',   'func'), b('tand(',   'tand(',   'func'),
  b('cosecd(', 'cosecd(', 'func'), b('secd(',   'secd(',   'func'), b('cotd(',   'cotd(',   'func'),
]

const BOOL_VAL_BTNS = [
  b('0', '0', 'digit'), b('1', '1', 'digit'),
  b('(', '(', 'op'),    b(')', ')', 'op'),    b(',', ',', 'op'),
]

/**
 * BOOL_BITWISE_BTNS — FIX:
 * Unicode operator symbols (⊕, ⊙, ↑, ↓) replaced with function call syntax.
 * exp4j rejects unicode characters as operator symbols at registration time,
 * crashing the entire expression builder. These 4 operations are now named
 * 2-argument functions implemented in BooleanUtils.buildBooleanExpression().
 *
 * Usage: xor(1,1) → 0,  nand(1,1) → 0,  nor(0,0) → 1,  xnor(1,1) → 1
 *
 * ASCII operators (&, |, !, !=, ==, >, <, >=, <=, <<, >>) remain as operators
 * because all their characters are in exp4j's allowed symbol set.
 */
const BOOL_BITWISE_BTNS = [
  b('& (AND)',      '&',     'op'),
  b('| (OR)',       '|',     'op'),
  b('xor(',        'xor(',  'func'),   // replaces ⊕
  b('xnor(',       'xnor(', 'func'),   // replaces ⊙
  b('nand(',       'nand(', 'func'),   // replaces ↑
  b('nor(',        'nor(',  'func'),   // replaces ↓
  b('! (NOT)',      '!',     'op'),
  b('!= (NEQ)',     '!=',    'op'),
  b('== (EQ)',      '==',    'op'),
  b('>',            '>',     'op'),
  b('<',            '<',     'op'),
  b('>=',           '>=',    'op'),
  b('<=',           '<=',    'op'),
  b('<< (LSHIFT)', '<<',    'op'),
  b('>> (RSHIFT)', '>>',    'op'),
]

/**
 * BOOL_LOGIC_BTNS — FIX:
 * All 5 operators used unicode symbols (→, ←, ↔, ⊄, ⊅) — all rejected by exp4j.
 * Replaced with named 2-argument functions.
 *
 * Usage:
 *   impl(1,0)  → 0   (implication:          a → b  =  !a || b)
 *   rimpl(0,1) → 0   (reverse implication:  a ← b  =  a || !b)
 *   bicon(1,0) → 0   (biconditional:        a ↔ b  =  a == b)
 *   nimp(1,0)  → 1   (nonimplication:       a ⊄ b  =  a && !b)
 *   cnimp(0,1) → 1   (converse nonimpl.:    a ⊅ b  =  !a && b)
 */
const BOOL_LOGIC_BTNS = [
  b('impl(',  'impl(',  'func'),   // replaces →
  b('rimpl(', 'rimpl(', 'func'),   // replaces ←
  b('bicon(', 'bicon(', 'func'),   // replaces ↔
  b('nimp(',  'nimp(',  'func'),   // replaces ⊄
  b('cnimp(', 'cnimp(', 'func'),   // replaces ⊅
]

const BOOL_FUNC_BTNS = [
  b('majority(', 'majority(', 'func'),
  b('parity(',   'parity(',   'func'),
]

const BOOL_CONST_BINS = [
  b('TRUE', '1', 'const'),
  b('FALSE', '0', 'const'),
]

// ─── Button groups per tab ────────────────────────────────────────────────────

const GRP = (groupLabel, buttons) => ({ groupLabel, buttons })

const BUTTON_GROUPS = {
  simple: [
    GRP('Numbers',   DIGIT_BTNS),
    GRP('Operators', ARITH_BTNS),
    GRP('Functions', [...MATH_FUNC_BTNS, ...CUSTOM_FUNC_BTNS]),
    GRP('Constants', CONST_BTNS),
  ],
  intermediate: [
    GRP('Numbers',   DIGIT_BTNS),
    GRP('Operators', ARITH_BTNS),
    GRP('Functions', [...MATH_FUNC_BTNS, ...CUSTOM_FUNC_BTNS]),
    GRP('Constants', CONST_BTNS),
  ],
  boolean: [
    GRP('Values & Grouping', BOOL_VAL_BTNS),
    GRP('Bitwise & Compare', BOOL_BITWISE_BTNS),
    GRP('Logic Functions',   BOOL_LOGIC_BTNS),
    GRP('Multi-arg Functions', BOOL_FUNC_BTNS),
    GRP('Constants', BOOL_CONST_BINS),
  ],
  trig: [
    GRP('Numbers',         DIGIT_BTNS),
    GRP('Arithmetic',      ARITH_BTNS),
    GRP('Trig (Radians)',  TRIG_RAD_BTNS),
    GRP('Trig (Degrees)',  TRIG_DEG_BTNS),
    GRP('Constants',       CONST_BTNS),
  ],
  combined: [
    GRP('Numbers',         DIGIT_BTNS),
    GRP('Arithmetic',      ARITH_BTNS),
    GRP('Math Functions',  [...MATH_FUNC_BTNS, ...CUSTOM_FUNC_BTNS]),
    GRP('Trig (Radians)',  TRIG_RAD_BTNS),
    GRP('Trig (Degrees)',  TRIG_DEG_BTNS),
    GRP('Boolean Ops',     BOOL_BITWISE_BTNS),
    GRP('Logic Functions', BOOL_LOGIC_BTNS),
    GRP('Boolean Funcs',   BOOL_FUNC_BTNS),
    GRP('Constants',       CONST_BTNS),
  ],
}

const NOTES = {
  intermediate: 'Results from this calculator are automatically saved to calculation history.',
}

// ─── Component ────────────────────────────────────────────────────────────────

export default function CalculatorPage() {
  const [activeTab, setActiveTab] = useState('simple')
  const { username, logout }      = useAuth()
  const navigate                  = useNavigate()

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  return (
    <div className={styles.page}>

      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <span className={styles.brandIcon} aria-hidden="true">⚙</span>
          <span className={styles.brandName}>UsefulTools</span>
          <button className={styles.backBtn} onClick={() => navigate('/dashboard')}>
            ← Dashboard
          </button>
        </div>
        <div className={styles.headerRight}>
          <span className={styles.usernameLabel}>{username}</span>
          <button className={styles.logoutBtn} onClick={handleLogout}>Sign out</button>
        </div>
      </header>

      <nav className={styles.tabBar} aria-label="Calculator modes">
        {TABS.map(tab => (
          <button
            key={tab.id}
            className={activeTab === tab.id ? styles.tabActive : styles.tab}
            onClick={() => setActiveTab(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </nav>

      <main className={styles.content}>
        {activeTab === 'complex'
          ? <ComplexCalc />
          : (
            <StandardCalc
              key={activeTab}
              evaluateFn={EVALUATE_FNS[activeTab]}
              buttonGroups={BUTTON_GROUPS[activeTab]}
              note={NOTES[activeTab] || ''}
              mode={activeTab}
            />
          )
        }
      </main>

    </div>
  )
}