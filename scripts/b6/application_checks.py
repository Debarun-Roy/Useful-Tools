"""Real Python exports using the same B5 application matrix and browser assertions."""
import argparse,hashlib,http.client,importlib.util,json,os,re,shutil,ssl,subprocess,sys,time,uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/b2'))
from postgres import Cluster
from redis_service import Server as RedisServer
from shared_parity import extra_matrix
module_spec=importlib.util.spec_from_file_location('b5_application_checks',ROOT/'scripts/b5/application_checks.py')
b5=importlib.util.module_from_spec(module_spec);module_spec.loader.exec_module(b5)

def normalized(value):
    if isinstance(value,list):return [normalized(v) for v in value]
    if isinstance(value,dict):
        result={}
        for key,item in value.items():
            if key in ('requestId','id'):
                assert str(uuid.UUID(item))==item
                result[key]='<uuid>'
            elif key=='csrfToken':
                assert re.fullmatch('[A-Za-z0-9_-]{43}',item)
                result[key]='<csrf>'
            else:result[key]=normalized(item)
        return result
    return value

class SharedClient(b5.Client):
    def call(self,path,method='GET',body=None,expected=200,**kwargs):
        value=super().call(path,method,body,expected,**kwargs)
        if hasattr(self.app,'trace'):
            trace_path=re.sub(r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}',lambda m: '<uuid>' if str(uuid.UUID(m[0]))==m[0] else m[0],path)
            self.app.trace.append({'path':trace_path,'method':method,'status':expected,'body':normalized(value)})
        return value
b5.Client=SharedClient

