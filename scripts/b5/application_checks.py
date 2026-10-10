"""Build and execute actual HTTP-exported WARs; isolated databases and synthetic CAPTCHA only."""
import argparse,concurrent.futures,copy,http.client,json,os,shutil,sqlite3,ssl,subprocess,sys,time,uuid,zipfile
from pathlib import Path
from urllib.parse import urlsplit
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/b2'))
from postgres import Cluster
PASSWORD='synthetic B5 password unchanged '
class Client:
    def __init__(self,app):self.app=app;self.cookie=None;self.token=None
    def call(self,path,method='GET',body=None,expected=200,token=True,origin=True,cookie=True,media='application/json'):
        app=self.app;headers={}
        if cookie and self.cookie:headers['Cookie']='JSESSIONID='+self.cookie
        if method!='GET':
            headers['Content-Type']=media
            if token and self.token:headers['X-CSRF-Token']=self.token if token is True else token
            if origin:headers['Origin']=app.origin if origin is True else origin
        connection=http.client.HTTPSConnection('127.0.0.1',app.item['port'],timeout=20,context=ssl._create_unverified_context())
        try:
            payload=body if isinstance(body,bytes) else json.dumps(body).encode() if body is not None else None
            connection.request(method,path,payload,headers);response=connection.getresponse();raw=response.read();app.count+=1
            assert response.status==expected,(path,method,response.status,expected)
            assert response.getheader('Cache-Control')=='no-store' and response.getheader('X-Content-Type-Options')=='nosniff'
            app.statuses.add((path,method,expected));app.last_headers={k.title():v for k,v in response.getheaders()}
            for name,value in response.getheaders():
                if name.lower()=='set-cookie' and value.startswith('JSESSIONID='):
                    assert 'HttpOnly' in value and 'Secure' in value and 'SameSite=Lax' in value and 'Path=/' in value
                    self.cookie=value.split(';')[0].split('=',1)[1] or None
            if expected==204:assert raw==b'';return None
            assert response.getheader('Content-Type').startswith('application/json');result=json.loads(raw)
            assert PASSWORD.encode() not in raw and b'password_hash' not in raw and b'$argon2' not in raw
            if expected>=400:assert set(result)=={'error','requestId'}
            app.validate_response(path,method,expected,result)
            return result
        finally:connection.close()
    def bootstrap(self):self.token=self.call('/api/auth/csrf-token')['csrfToken'];return self
    def credentials(self,action,username='b5_alice',token=None):
        value={'username':username,'password':PASSWORD}
        if self.app.item['captcha']!='off':value['captchaToken']=token or action+':'+uuid.uuid4().hex
        return value
    def register(self,username='b5_alice',expected=201):return self.call('/api/auth/register','POST',self.credentials('register',username),expected)
    def login(self,username='b5_alice',expected=200):return self.call('/api/auth/login','POST',self.credentials('login',username),expected)
