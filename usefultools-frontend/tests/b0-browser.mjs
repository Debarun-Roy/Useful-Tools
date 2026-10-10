import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from '@playwright/test'

// Built frontend + real local WAR, with third-party assets isolated from external networks.
const dist = path.resolve('dist')
const api = process.env.B0_BACKEND_URL
assert.match(api || '', /^http:\/\/127\.0\.0\.1:\d+$/)
const server = createServer(async (req, res) => {
  try {
    if (req.url.startsWith('/api/')) {
      const upstream = await fetch(`${api}${req.url}`, { redirect: 'manual' })
      res.writeHead(upstream.status, {'content-type': upstream.headers.get('content-type') || 'application/json'})
      res.end(Buffer.from(await upstream.arrayBuffer()))
      return
    }
    const requested = decodeURIComponent(new URL(req.url, 'http://local').pathname)
    const file = path.resolve(dist, '.' + requested)
    assert.ok(file.startsWith(dist + path.sep) || file === dist)
    const extension = path.extname(file)
    const actual = extension ? file : path.join(dist, 'index.html')
    const mime = {'.html':'text/html', '.js':'text/javascript', '.css':'text/css', '.svg':'image/svg+xml', '.png':'image/png'}
    res.writeHead(200, {'content-type': mime[path.extname(actual)] || 'application/octet-stream'})
    res.end(await readFile(actual))
  } catch (error) { res.writeHead(500); res.end('Local smoke server error') }
})
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
const origin = `http://127.0.0.1:${server.address().port}`
let browser
try {
  browser = await chromium.launch({headless: true, ...(process.env.B0_BROWSER_CHANNEL ? {channel:process.env.B0_BROWSER_CHANNEL} : {})})
  console.log('Browser version:', browser.version())
  const page = await browser.newPage()
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.origin === origin) return route.continue()
    // No CAPTCHA provider calls or remote fonts. No successful CAPTCHA simulation.
    if (url.hostname === 'fonts.googleapis.com') return route.fulfill({contentType:'text/css', body:''})
    if (['www.google.com', 'www.recaptcha.net'].includes(url.hostname)) return route.fulfill({contentType:'text/javascript', body:''})
    throw new Error(`Unexpected external request blocked: ${url.origin}`)
  })
  await page.goto(`${origin}/login`, {waitUntil:'networkidle'})
  await page.getByLabel('Username', {exact:true}).waitFor()
  await page.getByLabel('Password', {exact:true}).waitFor()
  assert.ok((await page.locator('#root').innerText()).length > 100)
  const response = await page.request.get(`${origin}/api/auth/session-status`)
  assert.equal(response.status(), 401)
  assert.equal((await response.json()).errorCode, 'UNAUTHENTICATED')
  await page.screenshot({path:process.env.B0_SCREENSHOT || '../.b0/frontend.png', fullPage:true})
  assert.deepEqual(errors, [])
  console.log('PASS built frontend: login form, no console/runtime errors, same-origin real WAR 401 contract')
} finally {
  if (browser) await browser.close()
  await new Promise(resolve => server.close(resolve))
}
