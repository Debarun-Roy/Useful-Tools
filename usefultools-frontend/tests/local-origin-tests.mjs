import assert from 'node:assert/strict'
import test from 'node:test'
import { localOriginPlugin } from '../dev/localOrigin.mjs'

function request(method, host, url) {
  let middleware, passed = false, ended = false
  const response = { headers: {}, setHeader(k, v) { this.headers[k] = v }, end() { ended = true } }
  const plugin = localOriginPlugin()
  assert.equal(plugin.apply, 'serve')
  assert.equal(plugin.configurePreviewServer, undefined)
  plugin.configureServer({ middlewares: { use(fn) { middleware = fn } } })
  middleware({ method, headers: { host }, url }, response, () => { passed = true })
  return { response, passed, ended }
}

for (const path of ['/', '/login', '/backend-support?tab=rest', '//evil.invalid/login']) {
  test(`IP page redirects to a fixed localhost authority: ${path}`, () => {
    const { response, passed, ended } = request('GET', '127.0.0.1:5173', path)
    assert.equal(response.statusCode, 302)
    assert.equal(response.headers.Location, 'http://localhost:5173' + path)
    assert.equal(new URL(response.headers.Location).origin, 'http://localhost:5173')
    assert.equal(response.headers['Cache-Control'], 'no-store')
    assert(!passed && ended)
  })
}
test('HEAD navigation redirects without a response body', () => {
  assert.equal(request('HEAD', '127.0.0.1:5173', '/login').response.statusCode, 302)
})
for (const [method, host, path] of [
  ['GET', 'localhost:5173', '/login'],
  ['GET', 'evil.invalid:5173', '/login'],
  ['GET', '127.0.0.1:5174', '/login'],
  ['POST', '127.0.0.1:5173', '/api/auth/login-guest'],
  ['POST', '127.0.0.1:5173', '/login'],
  ['GET', '127.0.0.1:5173', '/api/auth/csrf-token'],
  ['GET', '127.0.0.1:5173', '/api?x=1'],
  ['OPTIONS', '127.0.0.1:5173', '/api/auth/login-guest'],
]) {
  test(`Does not redirect ${method} ${host}${path}`, () => {
    const { passed, ended, response } = request(method, host, path)
    assert(passed && !ended)
    assert.equal(response.headers.Location, undefined)
  })
}
