import { useState } from 'react'
import styles from './FinancialCalc.module.css'

/**
 * Compound Interest Calculator
 * Formula: A = P(1 + r/n)^(nt)
 * Where:
 *   P = Principal
 *   r = Annual interest rate (as decimal)
 *   n = Number of times interest is compounded per year
 *   t = Time in years
 *   A = Final amount
 */
export default function CompoundInterest() {
  const [principal, setPrincipal] = useState('')
  const [rate, setRate] = useState('')
  const [time, setTime] = useState('')
  const [frequency, setFrequency] = useState('annually')
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const FREQUENCIES = {
    annually: 1,
    semiannually: 2,
    quarterly: 4,
    monthly: 12,
    daily: 365,
  }

  function isNum(val) {
    return val.trim() !== '' && !isNaN(parseFloat(val))
  }

  function validate() {
    if (!isNum(principal) || !isNum(rate) || !isNum(time)) {
      setError('Please enter valid numbers.')
      return false
    }
    const p = parseFloat(principal)
    const r = parseFloat(rate)
    const t = parseFloat(time)
    if (p <= 0 || r < 0 || t <= 0) {
      setError('Principal and time must be positive. Rate cannot be negative.')
      return false
    }
    return true
  }

  function calculateCompoundInterest() {
    if (!validate()) return

    setLoading(true)
    setError('')
    setResult(null)

    try {
      const p = parseFloat(principal)
      const r = parseFloat(rate) / 100 // Convert percentage to decimal
      const t = parseFloat(time)
      const n = FREQUENCIES[frequency]

      // Compound Interest Formula: A = P(1 + r/n)^(nt)
      const amount = p * Math.pow(1 + r / n, n * t)
      const interestEarned = amount - p

      setResult({
        principal: p,
        amount: parseFloat(amount.toFixed(2)),
        interest: parseFloat(interestEarned.toFixed(2)),
        rate: parseFloat(rate),
        time,
        frequency,
        isPositive: interestEarned >= 0,
      })
    } catch (err) {
      setError('Calculation error. Please check your inputs.')
    } finally {
      setLoading(false)
    }
  }

  function handleClear() {
    setPrincipal('')
    setRate('')
    setTime('')
    setFrequency('annually')
    setResult(null)
    setError('')
  }

  return (
    <div className={styles.calculator}>
      <div className={styles.displayPanel}>
        <div className={styles.inputGrid}>
          <div className={styles.inputGroup}>
            <label htmlFor="principal" className={styles.label}>Principal (₹)</label>
            <input
              id="principal"
              type="number"
              placeholder="Enter initial amount"
              value={principal}
              onChange={(e) => {
                setPrincipal(e.target.value)
                setResult(null)
                setError('')
              }}
              className={styles.input}
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="rate" className={styles.label}>Annual Rate (% p.a.)</label>
            <input
              id="rate"
              type="number"
              placeholder="Enter interest rate"
              value={rate}
              onChange={(e) => {
                setRate(e.target.value)
                setResult(null)
                setError('')
              }}
              className={styles.input}
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="time" className={styles.label}>Time (Years)</label>
            <input
              id="time"
              type="number"
              placeholder="Enter duration"
              value={time}
              onChange={(e) => {
                setTime(e.target.value)
                setResult(null)
                setError('')
              }}
              className={styles.input}
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="frequency" className={styles.label}>Compounding Frequency</label>
            <select
              id="frequency"
              value={frequency}
              onChange={(e) => {
                setFrequency(e.target.value)
                setResult(null)
              }}
              className={styles.select}
            >
              <option value="annually">Annually</option>
              <option value="semiannually">Semi-Annually</option>
              <option value="quarterly">Quarterly</option>
              <option value="monthly">Monthly</option>
              <option value="daily">Daily</option>
            </select>
          </div>
        </div>

        {error && <div className={styles.errorRow}>{error}</div>}

        {result && (
          <div className={styles.resultsGrid}>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Principal Amount</div>
              <div className={styles.resultValue}>₹ {result.principal.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Interest Earned</div>
              <div className={`${styles.resultValue} ${result.isPositive ? styles.positive : styles.negative}`}>
                ₹ {result.interest.toLocaleString('en-IN', { maximumFractionDigits: 2 })}
              </div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Final Amount</div>
              <div className={styles.resultValue}>₹ {result.amount.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Return</div>
              <div className={`${styles.resultValue} ${result.isPositive ? styles.positive : styles.negative}`}>
                {((result.interest / result.principal) * 100).toFixed(2)}%
              </div>
            </div>
          </div>
        )}
      </div>

      <div className={styles.controlRow}>
        <button onClick={handleClear} className={styles.btnClear} disabled={loading}>Clear</button>
        <button onClick={calculateCompoundInterest} className={styles.btnEquals} disabled={loading}>
          {loading ? 'Calculating...' : 'Calculate Interest'}
        </button>
      </div>
    </div>
  )
}
