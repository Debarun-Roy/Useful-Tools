import { useState } from 'react'
import { calculateProbability } from '../../../api/apiClient'

export default function ProbabilityCalc() {
  const [distribution, setDistribution] = useState('normal')
  const [params, setParams] = useState({ mean: 0, std: 1, x: 0 })
  const [result, setResult] = useState('')
  const [error, setError] = useState('')

  function updateParam(key, value) {
    setParams(p => ({ ...p, [key]: parseFloat(value) || 0 }))
  }

  async function handleCalculate() {
    try {
      const res = await calculateProbability(distribution, params)
      setResult(res.result)
      setError('')
    } catch (err) {
      setResult('')
      setError(err.message || 'Error calculating probability')
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div>
        <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600' }}>
          Distribution:
        </label>
        <select
          value={distribution}
          onChange={e => setDistribution(e.target.value)}
          style={{
            width: '100%',
            padding: '12px',
            border: '1px solid var(--clr-border)',
            borderRadius: '8px',
            fontSize: '16px',
          }}
        >
          <option value="normal">Normal</option>
          <option value="binomial">Binomial</option>
          <option value="poisson">Poisson</option>
        </select>
      </div>

      {distribution === 'normal' && (
        <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: '120px' }}>
            <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600' }}>
              Mean (μ):
            </label>
            <input
              type="number"
              value={params.mean}
              onChange={e => updateParam('mean', e.target.value)}
              style={{
                width: '100%',
                padding: '12px',
                border: '1px solid var(--clr-border)',
                borderRadius: '8px',
                fontSize: '16px',
              }}
            />
          </div>
          <div style={{ flex: 1, minWidth: '120px' }}>
            <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600' }}>
              Std Dev (σ):
            </label>
            <input
              type="number"
              value={params.std}
              onChange={e => updateParam('std', e.target.value)}
              style={{
                width: '100%',
                padding: '12px',
                border: '1px solid var(--clr-border)',
                borderRadius: '8px',
                fontSize: '16px',
              }}
            />
          </div>
          <div style={{ flex: 1, minWidth: '120px' }}>
            <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600' }}>
              x:
            </label>
            <input
              type="number"
              value={params.x}
              onChange={e => updateParam('x', e.target.value)}
              style={{
                width: '100%',
                padding: '12px',
                border: '1px solid var(--clr-border)',
                borderRadius: '8px',
                fontSize: '16px',
              }}
            />
          </div>
        </div>
      )}

      {distribution === 'binomial' && (
        <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: '120px' }}>
            <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600' }}>
              n (trials):
            </label>
            <input
              type="number"
              value={params.n || 10}
              onChange={e => updateParam('n', e.target.value)}
              style={{
                width: '100%',
                padding: '12px',
                border: '1px solid var(--clr-border)',
                borderRadius: '8px',
                fontSize: '16px',
              }}
            />
          </div>
          <div style={{ flex: 1, minWidth: '120px' }}>
            <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600' }}>
              p (probability):
            </label>
            <input
              type="number"
              step="0.01"
              value={params.p || 0.5}
              onChange={e => updateParam('p', e.target.value)}
              style={{
                width: '100%',
                padding: '12px',
                border: '1px solid var(--clr-border)',
                borderRadius: '8px',
                fontSize: '16px',
              }}
            />
          </div>
          <div style={{ flex: 1, minWidth: '120px' }}>
            <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600' }}>
              k (successes):
            </label>
            <input
              type="number"
              value={params.k || 5}
              onChange={e => updateParam('k', e.target.value)}
              style={{
                width: '100%',
                padding: '12px',
                border: '1px solid var(--clr-border)',
                borderRadius: '8px',
                fontSize: '16px',
              }}
            />
          </div>
        </div>
      )}

      {distribution === 'poisson' && (
        <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: '120px' }}>
            <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600' }}>
              λ (rate):
            </label>
            <input
              type="number"
              value={params.lambda || 1}
              onChange={e => updateParam('lambda', e.target.value)}
              style={{
                width: '100%',
                padding: '12px',
                border: '1px solid var(--clr-border)',
                borderRadius: '8px',
                fontSize: '16px',
              }}
            />
          </div>
          <div style={{ flex: 1, minWidth: '120px' }}>
            <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600' }}>
              k (events):
            </label>
            <input
              type="number"
              value={params.k || 0}
              onChange={e => updateParam('k', e.target.value)}
              style={{
                width: '100%',
                padding: '12px',
                border: '1px solid var(--clr-border)',
                borderRadius: '8px',
                fontSize: '16px',
              }}
            />
          </div>
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
        Calculate P(X)
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