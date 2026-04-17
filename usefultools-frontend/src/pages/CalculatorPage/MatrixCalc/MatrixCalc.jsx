import { useState } from 'react'
import { calculateMatrix } from '../../../api/apiClient'
import styles from './MatrixCalc.module.css'

const OPERATIONS = [
  { id: 'determinant', label: 'Determinant',  needsTwo: false, scalarResult: true },
  { id: 'transpose',   label: 'Transpose',    needsTwo: false, scalarResult: false },
  { id: 'inverse',     label: 'Inverse',      needsTwo: false, scalarResult: false },
  { id: 'multiply',    label: 'Multiply  (A × B)', needsTwo: true,  scalarResult: false },
]

/** Returns a fresh size×size matrix of empty strings. */
function emptyMatrix(size) {
  return Array.from({ length: size }, () => Array(size).fill(''))
}

/** Parse a matrix of strings to a 2D array of numbers. Returns null if any cell is invalid. */
function parseMatrix(mat) {
  const result = []
  for (const row of mat) {
    const parsedRow = []
    for (const cell of row) {
      const n = Number(cell)
      if (cell.trim() === '' || isNaN(n)) return null
      parsedRow.push(n)
    }
    result.push(parsedRow)
  }
  return result
}

function MatrixInput({ matrix, onChange, label, size }) {
  return (
    <div className={styles.matrixBlock}>
      <span className={styles.matrixLabel}>{label}</span>
      <div
        className={styles.matrixGrid}
        style={{ gridTemplateColumns: `repeat(${size}, 1fr)` }}
      >
        {matrix.map((row, i) =>
          row.map((cell, j) => (
            <input
              key={`${i}-${j}`}
              type="text"
              inputMode="numeric"
              className={styles.cellInput}
              value={cell}
              onChange={e => onChange(i, j, e.target.value)}
              placeholder="0"
            />
          ))
        )}
      </div>
    </div>
  )
}

function MatrixDisplay({ matrix, size }) {
  return (
    <div
      className={styles.resultGrid}
      style={{ gridTemplateColumns: `repeat(${size}, 1fr)` }}
    >
      {matrix.map((row, i) =>
        row.map((cell, j) => (
          <span key={`${i}-${j}`} className={styles.resultCell}>
            {typeof cell === 'number'
              ? (Number.isInteger(cell) ? cell : parseFloat(cell.toFixed(8)))
              : cell}
          </span>
        ))
      )}
    </div>
  )
}

export default function MatrixCalc({ isGuest }) {
  const [size,      setSize]      = useState(2)
  const [operation, setOperation] = useState('determinant')
  const [matA,      setMatA]      = useState(emptyMatrix(2))
  const [matB,      setMatB]      = useState(emptyMatrix(2))
  const [result,    setResult]    = useState(null)
  const [error,     setError]     = useState('')
  const [loading,   setLoading]   = useState(false)

  const op = OPERATIONS.find(o => o.id === operation)

  function handleSizeChange(newSize) {
    setSize(newSize)
    setMatA(emptyMatrix(newSize))
    setMatB(emptyMatrix(newSize))
    setResult(null)
    setError('')
  }

  function handleOpChange(newOp) {
    setOperation(newOp)
    setResult(null)
    setError('')
  }

  function updateCell(mat, setMat, i, j, value) {
    setMat(prev => {
      const next = prev.map(r => [...r])
      next[i][j] = value
      return next
    })
    setResult(null)
    setError('')
  }

  async function handleCalculate() {
    const m1 = parseMatrix(matA)
    if (!m1) { setError('All cells in Matrix A must be valid numbers.'); return }

    let m2 = null
    if (op.needsTwo) {
      m2 = parseMatrix(matB)
      if (!m2) { setError('All cells in Matrix B must be valid numbers.'); return }
    }

    setLoading(true)
    setError('')
    setResult(null)

    try {
      const { data } = await calculateMatrix(operation, size, m1, m2)
      if (data.success) {
        setResult(data.data)
      } else {
        setError(data.error || 'Calculation failed. Please check your inputs.')
      }
    } catch {
      setError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setLoading(false)
    }
  }

  function handleClear() {
    setMatA(emptyMatrix(size))
    setMatB(emptyMatrix(size))
    setResult(null)
    setError('')
  }

  return (
    <div className={styles.calculator}>

      {/* ── Controls ─────────────────────────────────────────────── */}
      <div className={styles.controlRow}>

        <div className={styles.controlGroup}>
          <span className={styles.controlLabel}>Size</span>
          <div className={styles.toggle}>
            {[2, 3].map(s => (
              <button
                key={s}
                className={size === s ? styles.toggleActive : styles.toggleBtn}
                onClick={() => handleSizeChange(s)}
                disabled={loading}
              >
                {s}×{s}
              </button>
            ))}
          </div>
        </div>

        <div className={styles.controlGroup}>
          <span className={styles.controlLabel}>Operation</span>
          <div className={styles.opGrid}>
            {OPERATIONS.map(o => (
              <button
                key={o.id}
                className={operation === o.id ? styles.opBtnActive : styles.opBtn}
                onClick={() => handleOpChange(o.id)}
                disabled={loading}
              >
                {o.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* ── Matrix inputs ─────────────────────────────────────────── */}
      <div className={styles.matricesRow}>
        <MatrixInput
          matrix={matA}
          onChange={(i, j, v) => updateCell(matA, setMatA, i, j, v)}
          label={op.needsTwo ? 'Matrix A' : 'Matrix'}
          size={size}
        />

        {op.needsTwo && (
          <>
            <span className={styles.timesSign} aria-hidden="true">×</span>
            <MatrixInput
              matrix={matB}
              onChange={(i, j, v) => updateCell(matB, setMatB, i, j, v)}
              label="Matrix B"
              size={size}
            />
          </>
        )}
      </div>

      {/* ── Action row ────────────────────────────────────────────── */}
      <div className={styles.actionRow}>
        <button className={styles.calcBtn} onClick={handleCalculate} disabled={loading}>
          {loading ? 'Calculating…' : 'Calculate'}
        </button>
        <button className={styles.clearBtn} onClick={handleClear} disabled={loading}>
          Clear
        </button>
      </div>

      {/* ── Error ─────────────────────────────────────────────────── */}
      {error && (
        <div className={styles.errorRow} role="alert">{error}</div>
      )}

      {/* ── Result ────────────────────────────────────────────────── */}
      {result && (
        <div className={styles.resultPanel}>
          <span className={styles.resultLabel}>
            {op.label} result
          </span>

          {op.scalarResult ? (
            <div className={styles.scalarResult}>{result.result}</div>
          ) : (
            <MatrixDisplay matrix={result.result} size={size} />
          )}
        </div>
      )}

    </div>
  )
}