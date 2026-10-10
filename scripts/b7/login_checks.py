"""Actual UsefulTools credential login, existing-tool smoke and raw-log privacy check."""
import argparse,http.client,json,os,shutil,sqlite3,subprocess,sys,time
from pathlib import Path
from urllib.parse import urlencode,urlsplit
ROOT=Path(__file__).resolve().parents[2]
def main():
 p=argparse.ArgumentParser();p.add_argument('run');a=p.parse_args();run=Path(a.run).resolve();assert run.is_relative_to(ROOT/'.b7');run.mkdir(parents=True);commands=[];checks=[];credentials=[];status='FAIL';failure=None
 cp=os.pathsep.join([str(ROOT/'scripts/b1/runtime/target/test-classes'),(ROOT/'scripts/b1/runtime/classpath.txt').read_text().strip()]);classes=run/'classes';classes.mkdir()
 compile=[shutil.which('javac'),'-encoding','UTF-8','--release','17','-cp',cp,'-d',str(classes),str(ROOT/'scripts/b7/runtime/Server.java')];r=subprocess.run(compile,cwd=ROOT,capture_output=True,text=True);commands.append(dict(command=compile,cwd=str(ROOT),exitCode=r.returncode));assert r.returncode==0,r.stderr
 env=os.environ.copy();env.update(SQLITE_DB_PATH=str(run/'application.db'),SQLITE_DB_URL='',RECAPTCHA_SECRET_KEY='synthetic-b7-provider-secret',JAVA_TOOL_OPTIONS='-Xms32m -Xmx512m')
 command=[shutil.which('java'),'-cp',str(classes)+os.pathsep+cp,'b7.Server',str(run),str(ROOT/'Useful-Tools/target/UsefulTools.war')]
 with (run/'server.log').open('w',encoding='utf-8') as log:
  process=subprocess.Popen(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
  try:
   deadline=time.monotonic()+90
   while not (run/'ready.json').exists():
    assert process.poll() is None and time.monotonic()<deadline,'Login fixture unavailable';time.sleep(.2)
   origin=json.loads((run/'ready.json').read_text())['origin'];u=urlsplit(origin)
   def call(path,body=None,expected=200,cookie=None,csrf=None,form=False):
    c=http.client.HTTPConnection(u.hostname,u.port,timeout=15);headers={'Origin':origin}
    if cookie:headers['Cookie']=cookie
    if csrf:headers['X-XSRF-TOKEN']=csrf
    if body is not None:headers['Content-Type']='application/x-www-form-urlencoded' if form else 'application/json';body=urlencode(body) if form else json.dumps(body)
    try:
     c.request('POST' if body is not None else 'GET',path,body,headers);res=c.getresponse();raw=res.read();assert res.status==expected,(path,res.status,expected)
     return json.loads(raw),res.getheaders()
    finally:c.close()
   call('/api/auth/login',{},400,form=True)
   body=dict(username='b7_login',password='synthetic-password-b7',recaptchaToken='rejected-synthetic-token');call('/api/auth/login',body,403,form=True)
   body['recaptchaToken']='accepted-synthetic-token';body['password']='wrong-synthetic-password';call('/api/auth/login',body,401,form=True)
   body['password']='synthetic-password-b7';data,headers=call('/api/auth/login',body,form=True);assert data['success'] and data['data']['username']=='b7_login','login response shape'
   cookies=[v for k,v in headers if k.lower()=='set-cookie'];sessionCookies=[v for v in cookies if v.startswith('JSESSIONID=')];assert len({v.split(';')[0] for v in sessionCookies})==1,'Conflicting session cookies';session=sessionCookies[-1];assert all(x in session.lower() for x in ['secure','httponly','samesite=none','path=/']), 'Cookie attributes: '+json.dumps([v.split(';')[1:] for v in cookies]);cookie=session.split(';')[0];csrf=data['data']['csrfToken'];credentials.extend([cookie.split('=',1)[1],csrf,body['password'],body['recaptchaToken'],env['RECAPTCHA_SECRET_KEY']]);checks.append('real credential login and provider rejection; existing Secure/HttpOnly/SameSite cookie semantics')
   current,_=call('/api/auth/session-status',cookie=cookie);assert current['success']
   units,_=call('/api/units/list',cookie=cookie);assert units['success'] and len(units['data'])>100;checks.append('actual startup seeds bundled idempotent units resource; authenticated units endpoint returns reference data')
   result,_=call('/api/calculator/simple',{'expression':'2+3*4'},cookie=cookie,csrf=csrf);assert result['data']['result']==14
   call('/api/favorites/toggle',{'toolPath':'/calculator'},cookie=cookie,csrf=csrf);result,_=call('/api/favorites/list',cookie=cookie);assert '/calculator' in json.dumps(result)
   result,_=call('/api/search/tools?q=backend',cookie=cookie);assert '/backend-support' in json.dumps(result)
   call('/api/auth/logout',{},cookie=cookie,csrf=csrf);call('/api/backend-support/catalog',expected=401,cookie=cookie);checks.append('real login session calculator/favorites/search/logout and replay denial')
   status='PASS'
  except Exception as e:failure=type(e).__name__+': '+str(e)
  finally:
   (run/'stop').touch()
   try:process.wait(timeout=30)
   except subprocess.TimeoutExpired:process.terminate();process.wait(timeout=15)
   commands.append(dict(command=command,cwd=str(ROOT),exitCode=process.returncode))
 text=(run/'server.log').read_text(encoding='utf-8',errors='replace');leaks=[v for v in credentials if v and v in text]
 if leaks:
  status='FAIL';failure='Raw application log leaked a synthetic credential (redacted from retained log)'
  for v in leaks:text=text.replace(v,'[REDACTED]')
  (run/'server.log').write_text(text)
 elif credentials:checks.append('raw application stdout contains none of actual synthetic cookie/CSRF/password/provider values')
 result=dict(status=status,checks=checks,commands=commands,failure=failure,provider='actual loopback HTTP; isolated config override; no real Google request',syntheticSessionBypass=False)
 (run/'login-results.json').write_text(json.dumps(result,indent=2));print(json.dumps({'status':status,'checks':checks,'failure':failure}));return 0 if status=='PASS' else 1
if __name__=='__main__':sys.exit(main())
