import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import {
  logoutUser,
  evaluateSimple, evaluateIntermediate,
  evaluateBoolean, evaluateTrig,
  evaluateCombined,
} from '../../api/apiClient'
import AppHeader from '../../components/AppHeader/AppHeader'
import ToolHero from '../../components/ToolHero/ToolHero'
import PageTabs from '../../components/PageTabs/PageTabs'
import StandardCalc from './StandardCalc/StandardCalc'
import ComplexCalc from './ComplexCalc/ComplexCalc'
import FinancialCalc from './FinancialCalc/FinancialCalc'
import styles from './CalculatorPage.module.css'

const TABS = [
  { id: 'simple',       label: 'Simple',       icon: '1+' },
  { id: 'intermediate', label: 'Intermediate', icon: 'fx' },
  { id: 'boolean',      label: 'Boolean',      icon: '&&' },
  { id: 'trig',         label: 'Trig',         icon: 'sin' },
  { id: 'combined',     label: 'Combined',     icon: 'mix' },
  { id: 'complex',      label: 'Complex',      icon: 'z' },
  { id: 'financial',    label: 'Financial',    icon: '💰' },
]

const EVALUATE_FNS = {
  simple: evaluateSimple,
  intermediate: evaluateIntermediate,
  boolean: evaluateBoolean,
  trig: evaluateTrig,
  combined: evaluateCombined,
}

const b = (display, insert, type = 'default') => ({ display, insert, type })

const DIGIT_BTNS = [
  b('9','9','digit'), b('8','8','digit'), b('7','7','digit'),
  b('6','6','digit'), b('5','5','digit'), b('4','4','digit'),
  b('3','3','digit'), b('2','2','digit'), b('1','1','digit'),
  b('0','0','digit'), b('.', '.','digit'), b(',',',','op'),
]

const ARITH_BTNS = [
  b('+', '+', 'op'), b('-', '-', 'op'), b('*', '*', 'op'), b('/', '/', 'op'),
  b('^', '^', 'op'), b('% (mod)', '%', 'op'),
  b('(', '(', 'op'), b(')', ')', 'op'),
]

const MATH_FUNC_BTNS = [
  b('sin(',   'sin(',   'func'), b('cos(',   'cos(',   'func'), b('tan(',   'tan(',   'func'),
  b('asin(',  'asin(',  'func'), b('acos(',  'acos(',  'func'), b('atan(',  'atan(',  'func'),
  b('log(',   'log(',   'func'), b('log2(',  'log2(',  'func'), b('log10(', 'log10(', 'func'),
  b('logn(',  'logn(',  'func'), b('sqrt(',  'sqrt(',  'func'), b('cbrt(',  'cbrt(',  'func'),
  b('abs(',   'abs(',   'func'), b('ceil(',  'ceil(',  'func'), b('floor(', 'floor(', 'func'),
  b('sinh(', 'sinh(', 'func'),
  b('cosh(', 'cosh(', 'func'),
  b('tanh(', 'tanh(', 'func'),
  b('cosech(', 'cosech(', 'func'),
  b('sech(', 'sech(', 'func'),
  b('coth(', 'coth(', 'func'),
  b('asinh(', 'asinh(', 'func'),
  b('acosh(', 'acosh(', 'func'),
  b('atanh(', 'atanh(', 'func'),
  b('acosech(', 'acosech(', 'func'),
  b('acoth(', 'acoth(', 'func'),
  b('sinhd(', 'sinhd(', 'func'),
  b('coshd(', 'coshd(', 'func'),
  b('tanhd(', 'tanhd(', 'func'),
  b('cosechd(', 'cosechd(', 'func'),
  b('sechd(', 'sechd(', 'func'),
  b('cothd(', 'cothd(', 'func'),
  b('asinhd(', 'asinhd(', 'func'),
  b('acoshd(', 'acoshd(', 'func'),
  b('atanhd(', 'atanhd(', 'func'),
  b('acosechd(', 'acosechd(', 'func'),
  b('asechd(', 'asechd(', 'func'),
  b('acothd(', 'acothd(', 'func'),
]

const CUSTOM_FUNC_BTNS = [
  b('fact(',    'fact(',    'func'), b('nCr(',     'nCr(',     'func'),
  b('round(',   'round(',   'func'), b('trunc(',   'trunc(',   'func'),
  b('max(',     'max(',     'func'), b('min(',     'min(',     'func'),
  b('mean(',    'mean(',    'func'), b('median(',  'median(',  'func'),
  b('percent(', 'percent(', 'func'),
]

