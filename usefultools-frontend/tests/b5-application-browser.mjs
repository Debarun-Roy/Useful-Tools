import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from '@playwright/test'
const run = process.env.B5_APP_RUN, ready = JSON.parse(await readFile(path.join(run, 'ready.json'), 'utf8'))
const spec = JSON.parse(await readFile(process.env.B5_APP_SPEC, 'utf8'))
const browser = await chromium.launch({ headless: true, ...(process.env.B0_BROWSER_CHANNEL ? { channel: process.env.B0_BROWSER_CHANNEL } : {}) })
const context = await browser.newContext({ ignoreHTTPSErrors: true }), page = await context.newPage(), errors = []
page.on('pageerror', error => errors.push(error.message))
try {
  await page.route('**/*', route => new URL(route.request().url()).origin === ready.origin ? route.continue() : route.abort())
  await page.goto(ready.origin + '/client/', { waitUntil: 'networkidle' })
  const result = await page.evaluate(async ({ captcha }) => {
    let token
    async function request(url, method = 'GET', body) {
      const response = await fetch(url, { method, credentials: 'same-origin', headers: method === 'GET' ? {} : { 'Content-Type': 'application/json', 'X-CSRF-Token': token }, ...(body ? { body: JSON.stringify(body) } : {}) })
      return { status: response.status, data: response.status === 204 ? null : await response.json() }
    }
    function credentials(action) { return { username: 'browser_user', password: 'synthetic browser password unchanged ', ...(captcha ? { captchaToken: action + ':browser-' + crypto.randomUUID() } : {}) } }
    token = (await request('/api/auth/csrf-token')).data.csrfToken
    const registered = await request('/api/auth/register', 'POST', credentials('register'))
    const anonymous = await request('/api/auth/session')
    const logged = await request('/api/auth/login', 'POST', credentials('login'))
    const stale = await request('/api/user/profile', 'PATCH', { displayName: 'stale' })
    token = (await request('/api/auth/csrf-token')).data.csrfToken
    const updated = await request('/api/user/profile', 'PATCH', { displayName: 'Browser', preferences: { theme: 'dark' } })
    const profile = await request('/api/user/profile')
    const loggedOut = await request('/api/auth/logout', 'POST', {})
    const after = await request('/api/auth/session')
    document.querySelector('#status').textContent = 'Registration, login, CSRF rotation, profile and logout completed.'
    return { registered, anonymous, logged, stale, updated, profile, loggedOut, after, readableCookies: document.cookie }
  }, { captcha: spec.captcha.mode !== 'off' })
  assert.equal(result.registered.status, 201); assert.equal(result.anonymous.data.authenticated, false)
  assert.equal(result.logged.status, 200); assert.equal(result.stale.status, 403); assert.equal(result.updated.status, 200)
  assert.equal(result.profile.data.displayName, 'Browser'); assert.equal(result.loggedOut.status, 204); assert.equal(result.after.data.authenticated, false)
  assert.equal(result.readableCookies.includes('JSESSIONID'), false); assert.equal((await context.cookies()).some(c => c.name === 'JSESSIONID'), false)
  assert.deepEqual(errors, []); await page.screenshot({ path: path.join(run, 'browser-client.png'), fullPage: true })
  await writeFile(path.join(run, 'browser-results.json'), JSON.stringify({ status: 'PASS', browserVersion: browser.version(), https: true, realLogin: true, checks: ['registration without authentication', 'login and stale CSRF rejection', 'profile read/PATCH', 'logout and cookie removal', 'HttpOnly cookie inaccessible to JavaScript'], errors }, null, 2))
} finally { await browser.close() }
