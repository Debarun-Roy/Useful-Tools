import { useState } from 'react'
import { evaluateComplex } from '../../../api/apiClient'
import styles from './ComplexCalc.module.css'

/**
 * ComplexCalc — complex number calculator.
 *
 * DESIGN:
 * Unlike StandardCalc (where the user builds a text expression), ComplexCalc
 * uses four number inputs (real1, imag1, real2, imag2) and operation buttons.
 * Clicking an operation button immediately constructs the operation string and
 * calls the backend — there is no "=" button.
 *
 * OPERATION STRINGS sent to POST /api/calculator/complex:
 *   Binary:  complex_add(a+bi,c+di)        complex_subtract(a+bi,c+di)
 *   Unary:   conj(a+bi)                    real(a,b)    imag(a,b)    csq(a,b)
 *
 * BACKEND REGEX LIMITATION:
 * The backend parses binary and conj operations with a regex that requires the
 * format "a+bi" — both the real and imaginary parts must be non-negative for
 * these to parse correctly. Negative imaginary parts produce a backend parse
 * error. The real(a,b), imag(a,b), and csq(a,b) operations go through exp4j
 * directly and handle negative values correctly.
 *
 * This limitation is noted to the user via the hint text under the inputs.
 */

// Operation definitions.
// binary: true  → uses both complex numbers, requires real2+imag2 inputs.
// binary: false → uses only the first complex number.
// buildOp: constructs the exact operation string the backend expects.
const OPERATIONS = [
  {
    id:      'add',
    label:   'Add  (z₁ + z₂)',
    binary:  true,
    buildOp: (r1, i1, r2, i2) => `complex_add(${r1}+${i1}i,${r2}+${i2}i)`,
  },
  {
    id:      'subtract',
    label:   'Subtract  (z₁ − z₂)',
    binary:  true,
    buildOp: (r1, i1, r2, i2) => `complex_subtract(${r1}+${i1}i,${r2}+${i2}i)`,
  },
  {
    id:      'conj',
    label:   'Conjugate  (z̄₁)',
    binary:  false,
    buildOp: (r1, i1) => `conj(${r1}+${i1}i)`,
    requiresNonNegImag: true,
  },
  {
    id:      'real',
    label:   'Real Part  (Re z₁)',
    binary:  false,
    buildOp: (r1, i1) => `real(${r1},${i1})`,
  },
  {
    id:      'imag',
    label:   'Imag Part  (Im z₁)',
    binary:  false,
    buildOp: (r1, i1) => `imag(${r1},${i1})`,
  },
  {
    id:      'csq',
    label:   'csq  (a²−b²)',
    binary:  false,
    buildOp: (r1, i1) => `csq(${r1},${i1})`,
  },
]

