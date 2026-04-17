import { useState } from 'react'
import PageTabs from '../../../components/PageTabs/PageTabs'
import EMI from './EMI'
import TaxCalculator from './TaxCalculator'
import CompoundInterest from './CompoundInterest'
import SalaryBreakdown from './SalaryBreakdown'
import styles from './FinancialCalc.module.css'
import FinancialHistory from './FinancialHistory'

const TABS = [
  { id: 'emi',        label: 'EMI',                icon: '📊' },
  { id: 'tax',        label: 'Tax',                icon: '💰' },
  { id: 'compound',   label: 'Compound Interest',  icon: '📈' },
  { id: 'salary',     label: 'Salary Breakdown',   icon: '💵' },
]

export default function FinancialCalc({ isGuest }) {
  const [activeTab, setActiveTab] = useState('emi')

  function renderCalculator() {
    switch (activeTab) {
      case 'emi':      return <EMI isGuest={isGuest} />
      case 'tax':      return <TaxCalculator isGuest={isGuest} />
      case 'compound': return <CompoundInterest isGuest={isGuest} />
      case 'salary':   return <SalaryBreakdown isGuest={isGuest} />
      case 'history':  return <FinancialHistory />
      default:         return <EMI isGuest={isGuest} />
    }
  }

  return (
    <div className={styles.financialCalcContainer}>
      {!isGuest && (
        <div className={styles.note}>
          Results from this calculator are automatically saved to calculation history.
        </div>
      )}
      <PageTabs
        tabs={TABS}
        activeTab={activeTab}
        onChange={setActiveTab}
        ariaLabel="Financial Calculator Tabs"
      />
      <div className={styles.calculatorContent}>
        {renderCalculator()}
      </div>
    </div>
  )
}