import { useState } from 'react'
import { calculateEMI } from '../../../api/apiClient'
import styles from './FinancialCalc.module.css'

export default function EMI() {
  const [principal,  setPrincipal]  = useState('')
  const [annualRate, setAnnualRate] = useState('')
  const [tenure,     setTenure]     = useState('')
  const [tenureUnit, setTenureUnit] = useState('years')
  const [result,     setResult]     = useState(null)
  const [error,      setError]      = useState('')
  const [loading,    setLoading]    = useState(false)

  function isNum(val) {
    return val.trim() !== '' && !isNaN(parseFloat(val))
  }

  function validate() {
    if (!isNum(principal) || !isNum(annualRate) || !isNum(tenure)) {
      setError('Please enter valid numbers for all fields.')
      return false
    }
    if (parseFloat(principal) <= 0 || parseFloat(tenure) <= 0) {
      setError('Principal and tenure must be positive.')
      return false
    }
    if (parseFloat(annualRate) < 0) {
      setError('Interest rate cannot be negative.')
      return false
    }
    return true
  }

  async function handleCalculate() {
    if (!validate()) return
    setLoading(true); setError(''); setResult(null)

    try {
      const { data } = await calculateEMI(
        parseFloat(principal),
        parseFloat(annualRate),
        parseFloat(tenure),
        tenureUnit
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
    setPrincipal(''); setAnnualRate(''); setTenure(''); setTenureUnit('years')
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
              placeholder="Enter loan amount"
              value={principal}
              onChange={e => { setPrincipal(e.target.value); setResult(null); setError('') }}
              className={styles.input}
              disabled={loading}
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="rate" className={styles.label}>Annual Rate (%)</label>
            <input
              id="rate"
              type="number"
              placeholder="Enter interest rate"
              value={annualRate}
              onChange={e => { setAnnualRate(e.target.value); setResult(null); setError('') }}
              className={styles.input}
              disabled={loading}
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="tenure" className={styles.label}>Tenure</label>
            <div className={styles.tenureRow}>
              <input
                id="tenure"
                type="number"
                placeholder="Enter duration"
                value={tenure}
                onChange={e => { setTenure(e.target.value); setResult(null); setError('') }}
                className={styles.input}
                disabled={loading}
              />
              <select
                value={tenureUnit}
                onChange={e => setTenureUnit(e.target.value)}
                className={styles.select}
                disabled={loading}
              >
                <option value="years">Years</option>
                <option value="months">Months</option>
              </select>
            </div>
          </div>
        </div>

        {error && <div className={styles.errorRow} role="alert">{error}</div>}

        {result && (
          <div className={styles.resultsGrid}>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Monthly EMI</div>
              <div className={styles.resultValue}>
                ₹ {result.emi.toLocaleString('en-IN', { maximumFractionDigits: 2 })}
              </div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Total Amount</div>
              <div className={styles.resultValue}>
                ₹ {result.totalAmount.toLocaleString('en-IN', { maximumFractionDigits: 2 })}
              </div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Total Interest</div>
              <div className={styles.resultValue}>
                ₹ {result.totalInterest.toLocaleString('en-IN', { maximumFractionDigits: 2 })}
              </div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Tenure</div>
              <div className={styles.resultValue}>{result.tenureMonths} months</div>
            </div>
          </div>
        )}
      </div>

      <div className={styles.controlRow}>
        <button onClick={handleClear}     className={styles.btnClear}  disabled={loading}>Clear</button>
        <button onClick={handleCalculate} className={styles.btnEquals} disabled={loading}>
          {loading ? 'Calculating…' : 'Calculate EMI'}
        </button>
      </div>
    </div>
  )
}