export default function ComplexCalc({ isGuest }) {

  const [real1, setReal1] = useState('')
  const [imag1, setImag1] = useState('')
  const [real2, setReal2] = useState('')
  const [imag2, setImag2] = useState('')
  const [result,  setResult]  = useState(null)   // { real, imag, display } from backend
  const [error,   setError]   = useState('')
  const [loading, setLoading] = useState(false)
  const [lastOp,  setLastOp]  = useState(null)   // id of last clicked operation

  // ── Validation ─────────────────────────────────────────────────────────────

  function isNum(val) {
    return val.trim() !== '' && !isNaN(parseFloat(val.trim()))
  }

  function validate(op) {
    if (!isNum(real1) || !isNum(imag1)) {
      return 'Please enter valid numbers for the first complex number.'
    }
    if (op.binary && (!isNum(real2) || !isNum(imag2))) {
      return 'Please enter valid numbers for the second complex number.'
    }
    // The backend regex for conj and binary ops requires non-negative imaginary parts.
    if (op.requiresNonNegImag || op.binary) {
      if (parseFloat(imag1) < 0) {
        return 'The imaginary part of z₁ must be non-negative for this operation.'
      }
      if (op.binary && parseFloat(imag2) < 0) {
        return 'The imaginary part of z₂ must be non-negative for this operation.'
      }
    }
    return null   // null = no error
  }

  // ── Submit ─────────────────────────────────────────────────────────────────

  async function handleOperation(op) {
    const validationError = validate(op)
    if (validationError) { setError(validationError); return }

    setLoading(true)
    setError('')
    setResult(null)
    setLastOp(op.id)

    const r1 = real1.trim(), i1 = imag1.trim()
    const r2 = real2.trim(), i2 = imag2.trim()

    const operation = op.binary
      ? op.buildOp(r1, i1, r2, i2)
      : op.buildOp(r1, i1)

    try {
      const { data } = await evaluateComplex(operation)
      if (data.success) {
        setResult(data.data)
      } else {
        setError(data.error || 'Operation failed. Please check your inputs.')
      }
    } catch {
      setError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setLoading(false)
    }
  }

  function handleClear() {
    setReal1(''); setImag1(''); setReal2(''); setImag2('')
    setResult(null); setError(''); setLastOp(null)
  }

  // ─────────────────────────────────────────────────────────────────────────
  return (
    <div className={styles.calculator}>

      {!isGuest && (
        <div className={styles.note}>
          Results from this calculator are automatically saved to calculation history.
        </div>
      )}

      {/* ── Input section ─────────────────────────────────────────────── */}
      <div className={styles.inputSection}>

        <div className={styles.inputBlock}>
          <h3 className={styles.inputLabel}>First complex number  (z₁)</h3>
          <div className={styles.complexInputRow}>
            <div className={styles.inputGroup}>
              <label className={styles.partLabel}>Real</label>
              <input
                type="text"
                className={styles.numberInput}
                value={real1}
                onChange={e => { setReal1(e.target.value); setError(''); setResult(null) }}
                placeholder="e.g. 3"
                disabled={loading}
              />
            </div>
            <span className={styles.plusSign}>+</span>
            <div className={styles.inputGroup}>
              <label className={styles.partLabel}>Imaginary</label>
              <input
                type="text"
                className={styles.numberInput}
                value={imag1}
                onChange={e => { setImag1(e.target.value); setError(''); setResult(null) }}
                placeholder="e.g. 4"
                disabled={loading}
              />
            </div>
            <span className={styles.iSuffix}>i</span>
          </div>
        </div>

        <div className={styles.inputBlock}>
          <h3 className={styles.inputLabel}>
            Second complex number  (z₂)
            <span className={styles.binaryHint}>— used by binary operations only</span>
          </h3>
          <div className={styles.complexInputRow}>
            <div className={styles.inputGroup}>
              <label className={styles.partLabel}>Real</label>
              <input
                type="text"
                className={styles.numberInput}
                value={real2}
                onChange={e => { setReal2(e.target.value); setError(''); setResult(null) }}
                placeholder="e.g. 1"
                disabled={loading}
              />
            </div>
            <span className={styles.plusSign}>+</span>
            <div className={styles.inputGroup}>
              <label className={styles.partLabel}>Imaginary</label>
              <input
                type="text"
                className={styles.numberInput}
                value={imag2}
                onChange={e => { setImag2(e.target.value); setError(''); setResult(null) }}
                placeholder="e.g. 2"
                disabled={loading}
              />
            </div>
            <span className={styles.iSuffix}>i</span>
          </div>
        </div>

        <p className={styles.hint}>
          ⓘ For <strong>Add</strong>, <strong>Subtract</strong>, and <strong>Conjugate</strong>,
          imaginary parts must be non-negative (backend constraint).
          <strong> Real Part</strong>, <strong>Imag Part</strong>, and <strong>csq</strong> accept any value.
        </p>
      </div>

      {/* ── Operation buttons ──────────────────────────────────────────── */}
      <div className={styles.operationsSection}>
        <h3 className={styles.sectionLabel}>Operations</h3>

        <div className={styles.binaryOps}>
          <span className={styles.opsGroupLabel}>Binary (uses z₁ and z₂)</span>
          <div className={styles.opsRow}>
            {OPERATIONS.filter(op => op.binary).map(op => (
              <button
                key={op.id}
                className={`${styles.opBtn} ${lastOp === op.id ? styles.opBtnActive : ''}`}
                onClick={() => handleOperation(op)}
                disabled={loading}
              >
                {op.label}
              </button>
            ))}
          </div>
        </div>

        <div className={styles.unaryOps}>
          <span className={styles.opsGroupLabel}>Unary (uses z₁ only)</span>
          <div className={styles.opsRow}>
            {OPERATIONS.filter(op => !op.binary).map(op => (
              <button
                key={op.id}
                className={`${styles.opBtn} ${lastOp === op.id ? styles.opBtnActive : ''}`}
                onClick={() => handleOperation(op)}
                disabled={loading}
              >
                {op.label}
              </button>
            ))}
          </div>
        </div>

        <button className={styles.clearBtn} onClick={handleClear} disabled={loading}>
          Clear all
        </button>
      </div>

      {/* ── Result ────────────────────────────────────────────────────── */}
      {result && (
        <div className={styles.resultPanel}>
          <span className={styles.resultLabel}>Result</span>
          <span className={styles.resultDisplay}>{result.display}</span>
          <div className={styles.resultParts}>
            <span>Real: <strong>{result.real}</strong></span>
            <span>Imaginary: <strong>{result.imag}</strong></span>
          </div>
        </div>
      )}

      {/* ── Error ─────────────────────────────────────────────────────── */}
      {error && (
        <div className={styles.errorRow} role="alert">{error}</div>
      )}

    </div>
  )
}
