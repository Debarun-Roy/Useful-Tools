import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { revisionGuard } from '../src/pages/BackendSupportPage/revision.js'

test('late export completion cannot trigger a download after an edit or newer request', async () => {
  const guard = revisionGuard(); let finish; let downloads = 0
  const old = guard.start()
  const pending = new Promise(resolve => { finish = resolve }).then(() => { if (guard.current(old)) downloads++ })
  guard.edit(); guard.start(); finish(); await pending
  assert.equal(downloads, 0)
})
test('binary API success, structured JSON error, non-JSON failure, raw duplicate keys and CSRF', async () => {
  const source = (await readFile(new URL('../src/api/apiClient.js', import.meta.url), 'utf8'))
    .replace("'./apiBase'", JSON.stringify(new URL('../src/api/apiBase.js', import.meta.url).href))
    .replace('import.meta.env.VITE_API_BASE', "'/api'")
  const api = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
  const original = globalThis.fetch; const calls = []; const blob = new Blob(['zip'])
  let response = { ok: true, status: 200, headers: new Headers({ 'Content-Type': 'application/zip' }), blob: async () => blob }
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return response }
  try {
    api.setCsrfToken('synthetic'); const signal = new AbortController().signal
    assert.equal((await api.exportBackendSchema('{"x":1,"x":2}', 'a'.repeat(64), signal)).blob, blob)
    assert.equal(calls[0].url, '/api/backend-support/export')
    assert.equal(calls[0].options.signal, signal); assert.equal(calls[0].options.credentials, 'include')
    assert.equal(calls[0].options.headers['X-XSRF-TOKEN'], 'synthetic')
    assert.ok(calls[0].options.body.includes('"x":1,"x":2'))
    response = { ok: false, status: 409, text: async () => '{"success":false,"errorCode":"PREVIEW_DIGEST_MISMATCH"}' }
    assert.equal((await api.exportBackendSchema('{}', 'b'.repeat(64))).data.errorCode, 'PREVIEW_DIGEST_MISMATCH')
    response = { ok: false, status: 502, text: async () => '<h1>Unavailable</h1>' }
    assert.equal((await api.exportBackendSchema('{}', 'b'.repeat(64))).data.success, false)
    await api.generateBackendSchema('{}', signal); assert.equal(calls.at(-1).url, '/api/backend-support/generate')
  } finally { globalThis.fetch = original }
})
