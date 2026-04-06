import { useState } from 'react'
import { calculateProbability } from '../../../api/apiClient'

export default function ProbabilityCalc() {
  const [distribution, setDistribution] = useState('normal')
  const [params, setParams] = useState({
    // Normal distribution
    mean: 0, std: 1, x: 0,
    // Binomial distribution
    n: 10, p: 0.5, k: 5,
    // Poisson distribution
    lambda: 1, k_poisson: 0
  })
  const [result, setResult] = useState('')
  const [error, setError] = useState('')

  function updateParam(key, value) {
    setParams(p => ({ ...p, [key]: parseFloat(value) || 0 }))
  }

  function handleDistributionChange(newDistribution) {
    setDistribution(newDistribution)
    setResult('')
    setError('')
  }

  async function handleCalculate() {
    try {
      // Filter parameters based on distribution
      let filteredParams = {}
      switch (distribution) {
        case 'normal':
          filteredParams = { mean: params.mean, std: params.std, x: params.x }
          break
        case 'binomial':
          filteredParams = { n: params.n, p: params.p, k: params.k }
          break
        case 'poisson':
          filteredParams = { lambda: params.lambda, k: params.k_poisson }
          break
      }

      const res = await calculateProbability(distribution, filteredParams)
      if (res.status !== 200 || !res.data?.success) {
        throw new Error(res.data?.error || 'Error calculating probability')
      }
      const probabilityResult = res.data.data?.result
      if (probabilityResult === undefined || probabilityResult === null) {
        throw new Error('No probability returned from the server.')
      }
      setResult(String(probabilityResult))
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
          onChange={e => handleDistributionChange(e.target.value)}
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
              step="any"
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
              step="any"
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
              step="any"
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
              step="any"
              value={params.n}
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
              step="any"
              value={params.p}
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
              step="any"
              value={params.k}
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
              step="any"
              value={params.lambda}
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
              step="any"
              value={params.k_poisson}
              onChange={e => updateParam('k_poisson', e.target.value)}
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

      {result !== '' && result !== null && (
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