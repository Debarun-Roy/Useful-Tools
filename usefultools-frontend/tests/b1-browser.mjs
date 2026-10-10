import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { chromium, expect } from '@playwright/test'
import { THEME_OPTIONS } from '../src/theme/themeOptions.js'

const run = process.env.B1_RUN
const fixtures = JSON.parse(await readFile(path.join(run, 'sessions.json'), 'utf8'))
const origin = fixtures.origin
assert.match(origin, /^http:\/\/127\.0\.0\.1:\d+$/)
const browser = await chromium.launch({ headless: true, ...(process.env.B0_BROWSER_CHANNEL ? { channel: process.env.B0_BROWSER_CHANNEL } : {}) })
const checks = []; const errors = []; const expectedErrors = new Set()
async function check(name, fn) { await fn(); checks.push(name); console.log('PASS', name) }
async function open(role, url = '/backend-support') {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } })
  if (role) {
    const identity = fixtures[role]
    await context.addCookies([{ name: 'JSESSIONID', value: identity.session, url: origin, httpOnly: true }])
    await context.addInitScript(({ identity }) => {
      localStorage.setItem('username', identity.username); localStorage.setItem('_ut_role', identity.role)
      sessionStorage.setItem('_ut_xsrf', identity.csrf)
    }, { identity })
  }
  const page = await context.newPage()
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error' && !expectedErrors.has(page)) errors.push(message.text()) })
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.origin === origin) return route.continue()
    if (url.hostname === 'fonts.googleapis.com') return route.fulfill({ contentType: 'text/css', body: '' })
    if (['www.google.com','www.recaptcha.net'].includes(url.hostname)) return route.fulfill({ contentType: 'text/javascript', body: '' })
    errors.push('Unexpected external request: ' + url.origin); await route.abort()
  })
  await page.goto(origin + url, { waitUntil: 'networkidle' })
  return page
}
async function toggle(enabled) {
  const identity = fixtures.browseradmin
  const response = await fetch(origin + '/api/admin/tool-toggles', { method: 'PUT', headers: { Cookie: `JSESSIONID=${identity.session}`, Origin: origin, 'X-XSRF-TOKEN': identity.csrf, 'Content-Type': 'application/json' }, body: JSON.stringify({ toolPath: '/backend-support', enabled }) })
  assert.equal(response.status, 200)
}
async function load(page) {
  await page.getByRole('button', { name: 'Load and validate sample' }).click()
  await expect(page.getByRole('status', { name: 'Validation status' })).toContainText('Specification valid')
  await expect(page.getByLabel('Sample specification', { exact: true })).not.toHaveValue('')
}
try {
  await check('protected deep link', async () => {
    const page = await open(null)
    await expect(page).toHaveURL(origin + '/login')
    await page.context().close()
  })
  const user = await open('browseruser')
  await check('catalog, sample specification, custom input and accessible findings', async () => {
    await expect(user.getByLabel('Tool family')).toHaveCount(1)
    assert.equal(await user.getByLabel('Tool family').locator('option').count(), 6)
    await load(user)
    await expect(user.getByText('No findings from B1 checks.')).toBeVisible()
    await user.getByRole('button', { name: 'Custom JSON', exact: true }).click()
    const editor = user.getByLabel('Custom specification JSON')
    const draft = await editor.inputValue(); assert.ok(draft.includes('schemaVersion'))
    await user.getByRole('button', { name: 'Validate custom specification' }).click()
    await expect(user.getByRole('status', { name: 'Validation status' })).toContainText('Specification valid')
    const invalid = JSON.parse(draft); invalid.spec.tables[0].primaryKey = ['missing']
    await editor.fill(JSON.stringify(invalid))
    await expect(user.getByRole('region', { name: 'Validation findings' })).toHaveCount(0)
    expectedErrors.add(user) // This scenario deliberately receives the real server's 422.
    await user.getByRole('button', { name: 'Validate custom specification' }).click()
    await expect(user.getByRole('status', { name: 'Validation status' })).toContainText('Specification invalid')
    await expect(user.getByText('error: UNKNOWN_REFERENCE')).toBeVisible()
    await editor.fill(draft)
    await user.getByRole('button', { name: 'Validate custom specification' }).click()
    await expect(user.getByRole('status', { name: 'Validation status' })).toContainText('Specification valid')
    expectedErrors.delete(user)
    assert.equal(await user.evaluate(() => Object.values(localStorage).some(v => v.includes('schemaVersion'))), false)
  })
  await check('network errors preserve input and retry succeeds', async () => {
    const editor = user.getByLabel('Custom specification JSON'); const before = await editor.inputValue()
    expectedErrors.add(user)
    await user.route('**/api/backend-support/validate', route => route.abort('failed'), { times: 1 })
    await user.getByRole('button', { name: 'Validate custom specification' }).click()
    await expect(user.getByRole('status', { name: 'Validation status' })).toContainText('Network error')
    assert.equal(await editor.inputValue(), before)
    await user.getByRole('button', { name: 'Validate custom specification' }).click()
    await expect(user.getByRole('status', { name: 'Validation status' })).toContainText('Specification valid')
    expectedErrors.delete(user)
  })
  await check('in-flight responses cannot restore stale findings after edits', async () => {
    let release; let received
    const ready = new Promise(resolve => { received = resolve })
    const wait = new Promise(resolve => { release = resolve })
    await user.route('**/api/backend-support/validate', async route => {
      const response = await route.fetch(); received(); await wait
      try { await route.fulfill({ response }) } catch { /* Client aborted this now-stale request. */ }
    }, { times: 1 })
    await user.getByRole('button', { name: 'Validate custom specification' }).click()
    await ready
    await user.getByLabel('Custom specification JSON').fill('{}')
    release()
    await expect(user.getByRole('status', { name: 'Validation status' })).toContainText('draft changed')
    await expect(user.getByRole('region', { name: 'Validation findings' })).toHaveCount(0)
  })
  await check('unavailable catalog and retry preserve the draft', async () => {
    expectedErrors.add(user)
    await user.route('**/api/backend-support/catalog', route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ success: false, errorCode: 'TOOL_UNAVAILABLE' }) }), { times: 1 })
    await user.getByRole('button', { name: 'Refresh access' }).click()
    await expect(user.getByRole('alert')).toContainText('Catalog unavailable')
    await user.getByRole('button', { name: 'Refresh access' }).click()
    await expect(user.getByLabel('Custom specification JSON')).toHaveValue('{}')
    expectedErrors.delete(user)
  })
  await check('guest immutable samples and selection invalidation', async () => {
    const guest = await open('browserguest')
    await expect(guest.getByText('Guest access: immutable built-in samples only.')).toBeVisible()
    await expect(guest.getByRole('button', { name: 'Custom JSON' })).toHaveCount(0)
    await load(guest)
    assert.equal(await guest.getByLabel('Sample specification', { exact: true }).getAttribute('readonly'), '')
    await guest.getByLabel('Tool family').selectOption('etl')
    await expect(guest.getByRole('region', { name: 'Validation findings' })).toHaveCount(0)
    await load(guest)
    await guest.context().close()
  })
  await check('ten themes, desktop/mobile layouts, keyboard focus and status announcements', async () => {
    await user.getByRole('button', { name: 'Sample preview', exact: true }).click()
    await load(user)
    for (const theme of THEME_OPTIONS) {
      await user.evaluate(value => localStorage.setItem('usefultools.theme', value), theme.value)
      await user.reload({ waitUntil: 'networkidle' }); await load(user)
      for (const width of [1280,390]) {
        await user.setViewportSize({ width, height: 900 })
        assert.equal(await user.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false, `${theme.value} ${width} overflow`)
        await user.evaluate(() => scrollTo(0, 0))
        await user.screenshot({ path: path.join(run, `${theme.value}-${width}.png`), fullPage: true })
      }
      const colors = await user.getByLabel('Sample specification', { exact: true }).evaluate(el => ({ fg: getComputedStyle(el).color, bg: getComputedStyle(el).backgroundColor }))
      assert.notEqual(colors.fg, colors.bg, theme.value + ' text visibility')
    }
    await user.getByLabel('Tool family').focus(); await user.keyboard.press('Tab')
    await expect(user.getByLabel('Built-in sample')).toBeFocused()
    const outline = await user.getByLabel('Built-in sample').evaluate(el => getComputedStyle(el).outlineStyle)
    assert.notEqual(outline, 'none')
    await expect(user.getByRole('status', { name: 'Validation status' })).toHaveAttribute('aria-live', 'polite')
    await user.setViewportSize({ width: 1280, height: 900 })
  })
  await check('dashboard card, favorites, backend search and activity label', async () => {
    await user.goto(origin + '/dashboard', { waitUntil: 'networkidle' })
    await expect(user.getByRole('button', { name: 'Add Backend Support to favorites', exact: true })).toBeVisible()
    await user.getByRole('button', { name: 'Add Backend Support to favorites', exact: true }).click()
    await expect(user.getByRole('region', { name: 'Favorite tools' })).toContainText('Backend Support')
    await expect(user.getByText('Validated a backend specification').first()).toBeVisible()
    await user.getByLabel('Search tools', { exact: true }).fill('backend')
    await user.getByLabel('Select Backend Support', { exact: true }).click()
    await expect(user).toHaveURL(origin + '/backend-support')
  })
  await check('disabled direct link, dashboard and admin preview/toggle', async () => {
    await toggle(false)
    await user.reload({ waitUntil: 'networkidle' })
    await expect(user.getByText('This tool is disabled.', { exact: false })).toBeVisible()
    await expect(user.getByRole('button', { name: 'Load and validate sample' })).toBeDisabled()
    const admin = await open('browseradmin')
    await expect(admin.getByText('Admin preview: this tool is disabled for other users.')).toBeVisible()
    await load(admin)
    await user.goto(origin + '/dashboard', { waitUntil: 'networkidle' })
    const card = user.getByRole('region', { name: 'Favorite tools' }).getByRole('button', { name: /Backend Support.*Preview and validate/ })
    await card.focus(); await user.keyboard.press('Enter')
    await expect(user).toHaveURL(origin + '/dashboard')
    await admin.goto(origin + '/admin', { waitUntil: 'networkidle' })
    await admin.getByRole('button', { name: /Tool Toggles/ }).click()
    const checkbox = admin.getByLabel('Enable Backend Support (admin preview when disabled)', { exact: true })
    await expect(checkbox).not.toBeChecked()
    const saved = admin.waitForResponse(response => response.url().endsWith('/api/admin/tool-toggles') && response.request().method() === 'PUT')
    await checkbox.check()
    const response = await saved
    assert.equal(response.status(), 200); assert.equal((await response.json()).success, true)
    await expect(checkbox).toBeChecked()
    const catalog = await admin.request.get(origin + '/api/backend-support/catalog')
    assert.equal((await catalog.json()).data.access.enabled, true)
    await admin.context().close()
  })
  assert.deepEqual(errors, [])
  await writeFile(path.join(run, 'browser-results.json'), JSON.stringify({ status: 'PASS', browserVersion: browser.version(), channel: process.env.B0_BROWSER_CHANNEL || 'pinned-chromium', themes: THEME_OPTIONS.length, checks, count: checks.length, unexpectedErrors: errors }, null, 2))
} finally { await browser.close() }
