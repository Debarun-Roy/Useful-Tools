import { useState, useEffect, useRef } from 'react'
import { validateExpression } from '../../../api/apiClient'
import styles from './StandardCalc.module.css'

/**
 * StandardCalc — the shared calculator UI for all non-complex modes.
 *
 * PROPS:
 *   evaluateFn    — async function(expression) → { data }
 *   buttonGroups  — array of { groupLabel, buttons[] }
 *   note          — optional info string (Intermediate mode)
 *   mode          — the active tab id: 'simple'|'intermediate'|'trig'|
 *                   'boolean'|'combined'
 *                   Passed to validateExpression() so the backend uses the
 *                   correct expression builder for this mode.
 *   isGuest       — boolean flag: true if user is a guest
 *
 * FIX: Previously validateExpression(expression) was called without a mode.
 * The backend defaulted to standard arithmetic validation, which rejected
 * all boolean expressions (1&0, majority(1,0,1), etc.) because the boolean
 * unicode operators are not in the global OperatorRegistry.
 * Now validateExpression(expression, mode) passes the active tab id, and the
 * backend selects the appropriate ExpressionBuilder.
 */
export default function StandardCalc({ evaluateFn, buttonGroups, note, mode, isGuest }) {

  const [expression, setExpression] = useState('')
  const [result,     setResult]     = useState(null)
  const [error,      setError]      = useState('')
  const [loading,    setLoading]    = useState(false)
  const [validState, setValidState] = useState('neutral')

  const inputRef = useRef(null)

  // ── Live validation — debounced 600ms ──────────────────────────────────────
  useEffect(() => {
    if (!expression.trim()) {
      setValidState('neutral')
      return
    }
    // Reset immediately so = stays disabled during the debounce window.
    setValidState('neutral')

    const timer = setTimeout(async () => {
      try {
        // FIX: pass mode so backend uses the correct builder.
        const { data } = await validateExpression(expression, mode)
        if (data.success) {
          setValidState(data.data.valid ? 'valid' : 'invalid')
        } else {
          setValidState('neutral')
        }
      } catch {
        setValidState('neutral')
      }
    }, 600)

    return () => clearTimeout(timer)
  }, [expression, mode])

  // ── Handlers ───────────────────────────────────────────────────────────────

  function handleInsert(insert) {
    setExpression(prev => prev + insert)
    setResult(null)
    setError('')
    inputRef.current?.focus()
  }

  function handleBackspace() {
    setExpression(prev => prev.slice(0, -1))
    setResult(null)
    setError('')
    inputRef.current?.focus()
  }

  function handleClear() {
    setExpression('')
    setResult(null)
    setError('')
    setValidState('neutral')
    inputRef.current?.focus()
  }

  async function handleSubmit() {
    const trimmed = expression.trim()
    if (!trimmed || validState !== 'valid') return

    setLoading(true)
    setError('')
    setResult(null)

    try {
      const { data } = await evaluateFn(trimmed)

      if (data.success) {
        setResult(data.data.result)
      } else {
        setError(data.error || 'Evaluation failed. Please check the expression.')
      }
    } catch {
      setError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setLoading(false)
    }
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter')  { e.preventDefault(); handleSubmit() }
    if (e.key === 'Escape') { handleClear() }
  }

  function formatResult(val) {
    if (val === null || val === undefined) return ''
    if (val === 'NaN')       return 'Undefined (NaN)'
    if (val === 'Infinity')  return '∞ (Infinity)'
    if (val === '-Infinity') return '-∞ (-Infinity)'
    const num = typeof val === 'number' ? val : Number(val)
    if (Number.isFinite(num) && num === Math.trunc(num) && Math.abs(num) < 1e15) {
      return num.toFixed(0)
    }
    return String(val)
  }

  const inputClass = [
    styles.expressionInput,
    validState === 'valid'   ? styles.inputValid   : '',
    validState === 'invalid' ? styles.inputInvalid : '',
  ].join(' ')

  const equalsDisabled = loading || !expression.trim() || validState !== 'valid'

  // ─────────────────────────────────────────────────────────────────────────
  return (
    <div className={styles.calculator}>

      {note && <div className={styles.note}>{note}</div>}

      {!isGuest && (
        <div className={styles.note}>
          Results from this calculator are automatically saved to calculation history.
        </div>
      )}

      <div className={styles.displayPanel}>

        <div className={styles.expressionRow}>
          <input
            ref={inputRef}
            type="text"
            className={inputClass}
            value={expression}
            onChange={e => {
              setExpression(e.target.value)
              setResult(null)
              setError('')
            }}
            onKeyDown={handleKeyDown}
            placeholder="Type or use the buttons below…"
            autoComplete="off"
            spellCheck={false}
            disabled={loading}
          />
          <span
            className={styles.validDot}
            title={
              validState === 'valid'   ? 'Expression syntax is valid'   :
              validState === 'invalid' ? 'Expression syntax is invalid' :
              expression.trim()        ? 'Validating…'                  : ''
            }
            style={{
              background:
                validState === 'valid'   ? 'var(--clr-success)' :
                validState === 'invalid' ? 'var(--clr-error)'   :
                expression.trim()        ? '#f59e0b'            :
                'transparent',
            }}
          />
        </div>

        {result !== null && (
          <div className={styles.resultRow}>
            <span className={styles.resultEquals}>=</span>
            <span className={styles.resultValue}>{formatResult(result)}</span>
          </div>
        )}

        {error && (
          <div className={styles.errorRow} role="alert">{error}</div>
        )}
      </div>

      <div className={styles.controlRow}>
        <button className={styles.btnClear}     onClick={handleClear}     disabled={loading}>C</button>
        <button className={styles.btnBackspace} onClick={handleBackspace} disabled={loading}>⌫</button>
        <button
          className={styles.btnEquals}
          onClick={handleSubmit}
          disabled={equalsDisabled}
          title={
            validState === 'invalid'                      ? 'Expression is invalid'     :
            validState === 'neutral' && expression.trim() ? 'Validating expression…'   :
            ''
          }
        >
          {loading ? '…' : '='}
        </button>
      </div>

      <div className={styles.buttonGroups}>
        {buttonGroups.map(group => (
          <section key={group.groupLabel} className={styles.group}>
            <h3 className={styles.groupLabel}>{group.groupLabel}</h3>
            <div className={styles.buttonGrid}>
              {group.buttons.map((btn, idx) => (
                <button
                  key={`${btn.insert}-${idx}`}
                  className={styles[`btn_${btn.type}`] || styles.btn_default}
                  onClick={() => handleInsert(btn.insert)}
                  disabled={loading}
                  title={btn.insert !== btn.display ? `Inserts: ${btn.insert}` : undefined}
                >
                  {btn.display}
                </button>
              ))}
            </div>
          </section>
        ))}
      </div>

    </div>
  )
}