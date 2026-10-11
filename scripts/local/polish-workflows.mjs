// Focused integration coverage for the relocated historical validation controls.
import { chromium, expect } from '../../usefultools-frontend/node_modules/@playwright/test/index.mjs'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import path from 'node:path'
import assert from 'node:assert/strict'
const output = path.join((await readFile('.local/polish-current.txt', 'utf8')).trim(), 'workflows')
await mkdir(output)
const fixture = JSON.parse(await readFile(path.join(process.argv[2], 'sessions.json'), 'utf8')), origin = fixture.origin
assert(['127.0.0.1', 'localhost'].includes(new URL(origin).hostname))
const browser = await chromium.launch({ channel: 'chrome', headless: true }), checks = [], errors = [], external = [], fonts = []
async function open(role) {
 const identity = fixture[role], context = await browser.newContext({ viewport: { width: 375, height: 812 } })
 await context.addCookies([{ name: 'JSESSIONID', value: identity.session, url: origin, httpOnly: true }])
 await context.addInitScript(i => { localStorage.setItem('username', i.username); localStorage.setItem('_ut_role', i.role); sessionStorage.setItem('_ut_xsrf', i.csrf) }, identity)
 await context.route('**/*', route => { if (new URL(route.request().url()).origin !== origin) { external.push(route.request().url()); return route.abort() } return route.continue() })
 const page = await context.newPage(); page.on('pageerror', e => errors.push(e.message))
 page.on('response', r => { if (/\.ttf$/.test(r.url())) fonts.push({ url: r.url(), status: r.status() }) })
 await page.goto(origin + '/backend-support', { waitUntil: 'networkidle' }); return page
}
try {
 const page = await open('browseruser')
 await page.getByRole('combobox', { name: 'Tool family', exact: true }).selectOption('etl')
 const editor = page.getByRole('textbox', { name: 'ETL specification JSON', exact: true })
 await page.getByRole('textbox', { name: 'Source field 1', exact: true }).fill('retained_source_id')
 const original = await editor.inputValue()
 async function validateSample(id) {
  await page.getByRole('combobox', { name: 'Built-in sample', exact: true }).selectOption(id)
  const wait = page.waitForResponse(r => r.url().endsWith('/backend-support/validate'))
  await page.getByRole('button', { name: 'Load and validate sample', exact: true }).click()
  const response = await wait; assert.equal(JSON.parse(response.request().postData()).sampleId, id); assert((await response.json()).success)
  assert.equal(await editor.inputValue(), original)
 }
 await validateSample('csv-etl'); const csv = await page.getByRole('textbox', { name: 'Sample specification', exact: true }).inputValue()
 await validateSample('json-etl'); const json = await page.getByRole('textbox', { name: 'Sample specification', exact: true }).inputValue()
 assert.notEqual(csv, json); checks.push('historical CSV/JSON sample selection changes only its preview; ETL draft preserved')
 await page.getByRole('button', { name: 'Custom JSON', exact: true }).click()
 const custom = page.getByRole('textbox', { name: 'Custom specification JSON', exact: true })
 await expect(custom).toHaveValue(json)
 await custom.fill('{'); await page.getByRole('button', { name: 'Validate custom specification', exact: true }).click()
 await expect(page.getByRole('status', { name: 'Validation status', exact: true })).toContainText('Invalid JSON')
 await custom.fill(json)
 const validation = page.waitForResponse(r => r.url().endsWith('/backend-support/validate'))
 await page.getByRole('button', { name: 'Validate custom specification', exact: true }).click(); assert((await (await validation).json()).success)
 await page.getByRole('button', { name: 'Sample preview', exact: true }).click(); await page.getByRole('button', { name: 'Custom JSON', exact: true }).click()
 await expect(custom).toHaveValue(json); assert.equal(await editor.inputValue(), original)
 checks.push('historical custom copy, invalid JSON, validation and mode roundtrip preserve both drafts')
 await page.getByRole('button', { name: 'Sample preview', exact: true }).click()
 await page.route('**/backend-support/validate', route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ success: false, errorCode: 'TEST_UNAVAILABLE' }) }), { times: 1 })
 await page.getByRole('button', { name: 'Load and validate sample', exact: true }).click()
 await expect(page.getByRole('status', { name: 'Validation status', exact: true })).toContainText('Validation rejected')
 assert.equal(await editor.inputValue(), original)
 await page.screenshot({ path: path.join(output, 'historical-error-375.png') })
 const sample = page.getByRole('combobox', { name: 'Built-in sample', exact: true }); await sample.focus(); await page.keyboard.press('Tab')
 await expect(page.getByRole('button', { name: 'Sample preview', exact: true })).toBeFocused()
 assert(await page.getByRole('button', { name: 'Sample preview', exact: true }).evaluate(e => e.matches(':focus-visible')))
 checks.push('historical failed request preserves generator draft; selector-to-mode keyboard order and visible focus')
 const guest = await open('browserguest')
 await expect(guest.getByRole('button', { name: 'Custom JSON', exact: true })).toHaveCount(0)
 await expect(guest.getByRole('button', { name: 'Validate and generate schema', exact: true })).toBeDisabled()
 const guestResponse = guest.waitForResponse(r => r.url().endsWith('/backend-support/validate'))
 await guest.getByRole('button', { name: 'Load and validate sample', exact: true }).click(); assert((await (await guestResponse).json()).success)
 checks.push('guest historical sample validation works; custom input absent and generation disabled')
 assert.deepEqual(errors, []); assert.deepEqual(external, []); assert(fonts.length >= 2 && fonts.every(f => f.status === 200))
 await writeFile(path.join(output, 'results.json'), JSON.stringify({ status: 'PASS', browser: browser.version(), checks, errors, external, fonts }, null, 2))
 console.log('PASS historical workflow checks', checks.length)
} catch (error) { await writeFile(path.join(output, 'failure.json'), JSON.stringify({ failure: String(error), checks, errors, external }, null, 2)); throw error }
finally { await browser.close() }
