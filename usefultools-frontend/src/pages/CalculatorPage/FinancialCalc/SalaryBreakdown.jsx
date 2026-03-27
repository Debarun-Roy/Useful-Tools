import { useState } from 'react'
import styles from './FinancialCalc.module.css'

/**
 * Salary Breakdown Calculator
 * Calculates net salary after deductions and provides breakdown
 */
export default function SalaryBreakdown() {
  const [basicSalary, setBasicSalary] = useState('')
  const [hra, setHRA] = useState('')
  const [da, setDA] = useState('')
  const [allowances, setAllowances] = useState('')
  const [pfContribution, setPFContribution] = useState('')
  const [professionalTax, setProfessionalTax] = useState('')
  const [otherDeductions, setOtherDeductions] = useState('')
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  function isNum(val) {
    return val === '' || !isNaN(parseFloat(val))
  }

  function validate() {
    if (!basicSalary || isNaN(parseFloat(basicSalary)) || parseFloat(basicSalary) <= 0) {
      setError('Please enter valid basic salary.')
      return false
    }
    if (!isNum(hra) || !isNum(da) || !isNum(allowances) || !isNum(pfContribution) || !isNum(professionalTax) || !isNum(otherDeductions)) {
      setError('Please enter valid numbers for all fields.')
      return false
    }
    return true
  }

  function calculateSalaryBreakdown() {
    if (!validate()) return

    setLoading(true)
    setError('')
    setResult(null)

    try {
      const basic = parseFloat(basicSalary)
      const hraAmount = parseFloat(hra || 0)
      const daAmount = parseFloat(da || 0)
      const allowancesAmount = parseFloat(allowances || 0)
      const pfAmount = parseFloat(pfContribution || 0)
      const ptAmount = parseFloat(professionalTax || 0)
      const otherDed = parseFloat(otherDeductions || 0)

      // Gross Salary
      const grossSalary = basic + hraAmount + daAmount + allowancesAmount

      // Total Deductions
      const totalDeductions = pfAmount + ptAmount + otherDed

      // Net Salary
      const netSalary = grossSalary - totalDeductions

      // Calculate percentages
      const basicPercent = (basic / grossSalary) * 100
      const hraPercent = (hraAmount / grossSalary) * 100
      const daPercent = (daAmount / grossSalary) * 100
      const allowancesPercent = (allowancesAmount / grossSalary) * 100
      const deductionsPercent = (totalDeductions / grossSalary) * 100

      // Annual figures
      const annualGross = grossSalary * 12
      const annualDeductions = totalDeductions * 12
      const annualNet = netSalary * 12

      setResult({
        basicSalary: basic,
        hra: hraAmount,
        da: daAmount,
        allowances: allowancesAmount,
        grossSalary: parseFloat(grossSalary.toFixed(2)),
        pfContribution: pfAmount,
        professionalTax: ptAmount,
        otherDeductions: otherDed,
        totalDeductions: parseFloat(totalDeductions.toFixed(2)),
        netSalary: parseFloat(netSalary.toFixed(2)),
        basicPercent,
        hraPercent,
        daPercent,
        allowancesPercent,
        deductionsPercent,
        annualGross: parseFloat(annualGross.toFixed(2)),
        annualDeductions: parseFloat(annualDeductions.toFixed(2)),
        annualNet: parseFloat(annualNet.toFixed(2)),
      })
    } catch (err) {
      setError('Calculation error. Please check your inputs.')
    } finally {
      setLoading(false)
    }
  }

  function handleClear() {
    setBasicSalary('')
    setHRA('')
    setDA('')
    setAllowances('')
    setPFContribution('')
    setProfessionalTax('')
    setOtherDeductions('')
    setResult(null)
    setError('')
  }

  return (
    <div className={styles.calculator}>
      <div className={styles.displayPanel}>
        <div className={styles.inputGrid}>
          <div className={styles.inputGroup}>
            <label htmlFor="basicSalary" className={styles.label}>Basic Salary (₹)</label>
            <input
              id="basicSalary"
              type="number"
              placeholder="Enter monthly salary"
              value={basicSalary}
              onChange={(e) => {
                setBasicSalary(e.target.value)
                setResult(null)
                setError('')
              }}
              className={styles.input}
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="hra" className={styles.label}>HRA (₹)</label>
            <input
              id="hra"
              type="number"
              placeholder="House rent allowance"
              value={hra}
              onChange={(e) => {
                setHRA(e.target.value)
                setResult(null)
                setError('')
              }}
              className={styles.input}
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="da" className={styles.label}>DA (₹)</label>
            <input
              id="da"
              type="number"
              placeholder="Dearness allowance"
              value={da}
              onChange={(e) => {
                setDA(e.target.value)
                setResult(null)
                setError('')
              }}
              className={styles.input}
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="allowances" className={styles.label}>Other Allowances (₹)</label>
            <input
              id="allowances"
              type="number"
              placeholder="Other earnings"
              value={allowances}
              onChange={(e) => {
                setAllowances(e.target.value)
                setResult(null)
                setError('')
              }}
              className={styles.input}
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="pf" className={styles.label}>PF Contribution (₹)</label>
            <input
              id="pf"
              type="number"
              placeholder="Provident fund"
              value={pfContribution}
              onChange={(e) => {
                setPFContribution(e.target.value)
                setResult(null)
                setError('')
              }}
              className={styles.input}
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="pt" className={styles.label}>Professional Tax (₹)</label>
            <input
              id="pt"
              type="number"
              placeholder="Professional tax"
              value={professionalTax}
              onChange={(e) => {
                setProfessionalTax(e.target.value)
                setResult(null)
                setError('')
              }}
              className={styles.input}
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="other" className={styles.label}>Other Deductions (₹)</label>
            <input
              id="other"
              type="number"
              placeholder="Other deductions"
              value={otherDeductions}
              onChange={(e) => {
                setOtherDeductions(e.target.value)
                setResult(null)
                setError('')
              }}
              className={styles.input}
            />
          </div>
        </div>

        {error && <div className={styles.errorRow}>{error}</div>}

        {result && (
          <div>
            <div className={styles.breakdownSection}>
              <div className={styles.breakdownTitle}>Monthly Breakdown</div>
              <div className={styles.breakdownGrid}>
                <div className={styles.breakdownItem}>
                  <span className={styles.breakdownLabel}>Basic Salary</span>
                  <span className={styles.breakdownValue}>₹ {result.basicSalary.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</span>
                </div>
                {result.hra > 0 && (
                  <div className={styles.breakdownItem}>
                    <span className={styles.breakdownLabel}>HRA</span>
                    <span className={styles.breakdownValue}>₹ {result.hra.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</span>
                  </div>
                )}
                {result.da > 0 && (
                  <div className={styles.breakdownItem}>
                    <span className={styles.breakdownLabel}>DA</span>
                    <span className={styles.breakdownValue}>₹ {result.da.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</span>
                  </div>
                )}
                {result.allowances > 0 && (
                  <div className={styles.breakdownItem}>
                    <span className={styles.breakdownLabel}>Other Allowances</span>
                    <span className={styles.breakdownValue}>₹ {result.allowances.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</span>
                  </div>
                )}
              </div>
            </div>

            <div className={styles.resultsGrid}>
              <div className={styles.resultCard}>
                <div className={styles.resultLabel}>Gross Salary</div>
                <div className={styles.resultValue}>₹ {result.grossSalary.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</div>
              </div>
              <div className={styles.resultCard}>
                <div className={styles.resultLabel}>Total Deductions</div>
                <div className={styles.resultValue}>₹ {result.totalDeductions.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</div>
              </div>
              <div className={styles.resultCard}>
                <div className={styles.resultLabel}>Net Salary</div>
                <div className={styles.resultValue}>₹ {result.netSalary.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</div>
              </div>
              <div className={styles.resultCard}>
                <div className={styles.resultLabel}>Annual Net</div>
                <div className={styles.resultValue}>₹ {result.annualNet.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</div>
              </div>
            </div>
          </div>
        )}
      </div>

      <div className={styles.controlRow}>
        <button onClick={handleClear} className={styles.btnClear} disabled={loading}>Clear</button>
        <button onClick={calculateSalaryBreakdown} className={styles.btnEquals} disabled={loading}>
          {loading ? 'Calculating...' : 'Calculate Breakdown'}
        </button>
      </div>
    </div>
  )
}