class PythonApplication(b5.Application):
    def __init__(self,run,item,cluster,redis,cert,key):
        super().__init__(run,item,cluster,key,True)
        self.redis,self.cert,self.trace=redis,cert,[]
        self.venv=self.work/'venv';self.py=self.venv/'Scripts/python.exe'
    def build(self):
        self.command('venv',[sys.executable,'-m','venv',self.venv],ROOT)
        self.command('install',[self.py,'-m','pip','install','--no-index','--find-links',self.run/'wheels','-r',self.project/'requirements.txt'],self.project)
        self.command('pip-check',[self.py,'-m','pip','check'],self.project)
        self.command('inventory',[self.py,'-m','pip','freeze'],self.project)
        self.command('import',[self.py,'-c',f"import {self.spec['pythonModule']}.app"],self.project)
        self.command('unit-tests',[self.py,'-m','pytest','-q','--basetemp='+str(self.work/'pytest-temp'),'--junitxml='+str(self.work/'unit-tests.xml')],self.project)
        import xml.etree.ElementTree as ET
        suite=ET.parse(self.work/'unit-tests.xml').getroot().find('testsuite');self.unit_counts={k:int(suite.attrib.get(k,0)) for k in ['tests','failures','errors','skipped']}
        assert self.unit_counts['tests']>=5 and all(self.unit_counts[k]==0 for k in ['failures','errors','skipped'])
        self.groups.append('fresh exported Python environment, pinned install, pip check, import and unit tests')
    def start(self):
        env=os.environ.copy();names=self.spec['runtime'];env[names['initializeEnv']]='false' if hasattr(self,'sqlite_path') or hasattr(self,'database') else 'true'
        env[self.spec['captcha']['secretEnv']]='synthetic-provider-secret';env[names['redisEnv']]=f'redis://127.0.0.1:{self.redis.port}/0';env['WEB_CONCURRENCY']='1'
        if self.item['database']=='sqlite':
            if not hasattr(self,'sqlite_path'):self.sqlite_path=(self.work/'users.db').resolve()
            env[names['databaseEnv']]=str(self.sqlite_path)
        else:
            if not hasattr(self,'database'):self.database='b6_'+uuid.uuid4().hex;self.cluster.sql('CREATE DATABASE '+self.database)
            env[names['databaseEnv']]=f'postgresql://127.0.0.1:{self.cluster.port}/{self.database}?sslmode=disable';env[names['databaseUserEnv']]='b2';env[names['databasePasswordEnv']]='synthetic-only'
        self.runtime_env=env.copy()
        command=[str(self.py),str(ROOT/'scripts/b6/app_server.py'),str(self.work),str(self.project),str(self.cert),str(self.key)]
        self.log=(self.work/'server.log').open('w',encoding='utf-8');self.process=subprocess.Popen(command,cwd=ROOT,env=env,stdout=self.log,stderr=subprocess.STDOUT)
        self.server_command=dict(name='exported-asgi',command=command,cwd=str(ROOT),exitCode=None);self.commands.append(self.server_command)
        deadline=time.monotonic()+60
        while not (self.work/'ready.json').exists():
            if self.process.poll() is not None:raise RuntimeError('Exported ASGI failed startup; see '+str(self.work/'server.log'))
            if time.monotonic()>deadline:raise RuntimeError('Exported ASGI readiness timeout')
            time.sleep(.1)
        self.versions=json.loads((self.work/'ready.json').read_text())
    def startup_failures(self):
        self.stop();names=self.spec['runtime'];cases=[('missing-database',{names['databaseEnv']:None}),('invalid-database',{names['databaseEnv']:'not-a-database'}),('invalid-initialize',{names['initializeEnv']:'invalid'}),('missing-redis',{names['redisEnv']:None}),('unavailable-redis',{names['redisEnv']:'redis://127.0.0.1:1/0'}),('multiple-workers',{'WEB_CONCURRENCY':'2'})]
        if self.item['captcha']!='off':cases.append(('missing-captcha',{self.spec['captcha']['secretEnv']:None}))
        if self.item['database']=='postgresql':
            cases.append(('missing-database-password',{names['databasePasswordEnv']:None}))
            fresh='b6_'+uuid.uuid4().hex;foreign='b6_'+uuid.uuid4().hex
            self.cluster.sql('CREATE DATABASE '+fresh);self.cluster.sql('CREATE DATABASE '+foreign)
            self.cluster.sql('CREATE TABLE unrelated(id INTEGER)',foreign)
            cases.extend([('initialization-required',{names['databaseEnv']:f'postgresql://127.0.0.1:{self.cluster.port}/{fresh}?sslmode=disable',names['initializeEnv']:'false'}),('unrelated-schema',{names['databaseEnv']:f'postgresql://127.0.0.1:{self.cluster.port}/{foreign}?sslmode=disable',names['initializeEnv']:'true'})])
        else:
            foreign=str(self.work/'foreign.db');import sqlite3
            with sqlite3.connect(foreign) as db:db.execute('CREATE TABLE unrelated(id INTEGER)')
            cases.extend([('initialization-required',{names['databaseEnv']:str(self.work/'empty.db'),names['initializeEnv']:'false'}),('unrelated-schema',{names['databaseEnv']:foreign,names['initializeEnv']:'true'})])
        cases.append(('unsupported-version',{}))
        for name,changes in cases:
            if name=='unsupported-version':self.sql('ALTER TABLE auth_schema_version RENAME TO b6_saved_version');self.sql('CREATE TABLE auth_schema_version(version INTEGER)');self.sql('INSERT INTO auth_schema_version(version) VALUES(2)')
            directory=self.work/('startup-'+name);directory.mkdir();env=self.runtime_env.copy()
            for k,v in changes.items():
                if v is None:env.pop(k,None)
                else:env[k]=v
            command=self.server_command['command'].copy();command[2]=str(directory)
            try:
                with (directory/'server.log').open('w') as log:
                    process=subprocess.Popen(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
                    try:code=process.wait(timeout=35)
                    except subprocess.TimeoutExpired:
                        (directory/'stop').touch();process.wait(timeout=15);raise AssertionError('Invalid startup accepted: '+name)
                assert code!=0 and not (directory/'ready.json').exists(),name
                self.commands.append(dict(name='startup-'+name,command=command,cwd=str(ROOT),exitCode=code,expectedFailure=True))
            finally:
                if name=='unsupported-version':self.sql('DROP TABLE auth_schema_version');self.sql('ALTER TABLE b6_saved_version RENAME TO auth_schema_version')
        self.groups.append('actual ASGI rejects invalid runtime/store/worker/schema startup')
    def boundary_checks(self):
        command=[str(self.py),'-X','utf8',str(ROOT/'scripts/b6/boundary_checks.py'),str(self.project)]
        completed=subprocess.run(command,cwd=ROOT,env=self.runtime_env,capture_output=True,text=True,timeout=90)
        (self.work/'boundary.log').write_text(completed.stdout+completed.stderr)
        self.commands.append(dict(name='ASGI-boundaries',command=command,cwd=str(ROOT),exitCode=completed.returncode))
        assert completed.returncode==0,'ASGI boundary checks failed'
        self.groups.append('actual exported ASGI root path, forwarded spoof, development boundary and misleading/missing length checks')
    def store_checks(self):
        self.reset_window();client=SharedClient(self).bootstrap();anonymous=client.cookie;client.login();client.bootstrap()
        assert not self.control(inspectCookie=anonymous)['recordPresent']
        stored=self.control(inspectCookie=client.cookie);assert stored['recordKeys']==['born','csrf','last','userId']
        authenticated=client.cookie;client.call('/api/auth/logout','POST',{},204)
        assert not self.control(inspectCookie=authenticated)['recordPresent']
        client.bootstrap();client.login();client.bootstrap()
        state=self.control(inspectSessions=True)
        assert state['liveRecords']>0 and all(0<t<=self.spec['session']['idleSeconds'] for t in state['ttls'])
        self.control(expireSessions=True);time.sleep(1.15)
        assert client.call('/api/auth/session')=={'authenticated':False}
        client.bootstrap();client.login();client.bootstrap();old=client.cookie
        self.redis.__exit__()
        try:client.call('/api/auth/session',expected=503)
        finally:self.redis.__enter__()
        assert client.call('/api/auth/session')=={'authenticated':False}
        client.bootstrap();client.login();client.bootstrap();assert client.call('/api/auth/session')['authenticated']
        assert client.cookie!=old
        self.groups.append('live Redis serialization/deletion/rotation/TTL, store outage fails closed, Redis restart revokes sessions and service recovers')
        client.call('/api/auth/logout','POST',{},204);client.bootstrap()
        assert self.control(holdHash=True)['hashSlots']==2
        assert client.login(expected=503)['error']=='AUTH_BUSY'
        time.sleep(2.1);client.login();client.bootstrap()
        assert self.control(holdDatabase=True)['databaseSlots']==8
        client.call('/api/auth/session',expected=503)
        time.sleep(2.1);assert client.call('/api/auth/session')['authenticated']
        self.control(pauseRedis=True)
        began=time.monotonic()
        connection=http.client.HTTPSConnection('127.0.0.1',self.item['port'],timeout=15,context=ssl._create_unverified_context())
        try:
            connection.request('GET','/api/auth/session',headers={'Cookie':'JSESSIONID='+client.cookie})
            response=connection.getresponse();body=json.loads(response.read());elapsed=time.monotonic()-began
            assert response.status in (200,503) and elapsed<12
            if response.status==200:assert body['authenticated'] is True
            else:assert set(body)=={'error','requestId'}
        finally:connection.close()
        time.sleep(3.1);assert client.call('/api/auth/session')['authenticated']
        self.groups.append('B7 actual HTTPS DB8 saturation/recovery and real Redis CLIENT PAUSE latency bounded below12s with recovery')
        self.control(advance=self.spec['session']['absoluteSeconds']*1000+1)
        assert self.control(_timeout=60,fillSessions=True)['capacityReached']==4096
        SharedClient(self).call('/api/auth/csrf-token',expected=503)
        self.control(advance=self.spec['session']['absoluteSeconds']*1000+1)
        SharedClient(self).bootstrap()
        self.reset_window();assert self.control(fillLimiter=True)['limiterKeys']==4096
        SharedClient(self).call('/api/auth/session',expected=429)
        self.reset_window();assert SharedClient(self).call('/api/auth/session')=={'authenticated':False}
        self.groups.append('actual HTTPS hash worker/session/limiter capacity failures and recovery')

def execute(args):
    run=args.run.resolve();assert run.is_relative_to(ROOT/'.b6');run.mkdir(parents=True)
    exports=json.loads((args.exports/'export-results.json').read_text());assert exports['status']=='PASS'
    java_exports=json.loads((args.java_exports/'export-results.json').read_text()) if args.java_exports else None
    if java_exports:assert java_exports['status']=='PASS' and len(java_exports['exports'])==8
    cert,key=run/'cert.pem',run/'key.pem';openssl=shutil.which('openssl') or r'C:\Program Files\Git\usr\bin\openssl.exe'
    tls_command=[openssl,'req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(key),'-out',str(cert),'-days','2','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost,IP:127.0.0.1']
    subprocess.run(tls_command,check=True,capture_output=True)
    java_key=run/'java-https.p12'
    if java_exports:
        subprocess.run([shutil.which('keytool'),'-genkeypair','-alias','b5','-keyalg','RSA','-storetype','PKCS12','-keystore',str(java_key),'-storepass','b5-synthetic-only','-keypass','b5-synthetic-only','-dname','CN=localhost','-ext','SAN=dns:localhost,ip:127.0.0.1','-validity','2','-noprompt'],check=True,capture_output=True)
    result={'status':'FAIL','diagnostic':bool(args.only or not java_exports),'applications':[],'tlsCommand':tls_command}
    try:
        requirements=[(ROOT/item['directory']/'requirements.txt').read_bytes() for item in exports['exports']]
        assert len(set(requirements))==1,'Variant dependencies drifted'
        wheels=run/'wheels';wheels.mkdir()
        download=[sys.executable,'-m','pip','download','--only-binary=:all:','-r',str(ROOT/exports['exports'][0]['directory']/'requirements.txt'),'--dest',str(wheels)]
        if args.wheelhouse:download+=['--no-index','--find-links',str(args.wheelhouse.resolve())]
        install=subprocess.run(download,capture_output=True,text=True,timeout=300)
        (run/'download.log').write_text(install.stdout+install.stderr)
        result['download']={'command':download,'cwd':str(ROOT),'exitCode':install.returncode}
        if install.returncode:raise RuntimeError('BLOCKED: pinned wheel provisioning failed; see download.log')
        result['wheels']={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(wheels.glob('*.whl'))}
        with Cluster(run/'postgres') as cluster,RedisServer(run/'redis') as redis:
            for item in exports['exports']:
                if args.only and item['name']!=args.only:continue
                print('CHECK Python',item['name'],flush=True);app=PythonApplication(run,item,cluster,redis,cert,key);record={'name':item['name'],'status':'FAIL'};result['applications'].append(record)
                try:
                    app.build();app.start();app.matrix();extra_matrix(app,SharedClient);app.persistence_and_faults();app.browser(args.browser_channel)
                    shared_trace=app.trace.copy();app.bounded_load();app.store_checks();app.boundary_checks();app.startup_failures()
                    record.update(status='PASS',groups=app.groups,requests=app.count,versions=app.versions,unitTests=app.unit_counts,trace=shared_trace,exportSha256=item['zipSha256'])
                    print('PASS Python',item['name'],flush=True)
                finally:app.stop();record['commands']=app.commands
                if java_exports:
                    java_item=next(j for j in java_exports['exports'] if j['name']==item['name'])
                    java=b5.Application(run/'java',java_item,cluster,java_key,True);java.trace=[]
                    try:
                        java.build();java.start();java.matrix();extra_matrix(java,SharedClient);java.persistence_and_faults();java.browser(args.browser_channel)
                        record['java']={'status':'PASS','trace':java.trace,'requests':java.count,'versions':java.versions,'groups':java.groups,'commands':java.commands,'exportSha256':java_item['zipSha256']}
                        differences=[{'index':i,'python':p,'java':j} for i,(p,j) in enumerate(zip(shared_trace,java.trace)) if p!=j]
                        assert len(shared_trace)==len(java.trace),('trace lengths',len(shared_trace),len(java.trace))
                        assert not differences,('parity mismatch',differences[:3])
                        record['parity']={'status':'PASS','identicalResponses':len(shared_trace),'normalization':'validated UUIDs and 43-character CSRF only'}
                        print('PASS Java/Python parity',item['name'],flush=True)
                    finally:java.stop()
        result['redis']={'command':redis.command,'cwd':str(redis.directory),'exitCode':redis.process.returncode,'archiveSha256':__import__('redis_service').SHA256}
        result['status']='PASS'
    except Exception as error:
        result['failure']=str(error)
        if str(error).startswith('BLOCKED:'):result['status']='BLOCKED'
        raise
    finally:
        for secret in [key,cert,java_key]:secret.unlink(missing_ok=True)
        result['ephemeralTlsRemoved']=all(not p.exists() for p in [key,cert,java_key])
        (run/'application-results.json').write_text(json.dumps(result,indent=2)+'\n')
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('exports',type=Path);parser.add_argument('run',type=Path);parser.add_argument('--only');parser.add_argument('--wheelhouse',type=Path);parser.add_argument('--java-exports',type=Path);parser.add_argument('--browser-channel',choices=['chrome','msedge']);execute(parser.parse_args())
