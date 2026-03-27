import { useState } from 'react'
import styles from './FinancialCalc.module.css'

/**
 * Tax Calculator — Calculate income tax based on tax slabs
 * Supports both 2024-25 tax regimes (Old and New)
 */
export default function TaxCalculator() {
  const [income, setIncome] = useState('')
  const [regime, setRegime] = useState('new')
  const [deductions, setDeductions] = useState('')
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  // Tax slabs for 2024-25 (India)
  const TAX_SLABS = {
    new: [
      { from: 0, to: 300000, rate: 0 },
      { from: 300000, to: 600000, rate: 0.05 },
      { from: 600000, to: 900000, rate: 0.10 },
      { from: 900000, to: 1200000, rate: 0.15 },
      { from: 1200000, to: 1500000, rate: 0.20 },
      { from: 1500000, to: Infinity, rate: 0.30 },
    ],
    old: [
      { from: 0, to: 250000, rate: 0 },
      { from: 250000, to: 500000, rate: 0.05 },
      { from: 500000, to: 1000000, rate: 0.20 },
      { from: 1000000, to: Infinity, rate: 0.30 },
    ],
  }

  function isNum(val) {
    return val.trim() !== '' && !isNaN(parseFloat(val))
  }

  function validate() {
    if (!isNum(income)) {
      setError('Please enter valid income.')
      return false
    }
    const inc = parseFloat(income)
    if (inc < 0) {
      setError('Income cannot be negative.')
      return false
    }
    if (deductions && !isNum(deductions)) {
      setError('Please enter valid deductions.')
      return false
    }
    return true
  }

  function calculateTax() {
    if (!validate()) return

    setLoading(true)
    setError('')
    setResult(null)

    try {
      let taxableIncome = parseFloat(income)
      const ded = deductions ? parseFloat(deductions) : 0

      // Deduct standard deduction in new regime
      let standardDeduction = 0
      if (regime === 'new') {
        standardDeduction = Math.min(75000, taxableIncome)
        taxableIncome -= standardDeduction
      } else {
        taxableIncome -= ded
      }

      // Ensure taxable income is not negative
      taxableIncome = Math.max(0, taxableIncome)

      // Calculate tax based on slabs
      let tax = 0
      const slabs = TAX_SLABS[regime]

      for (let slab of slabs) {
        if (taxableIncome <= slab.from) break

        const incomeInThisSlab = Math.min(taxableIncome, slab.to) - slab.from
        tax += incomeInThisSlab * slab.rate
      }

      // Add cess (4% on tax above certain threshold in new regime)
      const cess = regime === 'new' ? tax * 0.04 : 0
      const totalTax = tax + cess

      setResult({
        grossIncome: parseFloat(income),
        standardDeduction,
        otherDeductions: ded,
        taxableIncome: Math.max(0, parseFloat(income) - standardDeduction - ded),
        taxBeforeCess: parseFloat(tax.toFixed(2)),
        cess: parseFloat(cess.toFixed(2)),
        totalTax: parseFloat(totalTax.toFixed(2)),
        netIncome: parseFloat((parseFloat(income) - totalTax).toFixed(2)),
      })
    } catch (err) {
      setError('Calculation error. Please check your inputs.')
    } finally {
      setLoading(false)
    }
  }

  function handleClear() {
    setIncome('')
    setRegime('new')
    setDeductions('')
    setResult(null)
    setError('')
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
              onChange={(e) => {
                setIncome(e.target.value)
                setResult(null)
                setError('')
              }}
              className={styles.input}
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="regime" className={styles.label}>Tax Regime</label>
            <select
              id="regime"
              value={regime}
              onChange={(e) => {
                setRegime(e.target.value)
                setResult(null)
              }}
              className={styles.select}
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
                onChange={(e) => {
                  setDeductions(e.target.value)
                  setResult(null)
                  setError('')
                }}
                className={styles.input}
              />
            </div>
          )}
        </div>

        {error && <div className={styles.errorRow}>{error}</div>}

        {result && (
          <div className={styles.resultsGrid}>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Gross Income</div>
              <div className={styles.resultValue}>₹ {result.grossIncome.toLocaleString('en-IN')}</div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Standard Deduction</div>
              <div className={styles.resultValue}>₹ {result.standardDeduction.toLocaleString('en-IN')}</div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Taxable Income</div>
              <div className={styles.resultValue}>₹ {result.taxableIncome.toLocaleString('en-IN')}</div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Tax</div>
              <div className={styles.resultValue}>₹ {result.totalTax.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</div>
            </div>
            <div className={styles.resultCard}>
              <div className={styles.resultLabel}>Net Income</div>
              <div className={styles.resultValue}>₹ {result.netIncome.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</div>
            </div>
          </div>
        )}
      </div>

      <div className={styles.controlRow}>
        <button onClick={handleClear} className={styles.btnClear} disabled={loading}>Clear</button>
        <button onClick={calculateTax} className={styles.btnEquals} disabled={loading}>
          {loading ? 'Calculating...' : 'Calculate Tax'}
        </button>
      </div>
    </div>
  )
}
