import { useState } from 'react'
import { calculateSalaryBreakdown } from '../../../api/apiClient'
import styles from './FinancialCalc.module.css'

export default function SalaryBreakdown() {
  const [basicSalary,    setBasicSalary]    = useState('')
  const [hra,            setHRA]            = useState('')
  const [da,             setDA]             = useState('')
  const [allowances,     setAllowances]     = useState('')
  const [pfContribution, setPFContribution] = useState('')
  const [profTax,        setProfTax]        = useState('')
  const [otherDed,       setOtherDed]       = useState('')
  const [result,         setResult]         = useState(null)
  const [error,          setError]          = useState('')
  const [loading,        setLoading]        = useState(false)

  function isOptNum(val) {
    return val === '' || (!isNaN(parseFloat(val)) && parseFloat(val) >= 0)
  }

  function validate() {
    if (!basicSalary.trim() || isNaN(parseFloat(basicSalary)) || parseFloat(basicSalary) <= 0) {
      setError('Please enter a valid basic salary.')
      return false
    }
    if (!isOptNum(hra) || !isOptNum(da) || !isOptNum(allowances) ||
        !isOptNum(pfContribution) || !isOptNum(profTax) || !isOptNum(otherDed)) {
      setError('All optional fields must be valid non-negative numbers.')
      return false
    }
    return true
  }

  function num(val) { return val ? parseFloat(val) : 0 }

  async function handleCalculate() {
    if (!validate()) return
    setLoading(true); setError(''); setResult(null)

    try {
      const { data } = await calculateSalaryBreakdown(
        parseFloat(basicSalary),
        num(hra), num(da), num(allowances),
        num(pfContribution), num(profTax), num(otherDed)
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
    setBasicSalary(''); setHRA(''); setDA(''); setAllowances('')
    setPFContribution(''); setProfTax(''); setOtherDed('')
    setResult(null); setError('')
  }

  const inr = (v) => v.toLocaleString('en-IN', { maximumFractionDigits: 2 })

  return (
    <div className={styles.calculator}>
      <div className={styles.displayPanel}>
        <div className={styles.inputGrid}>
          {[
            ['basicSalary', 'Basic Salary (₹)',      basicSalary,    setBasicSalary],
            ['hra',         'HRA (₹)',               hra,            setHRA],
            ['da',          'DA (₹)',                da,             setDA],
            ['allowances',  'Other Allowances (₹)',  allowances,     setAllowances],
            ['pf',          'PF Contribution (₹)',   pfContribution, setPFContribution],
            ['pt',          'Professional Tax (₹)',  profTax,        setProfTax],
            ['other',       'Other Deductions (₹)',  otherDed,       setOtherDed],
          ].map(([id, label, val, setter]) => (
            <div key={id} className={styles.inputGroup}>
              <label htmlFor={id} className={styles.label}>{label}</label>
              <input
                id={id}
                type="number"
                placeholder="0"
                value={val}
                onChange={e => { setter(e.target.value); setResult(null); setError('') }}
                className={styles.input}
                disabled={loading}
              />
            </div>
          ))}
        </div>

        {error && <div className={styles.errorRow} role="alert">{error}</div>}

        {result && (
          <>
            {/* Earnings breakdown */}
            {(result.hra > 0 || result.da > 0 || result.allowances > 0) && (
              <div className={styles.breakdownSection}>
                <div className={styles.breakdownTitle}>Earnings breakdown</div>
                <div className={styles.breakdownGrid}>
                  <div className={styles.breakdownItem}>
                    <span className={styles.breakdownLabel}>Basic Salary</span>
                    <span className={styles.breakdownValue}>₹ {inr(result.basicSalary)}</span>
                  </div>
                  {result.hra > 0 && (
                    <div className={styles.breakdownItem}>
                      <span className={styles.breakdownLabel}>HRA</span>
                      <span className={styles.breakdownValue}>₹ {inr(result.hra)}</span>
                    </div>
                  )}
                  {result.da > 0 && (
                    <div className={styles.breakdownItem}>
                      <span className={styles.breakdownLabel}>DA</span>
                      <span className={styles.breakdownValue}>₹ {inr(result.da)}</span>
                    </div>
                  )}
                  {result.allowances > 0 && (
                    <div className={styles.breakdownItem}>
                      <span className={styles.breakdownLabel}>Allowances</span>
                      <span className={styles.breakdownValue}>₹ {inr(result.allowances)}</span>
                    </div>
                  )}
                </div>
              </div>
            )}

            <div className={styles.resultsGrid}>
              <div className={styles.resultCard}>
                <div className={styles.resultLabel}>Gross Salary</div>
                <div className={styles.resultValue}>₹ {inr(result.grossSalary)}</div>
              </div>
              <div className={styles.resultCard}>
                <div className={styles.resultLabel}>Total Deductions</div>
                <div className={styles.resultValue}>₹ {inr(result.totalDeductions)}</div>
              </div>
              <div className={styles.resultCard}>
                <div className={styles.resultLabel}>Net Salary</div>
                <div className={styles.resultValue}>₹ {inr(result.netSalary)}</div>
              </div>
              <div className={styles.resultCard}>
                <div className={styles.resultLabel}>Annual Net</div>
                <div className={styles.resultValue}>₹ {inr(result.annualNet)}</div>
              </div>
            </div>
          </>
        )}
      </div>

      <div className={styles.controlRow}>
        <button onClick={handleClear}     className={styles.btnClear}  disabled={loading}>Clear</button>
        <button onClick={handleCalculate} className={styles.btnEquals} disabled={loading}>
          {loading ? 'Calculating…' : 'Calculate Breakdown'}
        </button>
      </div>
    </div>
  )
}