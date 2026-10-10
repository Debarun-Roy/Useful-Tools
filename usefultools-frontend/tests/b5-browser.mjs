import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { chromium, expect } from '@playwright/test'
import { THEME_OPTIONS } from '../src/theme/themeOptions.js'
const run = process.env.B5_RUN, fixtures = JSON.parse(await readFile(path.join(run, 'sessions.json'), 'utf8')), origin = fixtures.origin
const browser = await chromium.launch({ headless: true, ...(process.env.B0_BROWSER_CHANNEL ? { channel: process.env.B0_BROWSER_CHANNEL } : {}) })
const checks = [], errors = [], expected = new Set()
async function check(name, fn) { await fn(); checks.push(name); console.log('PASS', name) }
async function open(role) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, acceptDownloads: true }), identity = fixtures[role]
  await context.addCookies([{ name: 'JSESSIONID', value: identity.session, url: origin, httpOnly: true }])
  await context.addInitScript(i => { localStorage.setItem('username', i.username); localStorage.setItem('_ut_role', i.role); sessionStorage.setItem('_ut_xsrf', i.csrf) }, identity)
  const page = await context.newPage(); page.on('pageerror', e => errors.push(e.message)); page.on('console', m => { if (m.type() === 'error' && !expected.has(page)) errors.push(m.text()) })
  await page.route('**/*', route => new URL(route.request().url()).origin === origin ? route.continue() : route.fulfill({ body: '' }))
  await page.goto(origin + '/backend-support', { waitUntil: 'networkidle' }); await page.getByLabel('Tool family').selectOption('rest');
  await writeFile(path.join(run, 'browser-entry.txt'), await page.locator('body').innerText()); await page.screenshot({ path: path.join(run, 'browser-entry.png'), fullPage: true }); return page
}
const ui = page => page.getByRole('region', { name: 'Java auth starter', exact: true })
const generateButton = page => ui(page).getByRole('button', { name: 'Generate auth preview', exact: true })
const download = page => ui(page).getByRole('button', { name: 'Download auth ZIP', exact: true })
async function generate(page) {
  const wait = page.waitForResponse(r => r.url().endsWith('/backend-support/generate')); await generateButton(page).click(); const data = await (await wait).json(); assert.equal(data.success, true)
  await expect(download(page)).toBeEnabled(); return data.data
}
async function toggle(enabled) {
  const i = fixtures.browseradmin; const r = await fetch(origin + '/api/admin/tool-toggles', { method: 'PUT', headers: { Cookie: `JSESSIONID=${i.session}`, Origin: origin, 'X-XSRF-TOKEN': i.csrf, 'Content-Type': 'application/json' }, body: JSON.stringify({ toolPath: '/backend-support', enabled }) }); assert.equal(r.status, 200)
}
try {
  const page = await open('restbrowser'), region = ui(page), editor = region.getByLabel('auth specification JSON')
  await check('guided auth database modules origins CAPTCHA preview and actual ZIP', async () => {
    await region.getByLabel('Auth database', { exact: true }).selectOption('postgresql')
    await region.getByLabel('Project identifier').fill('browser-auth')
    await region.getByLabel('CAPTCHA mode', { exact: true }).selectOption('recaptcha-v3')
    await region.getByLabel('Minimum CAPTCHA score').fill('0.7')
    await expect(region.getByLabel('Auth target', { exact: true })).toHaveValue('java')
    const preview = await generate(page); const spec = JSON.parse(preview.files.find(f => f.path === 'auth-spec.json').content)
    assert.equal(spec.database, 'postgresql'); assert.equal(spec.captcha.minimumScore, 0.7); assert.deepEqual(spec.modules, ['core','profile'])
    const wait = page.waitForEvent('download'); await download(page).click(); const file = await wait
    await file.saveAs(path.join(run, 'browser-auth.zip')); await writeFile(path.join(run, 'browser-auth-preview.json'), JSON.stringify(preview))
  })
  await check('invalid unapplied JSON and all earlier module drafts retained', async () => {
    await editor.fill('{'); await region.getByRole('button', { name: 'Apply auth JSON', exact: true }).click(); await expect(region.getByRole('status')).toContainText('Invalid JSON'); await expect(region.getByLabel('Auth database', { exact: true })).toBeDisabled()
    for (const [module, title, label, value] of [['schema', 'Schema generator', 'Table database name', 'b5_preserved'], ['view', 'View generator', 'View name', 'b5_view']]) {
      await page.getByLabel('Tool family').selectOption(module); const other = page.getByRole('region', { name: title, exact: true }); await other.getByLabel(label, { exact: true }).fill(value)
      await page.getByLabel('Tool family').selectOption('rest'); await expect(editor).toHaveValue('{'); await page.getByLabel('Tool family').selectOption(module); await expect(other.getByLabel(label, { exact: true })).toHaveValue(value)
    }
    await page.getByLabel('Tool family').selectOption('rest'); await region.getByRole('button', { name: 'Discard auth JSON changes' }).click(); await expect(region.getByLabel('Project identifier', { exact: true })).toHaveValue('browser-auth')
  })
  await check('negative semantic validation findings navigate preserved JSON', async () => {
    const original = await editor.inputValue(), spec = JSON.parse(original); spec.modules = ['profile']; await editor.fill(JSON.stringify(spec)); await region.getByRole('button', { name: 'Apply auth JSON', exact: true }).click()
    expected.add(page); await generateButton(page).click(); await expect(region.getByRole('status')).toContainText('Generation blocked'); await expect(download(page)).toBeDisabled(); expected.delete(page)
    await region.getByRole('button', { name: /AUTH_CORE_REQUIRED/ }).click(); await expect(editor).toBeFocused(); await editor.fill(original); await region.getByRole('button', { name: 'Apply auth JSON', exact: true }).click()
  })
  await check('late generation and export invalidated by edits and module changes', async () => {
    for (const moduleChange of [false, true]) {
      let release, received; const ready = new Promise(r => { received = r }), wait = new Promise(r => { release = r })
      await page.route('**/backend-support/generate', async route => { const response = await route.fetch(); received(); await wait; try { await route.fulfill({ response }) } catch {} }, { times: 1 })
      await generateButton(page).click(); await ready
      if (moduleChange) await page.getByLabel('Tool family').selectOption('schema'); else await region.getByLabel('Session idleSeconds').fill('901')
      release(); if (moduleChange) await page.getByLabel('Tool family').selectOption('rest'); await expect(download(page), 'late-response moduleChange=' + moduleChange).toBeDisabled()
    }
    await generate(page); let release, received, downloads = 0; page.on('download', () => downloads++)
    const ready = new Promise(r => { received = r }), wait = new Promise(r => { release = r })
    await page.route('**/backend-support/export', async route => { const response = await route.fetch(); received(); await wait; try { await route.fulfill({ response }) } catch {} }, { times: 1 })
    await download(page).click(); await ready; await region.getByLabel('Session idleSeconds').fill('902'); release(); await generate(page); assert.equal(downloads, 0)
  })
  await check('delayed target and history changes invalidate old work and allow fresh results', async () => {
    for (const selection of ['target', 'history']) {
      let release, received, settled
      const ready = new Promise(r => { received = r }), wait = new Promise(r => { release = r }), done = new Promise(r => { settled = r })
      await page.route('**/backend-support/generate', async route => {
        try { const response = await route.fetch(); received(); await wait; await route.fulfill({ response }) }
        catch {} finally { settled() }
      }, { times: 1 })
      await generateButton(page).click(); await ready
      if (selection === 'target') {
        await region.getByLabel('Auth target', { exact: true }).selectOption('python')
        await page.getByRole('region', { name: 'Python auth starter', exact: true }).getByLabel('Auth target', { exact: true }).selectOption('java')
      } else {
        await page.getByLabel('Tool family').selectOption('schema'); await page.goBack()
        await expect(page.getByLabel('Tool family')).toHaveValue('rest')
      }
      release(); await done
      await expect(download(page)).toBeDisabled(); await expect(generateButton(page)).toBeEnabled()
      const preview = await generate(page)
      assert.equal(JSON.parse(preview.files.find(f => f.path === 'auth-spec.json').content).target, 'java')
    }
  })
  await check('export network failure preserves input', async () => {
    const before = await editor.inputValue(); expected.add(page)
    await page.route('**/backend-support/export', route => route.fulfill({ status: 502, contentType: 'text/html', body: 'temporary error' }), { times: 1 }); await download(page).click(); await expect(region.getByRole('status')).toContainText('Export failed'); expected.delete(page); assert.equal(await editor.inputValue(), before)
    await page.evaluate(() => Object.defineProperty(navigator, 'clipboard', { value: { writeText: async () => { throw new Error('Denied') } }, configurable: true })); await region.getByRole('button', { name: 'Copy auth artifact' }).click(); await expect(region.getByRole('status')).toContainText('Clipboard unavailable'); assert.equal(await editor.inputValue(), before)
  })
  await check('ten themes desktop mobile keyboard and no draft persistence', async () => {
    for (const theme of THEME_OPTIONS) {
      await page.evaluate(value => localStorage.setItem('usefultools.theme', value), theme.value); await page.reload({ waitUntil: 'networkidle' }); await page.getByLabel('Tool family').selectOption('rest')
      for (const width of [1280, 390]) { await page.setViewportSize({ width, height: 900 }); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false, `${theme.value} ${width}`); await region.screenshot({ path: path.join(run, `b5-etl-${theme.value}-${width}.png`) }) }
      await editor.fill((await editor.inputValue()) + '\n'); await editor.focus(); assert.notEqual(await editor.evaluate(el => getComputedStyle(el).outlineStyle), 'none'); await page.keyboard.press('Tab'); await expect(region.getByRole('button', { name: 'Apply auth JSON', exact: true })).toBeFocused()
    }
    assert.equal(await page.evaluate(() => Object.values(localStorage).some(v => v.includes('schemaVersion'))), false)
  })
  await check('guest disabled user and admin preview access', async () => {
    const guest = await open('restguest'); await expect(generateButton(guest)).toBeDisabled(); await guest.context().close()
    await toggle(false); await page.reload({ waitUntil: 'networkidle' }); await page.getByLabel('Tool family').selectOption('rest'); await expect(generateButton(page)).toBeDisabled()
    const admin = await open('restadmin'); assert.equal((await generate(admin)).adminPreview, true); await admin.context().close(); await toggle(true)
  })
  assert.deepEqual(errors, []); await writeFile(path.join(run, 'browser-results.json'), JSON.stringify({ status: 'PASS', browserVersion: browser.version(), themes: THEME_OPTIONS.length, screenshots: 20, checks, count: checks.length, unexpectedErrors: errors }, null, 2))
} catch (error) { await writeFile(path.join(run, 'browser-results.json'), JSON.stringify({ status: 'FAIL', checks, unexpectedErrors: errors, failure: String(error) }, null, 2)); throw error }
finally { await browser.close() }
