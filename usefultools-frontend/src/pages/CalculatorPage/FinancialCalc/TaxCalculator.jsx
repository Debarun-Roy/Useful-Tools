import { useState } from 'react'
import { calculateTax } from '../../../api/apiClient'
import styles from './FinancialCalc.module.css'

export default function TaxCalculator() {
  const [income,     setIncome]     = useState('')
  const [regime,     setRegime]     = useState('new')
  const [deductions, setDeductions] = useState('')
  const [result,     setResult]     = useState(null)
  const [error,      setError]      = useState('')
  const [loading,    setLoading]    = useState(false)

  function validate() {
    if (!income.trim() || isNaN(parseFloat(income)) || parseFloat(income) < 0) {
      setError('Please enter a valid income amount.')
      return false
    }
    if (deductions && (isNaN(parseFloat(deductions)) || parseFloat(deductions) < 0)) {
      setError('Please enter valid deductions.')
      return false
    }
    return true
  }

  async function handleCalculate() {
    if (!validate()) return
    setLoading(true); setError(''); setResult(null)

    try {
      const { data } = await calculateTax(
        parseFloat(income),
        regime,
        deductions ? parseFloat(deductions) : 0
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
    setIncome(''); setRegime('new'); setDeductions('')
    setResult(null); setError('')
  }

  return (
    <div className={styles.calculator}>
      <div className={styles.displayPanel}>
        <div className={styles.inputGrid}>
          <div className={styles.inputGroup}>
            <label htmlFor="income" className={styles.label}>Annual Income (₹)</label>
            <input
              id="income"
              type="number"
              placeholder="Enter annual income"
              value={income}
              onChange={e => { setIncome(e.target.value); setResult(null); setError('') }}
              className={styles.input}
              disabled={loading}
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="regime" className={styles.label}>Tax Regime</label>
            <select
              id="regime"
              value={regime}
              onChange={e => { setRegime(e.target.value); setResult(null) }}
              className={styles.select}
              disabled={loading}
            >
              <option value="new">New Regime (2024-25)</option>
              <option value="old">Old Regime (2024-25)</option>
            </select>
          </div>

          {regime === 'old' && (
            <div className={styles.inputGroup}>
              <label htmlFor="deductions" className={styles.label}>Deductions (₹)</label>
              <input
                id="deductions"
                type="number"
                placeholder="80C, 80D, etc."
                value={deductions}
                onChange={e => { setDeductions(e.target.value); setResult(null); setError('') }}
                className={styles.input}
                disabled={loading}
              />
            </div>
          )}
        </div>

        {error && <div className={styles.errorRow} role="alert">{error}</div>}

        {result && (
          <div className={styles.resultsGrid}>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Gross Income</div>
              <div className={styles.resultValue}>
                ₹ {result.grossIncome.toLocaleString('en-IN')}
              </div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Standard Deduction</div>
              <div className={styles.resultValue}>
                ₹ {result.standardDeduction.toLocaleString('en-IN')}
              </div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Taxable Income</div>
              <div className={styles.resultValue}>
                ₹ {result.taxableIncome.toLocaleString('en-IN')}
              </div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Total Tax</div>
              <div className={styles.resultValue}>
                ₹ {result.totalTax.toLocaleString('en-IN', { maximumFractionDigits: 2 })}
              </div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Net Income</div>
              <div className={styles.resultValue}>
                ₹ {result.netIncome.toLocaleString('en-IN', { maximumFractionDigits: 2 })}
              </div>
            </div>
          </div>
        )}
      </div>

      <div className={styles.controlRow}>
        <button onClick={handleClear}     className={styles.btnClear}  disabled={loading}>Clear</button>
        <button onClick={handleCalculate} className={styles.btnEquals} disabled={loading}>
          {loading ? 'Calculating…' : 'Calculate Tax'}
        </button>
      </div>
    </div>
  )
}