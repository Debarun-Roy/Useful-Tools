// Local-only visual record. Production references are captured separately, read-only.
import { chromium } from '../../usefultools-frontend/node_modules/@playwright/test/index.mjs'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import path from 'node:path'
import assert from 'node:assert/strict'

const root = process.cwd(), phase = process.argv[2]
assert(['before', 'after'].includes(phase))
const output = path.join((await readFile('.local/ui-current.txt', 'utf8')).trim(), phase)
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const rows = [], errors = [], external = [], fonts = []
try {
  for (const theme of ['observatory', 'starry-night']) {
    const context = await browser.newContext()
    await context.addInitScript(theme => localStorage.setItem('usefultools.theme', theme), theme)
    await context.route('**/*', route => {
      const url = new URL(route.request().url())
      if (['http:', 'https:'].includes(url.protocol) && url.origin !== 'http://localhost:5173') {
        external.push(url.origin); return route.abort()
      }
      return route.continue()
    })
    const page = await context.newPage()
    page.on('pageerror', error => errors.push(error.message))
    page.on('response', response => { if (/\.(woff2?|ttf)(\?|$)/.test(response.url())) fonts.push({ url: response.url(), status: response.status() }) })
    for (const viewport of [{ width: 1440, height: 900 }, { width: 768, height: 1024 }, { width: 375, height: 812 }]) {
      await page.setViewportSize(viewport)
      for (const route of ['/login', '/register', '/dashboard', '/calculator', '/converter', '/backend-support']) {
        if (route === '/dashboard') {
          await page.goto('http://localhost:5173/login')
          await page.getByRole('button', { name: 'Continue as Guest' }).click()
          await page.waitForURL('**/dashboard')
        }
        await page.goto('http://localhost:5173' + route, { waitUntil: 'networkidle' })
        await page.evaluate(() => document.fonts.ready)
        const cdp = await context.newCDPSession(page)
        await cdp.send('DOM.enable'); await cdp.send('CSS.enable')
        const { root: documentNode } = await cdp.send('DOM.getDocument')
        const { nodeId } = await cdp.send('DOM.querySelector', { nodeId: documentNode.nodeId, selector: 'h1' })
        const renderedFonts = nodeId ? (await cdp.send('CSS.getPlatformFontsForNode', { nodeId })).fonts : []
        await cdp.detach()
        const metrics = await page.evaluate(() => ({
          rootSize: getComputedStyle(document.documentElement).fontSize,
          bodyFont: getComputedStyle(document.body).fontFamily,
          overflow: document.documentElement.scrollWidth > innerWidth + 1,
          headings: [...document.querySelectorAll('h1')].map(e => { const s = getComputedStyle(e), b = e.getBoundingClientRect(); return { text: e.textContent, font: s.fontFamily, weight: s.fontWeight, size: s.fontSize, lineHeight: s.lineHeight, width: b.width, height: b.height } }),
          checkboxes: [...document.querySelectorAll('input[type=checkbox]')].filter(e => e.getBoundingClientRect().width).slice(0, 4).map(e => { const b = e.getBoundingClientRect(), l = e.closest('label').getBoundingClientRect(); return { width: b.width, height: b.height, labelHeight: l.height, labelWidth: l.width } })
        }))
        await page.mouse.move(0, 0)
        const name = `${theme}-${viewport.width}-${route.slice(1)}.png`
        await page.screenshot({ path: path.join(output, name) })
        if (route === '/backend-support') {
          await page.getByRole('checkbox', { name: 'Nullable', exact: true }).first().scrollIntoViewIfNeeded()
          await page.screenshot({ path: path.join(output, name.replace('.png', '-controls.png')) })
        }
        rows.push({ route, theme, viewport, renderedFonts, ...metrics, screenshot: name })
      }
    }
    await context.close()
  }
  assert.deepEqual(errors, []); assert.deepEqual(external, [])
  await writeFile(path.join(output, 'visual-results.json'), JSON.stringify({ browser: browser.version(), phase, rows, errors, external, fonts }, null, 2))
  console.log(JSON.stringify({ phase, screenshots: rows.length + 6, errors, external, fonts: [...new Set(fonts.map(f => f.url))] }))
} finally { await browser.close() }
