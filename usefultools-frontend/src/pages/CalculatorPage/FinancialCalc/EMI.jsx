import { useState } from 'react'
import styles from './FinancialCalc.module.css'

/**
 * EMI Calculator — Calculate Equated Monthly Installment
 * Formula: EMI = P * r * (1 + r)^n / ((1 + r)^n - 1)
 * Where:
 *   P = Principal (loan amount)
 *   r = Monthly interest rate (annual rate / 100 / 12)
 *   n = Number of months (number of years * 12)
 */
export default function EMI() {
  const [principal, setPrincipal] = useState('')
  const [annualRate, setAnnualRate] = useState('')
  const [tenure, setTenure] = useState('')
  const [tenureUnit, setTenureUnit] = useState('years')
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  function isNum(val) {
    return val.trim() !== '' && !isNaN(parseFloat(val))
  }

  function validate() {
    if (!isNum(principal) || !isNum(annualRate) || !isNum(tenure)) {
      setError('Please enter valid numbers.')
      return false
    }
    const p = parseFloat(principal)
    const r = parseFloat(annualRate)
    const t = parseFloat(tenure)
    if (p <= 0 || r < 0 || t <= 0) {
      setError('Principal and tenure must be positive. Rate cannot be negative.')
      return false
    }
    return true
  }

  function calculateEMI() {
    if (!validate()) return

    setLoading(true)
    setError('')
    setResult(null)

    try {
      const p = parseFloat(principal)
      const annualRatePercent = parseFloat(annualRate)
      let months = parseFloat(tenure)

      if (tenureUnit === 'years') {
        months = months * 12
      }

      // Convert annual rate to monthly rate
      const monthlyRate = annualRatePercent / 100 / 12

      // EMI Formula
      let emi = 0
      if (monthlyRate === 0) {
        // If rate is 0, EMI is simply principal divided by number of months
        emi = p / months
      } else {
        const numerator = p * monthlyRate * Math.pow(1 + monthlyRate, months)
        const denominator = Math.pow(1 + monthlyRate, months) - 1
        emi = numerator / denominator
      }

      const totalAmount = emi * months
      const totalInterest = totalAmount - p

      setResult({
        emi: parseFloat(emi.toFixed(2)),
        totalAmount: parseFloat(totalAmount.toFixed(2)),
        totalInterest: parseFloat(totalInterest.toFixed(2)),
        months: Math.round(months),
      })
    } catch (err) {
      setError('Calculation error. Please check your inputs.')
    } finally {
      setLoading(false)
    }
  }

  function handleClear() {
    setPrincipal('')
    setAnnualRate('')
    setTenure('')
    setTenureUnit('years')
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
              placeholder="Enter loan amount"
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
            <label htmlFor="rate" className={styles.label}>Annual Rate (%)</label>
            <input
              id="rate"
              type="number"
              placeholder="Enter interest rate"
              value={annualRate}
              onChange={(e) => {
                setAnnualRate(e.target.value)
                setResult(null)
                setError('')
              }}
              className={styles.input}
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
                onChange={(e) => {
                  setTenure(e.target.value)
                  setResult(null)
                  setError('')
                }}
                className={styles.input}
              />
              <select
                value={tenureUnit}
                onChange={(e) => setTenureUnit(e.target.value)}
                className={styles.select}
              >
                <option value="years">Years</option>
                <option value="months">Months</option>
              </select>
            </div>
          </div>
        </div>

        {error && <div className={styles.errorRow}>{error}</div>}

        {result && (
          <div className={styles.resultsGrid}>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Monthly EMI</div>
              <div className={styles.resultValue}>₹ {result.emi.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Total Amount</div>
              <div className={styles.resultValue}>₹ {result.totalAmount.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Total Interest</div>
              <div className={styles.resultValue}>₹ {result.totalInterest.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Tenure</div>
              <div className={styles.resultValue}>{result.months} months</div>
            </div>
          </div>
        )}
      </div>

      <div className={styles.controlRow}>
        <button onClick={handleClear} className={styles.btnClear} disabled={loading}>Clear</button>
        <button onClick={calculateEMI} className={styles.btnEquals} disabled={loading}>
          {loading ? 'Calculating...' : 'Calculate EMI'}
        </button>
      </div>
    </div>
  )
}
