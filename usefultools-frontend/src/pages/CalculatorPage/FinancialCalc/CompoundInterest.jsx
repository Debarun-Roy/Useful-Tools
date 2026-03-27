import { useState } from 'react'
import { calculateCompoundInterest } from '../../../api/apiClient'
import styles from './FinancialCalc.module.css'

export default function CompoundInterest() {
  const [principal,  setPrincipal]  = useState('')
  const [rate,       setRate]       = useState('')
  const [time,       setTime]       = useState('')
  const [frequency,  setFrequency]  = useState('annually')
  const [result,     setResult]     = useState(null)
  const [error,      setError]      = useState('')
  const [loading,    setLoading]    = useState(false)

  function validate() {
    if (!principal.trim() || !rate.trim() || !time.trim()) {
      setError('Please enter valid numbers for all fields.')
      return false
    }
    if (parseFloat(principal) <= 0 || parseFloat(time) <= 0) {
      setError('Principal and time must be positive.')
      return false
    }
    if (parseFloat(rate) < 0) {
      setError('Rate cannot be negative.')
      return false
    }
    return true
  }

  async function handleCalculate() {
    if (!validate()) return
    setLoading(true); setError(''); setResult(null)

    try {
      const { data } = await calculateCompoundInterest(
        parseFloat(principal),
        parseFloat(rate),
        parseFloat(time),
        frequency
      )
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
    setPrincipal(''); setRate(''); setTime(''); setFrequency('annually')
    setResult(null); setError('')
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
              onChange={e => { setPrincipal(e.target.value); setResult(null); setError('') }}
              className={styles.input}
              disabled={loading}
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="rate" className={styles.label}>Annual Rate (% p.a.)</label>
            <input
              id="rate"
              type="number"
              placeholder="Enter interest rate"
              value={rate}
              onChange={e => { setRate(e.target.value); setResult(null); setError('') }}
              className={styles.input}
              disabled={loading}
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="time" className={styles.label}>Time (Years)</label>
            <input
              id="time"
              type="number"
              placeholder="Enter duration"
              value={time}
              onChange={e => { setTime(e.target.value); setResult(null); setError('') }}
              className={styles.input}
              disabled={loading}
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="frequency" className={styles.label}>Compounding Frequency</label>
            <select
              id="frequency"
              value={frequency}
              onChange={e => { setFrequency(e.target.value); setResult(null) }}
              className={styles.select}
              disabled={loading}
            >
              <option value="annually">Annually</option>
              <option value="semiannually">Semi-Annually</option>
              <option value="quarterly">Quarterly</option>
              <option value="monthly">Monthly</option>
              <option value="daily">Daily</option>
            </select>
          </div>
        </div>

        {error && <div className={styles.errorRow} role="alert">{error}</div>}

        {result && (
          <div className={styles.resultsGrid}>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Principal Amount</div>
              <div className={styles.resultValue}>
                ₹ {result.principal.toLocaleString('en-IN', { maximumFractionDigits: 2 })}
              </div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Interest Earned</div>
              <div className={styles.resultValue}>
                ₹ {result.interestEarned.toLocaleString('en-IN', { maximumFractionDigits: 2 })}
              </div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Final Amount</div>
              <div className={styles.resultValue}>
                ₹ {result.finalAmount.toLocaleString('en-IN', { maximumFractionDigits: 2 })}
              </div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Effective Annual Rate</div>
              <div className={styles.resultValue}>{result.effectiveRate}%</div>
            </div>
          </div>
        )}
      </div>

      <div className={styles.controlRow}>
        <button onClick={handleClear}     className={styles.btnClear}  disabled={loading}>Clear</button>
        <button onClick={handleCalculate} className={styles.btnEquals} disabled={loading}>
          {loading ? 'Calculating…' : 'Calculate Interest'}
        </button>
      </div>
    </div>
  )
}