import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { chromium, expect } from '@playwright/test'
import { THEME_OPTIONS } from '../src/theme/themeOptions.js'
const run = process.env.B3_RUN, fixtures = JSON.parse(await readFile(path.join(run, 'sessions.json'), 'utf8')), origin = fixtures.origin
const browser = await chromium.launch({ headless: true, ...(process.env.B0_BROWSER_CHANNEL ? { channel: process.env.B0_BROWSER_CHANNEL } : {}) })
const checks = [], errors = [], expected = new Set(), titles = { migration: 'Migration planner', view: 'View generator', evaluator: 'Schema evaluator' }
async function check(name, fn) { await fn(); checks.push(name); console.log('PASS', name) }
async function open(role) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, acceptDownloads: true }), identity = fixtures[role]
  await context.addCookies([{ name: 'JSESSIONID', value: identity.session, url: origin, httpOnly: true }])
  await context.addInitScript(i => { localStorage.setItem('username', i.username); localStorage.setItem('_ut_role', i.role); sessionStorage.setItem('_ut_xsrf', i.csrf) }, identity)
  const page = await context.newPage(); page.on('pageerror', e => errors.push(e.message)); page.on('console', m => { if (m.type() === 'error' && !expected.has(page)) errors.push(m.text()) })
  await page.route('**/*', route => new URL(route.request().url()).origin === origin ? route.continue() : route.fulfill({ body: '' }))
  await page.goto(origin + '/backend-support', { waitUntil: 'networkidle' });
  await writeFile(path.join(run, 'browser-entry.txt'), await page.locator('body').innerText());
  await page.screenshot({ path: path.join(run, 'browser-entry.png'), fullPage: true }); return page
}
const ui = (page, module) => page.getByRole('region', { name: titles[module], exact: true })
const button = (page, module) => ui(page, module).getByRole('button', { name: module === 'evaluator' ? 'Evaluate schema' : `Generate ${module}`, exact: true })
const download = (page, module) => ui(page, module).getByRole('button', { name: module === 'evaluator' ? 'Download evaluation report' : `Download ${module} ZIP`, exact: true })
async function select(page, module) { await page.getByLabel('Tool family').selectOption(module); await expect(ui(page, module)).toBeVisible() }
async function generate(page, module) {
  const wait = page.waitForResponse(r => r.url().endsWith('/backend-support/generate')); await button(page, module).click(); const data = await (await wait).json(); assert.equal(data.success, true)
  await expect(download(page, module)).toBeEnabled(); return data.data
}
async function exportBundle(page, module, preview) {
  const wait = page.waitForEvent('download'); await download(page, module).click(); const file = await wait; await file.saveAs(path.join(run, `browser-${module}.zip`)); await writeFile(path.join(run, `browser-${module}-preview.json`), JSON.stringify(preview))
}
async function toggle(enabled) {
  const i = fixtures.browseradmin; const r = await fetch(origin + '/api/admin/tool-toggles', { method: 'PUT', headers: { Cookie: `JSESSIONID=${i.session}`, Origin: origin, 'X-XSRF-TOKEN': i.csrf, 'Content-Type': 'application/json' }, body: JSON.stringify({ toolPath: '/backend-support', enabled }) }); assert.equal(r.status, 200)
}
try {
  const page = await open('migrationbrowser')
  await check('guided migration preview/export and destructive complete-plan block', async () => {
    await select(page, 'migration'); const region = ui(page, 'migration')
    await region.getByLabel('New column ID / name').fill('browser_note'); await region.getByRole('button', { name: 'Append column', exact: true }).click()
    await region.getByLabel('External dependencies reviewed; maintenance window and backup required').check()
    const preview = await generate(page, 'migration'); assert.ok(preview.files.find(f => f.path === 'up.sql').content.includes('browser_note')); await exportBundle(page, 'migration', preview)
    const editor = region.getByLabel('Full migration specification JSON'), spec = JSON.parse(await editor.inputValue()); spec.after.tables[0].columns.splice(2, 1); spec.acknowledgeDestructive = true
    await editor.fill(JSON.stringify(spec)); await region.getByRole('button', { name: 'Apply migration JSON' }).click(); expected.add(page)
    await button(page, 'migration').click(); await expect(region.getByRole('status')).toContainText('Blocked'); await expect(region.getByRole('region', { name: 'migration findings', exact: true })).toContainText('DESTRUCTIVE_COLUMN_DROP'); await expect(download(page, 'migration')).toBeDisabled(); expected.delete(page)
  })
  await check('guided view configuration, joins grouping aggregates through download', async () => {
    await select(page, 'view'); const region = ui(page, 'view')
    await region.getByLabel('View name', { exact: true }).fill('browser_view'); await region.getByLabel('Join type').selectOption('inner'); await region.getByRole('button', { name: 'Group all projected columns' }).click()
    await region.getByLabel('Filter operator').selectOption('eq'); await region.getByLabel('Filter column').selectOption('c/name')
    await region.getByLabel('Filter literal (JSON primitive)').fill('"'); await expect(button(page, 'view')).toBeDisabled()
    await region.getByLabel('Filter literal (JSON primitive)').fill('"Ada"'); await region.getByRole('button', { name: 'Apply filter literal' }).click()
    const p = await generate(page, 'view'); assert.ok(p.files.find(f => f.path === 'view.sql').content.includes('INNER JOIN')); await exportBundle(page, 'view', p)
  })
  await check('evaluation errors stay downloadable with severity and field navigation', async () => {
    await select(page, 'evaluator'); const p = await generate(page, 'evaluator'); assert.equal(p.details.hasErrors, true); await exportBundle(page, 'evaluator', p)
    const region = ui(page, 'evaluator'); await expect(region.getByRole('status')).toContainText('downloaded')
    await region.getByLabel('Finding severity').selectOption('error'); await region.getByRole('region', { name: 'evaluator findings', exact: true }).getByRole('button').first().click(); await expect(region.getByLabel('Full evaluator specification JSON')).toBeFocused()
  })
  await check('invalid JSON and separate module drafts preserved including schema editor', async () => {
    const region = ui(page, 'evaluator'), editor = region.getByLabel('Full evaluator specification JSON'); await editor.fill('{'); await region.getByRole('button', { name: 'Apply evaluator JSON' }).click(); await expect(region.getByRole('status')).toContainText('JSON is invalid')
    await select(page, 'view'); await expect(ui(page, 'view').getByLabel('View name', { exact: true })).toHaveValue('browser_view'); await select(page, 'evaluator'); await expect(editor).toHaveValue('{'); await expect(region.getByLabel('Naming convention')).toBeDisabled(); await region.getByRole('button', { name: 'Discard unapplied evaluator JSON' }).click()
    await page.getByLabel('Tool family').selectOption('schema'); const schema = page.getByRole('region', { name: 'Schema generator', exact: true }); await schema.getByLabel('Table database name', { exact: true }).fill('preserved_schema'); await select(page, 'view'); await page.getByLabel('Tool family').selectOption('schema'); await expect(schema.getByLabel('Table database name', { exact: true })).toHaveValue('preserved_schema')
  })
  await check('late generate after edit or module change and late export cannot revive artifacts', async () => {
    for (const module of ['view', 'evaluator']) {
      await select(page, module); let release, received; const ready = new Promise(r => { received = r }), wait = new Promise(r => { release = r })
      await page.route('**/backend-support/generate', async route => { const response = await route.fetch(); received(); await wait; try { await route.fulfill({ response }) } catch {} }, { times: 1 })
      await button(page, module).click(); await ready
      if (module === 'view') await ui(page, module).getByLabel('View name', { exact: true }).fill('edited_view'); else await select(page, 'migration')
      release(); if (module === 'evaluator') await select(page, module); await expect(download(page, module)).toBeDisabled()
    }
    await select(page, 'view'); await generate(page, 'view'); let release, received, downloads = 0; page.on('download', () => downloads++)
    const ready = new Promise(r => { received = r }), wait = new Promise(r => { release = r })
    await page.route('**/backend-support/export', async route => { const response = await route.fetch(); received(); await wait; try { await route.fulfill({ response }) } catch {} }, { times: 1 })
    await download(page, 'view').click(); await ready; await select(page, 'evaluator'); release(); await generate(page, 'evaluator'); assert.equal(downloads, 0)
  })
  await check('export and clipboard failures preserve the current draft', async () => {
    const region = ui(page, 'evaluator'), before = await region.getByLabel('Full evaluator specification JSON').inputValue(); expected.add(page)
    await page.route('**/backend-support/export', route => route.fulfill({ status: 502, contentType: 'text/html', body: 'temporary error' }), { times: 1 }); await download(page, 'evaluator').click(); await expect(region.getByRole('status')).toContainText('Export failed'); expected.delete(page)
    assert.equal(await region.getByLabel('Full evaluator specification JSON').inputValue(), before)
    await page.evaluate(() => Object.defineProperty(navigator, 'clipboard', { value: { writeText: async () => { throw new Error('Denied') } }, configurable: true })); await region.getByRole('button', { name: 'Copy artifact' }).click(); await expect(region.getByRole('status')).toContainText('Clipboard unavailable')
  })
  await check('all three workflows in ten themes desktop/mobile and keyboard focus', async () => {
    for (const theme of THEME_OPTIONS) {
      await page.evaluate(value => localStorage.setItem('usefultools.theme', value), theme.value); await page.reload({ waitUntil: 'networkidle' })
      for (const module of ['migration', 'view', 'evaluator']) {
        await select(page, module)
        for (const width of [1280, 390]) { await page.setViewportSize({ width, height: 900 }); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false, `${theme.value} ${module} ${width}`); await ui(page, module).screenshot({ path: path.join(run, `b3-${module}-${theme.value}-${width}.png`) }) }
        const editor = ui(page, module).getByLabel(`Full ${module} specification JSON`); await editor.focus(); assert.notEqual(await editor.evaluate(el => getComputedStyle(el).outlineStyle), 'none'); await page.keyboard.press('Tab'); await expect(ui(page, module).getByRole('button', { name: `Apply ${module} JSON` })).toBeFocused()
      }
    }
    assert.equal(await page.evaluate(() => Object.values(localStorage).some(v => v.includes('schemaVersion'))), false)
  })
  await check('guest and disabled user deny every module; disabled admin can generate', async () => {
    const guest = await open('browserguest'); for (const module of ['migration', 'view', 'evaluator']) { await select(guest, module); await expect(button(guest, module)).toBeDisabled() }; await guest.context().close()
    await toggle(false); await page.reload({ waitUntil: 'networkidle' }); for (const module of ['migration', 'view', 'evaluator']) { await select(page, module); await expect(button(page, module)).toBeDisabled() }
    const admin = await open('browseradmin'); for (const module of ['migration', 'view', 'evaluator']) { await select(admin, module); if (module === 'migration') await ui(admin, module).getByLabel('External dependencies reviewed; maintenance window and backup required').check(); assert.equal((await generate(admin, module)).adminPreview, true) }; await admin.context().close(); await toggle(true)
  })
  assert.deepEqual(errors, []); await writeFile(path.join(run, 'browser-results.json'), JSON.stringify({ status: 'PASS', browserVersion: browser.version(), themes: THEME_OPTIONS.length, screenshots: 60, checks, count: checks.length, unexpectedErrors: errors }, null, 2))
} catch (error) { await writeFile(path.join(run, 'browser-results.json'), JSON.stringify({ status: 'FAIL', checks, unexpectedErrors: errors, failure: String(error) }, null, 2)); throw error }
finally { await browser.close() }
