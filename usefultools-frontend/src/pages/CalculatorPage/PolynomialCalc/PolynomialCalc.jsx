import { useState } from 'react'
import { calculatePolynomial } from '../../../api/apiClient'

export default function PolynomialCalc() {
  const [operation, setOperation] = useState('evaluate')
  const [coefficients, setCoefficients] = useState('')
  const [x, setX] = useState(0)
  const [result, setResult] = useState('')
  const [error, setError] = useState('')

  async function handleCalculate() {
    try {
      const coeffs = coefficients.split(',').map(c => parseFloat(c.trim())).filter(c => !isNaN(c))
      if (coeffs.length === 0) throw new Error('Invalid coefficients')

      const res = await calculatePolynomial(operation, coeffs, x)
      setResult(res.result)
      setError('')
    } catch (err) {
      setResult('')
      setError(err.message || 'Error calculating polynomial')
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div>
        <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600' }}>
          Operation:
        </label>
        <select
          value={operation}
          onChange={e => setOperation(e.target.value)}
          style={{
            width: '100%',
            padding: '12px',
            border: '1px solid var(--clr-border)',
            borderRadius: '8px',
            fontSize: '16px',
          }}
        >
          <option value="evaluate">Evaluate</option>
          <option value="derivative">Derivative</option>
          <option value="integral">Integral</option>
          <option value="roots">Find Roots</option>
        </select>
      </div>

      <div>
        <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600' }}>
          Coefficients (comma-separated, highest degree first):
        </label>
        <input
          type="text"
          value={coefficients}
          onChange={e => setCoefficients(e.target.value)}
          placeholder="e.g., 1, -3, 2 (for x² - 3x + 2)"
          style={{
            width: '100%',
            padding: '12px',
            border: '1px solid var(--clr-border)',
            borderRadius: '8px',
            fontSize: '16px',
          }}
        />
      </div>

      {(operation === 'evaluate' || operation === 'derivative') && (
        <div>
          <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600' }}>
            x value:
          </label>
          <input
            type="number"
            value={x}
            onChange={e => setX(parseFloat(e.target.value) || 0)}
            style={{
              width: '100%',
              padding: '12px',
              border: '1px solid var(--clr-border)',
              borderRadius: '8px',
              fontSize: '16px',
            }}
          />
        </div>
      )}

      <button
        onClick={handleCalculate}
        style={{
          padding: '12px 24px',
          background: 'var(--clr-primary)',
          color: '#fff',
          border: 'none',
          borderRadius: '8px',
          fontSize: '16px',
          fontWeight: '600',
          cursor: 'pointer',
        }}
      >
        Calculate
      </button>

      {result && (
        <div>
          <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600' }}>
            Result:
          </label>
          <div style={{
            padding: '12px',
            background: 'var(--clr-input-bg)',
            border: '1px solid var(--clr-border)',
            borderRadius: '8px',
            fontFamily: 'var(--font-mono)',
            fontSize: '16px',
          }}>
            {result}
          </div>
        </div>
      )}

      {error && (
        <div style={{
          padding: '12px',
          background: 'var(--clr-error-bg)',
          border: '1px solid var(--clr-error-border)',
          borderRadius: '8px',
          color: 'var(--clr-error)',
        }}>
          {error}
        </div>
      )}
    </div>
  )
}