import test from 'node:test'
import assert from 'node:assert/strict'
import { identifyHash } from '../src/utils/hashIdentifier.js'
import { generateBase64Url } from '../src/utils/keyGenerator.js'

const jwt = 'eyJhbGciOiJIUzI1NiJ9.e30.synthetic'
test('browser base64 APIs work without a Buffer shim', () => {
  const saved = globalThis.Buffer
  try {
    globalThis.Buffer = undefined
    assert.ok(identifyHash(jwt).candidates.some(c => c.name === 'JWT'))
    const key = generateBase64Url(32)
    assert.match(key, /^[A-Za-z0-9_-]{43}$/)
    assert.equal(atob(key.replace(/-/g, '+').replace(/_/g, '/') + '=').length, 32)
  } finally { globalThis.Buffer = saved }
})
test('Node fallback works without browser base64 APIs', () => {
  const saved = { atob: globalThis.atob, btoa: globalThis.btoa }
  try {
    globalThis.atob = undefined
    globalThis.btoa = undefined
    assert.ok(identifyHash(jwt).candidates.some(c => c.name === 'JWT'))
    const key = generateBase64Url(32)
    assert.equal(Buffer.from(key, 'base64url').length, 32)
    assert.match(key, /^[A-Za-z0-9_-]{43}$/)
  } finally { Object.assign(globalThis, saved) }
})
