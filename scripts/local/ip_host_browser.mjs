// Real browser navigation from the reported IP URL; no localhost request interception.
import { chromium } from '../../usefultools-frontend/node_modules/@playwright/test/index.mjs'
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const output = new URL('../../.local/verification/ip-host-' + new Date().toISOString().replaceAll(':', '-') + '/', import.meta.url)
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ channel: 'chrome', headless: true,
  args: ['--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE localhost, EXCLUDE 127.0.0.1'] })
const apiOrigins = [], external = [], checks = []
try {
  const context = await browser.newContext()
  const redirect = await context.request.get('http://127.0.0.1:5173/login?from=ip', { maxRedirects: 0 })
  assert.equal(redirect.status(), 302)
  assert.equal(redirect.headers().location, 'http://localhost:5173/login?from=ip')
  assert.equal(redirect.headers()['cache-control'], 'no-store')
  const rejected = await context.request.post('http://127.0.0.1:5173/api/auth/login-guest', {
    maxRedirects: 0, headers: { Origin: 'http://127.0.0.1:5173', Referer: 'http://127.0.0.1:5173/login' },
  })
  assert.equal(rejected.status(), 403)
  assert.equal((await rejected.json()).errorCode, 'LOCAL_REQUEST_REJECTED')
  assert.equal(rejected.headers().location, undefined)
  checks.push('Page response is a non-cached 302; exact original IP API POST still rejects without redirect')
  const page = await context.newPage()
  const cdp = await context.newCDPSession(page)
  await cdp.send('Network.enable')
  await cdp.send('Network.setBlockedURLs', { urls: ['https://*'] })
  page.on('request', request => {
    const url = new URL(request.url())
    if (url.pathname.startsWith('/api/')) apiOrigins.push(url.origin)
    if (['http:', 'https:'].includes(url.protocol) && !['localhost', '127.0.0.1'].includes(url.hostname)) external.push(url.origin)
  })
  await page.goto('http://127.0.0.1:5173/login?from=ip#guest')
  assert.equal(page.url(), 'http://localhost:5173/login?from=ip#guest')
  checks.push('IP page redirects before app load; path, query and fragment preserved')
  const login = page.waitForResponse(r => r.url().endsWith('/api/auth/login-guest'))
  await page.getByRole('button', { name: 'Continue as Guest' }).click()
  const response = await login
  assert.equal(response.status(), 200)
  const headers = await response.request().allHeaders()
  assert.equal(headers.origin, 'http://localhost:5173')
  assert(headers.referer.startsWith('http://localhost:5173/'))
  await page.waitForURL('**/dashboard')
  checks.push('Guest Login sends canonical Origin and Referer and reaches dashboard')
  await page.goto('http://127.0.0.1:5173/backend-support')
  assert.equal(page.url(), 'http://localhost:5173/backend-support')
  await page.getByRole('button', { name: 'Load and validate sample' }).click()
  await page.getByRole('status', { name: 'Validation status' }).filter({ hasText: 'Specification valid' }).waitFor()
  const cookies = await context.cookies()
  assert(cookies.some(c => c.name === 'JSESSIONID' && c.domain === 'localhost' && c.httpOnly && c.sameSite === 'Lax'))
  assert(cookies.every(c => c.domain === 'localhost'))
  assert(apiOrigins.length > 0 && apiOrigins.every(origin => origin === 'http://localhost:5173'))
  assert.deepEqual(external, [])
  checks.push('IP deep link reuses localhost session; sample validation passes; cookies and APIs stay on localhost')
  await page.screenshot({ path: fileURLToPath(new URL('sample-success.png', output)) })
  const result = { status: 'PASS', browser: browser.version(), checks, apiOrigins: [...new Set(apiOrigins)], external }
  await writeFile(new URL('results.json', output), JSON.stringify(result, null, 2))
  console.log(JSON.stringify(result))
} finally { await browser.close() }
