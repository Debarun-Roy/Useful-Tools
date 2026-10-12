import { chromium, expect } from '../../usefultools-frontend/node_modules/@playwright/test/index.mjs'
import { createServer } from '../../usefultools-frontend/node_modules/vite/dist/node/index.js'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import assert from 'node:assert/strict'

const run = process.argv[2], fixture = JSON.parse(await readFile(path.join(run, 'sessions.json'), 'utf8'))
assert(['localhost', '127.0.0.1'].includes(new URL(fixture.origin).hostname))
process.env.VITE_API_BASE = '/api'
process.env.VITE_RECAPTCHA_SITE_KEY = ''
const vite = await createServer({ root: path.resolve('usefultools-frontend'), mode: 'development', server: {
  host: 'localhost', port: 0, strictPort: false, proxy: { '/api': { target: fixture.origin, changeOrigin: true } }
} })
await vite.listen()
const devOrigin = `http://localhost:${vite.httpServer.address().port}`
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const checks = [], events = []
try {
  for (const [mode, origin] of [['development StrictMode', devOrigin], ['built production React', fixture.origin]]) {
    const context = await browser.newContext(), identity = fixture.browseruser
    await context.addCookies([{ name: 'JSESSIONID', value: identity.session, url: origin, httpOnly: true }])
    await context.addInitScript(i => { localStorage.setItem('username', i.username); localStorage.setItem('_ut_role', i.role); sessionStorage.setItem('_ut_xsrf', i.csrf) }, identity)
    await context.route('**/*', route => new URL(route.request().url()).origin === origin ? route.continue() : route.abort())
    const page = await context.newPage(), requests = [], finished = [], failed = []
    const catalog = r => r.url().endsWith('/backend-support/catalog')
    page.on('request', r => { if (catalog(r)) { requests.push(r); events.push({ mode, state: 'started', page: page.url() }) } })
    page.on('response', r => { if(catalog(r)) events.push({mode,state:'response',status:r.status()}) })
    page.on('requestfinished', r => { if (catalog(r)) { finished.push(r); events.push({ mode, state: 'completed' }) } })
    page.on('requestfailed', r => { if (catalog(r)) { failed.push(r); events.push({ mode, state: 'failed', reason: r.failure() }) } })
    const ready = () => expect(page.getByRole('combobox', { name: 'Tool family', exact: true })).toBeVisible()
    await page.goto(origin + '/dashboard', { waitUntil: 'networkidle' })
    assert.equal(requests.length, 1, mode + ' dashboard mount')
    // Client-side dashboard navigation exercises effect replay without a full reload.
    await page.getByRole('button', { name: /^Backend Support Preview and validate/ }).click()
    await ready(); await page.waitForLoadState('networkidle')
    assert.equal(requests.length, 2, mode + ' dashboard to tool'); assert.equal(failed.length, 0)
    await page.reload({ waitUntil: 'networkidle' }); await ready()
    assert.equal(requests.length, 3, mode + ' direct mount')
    await page.getByRole('button', { name: 'Refresh access' }).click(); await ready(); await page.waitForLoadState('networkidle')
    assert.equal(requests.length, 4, mode + ' refresh')
    checks.push(mode + ': dashboard, client navigation, direct load, refresh each start one successful request')
    // Explicit server/network errors must surface and recover on refresh.
    for (const fault of ['server', 'network']) {
      await page.route('**/backend-support/catalog', route => fault === 'server'
        ? route.fulfill({ status: 503, contentType: 'application/json', body: '{"success":false}' }) : route.abort('failed'), { times: 1 })
      await page.getByRole('button', { name: 'Refresh access' }).click()
      await expect(page.getByRole('alert')).toContainText('Catalog unavailable')
      await page.getByRole('button', { name: 'Refresh access' }).click(); await ready()
    }
    checks.push(mode + ': HTTP 503 and network failure surface and refresh recovers')
    // Hold one response, supersede it, then release an obsolete failure.
    let release, held
    const hold = new Promise(resolve => { held = resolve })
    const gate = new Promise(resolve => { release = resolve })
    await page.route('**/backend-support/catalog', async route => { held(); await gate; await route.fulfill({ status: 503, contentType: 'application/json', body: '{"success":false}' }).catch(() => {}) }, { times: 1 })
    await page.getByRole('button', { name: 'Refresh access' }).click(); await hold
    await page.getByRole('button', { name: 'Refresh access' }).click(); await ready(); release()
    await page.waitForLoadState('networkidle'); await expect(page.getByRole('alert')).toHaveCount(0)
    checks.push(mode + ': superseded request is cancelled and stale error cannot replace current catalog')
    let releaseNav, heldNav
    const navHold = new Promise(resolve => { heldNav = resolve }), navGate = new Promise(resolve => { releaseNav = resolve })
    await page.route('**/backend-support/catalog', async route => { heldNav(); await navGate; await route.abort().catch(() => {}) }, { times: 1 })
    const beforeCancel = failed.length
    await page.getByRole('button', { name: 'Refresh access' }).click(); await navHold
    await page.getByRole('button', { name: 'Dashboard', exact: true }).click()
    releaseNav(); await page.waitForLoadState('networkidle'); await page.getByRole('button', { name: /^Backend Support Preview and validate/ }).click(); await ready()
    await expect.poll(() => failed.length).toBeGreaterThan(beforeCancel)
    checks.push(mode + ': rapid navigation cancels owned pending request and remount recovers')
    // Session expiry must invalidate authentication rather than display prior access.
    await page.route('**/backend-support/catalog', route => route.fulfill({ status: 401, contentType: 'application/json', body: '{"success":false,"errorCode":"UNAUTHORIZED"}' }), { times: 1 })
    await page.getByRole('button', { name: 'Refresh access' }).click(); await page.waitForURL('**/login')
    checks.push(mode + ': expired session redirects to login without cached access')
    await context.close()
  }
  // In-memory control: synchronous effect setup reproduces the original replay.
  // No source file is changed and this server is verification-only.
  const baseline=await createServer({root:path.resolve('usefultools-frontend'),mode:'development',
    plugins:[{name:'verification-synchronous-catalog',enforce:'pre',transform(code,id){
      if(id.endsWith('/BackendSupportPage/BackendSupportPage.jsx')) return code.replace('queueMicrotask(() => {',';((callback) => callback())(() => {')
    }}],server:{host:'localhost',port:0,strictPort:false,proxy:{'/api':{target:fixture.origin,changeOrigin:true}}}})
  try {
    await baseline.listen(); const origin=`http://localhost:${baseline.httpServer.address().port}`
    const context=await browser.newContext(), identity=fixture.browseruser
    await context.addCookies([{name:'JSESSIONID',value:identity.session,url:origin,httpOnly:true}])
    await context.addInitScript(i=>{localStorage.setItem('username',i.username);localStorage.setItem('_ut_role',i.role);sessionStorage.setItem('_ut_xsrf',i.csrf)},identity)
    await context.route('**/*',r=>new URL(r.request().url()).origin===origin?r.continue():r.abort())
    const page=await context.newPage(); let starts=0,cancels=0
    page.on('request',r=>{if(r.url().endsWith('/backend-support/catalog'))starts++})
    page.on('requestfailed',r=>{if(r.url().endsWith('/backend-support/catalog'))cancels++})
    await page.goto(origin+'/backend-support',{waitUntil:'networkidle'})
    await expect(page.getByRole('combobox',{name:'Tool family',exact:true})).toBeVisible()
    assert.equal(starts,2);assert.equal(cancels,1)
    checks.push('causality control: synchronous effect setup under StrictMode reproduces two requests and one cancellation')
    events.push({mode:'synchronous control',starts,cancels});await context.close()
  } finally {await baseline.close()}
  await writeFile(path.join(run, 'catalog-results.json'), JSON.stringify({ status: 'PASS', checks, events }, null, 2))
  console.log(JSON.stringify({ status: 'PASS', checks }))
} catch(error) {
  for(const context of browser.contexts()) for(const page of context.pages()) { await writeFile(path.join(run,'catalog-page.txt'),page.url()+'\n'+await page.locator('body').innerText()); await page.screenshot({path:path.join(run,'catalog-page.png')}) }
  await writeFile(path.join(run, 'catalog-failure.json'), JSON.stringify({ error: String(error), checks, events }, null, 2)); throw error
} finally { await browser.close(); await vite.close() }

