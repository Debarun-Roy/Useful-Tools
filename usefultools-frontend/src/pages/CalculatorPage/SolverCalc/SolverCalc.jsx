import { useState } from 'react'
import { solveEquation } from '../../../api/apiClient'

export default function SolverCalc() {
  const [equation, setEquation] = useState('')
  const [result, setResult] = useState('')
  const [error, setError] = useState('')

  async function handleSolve() {
    if (!equation.trim()) return
    try {
      const res = await solveEquation(equation)
      if (res.status !== 200 || !res.data?.success) {
        throw new Error(res.data?.error || 'Error solving equation')
      }
      const solverResult = res.data.data?.result
      if (solverResult === undefined || solverResult === null) {
        throw new Error('No solution returned from the server.')
      }
      setResult(Array.isArray(solverResult) ? solverResult.join(', ') : String(solverResult))
      setError('')
    } catch (err) {
      setResult('')
      setError(err.message || 'Error solving equation')
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div>
        <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600' }}>
          Equation (set to 0):
        </label>
        <input
          type="text"
          value={equation}
          onChange={e => setEquation(e.target.value)}
          placeholder="e.g. x^2 - 4"
          style={{
            width: '100%',
            padding: '12px',
            border: '1px solid var(--clr-border)',
            borderRadius: '8px',
            fontSize: '16px',
            fontFamily: 'var(--font-mono)',
          }}
        />
      </div>

      <button
        onClick={handleSolve}
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
        Solve
      </button>

      {result !== '' && result !== null && (
        <div>
          <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600' }}>
            Solution:
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