class Application:
    def __init__(self,run,item,cluster,key,validator=True):
        self.run=run;self.item=item;self.cluster=cluster;self.key=key;self.count=0;self.groups=[];self.statuses=set();self.control_id=0;self.commands=[];self.last_headers={}
        self.project=ROOT/item['directory'];self.work=run/item['name'];self.work.mkdir(parents=True);self.spec=json.loads((self.project/'auth-spec.json').read_text());self.origin=self.spec['origins'][0]
        self.openapi=json.loads((self.project/'openapi.json').read_text());self.validator=validator
        if validator:
            from openapi_spec_validator import validate
            from jsonschema import Draft4Validator,RefResolver,FormatChecker
            validate(self.openapi);self.schema_validator=Draft4Validator;self.resolver=RefResolver.from_schema(self.openapi);self.formats=FormatChecker()
    def validate_response(self,path,method,status,value):
        if not self.validator:return
        schema=self.openapi.get('paths',{}).get(path,{}).get(method.lower(),{}).get('responses',{}).get(str(status),{}).get('content',{}).get('application/json',{}).get('schema')
        if schema:self.schema_validator(schema,resolver=self.resolver,format_checker=self.formats).validate(value)
        else:
            assert status>=400,'Missing OpenAPI success schema'
            self.schema_validator(self.openapi['components']['schemas']['Error'],resolver=self.resolver).validate(value)
    def command(self,name,command,cwd):
        command=list(map(str,command));command[0]=shutil.which(command[0]) or command[0]
        result=subprocess.run(command,cwd=cwd,capture_output=True,encoding='utf-8',errors='replace',timeout=300)
        (self.work/(name+'.log')).write_text(result.stdout+result.stderr,encoding='utf-8')
        self.commands.append(dict(name=name,command=command,cwd=str(cwd),exitCode=result.returncode));assert result.returncode==0,name+' failed; see '+str(self.work/(name+'.log'))
    def build(self):
        self.command('maven',['mvn','-B','-ntp','clean','verify'],self.project)
        import xml.etree.ElementTree as ET
        suite=ET.parse(self.project/'target/surefire-reports/TEST-example.auth.RuntimeTest.xml').getroot();assert int(suite.attrib['tests'])>=5 and all(int(suite.attrib.get(k,0))==0 for k in ['failures','errors','skipped']);self.unit_counts={k:int(suite.attrib.get(k,0)) for k in ['tests','failures','errors','skipped']}
        with zipfile.ZipFile(self.project/'target/auth-starter.war') as archive:
            names=archive.namelist();assert 'WEB-INF/classes/example/auth/AuthServlet.class' in names
            assert not any('RuntimeTest' in n or 'StarterServer' in n or 'servlet-api' in n or 'junit' in n for n in names)
            libs=[n for n in names if n.startswith('WEB-INF/lib/') and n.endswith('.jar')];assert len(libs)==len(set(libs));self.libraries=sorted(libs)
        self.groups.append('clean exported project build/tests and WAR inspection')
    def start(self):
        env=os.environ.copy();names=self.spec['runtime'];env[names['initializeEnv']]='false' if hasattr(self,'sqlite_path') or hasattr(self,'database') else 'true';env[self.spec['captcha']['secretEnv']]='synthetic-provider-secret'
        if self.item['database']=='sqlite':
            if not hasattr(self,'sqlite_path'):self.sqlite_path=(self.work/'users.db').resolve()
            env[names['databaseEnv']]=str(self.sqlite_path)
        else:
            if not hasattr(self,'database'):
                self.database='b5_'+uuid.uuid4().hex;self.cluster.sql('CREATE DATABASE '+self.database)
            env[names['databaseEnv']]=f'jdbc:postgresql://127.0.0.1:{self.cluster.port}/{self.database}?sslmode=disable';env[names['databaseUserEnv']]='b2';env[names['databasePasswordEnv']]='synthetic-only'
        runtime=ROOT/'scripts/b1/runtime';cp=os.pathsep.join([str(runtime/'target/test-classes'),(runtime/'classpath.txt').read_text().strip()])
        command=[shutil.which('java'),'-cp',cp,'b5.StarterServer',str(self.work),str(self.project/'target/auth-starter.war'),str(self.key)]
        self.runtime_env=env.copy()
        self.log=(self.work/'server.log').open('w',encoding='utf-8');self.process=subprocess.Popen(command,cwd=ROOT,env=env,stdout=self.log,stderr=subprocess.STDOUT)
        self.server_command=dict(name='exported-war',command=command,cwd=str(ROOT),exitCode=None);self.commands.append(self.server_command)
        deadline=time.monotonic()+60
        while not (self.work/'ready.json').exists():
            if self.process.poll() is not None:raise RuntimeError('Exported WAR failed startup: '+str(self.work/'server.log'))
            if time.monotonic()>deadline:raise RuntimeError('Exported WAR readiness timed out: '+str(self.work/'server.log'))
            time.sleep(.2)
        self.versions=json.loads((self.work/'ready.json').read_text())
    def stop(self):
        if hasattr(self,'process'):
            (self.work/'stop').touch()
            try:self.process.wait(timeout=20)
            except subprocess.TimeoutExpired:self.process.terminate();self.process.wait(timeout=10)
            self.server_command['exitCode']=self.process.returncode;self.log.close()
            assert not any(v in (self.work/'server.log').read_text(errors='replace') for v in [PASSWORD,'synthetic-provider-secret','$argon2id$'])
    def browser(self,channel):
        if not self.item['profile']:return
        self.reset_window();env=os.environ.copy();env.update(B5_APP_RUN=str(self.work),B5_APP_SPEC=str(self.project/'auth-spec.json'),PLAYWRIGHT_BROWSERS_PATH=str(ROOT/'.b0/browsers'))
        if channel:env['B0_BROWSER_CHANNEL']=channel
        else:env.pop('B0_BROWSER_CHANNEL',None)
        command=[shutil.which('node'),'tests/b5-application-browser.mjs'];result=subprocess.run(command,cwd=ROOT/'usefultools-frontend',env=env,capture_output=True,text=True,timeout=90)
        (self.work/'browser.log').write_text(result.stdout+result.stderr,encoding='utf-8');self.commands.append(dict(name='https-browser',command=command,cwd=str(ROOT/'usefultools-frontend'),exitCode=result.returncode))
        assert result.returncode==0,'HTTPS browser failed; inspect browser.log';self.groups.append('real browser HTTPS cookie/CSRF/client sequence')
    def startup_failures(self):
        self.stop();names=self.spec['runtime'];cases=[('missing-database',{names['databaseEnv']:None}),('invalid-database',{names['databaseEnv']:'not-a-database'}),('invalid-init',{names['initializeEnv']:'yes'})]
        if self.item['captcha']!='off':cases.append(('missing-captcha-secret',{self.spec['captcha']['secretEnv']:None}))
        if self.item['database']=='postgresql':cases.append(('missing-database-password',{names['databasePasswordEnv']:None}))
        if self.item['database']=='sqlite':
            fresh=str(self.work/'must-initialize.db');foreign=str(self.work/'unrelated.db')
            with sqlite3.connect(foreign) as db:db.execute('CREATE TABLE unrelated(id INTEGER)')
        else:
            fresh_name='b5_'+uuid.uuid4().hex;foreign_name='b5_'+uuid.uuid4().hex;self.cluster.sql('CREATE DATABASE '+fresh_name);self.cluster.sql('CREATE DATABASE '+foreign_name)
            self.cluster.sql('CREATE TABLE unrelated(id INTEGER)',foreign_name)
            fresh=f'jdbc:postgresql://127.0.0.1:{self.cluster.port}/{fresh_name}?sslmode=disable';foreign=f'jdbc:postgresql://127.0.0.1:{self.cluster.port}/{foreign_name}?sslmode=disable'
        cases.extend([('initialization-required',{names['databaseEnv']:fresh,names['initializeEnv']:'false'}),('unrelated-schema',{names['databaseEnv']:foreign,names['initializeEnv']:'true'}),('unsupported-version',{})])
        for name,changes in cases:
            if name=='unsupported-version':
                self.sql('ALTER TABLE auth_schema_version RENAME TO b5_saved_version');self.sql('CREATE TABLE auth_schema_version(version INTEGER)');self.sql('INSERT INTO auth_schema_version(version) VALUES(2)')
            directory=self.work/('startup-'+name);directory.mkdir();env=self.runtime_env.copy()
            for key,value in changes.items():
                if value is None:env.pop(key,None)
                else:env[key]=value
            command=self.server_command['command'].copy();command[4]=str(directory)
            with (directory/'server.log').open('w',encoding='utf-8') as log:
                process=subprocess.Popen(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
                try:
                    deadline=time.monotonic()+45
                    while process.poll() is None and not (directory/'ready.json').exists() and time.monotonic()<deadline:time.sleep(.1)
                    ready=(directory/'ready.json').exists();(directory/'stop').touch()
                    try:code=process.wait(timeout=10)
                    except subprocess.TimeoutExpired:process.terminate();code=process.wait(timeout=10)
                    assert not ready and code!=0,'Invalid startup configuration accepted: '+name
                finally:
                    if process.poll() is None:process.terminate();process.wait(timeout=10)
                    if name=='unsupported-version':self.sql('DROP TABLE auth_schema_version');self.sql('ALTER TABLE b5_saved_version RENAME TO auth_schema_version')
            self.commands.append(dict(name='startup-'+name,command=command,cwd=str(ROOT),exitCode=code,expectedFailure=True))
        assert self.sql("SELECT count(*) FROM auth_users WHERE username='b5_alice'")=='1'
        self.groups.append('actual WAR rejects missing/invalid runtime configuration and unsafe schema initialization/version')
    def control(self,_timeout=10,**values):
        self.control_id+=1;temp=self.work/'control.tmp';temp.write_text(json.dumps(dict(id=self.control_id,**values)));temp.replace(self.work/'control.json');deadline=time.monotonic()+_timeout
        while time.monotonic()<deadline:
            path=self.work/('control-result-'+str(self.control_id)+'.json')
            if path.exists() and json.loads(path.read_text())['id']==self.control_id:return json.loads(path.read_text())
            time.sleep(.03)
        raise RuntimeError('External clock/control timeout')
    def reset_window(self):self.control(advance=60001)
    def sql(self,query):
        if self.item['database']=='postgresql':return self.cluster.sql(query,self.database)
        with sqlite3.connect(self.sqlite_path) as db:
            rows=db.execute(query).fetchall();return '\n'.join('|'.join(map(str,r)) for r in rows)
    def bounded_load(self):
        # Representative DB variants; load is separate from shared parity trace and fault injections.
        if self.item['captcha']!='off' or not self.item['profile']:return
        self.reset_window();c=Client(self).bootstrap();c.login();c.bootstrap()
        for _ in range(10):assert c.call('/api/auth/session')['authenticated']
        self.reset_window();start=time.perf_counter();futures=[];lateness=[]
        def sample():
            began=time.perf_counter();assert c.call('/api/auth/session')['authenticated'];return time.perf_counter()-began
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            for i in range(60):
                delay=start+i/2-time.perf_counter()
                if delay>0:time.sleep(delay)
                if len(futures)>=2:futures[-2].result()
                lateness.append(max(0,time.perf_counter()-(start+i/2)));futures.append(pool.submit(sample))
            latency=sorted(f.result() for f in futures)
        remaining=30-(time.perf_counter()-start)
        if remaining>0:time.sleep(remaining)
        duration=time.perf_counter()-start
        result=dict(status='PASS',operation='authenticated session over actual HTTPS',samples=60,warmups=10,arrivalPerSecond=2,maxInFlight=2,durationSeconds=duration,throughput=60/duration,p50=latency[29],p95=latency[56],p99=latency[59],maxArrivalDelaySeconds=max(lateness),statusCounts={'200':60},environment='local loopback; no staging/auth-latency SLO acceptance')
        (self.work/'auth-performance-results.json').write_text(json.dumps(result,indent=2))
        self.groups.append('B7 generated-auth normal workload:60 authenticated HTTPS requests/30s,2per-second/max2,10warmups; all200')
    def resource_bounds(self):
        self.reset_window();c=Client(self).bootstrap()
        self.control(holdHash=True);c.login(expected=503);time.sleep(2.1);c.login();assert c.call('/api/auth/session')['authenticated']
        self.reset_window();self.control(fillLimiter=True)
        Client(self).call('/api/auth/csrf-token',expected=429)
        self.reset_window();assert c.call('/api/auth/session')['authenticated']
        self.control(fillSessions=True)
        try:Client(self).call('/api/auth/csrf-token',expected=503)
        finally:self.control(clearCapacitySessions=True)
        Client(self).bootstrap();assert c.call('/api/auth/session')['authenticated']
        self.groups.append('B7 actual HTTPS hash2, limiter4096, session4096 saturation and recovery; authenticated session survives')
    def persistence_and_faults(self):
        self.reset_window();c=Client(self).bootstrap();c.login();c.bootstrap()
        hashes=self.sql('SELECT password_hash FROM auth_users ORDER BY username').splitlines();assert len(set(hashes))==len(hashes) and all(x.startswith('$argon2id$v=19$m=19456,t=2,p=1$') for x in hashes)
        original=self.sql("SELECT password_hash FROM auth_users WHERE username='b5_alice'")
        self.sql("UPDATE auth_users SET password_hash='"+'x'*60+"' WHERE username='b5_alice'")
        Client(self).bootstrap().login(expected=401)
        self.sql("UPDATE auth_users SET password_hash='"+original+"' WHERE username='b5_alice'")
        if self.item['profile']:
            before=c.call('/api/user/profile')
            if self.item['database']=='sqlite':self.sql("CREATE TRIGGER b5_reject BEFORE UPDATE OF preferences ON auth_users BEGIN SELECT RAISE(ABORT,'synthetic rollback'); END")
            else:
                self.sql("CREATE FUNCTION b5_reject_fn() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic rollback'; END $$")
                self.sql('CREATE TRIGGER b5_reject BEFORE UPDATE OF preferences ON auth_users FOR EACH ROW EXECUTE FUNCTION b5_reject_fn()')
            c.call('/api/user/profile','PATCH',{'displayName':'must roll back','preferences':{'x':'blocked'}},503)
            if self.item['database']=='sqlite':self.sql('DROP TRIGGER b5_reject')
            else:self.sql('DROP TRIGGER b5_reject ON auth_users');self.sql('DROP FUNCTION b5_reject_fn()')
            assert c.call('/api/user/profile')==before
        self.sql('ALTER TABLE auth_users RENAME TO b5_users_unavailable')
        try:c.call('/api/auth/session',expected=503)
        finally:self.sql('ALTER TABLE b5_users_unavailable RENAME TO auth_users')
        assert c.call('/api/auth/session')['authenticated'];self.groups.append('distinct salts, malformed hash, database errors and transaction rollback')
        old_cookie=c.cookie;prior_work=self.work;self.stop();self.work=prior_work/'restart';self.work.mkdir();self.start()
        replay=Client(self);replay.cookie=old_cookie;assert replay.call('/api/auth/session')=={'authenticated':False}
        replay.bootstrap();replay.login();assert replay.call('/api/auth/session')['authenticated'];self.groups.append('restart invalidates session while durable credentials survive')
    def matrix(self):
        a=Client(self).bootstrap();assert a.call('/api/auth/session')=={'authenticated':False};anonymous=a.cookie;old_token=a.token
        assert a.register()=={'registered':True};assert a.call('/api/auth/session')=={'authenticated':False}
        a.register(expected=409);a.login();assert a.cookie!=anonymous
        assert a.call('/api/auth/session')['user']['role']=='user';old=Client(self);old.cookie=anonymous;assert old.call('/api/auth/session')=={'authenticated':False}
        a.call('/api/auth/logout','POST',{},403,token=old_token);a.bootstrap();assert a.token!=old_token
        a.login(expected=409)
        b=Client(self).bootstrap();b.register('b5_bob');b.login('b5_bob');b.bootstrap()
        if self.item['profile']:
            aid=a.call('/api/user/profile')['id'];bid=b.call('/api/user/profile')['id'];assert aid!=bid
            a.call('/api/user/profile','PATCH',{'displayName':'Alice','preferences':{'theme':'dark'}})
            assert b.call('/api/user/profile')['displayName']==''
            for key in ['id','owner','username','role','password_hash']:a.call('/api/user/profile','PATCH',{key:bid},400)
            a.call('/api/user/profile','PATCH',{'preferences':{'nested':{}}},400)
            a.call('/api/user/profile','PATCH',{'displayName':'wrong token'},403,token=b.token)
            a.call('/api/user/profile?owner='+bid,expected=400)
            assert a.call('/api/user/profile')['displayName']=='Alice'
        else:a.call('/api/user/profile',expected=404)
        self.groups.append('registration, real login, fixation/rotation, optional profile and cross-user assignment')
        c=Client(self).bootstrap()
        wrong=c.credentials('login');wrong['password']='synthetic incorrect password';first=c.call('/api/auth/login','POST',wrong,401)
        second=c.login('b5_missing',401);assert first['error']==second['error']=='INVALID_CREDENTIALS'
        for field in ['id','role','hash','owner']:
            body=c.credentials('register','new_'+field);body[field]='injected';c.call('/api/auth/register','POST',body,400)
        for token in [False,'x'*43]:c.call('/api/auth/login','POST',c.credentials('login'),403,token=token)
        for origin in [False,'https://untrusted.invalid']:c.call('/api/auth/login','POST',c.credentials('login'),403,origin=origin)
        c.call('/api/auth/login','POST',b'{"username":"a","username":"b"}',400)
        c.call('/api/auth/login','POST',b'{bad',400);c.call('/api/auth/login','POST',b' '*16385,413)
        c.call('/api/auth/login','POST',{},415,media='text/plain');c.call('/api/auth/login','PUT',{},405)
        c.call('/api/auth/session;jsessionid=synthetic',expected=400)
        self.groups.append('generic invalid login, mass assignment, strict JSON/media/method/Origin/CSRF')
        self.reset_window()
        c=Client(self).bootstrap();fixtures=json.loads((ROOT/'contracts/backend-support/v0.5.0/parity-fixtures.json').read_text())
        for fixture in fixtures['negativeCases']:
            body=copy.deepcopy(fixture.get('body',{}))
            for key,value in list(body.items()):
                if value=='$PASSWORD':body[key]=PASSWORD
                if value=='$CAPTCHA_LOGIN':
                    if self.item['captcha']=='off':del body[key]
                    else:body[key]='login:'+uuid.uuid4().hex
            if 'raw' in fixture:body=fixture['raw'].encode()
            result=c.call(fixture['path'],fixture['method'],body,fixture['status'],token=fixture.get('csrf',True),origin=fixture.get('origin',True),media=fixture.get('media','application/json'))
            assert result['error']==fixture['error'],fixture['id']
        self.groups.append('shared framework-neutral B6 negative fixtures')
        self.reset_window()
        # Two independently bootstrapped anonymous sessions register the same username concurrently.
        clients=[Client(self).bootstrap(),Client(self).bootstrap()]
        def duplicate(client):
            body=client.credentials('register','b5_race')
            # The helper captures status explicitly here; neither expected outcome is silently swallowed.
            connection=http.client.HTTPSConnection('127.0.0.1',self.item['port'],context=ssl._create_unverified_context(),timeout=20)
            try:
                connection.request('POST','/api/auth/register',json.dumps(body),{'Content-Type':'application/json','Origin':self.origin,'Cookie':'JSESSIONID='+client.cookie,'X-CSRF-Token':client.token})
                response=connection.getresponse();value=json.loads(response.read());self.validate_response('/api/auth/register','POST',response.status,value);return response.status
            finally:connection.close()
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:assert sorted(executor.map(duplicate,clients))==[201,409]
        assert self.sql("SELECT count(*) FROM auth_users WHERE username='b5_race'")=='1'
        assert self.sql("SELECT count(*) FROM auth_users WHERE role<>'user'")=='0';self.groups.append('database unique concurrency and persistence')
        self.reset_window();c=Client(self).bootstrap()
        if self.item['captcha']!='off':
            for index,(variant,status) in enumerate([('wrong-action',403),('wrong-host',403),('low-score',403),('expired',403),('future',403),('wrong-type',403),('rejected',403),('malformed',403),('timeout',503),('unavailable',503),('redirect',503),('oversize',503)]):
                if index==10:self.reset_window()
                token=variant if status==503 else 'login:'+variant
                c.call('/api/auth/login','POST',c.credentials('login','b5_alice',token),status)
            self.reset_window();replay='login:replay'
            c.call('/api/auth/login','POST',c.credentials('login','b5_alice',replay));c.bootstrap();c.call('/api/auth/logout','POST',{},204);c.bootstrap()
            c.call('/api/auth/login','POST',c.credentials('login','b5_alice',replay),403)
            self.groups.append('CAPTCHA actual transport/form/redirect/size and valid/action/host/score/age/type/replay/rejection/malformed/timeout/unavailable')
        else:
            body=c.credentials('login');body['captchaToken']='not-allowed';c.call('/api/auth/login','POST',body,400)
            assert self.control()['providerCalls']==0;self.groups.append('CAPTCHA explicit off and no provider requests')
        self.reset_window();c=Client(self).bootstrap()
        for i in range(10):c.login('rate_account',401)
        c.login('rate_account',429);assert int(self.last_headers['Retry-After'])>0
        self.reset_window();c.login('rate_account',401);self.groups.append('account throttling Retry-After and clock reset')
        self.reset_window()
        for i in range(30):Client(self).bootstrap().login('rate_user_'+str(i),401)
        Client(self).bootstrap().login('rate_user_overflow',429)
        self.reset_window();c=Client(self).bootstrap();c.login();c.bootstrap()
        for _ in range(120):assert c.call('/api/auth/session')['authenticated']
        c.call('/api/auth/session',expected=429);self.reset_window();assert c.call('/api/auth/session')['authenticated']
        self.groups.append('address throttle survives fresh sessions and authenticated route limit resets')
        self.reset_window();c=Client(self).bootstrap();c.login();c.bootstrap();old_cookie=c.cookie
        self.control(advance=self.spec['session']['idleSeconds']*1000+1);assert c.call('/api/auth/session')=={'authenticated':False}
        c.call('/api/auth/logout','POST',{},204);c.call('/api/auth/logout','POST',{},204)
        c.bootstrap();c.login();c.bootstrap()
        # Touch below idle threshold until absolute lifetime expires.
        step=(self.spec['session']['idleSeconds']-1)*1000;elapsed=0
        while elapsed+step<self.spec['session']['absoluteSeconds']*1000:
            self.control(advance=step);elapsed+=step;assert c.call('/api/auth/session')['authenticated']
        self.control(advance=self.spec['session']['absoluteSeconds']*1000-elapsed+1);assert c.call('/api/auth/session')=={'authenticated':False}
        c.bootstrap();c.login();c.bootstrap();old_cookie=c.cookie;c.call('/api/auth/logout','POST',{},204)
        replay=Client(self);replay.cookie=old_cookie;assert replay.call('/api/auth/session')=={'authenticated':False};replay.call('/api/auth/logout','POST',{},204)
        c.bootstrap();self.control(clearCsrf=True);c.call('/api/auth/login','POST',c.credentials('login'),403);c.call('/api/auth/csrf-token',expected=403)
        self.groups.append('idle/absolute expiry, logout invalidation/replay/idempotency and missing stored CSRF')
        self.versions['inspectionClientDatabase']=self.sql('SELECT version()') if self.item['database']=='postgresql' else self.sql('SELECT sqlite_version()')
def execute(args):
    run=args.run.resolve();assert run.is_relative_to(ROOT/'.b5');run.mkdir(parents=True,exist_ok=True)
    exports=json.loads((args.exports/'export-results.json').read_text());assert exports['status']=='PASS'
    key=run/'https.p12';key_command=[shutil.which('keytool'),'-genkeypair','-alias','b5','-keyalg','RSA','-storetype','PKCS12','-keystore',str(key),'-storepass','b5-synthetic-only','-keypass','b5-synthetic-only','-dname','CN=localhost','-ext','SAN=dns:localhost,ip:127.0.0.1','-validity','2','-noprompt']
    subprocess.run(key_command,check=True,capture_output=True)
    result={'status':'FAIL','diagnostic':bool(args.only or args.no_openapi),'applications':[]}
    try:
        with Cluster(run/'postgres') as cluster:
            for item in exports['exports']:
                if args.only and item['name']!=args.only:continue
                app=Application(run,item,cluster,key,not args.no_openapi);record={'name':item['name'],'status':'FAIL'};result['applications'].append(record)
                try:
                    app.build();app.start();app.matrix();app.resource_bounds();app.bounded_load();app.persistence_and_faults();app.browser(args.browser_channel);app.startup_failures();record.update(status='PASS',groups=app.groups,requests=app.count,versions=app.versions,statuses=sorted(app.statuses),unitTests=app.unit_counts,warLibraries=app.libraries)
                finally:app.stop();record['commands']=app.commands
        result['status']='PASS'
    finally:
        key.unlink(missing_ok=True);result['ephemeralTlsRemoved']=not key.exists()
        (run/'application-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print('PASS exported applications',len(result['applications']),flush=True)
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('exports',type=Path);parser.add_argument('run',type=Path);parser.add_argument('--only');parser.add_argument('--browser-channel',choices=['chrome','msedge']);parser.add_argument('--no-openapi',action='store_true',help='Diagnostic only, never certification');execute(parser.parse_args())
