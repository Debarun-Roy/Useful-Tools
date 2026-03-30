import { useState, useEffect, useCallback } from 'react'
import { fetchFinancialHistory } from '../../../api/apiClient'
import styles from '../HistoryTab/HistoryTab.module.css'
import fcStyles from './FinancialCalc.module.css'

const TYPES = [
  { id: 'emi',    label: 'EMI' },
  { id: 'tax',    label: 'Tax' },
  { id: 'ci',     label: 'Compound Interest' },
  { id: 'salary', label: 'Salary' },
]

const PAGE_SIZE = 10

function formatINR(v) {
  return '₹\u202f' + Number(v).toLocaleString('en-IN', { maximumFractionDigits: 2 })
}

function formatTimestamp(iso) {
  try {
    return new Date(iso).toLocaleString(undefined, {
      dateStyle: 'medium', timeStyle: 'short',
    })
  } catch { return iso }
}

// ── Per-type renderers ────────────────────────────────────────────────────────

function EMIRows({ entries }) {
  return entries.map((e, i) => (
    <tr key={i} className={styles.row}>
      <td className={styles.table && 'td'}>{formatINR(e.principal)}</td>
      <td>{e.annualRate}%</td>
      <td>{e.tenureMonths} mo</td>
      <td><strong>{formatINR(e.emi)}/mo</strong></td>
      <td>{formatINR(e.totalInterest)}</td>
      <td><span className={styles.timestamp}>{formatTimestamp(e.calculatedAt)}</span></td>
    </tr>
  ))
}

function TaxRows({ entries }) {
  return entries.map((e, i) => (
    <tr key={i} className={styles.row}>
      <td>{formatINR(e.grossIncome)}</td>
      <td>{e.regime === 'new' ? 'New' : 'Old'}</td>
      <td>{formatINR(e.taxableIncome)}</td>
      <td><strong>{formatINR(e.totalTax)}</strong></td>
      <td>{formatINR(e.netIncome)}</td>
      <td><span className={styles.timestamp}>{formatTimestamp(e.calculatedAt)}</span></td>
    </tr>
  ))
}

function CIRows({ entries }) {
  return entries.map((e, i) => (
    <tr key={i} className={styles.row}>
      <td>{formatINR(e.principal)}</td>
      <td>{e.annualRate}% · {e.frequency}</td>
      <td>{e.timeYears} yr</td>
      <td><strong>{formatINR(e.finalAmount)}</strong></td>
      <td>{formatINR(e.interestEarned)}</td>
      <td><span className={styles.timestamp}>{formatTimestamp(e.calculatedAt)}</span></td>
    </tr>
  ))
}

function SalaryRows({ entries }) {
  return entries.map((e, i) => (
    <tr key={i} className={styles.row}>
      <td>{formatINR(e.basicSalary)}</td>
      <td>{formatINR(e.grossSalary)}</td>
      <td>{formatINR(e.totalDeductions)}</td>
      <td><strong>{formatINR(e.netSalary)}</strong></td>
      <td><span className={styles.timestamp}>{formatTimestamp(e.calculatedAt)}</span></td>
    </tr>
  ))
}

const HEADERS = {
  emi:    ['Principal', 'Rate', 'Tenure', 'EMI', 'Interest', 'When'],
  tax:    ['Gross Income', 'Regime', 'Taxable', 'Total Tax', 'Net Income', 'When'],
  ci:     ['Principal', 'Rate & Freq', 'Time', 'Final Amount', 'Interest', 'When'],
  salary: ['Basic', 'Gross', 'Deductions', 'Net Salary', 'When'],
}

// ── Main component ────────────────────────────────────────────────────────────

export default function FinancialHistory() {
  const [activeType, setActiveType] = useState('emi')
  const [entries,    setEntries]    = useState([])
  const [total,      setTotal]      = useState(0)
  const [page,       setPage]       = useState(0)
  const [loading,    setLoading]    = useState(false)
  const [error,      setError]      = useState('')

  const totalPages = Math.ceil(total / PAGE_SIZE)

  const load = useCallback(async (type, p) => {
    setLoading(true)
    setError('')
    try {
      const { data } = await fetchFinancialHistory(type, p, PAGE_SIZE)
      if (data.success) {
        setEntries(data.data.entries)
        setTotal(data.data.total)
        setPage(p)
      } else {
        setError(data.error || 'Failed to load history.')
      }
    } catch {
      setError('Could not reach the server.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load(activeType, 0)
  }, [activeType, load])

  function switchType(type) {
    setActiveType(type)
    setPage(0)
    setEntries([])
    setTotal(0)
  }

  function renderRows() {
    if (entries.length === 0) return null
    switch (activeType) {
      case 'emi':    return <EMIRows    entries={entries} />
      case 'tax':    return <TaxRows    entries={entries} />
      case 'ci':     return <CIRows     entries={entries} />
      case 'salary': return <SalaryRows entries={entries} />
      default:       return null
    }
  }

  return (
    <div className={styles.historyTab}>

      {/* Sub-type selector */}
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
        {TYPES.map(t => (
          <button
            key={t.id}
            className={activeType === t.id
              ? fcStyles.modeActive
              : fcStyles.modeBtn}
            onClick={() => switchType(t.id)}
            disabled={loading}
          >
            {t.label}
          </button>
        ))}
      </div>

      {error && (
        <div className={styles.errorBanner} role="alert">{error}</div>
      )}

      {!loading && entries.length === 0 && !error && (
        <div className={styles.empty}>
          No {TYPES.find(t => t.id === activeType)?.label} calculations recorded yet.
        </div>
      )}

      {entries.length > 0 && (
        <>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  {HEADERS[activeType].map(h => (
                    <th key={h}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {renderRows()}
              </tbody>
            </table>
          </div>

          <div className={styles.pagination}>
            <button
              className={styles.pageBtn}
              onClick={() => load(activeType, page - 1)}
              disabled={page === 0 || loading}
            >
              ← Previous
            </button>
            <span className={styles.pageInfo}>
              Page {page + 1} of {totalPages || 1}
              <span className={styles.totalCount}> · {total} total</span>
            </span>
            <button
              className={styles.pageBtn}
              onClick={() => load(activeType, page + 1)}
              disabled={page >= totalPages - 1 || loading}
            >
              Next →
            </button>
          </div>
        </>
      )}
    </div>
  )
}