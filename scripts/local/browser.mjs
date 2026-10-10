import { fileURLToPath } from 'node:url'
import { chromium } from '../../usefultools-frontend/node_modules/@playwright/test/index.mjs'
import assert from 'node:assert/strict'
import { writeFile, mkdir } from 'node:fs/promises'

const output = new URL('../../.local/verification/browser-' + new Date().toISOString().replaceAll(':', '-') + '/', import.meta.url)
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const checks = [], external = [], apiPaths = [], pageErrors = []
try {
  const context = await browser.newContext()
  const page = await context.newPage()
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('request', request => {
    const url = new URL(request.url())
    if (['http:', 'https:'].includes(url.protocol) && url.hostname !== 'localhost') external.push(url.origin)
    if (url.pathname.startsWith('/api')) apiPaths.push(url.pathname)
  })
  // Block unexpected external traffic before it leaves the browser, and fail if attempted.
  await context.route('**/*', route => {
    const url = new URL(route.request().url())
    return ['http:', 'https:'].includes(url.protocol) && url.hostname !== 'localhost' ? route.abort() : route.continue()
  })
  for (const path of ['/login', '/register', '/forgot-password']) {
    await page.goto('http://localhost:5173' + path)
    await page.getByText('Local account login, registration and captcha recovery are unavailable', { exact: false }).waitFor()
    await page.getByLabel('Username', { exact: true }).fill('local-synthetic')
    if (path !== '/forgot-password') await page.getByLabel('Password', { exact: true }).fill('Synthetic!Password794')
    await page.locator('form button[type=submit]').click()
    await page.getByRole('alert').filter({ hasText: 'Captcha is unavailable' }).waitFor()
  }
  assert.deepEqual(pageErrors, [])
  checks.push('login/register/recovery render explicit missing-key state without provider crashes')
  await page.goto('http://localhost:5173/login')
  await page.getByRole('button', { name: 'Continue as Guest' }).click()
  await page.waitForURL('**/dashboard')
  await page.goto('http://localhost:5173/backend-support')
  await page.getByText('Guest access: immutable built-in samples only.').waitFor()
  await page.getByRole('button', { name: 'Load and validate sample' }).click()
  await page.getByRole('status', { name: 'Validation status' }).filter({ hasText: 'Specification valid' }).waitFor()
  checks.push('browser guest login -> Vite proxy -> local catalog -> sample validation -> rendered findings')
  const cookies = await context.cookies()
  const session = cookies.find(c => c.name === 'JSESSIONID')
  const csrfCookie = cookies.find(c => c.name === 'XSRF-TOKEN')
  assert(session && session.httpOnly && !session.secure && session.sameSite === 'Lax' && session.path === '/')
  assert(csrfCookie && !csrfCookie.httpOnly && !csrfCookie.secure && csrfCookie.sameSite === 'Lax')
  assert(cookies.every(c => c.domain === 'localhost'))
  checks.push('local-only HttpOnly session; Lax/non-Secure cookies; localhost host; root path')
  const result = await page.evaluate(async () => {
    const catalog = await (await fetch('/api/backend-support/catalog')).json()
    const token = decodeURIComponent(document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN=')).split('=').slice(1).join('='))
    const sampleId = catalog.data.modules[0].sampleIds[0]
    const call = async (path, body, csrf = true) => {
      const response = await fetch('/api/' + path, { method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(csrf ? { 'X-XSRF-TOKEN': token } : {}) }, body: JSON.stringify(body) })
      return { status: response.status, code: (await response.json()).code }
    }
    const missing = await call('backend-support/validate', { sampleId }, false)
    const custom = await call('backend-support/validate', { request: {} })
    const generate = await call('backend-support/generate', { request: {} })
    const unknown = await call('backend-support/validate', { sampleId: 'does-not-exist' })
    const units = await (await fetch('/api/units/list')).json()
    const logout = await call('auth/logout', {})
    const expired = await fetch('/api/backend-support/catalog')
    return { missing, custom, generate, unknown, units: units.data.length, logout: logout.status, expired: expired.status }
  })
  assert.equal(result.missing.status, 403)
  assert.equal(result.custom.status, 403)
  assert.equal(result.generate.status, 403)
  assert.equal(result.unknown.status, 400)
  assert(result.units > 100)
  assert.equal(result.logout, 200)
  assert.equal(result.expired, 401)
  checks.push('missing CSRF, guest custom/generation denial, failed request, SQLite reference data, logout/replay denial')
  // Expired-session response must also be surfaced by the React API client.
  await page.getByRole('button', { name: 'Refresh access' }).click()
  await page.waitForURL('**/login')
  checks.push('invalidated session returns the browser to login')
  assert.deepEqual(pageErrors, [])
  assert.deepEqual(external, [])
  assert(apiPaths.length > 5 && apiPaths.every(p => !p.startsWith('/api/api/')))
  checks.push('all observed browser HTTP requests stay on localhost; exactly one /api prefix')
  await page.screenshot({ path: fileURLToPath(new URL('logout.png', output)) })
  await writeFile(new URL('browser-results.json', output), JSON.stringify({ status: 'PASS', checks,
    apiPaths: [...new Set(apiPaths)], externalOrigins: external, realRecaptcha: 'BLOCKED: independent keys unavailable' }, null, 2))
  console.log(JSON.stringify({ status: 'PASS', checks }))
} finally {
  await browser.close()
}
