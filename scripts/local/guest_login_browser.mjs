// Native browser Guest Login plus an explicitly simulated missing-Origin regression.
import { chromium } from '../../usefultools-frontend/node_modules/@playwright/test/index.mjs'
import { mkdir, writeFile } from 'node:fs/promises'
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'

const output = new URL('../../.local/verification/guest-browser/', import.meta.url)
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ channel: 'chrome', headless: true,
  args: ['--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE localhost'] })
const checks = [], external = []
try {
  for (const mode of ['native', 'missing-origin', 'missing-both']) {
    const context = await browser.newContext()
    const page = await context.newPage()
    const cdp = await context.newCDPSession(page)
    await cdp.send('Network.enable')
    await cdp.send('Network.setBlockedURLs', { urls: ['https://*'] })
    page.on('request', request => {
      const url = new URL(request.url())
      if (['http:', 'https:'].includes(url.protocol) && url.hostname !== 'localhost') external.push(url.origin)
    })
    // Do not intercept native requests: routing can alter browser-generated headers.
    if (mode !== 'native') await page.route('**/api/auth/login-guest', async route => {
      const headers = await route.request().allHeaders()
      delete headers.origin
      if (mode === 'missing-both') delete headers.referer
      // API forwarding makes removal deterministic; Chromium can regenerate
      // Origin/Referer after route.continue. This is explicitly simulated.
      const response = await route.fetch({ headers })
      await route.fulfill({ response })
    })
    await page.goto('http://localhost:5173/login')
    const pending = page.waitForResponse(r => r.url().endsWith('/api/auth/login-guest'))
    await page.getByRole('button', { name: 'Continue as Guest' }).click()
    const response = await pending
    assert.equal(response.status(), mode === 'missing-both' ? 403 : 200)
    if (mode === 'missing-both') {
      await page.getByRole('alert').filter({ hasText: 'trusted Origin or Referer' }).waitFor()
      assert(!(await page.getByRole('alert').innerText()).includes('<html'))
    } else {
      await page.waitForURL('**/dashboard')
      await page.goto('http://localhost:5173/backend-support')
      await page.getByRole('button', { name: 'Load and validate sample' }).click()
      await page.getByRole('status', { name: 'Validation status' }).filter({ hasText: 'Specification valid' }).waitFor()
    }
    await page.screenshot({ path: fileURLToPath(new URL(mode + '.png', output)) })
    checks.push({ mode, status: response.status(), result: 'PASS' })
    await context.close()
  }
  assert.deepEqual(external, [])
  const result = { status: 'PASS', browser: browser.version(), checks, externalOrigins: external }
  await writeFile(new URL('results.json', output), JSON.stringify(result, null, 2))
  console.log(JSON.stringify(result))
} finally { await browser.close() }