const CONST_BTNS = [
  b('pi', '3.141592653589793', 'const'),
  b('e', '2.718281828459045', 'const'),
  b('phi', '1.618033988749895', 'const'),
  b('gamma', '0.577215664901532', 'const'),
  b('tau', '6.283185307179586', 'const'),
  b('ln10', '2.30258509299', 'const'),
  b('ln2', '0.69314718056', 'const'),
  b('log2e', '1.44269504089', 'const'),
  b('log10e', '0.43429448190', 'const'),
  b('ln(pi)', '1.14472988585', 'const'),
  b('ln(2pi)', '1.83787706641', 'const'),
  b('log2(10)', '3.321928', 'const'),
  b('log10(2)', '0.301029995666', 'const'),
  b('c', '299792458', 'const'),
  b('g', '9.80665', 'const'),
  b('G', '6.67430e-11', 'const'),
  b('h', '6.62607015e-34', 'const'),
  b('TRUE', '1', 'const'),
  b('FALSE', '0', 'const'),
  b('sqrt2', '1.414213562373095', 'const'),
  b('sqrt3', '1.732050807568877', 'const'),
  b('sqrt5', '2.23606797749979', 'const'),
  b('1/sqrt2', '0.707106781186547', 'const'),
  b('pi/2', '1.5707963267948966', 'const'),
  b('pi/3', '1.0471975511965976', 'const'),
  b('pi/4', '0.7853981633974483', 'const'),
  b('2pi', '6.283185307179586', 'const'),
  b('k', '1.380649e-23', 'const'),
  b('NA', '6.02214076e23', 'const'),
  b('epsilon0', '8854e-12', 'const'),
  b('mu0', '1.25663706e-6', 'const'),
  b('zeta(3)', '1.202056903159594', 'const'),
  b('K', '0.915965594177219', 'const'),
  b('M', '0.261497212847643', 'const'),
  b('B2', '0.660161815846869', 'const'),
  b('A', '1.28242712910062', 'const'),
  b('sqrt(pi)', '1.7724538509055159', 'const'),
  b('pi^2', '9.869604401089358', 'const'),
  b('pi^3', '31.006276680299816', 'const'),
  b('e^2', '7.3890560989306495', 'const'),
  b('e^pi', '23.140692632779263', 'const'),
  b('2^sqrt2', '2.665144142690225', 'const'),
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

const TRIG_DEG_HYP = [
  b('sinh(', 'sinh(', 'func'),
  b('cosh(', 'cosh(', 'func'),
  b('tanh(', 'tanh(', 'func'),
  b('cosech(', 'cosech(', 'func'),
  b('sech(', 'sech(', 'func'),
  b('coth(', 'coth(', 'func'),
  b('asinh(', 'asinh(', 'func'),
  b('acosh(', 'acosh(', 'func'),
  b('atanh(', 'atanh(', 'func'),
  b('acosech(', 'acosech(', 'func'),
  b('acoth(', 'acoth(', 'func'),
]

const TRIG_DEG_COMBINED = [
  b('sinhd(', 'sinhd(', 'func'),
  b('coshd(', 'coshd(', 'func'),
  b('tanhd(', 'tanhd(', 'func'),
  b('cosechd(', 'cosechd(', 'func'),
  b('sechd(', 'sechd(', 'func'),
  b('cothd(', 'cothd(', 'func'),
  b('asinhd(', 'asinhd(', 'func'),
  b('acoshd(', 'acoshd(', 'func'),
  b('atanhd(', 'atanhd(', 'func'),
  b('acosechd(', 'acosechd(', 'func'),
  b('asechd(', 'asechd(', 'func'),
  b('acothd(', 'acothd(', 'func'),
]

const BOOL_VAL_BTNS = [
  b('0', '0', 'digit'), b('1', '1', 'digit'),
  b('(', '(', 'op'),    b(')', ')', 'op'),    b(',', ',', 'op'),
]

const BOOL_BITWISE_BTNS = [
  b('& (AND)',      '&',     'op'),
  b('| (OR)',       '|',     'op'),
  b('xor(',        'xor(',  'func'),
  b('xnor(',       'xnor(', 'func'),
  b('nand(',       'nand(', 'func'),
  b('nor(',        'nor(',  'func'),
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

const BOOL_LOGIC_BTNS = [
  b('impl(',  'implication(',  'func'),
  b('rimpl(', 'reverseImplication(', 'func'),
  b('bicon(', 'biconditional(', 'func'),
  b('nimp(',  'nonimplication(',  'func'),
  b('cnimp(', 'converseNonimplication(', 'func'),
]

const BOOL_FUNC_BTNS = [
  b('majority(', 'majority(', 'func'),
  b('parity(',   'parity(',   'func'),
]

const BOOL_CONST_BINS = [
  b('TRUE', '1', 'const'),
  b('FALSE', '0', 'const'),
]

const GRP = (groupLabel, buttons) => ({ groupLabel, buttons })

const BUTTON_GROUPS = {
  simple: [
    GRP('Numbers', DIGIT_BTNS),
    GRP('Operators', ARITH_BTNS),
    GRP('Functions', [...MATH_FUNC_BTNS, ...CUSTOM_FUNC_BTNS]),
    GRP('Constants', CONST_BTNS),
  ],
  intermediate: [
    GRP('Numbers', DIGIT_BTNS),
    GRP('Operators', ARITH_BTNS),
    GRP('Functions', [...MATH_FUNC_BTNS, ...CUSTOM_FUNC_BTNS]),
    GRP('Constants', CONST_BTNS),
  ],
  boolean: [
    GRP('Values & Grouping', BOOL_VAL_BTNS),
    GRP('Bitwise & Compare', BOOL_BITWISE_BTNS),
    GRP('Logic Functions', BOOL_LOGIC_BTNS),
    GRP('Multi-arg Functions', BOOL_FUNC_BTNS),
    GRP('Constants', BOOL_CONST_BINS),
  ],
  trig: [
    GRP('Numbers', DIGIT_BTNS),
    GRP('Arithmetic', ARITH_BTNS),
    GRP('Trig (Radians)', TRIG_RAD_BTNS),
    GRP('Trig (Degrees)', TRIG_DEG_BTNS),
    GRP('Trig (Hyperbolic)', TRIG_DEG_HYP),
    GRP('Trig (Combined)', TRIG_DEG_COMBINED),
    GRP('Constants', CONST_BTNS),
  ],
  combined: [
    GRP('Numbers', DIGIT_BTNS),
    GRP('Arithmetic', ARITH_BTNS),
    GRP('Math Functions', [...MATH_FUNC_BTNS, ...CUSTOM_FUNC_BTNS]),
    GRP('Trig (Radians)', TRIG_RAD_BTNS),
    GRP('Trig (Degrees)', TRIG_DEG_BTNS),
    GRP('Trig (Hyperbolic)', TRIG_DEG_HYP),
    GRP('Trig (Combined)', TRIG_DEG_COMBINED),
    GRP('Boolean Ops', BOOL_BITWISE_BTNS),
    GRP('Logic Functions', BOOL_LOGIC_BTNS),
    GRP('Boolean Funcs', BOOL_FUNC_BTNS),
    GRP('Constants', CONST_BTNS),
  ],
}

const NOTES = {
  intermediate: 'Results from this calculator are automatically saved to calculation history.',
}

export default function CalculatorPage() {
  const [activeTab, setActiveTab] = useState('simple')
  const { username, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  return (
    <div className={styles.page}>
      <AppHeader
        username={username}
        onLogout={handleLogout}
        onBack={() => navigate('/dashboard')}
        backLabel="Dashboard"
        glyph="="
      />

      <div className={styles.chrome}>
        <ToolHero
          badge="Sprint 2 · Calculator"
          title="Computation"
          accent="Flight Deck"
          description="Switch between arithmetic, boolean, trigonometric, combined, complex, and financial modes inside the same futuristic tool shell."
          stats={[
            { value: '7', label: 'modes' },
            { value: '50+', label: 'functions' },
            { value: '1', label: 'workspace' },
          ]}
        />

        <PageTabs
          tabs={TABS}
          activeTab={activeTab}
          onChange={setActiveTab}
          ariaLabel="Calculator modes"
        />

        <main className={styles.main}>
          <section className={styles.content}>
            {activeTab === 'complex'
              ? <ComplexCalc />
              : activeTab === 'financial'
              ? <FinancialCalc />
              : (
                <StandardCalc
                  key={activeTab}
                  evaluateFn={EVALUATE_FNS[activeTab]}
                  buttonGroups={BUTTON_GROUPS[activeTab]}
                  note={NOTES[activeTab] || ''}
                  mode={activeTab}
                />
              )}
          </section>
        </main>
      </div>
    </div>
  )
}
