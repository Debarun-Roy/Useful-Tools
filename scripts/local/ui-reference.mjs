// Read-only public frontend reference. ALL application API traffic is fulfilled locally.
import { chromium } from '../../usefultools-frontend/node_modules/@playwright/test/index.mjs'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import path from 'node:path'

const origin = 'https://useful-tools-deba.vercel.app'
const output = path.join((await readFile('.local/ui-current.txt', 'utf8')).trim(), 'reference')
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const fixtures = {}, blocked = [], assets = [], rows = [], pending = []
try {
  const local = await browser.newContext(), page = await local.newPage()
  await local.route('**/*', route => new URL(route.request().url()).origin === 'http://localhost:5173' ? route.continue() : route.abort())
  await page.goto('http://localhost:5173/login')
  await page.getByRole('button', { name: 'Continue as Guest' }).click(); await page.waitForURL('**/dashboard')
  page.on('response', response => {
    const url = new URL(response.url())
    if (url.pathname.startsWith('/api/') && response.request().method() === 'GET') pending.push(response.json().then(data => { fixtures[url.pathname] = data }).catch(() => {}))
  })
  for (const route of ['/dashboard', '/calculator', '/converter']) await page.goto('http://localhost:5173' + route, { waitUntil: 'networkidle' })
  await Promise.all(pending); await local.close()
  for (const theme of ['observatory', 'starry-night']) {
    const context = await browser.newContext()
    await context.addInitScript(theme => {
      localStorage.setItem('usefultools.theme', theme)
      localStorage.setItem('username', 'Guest User'); localStorage.setItem('_ut_role', 'user'); sessionStorage.setItem('_ut_xsrf', 'synthetic-visual-only')
    }, theme)
    await context.route('**/*', route => {
      const request = route.request(), url = new URL(request.url())
      if (url.pathname.startsWith('/api/')) {
        blocked.push({ path: url.pathname, method: request.method(), reason: 'synthetic local response; not sent to production' })
        return route.fulfill({ contentType: 'application/json', body: JSON.stringify(fixtures[url.pathname] || { success: true, data: {} }) })
      }
      if (request.method() === 'GET' && (url.origin === origin || ['fonts.googleapis.com', 'fonts.gstatic.com'].includes(url.hostname))) return route.continue()
      blocked.push({ origin: url.origin, path: url.pathname, reason: 'integration blocked' }); return route.fulfill({ body: '' })
    })
    const reference = await context.newPage()
    reference.on('response', response => {
      if (/\.(js|css|woff2?|ttf)(\?|$)/.test(response.url())) pending.push(response.body().then(data => assets.push({ url: response.url(), status: response.status(), sha256: createHash('sha256').update(data).digest('hex') })).catch(() => {}))
    })
    for (const viewport of [{ width: 1440, height: 900 }, { width: 768, height: 1024 }, { width: 375, height: 812 }]) {
      await reference.setViewportSize(viewport)
      for (const route of ['/login', '/register', '/dashboard', '/calculator', '/converter']) {
        await reference.goto(origin + route, { waitUntil: 'networkidle' }); await reference.evaluate(() => document.fonts.ready)
        const cdp = await context.newCDPSession(reference); await cdp.send('DOM.enable'); await cdp.send('CSS.enable')
        const { root } = await cdp.send('DOM.getDocument'), { nodeId } = await cdp.send('DOM.querySelector', { nodeId: root.nodeId, selector: 'h1' })
        const renderedFonts = nodeId ? (await cdp.send('CSS.getPlatformFontsForNode', { nodeId })).fonts : []; await cdp.detach()
        const metrics = await reference.evaluate(() => ({ rootSize: getComputedStyle(document.documentElement).fontSize, headings: [...document.querySelectorAll('h1')].map(e => { const s = getComputedStyle(e), b = e.getBoundingClientRect(); return { text: e.textContent, font: s.fontFamily, weight: s.fontWeight, size: s.fontSize, lineHeight: s.lineHeight, width: b.width, height: b.height } }) }))
        const filename = `${theme}-${viewport.width}-${route.slice(1)}.png`; await reference.screenshot({ path: path.join(output, filename) })
        rows.push({ route, theme, viewport, renderedFonts, ...metrics, screenshot: filename })
      }
    }
    await context.close()
  }
  await Promise.all(pending)
  await writeFile(path.join(output, 'reference-results.json'), JSON.stringify({ origin, capturedAt: new Date().toISOString(), browser: browser.version(), sourceSha: 'not independently established; public asset hashes identify reference', mode: 'read-only public assets; synthetic local guest GET data; provider and all production APIs blocked; no submissions', rows, assets, blocked }, null, 2))
  console.log('PASS read-only reference captures', rows.length)
} finally { await browser.close() }
