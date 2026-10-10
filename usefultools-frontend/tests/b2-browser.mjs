import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import path from 'node:path'
import { chromium, expect } from '@playwright/test'
import { THEME_OPTIONS } from '../src/theme/themeOptions.js'

const run = process.env.B2_RUN
const fixtures = JSON.parse(await readFile(path.join(run, 'sessions.json'), 'utf8')), origin = fixtures.origin
const browser = await chromium.launch({ headless: true, ...(process.env.B0_BROWSER_CHANNEL ? { channel: process.env.B0_BROWSER_CHANNEL } : {}) })
const checks = [], errors = [], expected = new Set()
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
async function check(name, fn) { await fn(); checks.push(name); console.log('PASS', name) }
async function open(role) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, acceptDownloads: true })
  const identity = fixtures[role]
  await context.addCookies([{ name: 'JSESSIONID', value: identity.session, url: origin, httpOnly: true }])
  await context.addInitScript(identity => {
    localStorage.setItem('username', identity.username); localStorage.setItem('_ut_role', identity.role)
    sessionStorage.setItem('_ut_xsrf', identity.csrf)
    const original = URL.revokeObjectURL; window.revokedDownloads = 0
    URL.revokeObjectURL = url => { window.revokedDownloads++; original(url) }
  }, identity)
  const page = await context.newPage()
  page.on('pageerror', e => errors.push(e.message))
  page.on('console', m => { if (m.type() === 'error' && !expected.has(page)) errors.push(m.text()) })
  await page.route('**/*', route => new URL(route.request().url()).origin === origin ? route.continue() : route.fulfill({ body: '' }))
  await page.goto(origin + '/backend-support', { waitUntil: 'networkidle' }); return page
}
const region = page => page.getByRole('region', { name: 'Schema generator', exact: true })
async function generate(page) {
  const response = page.waitForResponse(r => r.url().endsWith('/backend-support/generate'))
  await region(page).getByRole('button', { name: 'Validate and generate schema', exact: true }).click()
  const data = (await (await response).json()).data
  await expect(region(page).getByRole('status')).toContainText('Schema validated and generated')
  return data
}
async function toggle(enabled) {
  const identity = fixtures.browseradmin
  const res = await fetch(origin + '/api/admin/tool-toggles', { method: 'PUT', headers: { Cookie: `JSESSIONID=${identity.session}`, Origin: origin, 'X-XSRF-TOKEN': identity.csrf, 'Content-Type': 'application/json' }, body: JSON.stringify({ toolPath: '/backend-support', enabled }) })
  assert.equal(res.status, 200)
}
try {
  const page = await open('browseruser'), ui = region(page)
  await check('forms rename stable references, validate generate preview and downloaded hashes', async () => {
    await ui.getByLabel('Table database name', { exact: true }).fill('customers_renamed')
    await ui.getByLabel('Selected table').selectOption('1')
    await ui.getByRole('button', { name: 'Relationships', exact: true }).click()
    assert.equal(await ui.getByLabel('Referenced table ID').inputValue(), 'customers')
    const data = await generate(page)
    assert.ok(data.files.find(f => f.path === 'schema.sql').content.includes('REFERENCES "customers_renamed"'))
    const downloadFile = page.waitForEvent('download'); await ui.getByRole('button', { name: 'Download file', exact: true }).click()
    const file = await downloadFile; const bytes = await readFile(await file.path())
    assert.equal(hash(bytes), data.manifest.core.files.find(f => f.path === 'schema.sql').sha256)
    const downloadZip = page.waitForEvent('download'); await ui.getByRole('button', { name: 'Download bundle', exact: true }).click()
    const zip = await downloadZip; await zip.saveAs(path.join(run, 'browser-export.zip'))
    await writeFile(path.join(run, 'browser-preview.json'), JSON.stringify(data))
    await expect.poll(() => page.evaluate(() => window.revokedDownloads)).toBe(2)
    await ui.getByLabel('Preview file').focus(); await page.keyboard.press('Home'); await page.keyboard.press('ArrowDown')
    assert.notEqual(await ui.getByLabel('Preview file').evaluate(el => getComputedStyle(el).outlineStyle), 'none')
    assert.equal(await page.evaluate(() => Object.values(localStorage).some(v => v.includes('schemaVersion'))), false)
  })
  await check('invalid JSON stays separate, explicit apply, findings and source text escaping', async () => {
    await ui.getByRole('button', { name: 'JSON', exact: true }).click()
    const editor = ui.getByLabel('Schema generation JSON'), before = await editor.inputValue()
    await editor.fill('{'); await ui.getByRole('button', { name: 'Apply JSON to forms' }).click()
    await expect(ui.getByRole('status')).toContainText('JSON is invalid')
    await expect(ui.getByRole('button', { name: 'Tables', exact: true })).toBeDisabled()
    await expect(ui.getByRole('button', { name: 'Download bundle' })).toBeDisabled()
    const spec = JSON.parse(before); spec.tables[0].columns[1].default = { kind: 'literal', value: '<script>window.injected=true</script>' }
    await editor.fill(JSON.stringify(spec)); await ui.getByRole('button', { name: 'Apply JSON to forms' }).click()
    await generate(page); assert.equal(await page.evaluate(() => window.injected), undefined)
    const invalid = structuredClone(spec); invalid.tables[0].primaryKey = ['missing']
    await editor.fill(JSON.stringify(invalid)); expected.add(page)
    await ui.getByRole('button', { name: 'Validate and generate schema' }).click()
    await expect(ui.getByRole('status')).toContainText('Generation rejected')
    await expect(ui.getByRole('region', { name: 'Generation findings' })).toContainText('UNKNOWN_COLUMN')
    expected.delete(page); await editor.fill(JSON.stringify(spec)); await ui.getByRole('button', { name: 'Apply JSON to forms' }).click()
  })
  await check('late generation after target edit cannot restore preview', async () => {
    let release, received
    const ready = new Promise(resolve => { received = resolve }), wait = new Promise(resolve => { release = resolve })
    await page.route('**/backend-support/generate', async route => { const response = await route.fetch(); received(); await wait; try { await route.fulfill({ response }) } catch { /* aborted */ } }, { times: 1 })
    await ui.getByRole('button', { name: 'Validate and generate schema' }).click(); await ready
    await ui.getByLabel('Schema dialect').selectOption('postgresql'); release()
    await expect(ui.getByRole('region', { name: 'Generated files' })).toHaveCount(0)
    await expect(ui.getByRole('button', { name: 'Download bundle' })).toBeDisabled()
    const data = await generate(page); assert.equal(data.manifest.core.target, 'postgresql')
  })
  await check('export failure preserves input and late export does not download', async () => {
    const before = await ui.getByLabel('Schema generation JSON').inputValue()
    expected.add(page)
    await page.route('**/backend-support/export', route => route.fulfill({ status: 502, contentType: 'text/html', body: 'Temporary gateway failure' }), { times: 1 })
    await ui.getByRole('button', { name: 'Download bundle' }).click(); await expect(ui.getByRole('status')).toContainText('Export failed')
    assert.equal(await ui.getByLabel('Schema generation JSON').inputValue(), before); expected.delete(page)
    let release, received, downloads = 0
    const ready = new Promise(resolve => { received = resolve }), wait = new Promise(resolve => { release = resolve })
    page.on('download', () => downloads++)
    await page.route('**/backend-support/export', async route => { const response = await route.fetch(); received(); await wait; try { await route.fulfill({ response }) } catch { /* aborted */ } }, { times: 1 })
    await ui.getByRole('button', { name: 'Download bundle' }).click(); await ready
    await ui.getByLabel('Schema dialect').selectOption('sqlite'); release()
    await generate(page); assert.equal(downloads, 0)
  })
  await check('clipboard failure is announced, sample switching preserves personal draft', async () => {
    await page.evaluate(() => Object.defineProperty(navigator, 'clipboard', { value: { writeText: async () => { throw new Error('Denied') } }, configurable: true }))
    await ui.getByRole('button', { name: 'Copy file' }).click(); await expect(ui.getByRole('status')).toContainText('Clipboard unavailable')
    const before = await ui.getByLabel('Schema generation JSON').inputValue()
    await ui.getByLabel('Schema source').selectOption('sample'); await expect(ui.getByLabel('Schema generation JSON')).toHaveAttribute('readonly', '')
    await ui.getByLabel('Schema source').selectOption('draft'); assert.equal(await ui.getByLabel('Schema generation JSON').inputValue(), before)
  })
  await check('all themes desktop/mobile, associated labels and keyboard controls', async () => {
    for (const theme of THEME_OPTIONS) {
      await page.evaluate(value => localStorage.setItem('usefultools.theme', value), theme.value)
      await page.reload({ waitUntil: 'networkidle' }); await generate(page)
      for (const width of [1280, 390]) {
        await page.setViewportSize({ width, height: 900 })
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false, `${theme.value} ${width}`)
        await page.screenshot({ path: path.join(run, `b2-${theme.value}-${width}.png`), fullPage: true })
      }
      const colors = await ui.getByLabel('Generated file content').evaluate(el => ({ fg: getComputedStyle(el).color, bg: getComputedStyle(el).backgroundColor }))
      assert.notEqual(colors.fg, colors.bg)
    }
    await ui.getByLabel('Preview file').focus(); await page.keyboard.press('Tab'); await expect(ui.getByLabel('Generated file content')).toBeFocused()
  })
  await check('guest restrictions, disabled user and real admin preview generation', async () => {
    const guest = await open('browserguest'); await expect(region(guest).getByRole('button', { name: 'Validate and generate schema' })).toBeDisabled(); await guest.context().close()
    await toggle(false); await page.reload({ waitUntil: 'networkidle' }); await expect(ui.getByRole('button', { name: 'Validate and generate schema' })).toBeDisabled()
    const admin = await open('browseradmin'); await expect(admin.getByText('Admin preview: this tool is disabled for other users.')).toBeVisible()
    assert.equal((await generate(admin)).adminPreview, true); await admin.context().close(); await toggle(true)
  })
  assert.deepEqual(errors, [])
  await writeFile(path.join(run, 'browser-results.json'), JSON.stringify({ status: 'PASS', browserVersion: browser.version(), channel: process.env.B0_BROWSER_CHANNEL || 'pinned-chromium', themes: THEME_OPTIONS.length, checks, count: checks.length, unexpectedErrors: errors }, null, 2))
} finally { await browser.close() }
