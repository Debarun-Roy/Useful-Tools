import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {chromium} from '../../usefultools-frontend/node_modules/@playwright/test/index.mjs';
import {THEME_OPTIONS,STORAGE_KEY} from '../../usefultools-frontend/src/theme/themeOptions.js';
const run=process.env.B7_RUN, fixture=process.env.B7_FIXTURE;
const identities=JSON.parse(await readFile(path.join(fixture,'sessions.json'),'utf8')),origin=identities.origin,i=identities.browseruser;
const browser=await chromium.launch({headless:true,channel:process.env.B0_BROWSER_CHANNEL||'chrome'}),results=[],errors=[],network=[],consoleErrors=[];
let page,context;
try {
 for(const theme of THEME_OPTIONS){
  // Release each theme's renderer state; all seven surfaces still run in one retained-draft context.
  if(context)await context.close();
  context=await browser.newContext({viewport:{width:1280,height:900}});
  await context.addCookies([{name:'JSESSIONID',value:i.session,url:origin,httpOnly:true}]);
  await context.addInitScript(({identity,key,theme})=>{localStorage.setItem('username',identity.username);localStorage.setItem('_ut_role',identity.role);sessionStorage.setItem('_ut_xsrf',identity.csrf);localStorage.setItem(key,theme)}, {identity:i,key:STORAGE_KEY,theme:theme.value});
  page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
  page.on('console',m=>{if(m.type()==='error')consoleErrors.push(m.text())});
  page.on('response',r=>{if(new URL(r.url()).pathname.startsWith('/assets/'))network.push({path:new URL(r.url()).pathname,status:r.status(),type:r.headers()['content-type']})});
  page.on('requestfailed',r=>network.push({path:new URL(r.url()).pathname,failure:r.failure()?.errorText}));
  await page.route('**/*',r=>new URL(r.request().url()).origin===origin?r.continue():r.fulfill({body:''}));
  await page.route('**/__b7/axe.js',r=>r.fulfill({path:path.resolve('.b7/tools/node_modules/axe-core/axe.min.js'),contentType:'application/javascript'}));
  await page.goto(origin+'/backend-support',{waitUntil:'networkidle'});
  await page.addScriptTag({url:origin+'/__b7/axe.js'});
  for(const module of ['schema','migration','view','evaluator','etl','rest','rest-python']){
   await page.getByLabel('Tool family').selectOption(module==='rest-python'?'rest':module);
   if(module.startsWith('rest'))await page.locator('select[aria-label="Auth target"]:visible').selectOption(module==='rest-python'?'python':'java');
   const axe=await page.evaluate(async()=>{const r=await axe.run(document,{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21aa']}});return {version:r.testEngine.version,violations:r.violations.map(v=>({id:v.id,impact:v.impact,help:v.help,nodes:v.nodes.map(n=>({target:n.target,failureSummary:n.failureSummary}))})),incomplete:r.incomplete.map(v=>v.id)}});
   await page.getByLabel('Tool family').focus();await page.keyboard.press('Tab');assert(await page.evaluate(()=>document.activeElement!==document.body));
   results.push({theme:theme.value,module,...axe});
  }
  await page.screenshot({path:path.join(run,'desktop-'+theme.value+'.png'),fullPage:true});
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(run,'mobile-'+theme.value+'.png'),fullPage:true});
  const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth+2);results.push({theme:theme.value,check:'mobileOverflow',passed:!overflow});
  await page.setViewportSize({width:1280,height:900});
 }
 await page.goto(origin+'/backend-support?module=view',{waitUntil:'networkidle'});assert.equal(await page.getByLabel('Tool family').inputValue(),'view');
 await page.getByLabel('Tool family').selectOption('etl');await page.goBack();assert.equal(await page.getByLabel('Tool family').inputValue(),'view');
 await page.setViewportSize({width:640,height:450});
 assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+2));
 results.push({check:'200-percent-equivalent-reflow',passed:true,detail:'640 CSS px on 1280px reference; viewport reflow, not browser-native zoom'});
 await page.setViewportSize({width:1280,height:900});
 await page.goto(origin+'/dashboard',{waitUntil:'networkidle'});
 await page.getByText('Web Dev Helpers',{exact:true}).click();
 await page.getByRole('button',{name:/REST Tester/}).click();
 await page.getByPlaceholder('https://api.example.com/endpoint').fill(origin+'/api/backend-support/catalog');
 const response=page.waitForResponse(r=>r.url()===origin+'/api/backend-support/catalog'&&r.request().method()==='GET');
 await page.getByRole('button',{name:'Send',exact:true}).click();assert.equal((await response).status(),200);
 const status=page.locator('span[class*="statusCode"]');await status.waitFor();assert.match((await status.innerText()).trim(),/^200(?: OK)?$/);
 results.push({check:'existing-rest-tester-real-catalog-request',passed:true});
 const failures=results.filter(r=>r.violations?.length||r.passed===false);
 await writeFile(path.join(run,'accessibility-results.json'),JSON.stringify({status:failures.length||errors.length||consoleErrors.length?'FAIL':'PASS',browser:browser.version(),channel:process.env.B0_BROWSER_CHANNEL,results,errors,consoleErrors,limitations:['Automated accessibility and scripted keyboard only; screen-reader and human usability acceptance not executed.']},null,2));
 assert.equal(errors.length,0);assert.equal(consoleErrors.length,0);assert.equal(failures.length,0,`${failures.length} accessibility/layout failures`);
}catch(error){
 await writeFile(path.join(run,'browser-failure.json'),JSON.stringify({message:error.message,url:page?.url(),text:page?await page.locator('body').innerText():null,errors,results,network,consoleErrors},null,2));
 if(page)await page.screenshot({path:path.join(run,'browser-failure.png'),fullPage:true});throw error;
}finally{await browser.close()}
