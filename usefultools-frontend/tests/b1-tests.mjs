import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { revisionGuard } from '../src/pages/BackendSupportPage/revision.js'

test('editing invalidates in-flight findings even without abort', () => {
  const guard = revisionGuard(); const old = guard.start(); guard.edit()
  assert.equal(guard.current(old), false)
  assert.equal(guard.current(guard.start()), true)
})
test('out-of-order requests cannot replace a newer result', async () => {
  const guard = revisionGuard(); let firstResolve; let displayed
  const first = guard.start()
  const delayed = new Promise(resolve => { firstResolve = resolve }).then(value => { if (guard.current(first)) displayed = value })
  const second = guard.start(); if (guard.current(second)) displayed = 'newer'
  firstResolve('older'); await delayed
  assert.equal(displayed, 'newer')
})
test('API helper composes one /api prefix, sends CSRF and forwards AbortSignal', async () => {
  const source = (await readFile(new URL('../src/api/apiClient.js', import.meta.url), 'utf8'))
    .replace("'./apiBase'", JSON.stringify(new URL('../src/api/apiBase.js', import.meta.url).href))
    .replace('import.meta.env.VITE_API_BASE', "'/api'")
  const api = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
  const original = globalThis.fetch; const calls = []
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return { status: 422, text: async () => JSON.stringify({ success: false, errorCode: 'INVALID_SPECIFICATION', data: { result: { valid: false } } }) } }
  try {
    api.setCsrfToken('synthetic-unit-token')
    const controller = new AbortController()
    const response = await api.validateBackendSpec({ sampleId: 'customers-orders' }, controller.signal)
    assert.equal(calls[0].url, '/api/backend-support/validate')
    assert.equal(calls[0].options.signal, controller.signal)
    assert.equal(calls[0].options.credentials, 'include')
    assert.equal(calls[0].options.headers['X-XSRF-TOKEN'], 'synthetic-unit-token')
    assert.equal(response.status, 422); assert.equal(response.data.data.result.valid, false)
    await api.fetchBackendCatalog(controller.signal)
    assert.equal(calls[1].url, '/api/backend-support/catalog')
  } finally { globalThis.fetch = original }
})
