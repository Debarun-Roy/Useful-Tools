"""B7 local actual-WAR acceptance, bounded workload and recovery; synthetic data only."""
import argparse,copy,hashlib,http.client,io,json,os,platform,shutil,socket,sqlite3,subprocess,sys,time,zipfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.parse import urlsplit
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/b2'))
from fixtures import twenty_tables
parser=argparse.ArgumentParser();parser.add_argument('run');parser.add_argument('--browser-channel',default='chrome');parser.add_argument('--only',choices=['browser'],help='Diagnostic subset only; never certification');args=parser.parse_args()
run=Path(args.run).resolve();assert run.is_relative_to(ROOT/'.b7');run.mkdir(parents=True,exist_ok=True)
fixture=ROOT/'.b6'/('b7-'+hashlib.sha256(str(run).encode()).hexdigest()[:16]);fixture.mkdir();checks=[];commands=[];sessions={};counter=0
runtime=ROOT/'scripts/b1/runtime';cp=os.pathsep.join([str(runtime/'target/test-classes'),(runtime/'classpath.txt').read_text().strip()])
env=os.environ.copy();env.update(SQLITE_DB_PATH=str(fixture/'application.db'),SQLITE_DB_URL='',RECAPTCHA_SECRET_KEY='',JAVA_TOOL_OPTIONS='-Xms32m -Xmx512m',B7_RUN=str(run),B7_FIXTURE=str(fixture),B0_BROWSER_CHANNEL=args.browser_channel)
command=[shutil.which('java'),'-cp',cp,'b1.Server',str(fixture),str(ROOT/'Useful-Tools/target/UsefulTools.war'),str(ROOT/'usefultools-frontend/dist')]

def call(body,endpoint='generate',role='restfixture0',expected=None):
 u=urlsplit(sessions['origin']);i=sessions[role];c=http.client.HTTPConnection(u.hostname,u.port,timeout=20);start=time.perf_counter()
 try:
  c.request('POST','/api/backend-support/'+endpoint,body if isinstance(body,bytes) else json.dumps(body).encode(),{'Content-Type':'application/json','Origin':sessions['origin'],'Cookie':'JSESSIONID='+i['session'],'X-XSRF-TOKEN':i['csrf']})
  r=c.getresponse();raw=r.read();status=r.status;elapsed=time.perf_counter()-start
  assert r.getheader('Cache-Control')=='no-store'
  if expected is not None:assert status==expected,(endpoint,status,expected)
  assert len(raw)<=32*1024*1024
  return status,raw,elapsed
 finally:c.close()

def export(spec,name):
 global counter
 role='etlfixture'+str(counter%20);counter+=1
 _,raw,_=call({'request':spec},role=role,expected=200);preview=json.loads(raw)['data'];body={'request':spec,'expectedDigest':preview['digest']}
 _,raw,_=call(body,'export',role,200);assert raw==call(body,'export',role,200)[1]
 dest=run/'bundles'/name;dest.mkdir(parents=True)
 with zipfile.ZipFile(io.BytesIO(raw)) as z:
  names=z.namelist();assert names==sorted(names) and len(names)<=100 and sum(i.file_size for i in z.infolist())<=5*1024*1024
  assert len({n.casefold() for n in names})==len(names)
  for n in names:
   p=(dest/n).resolve();assert p.is_relative_to(dest.resolve()) and '\\' not in n and not n.startswith('/');p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n))
  for f in preview['files']:assert z.read(f['path'])==f['content'].encode()
  for f in preview['manifest']['core']['files']:assert hashlib.sha256(z.read(f['path'])).hexdigest()==f['sha256']
 (run/(name+'-artifact.json')).write_text(json.dumps({'zipSha256':hashlib.sha256(raw).hexdigest(),'digest':preview['digest'],'files':preview['manifest']['core']['files']},indent=2))
 return dest

class Blocked(RuntimeError):pass

def verdict():
 return 'FAIL' if any(c['status']=='FAIL' for c in checks) else 'BLOCKED' if any(c['status']=='BLOCKED' for c in checks) else 'PASS'

def check(name,fn):
 try:detail=fn();checks.append(dict(name=name,status='PASS',detail=detail));print('PASS',name,flush=True)
 except (Blocked,subprocess.TimeoutExpired) as e:checks.append(dict(name=name,status='BLOCKED',detail=type(e).__name__+': '+str(e)));print('BLOCKED',name,str(e),flush=True)
 except Exception as e:checks.append(dict(name=name,status='FAIL',detail=type(e).__name__+': '+str(e)));print('FAIL',name,str(e),flush=True)
 (run/'local-results.json').write_text(json.dumps(dict(status=verdict(),diagnostic=bool(args.only),checks=checks,commands=commands),indent=2))

def integration():
 # Existing B3/B4 suites exercise both engines, exported procedures and rollback.
 # This additional journey combines current exported schema, ETL and view in one SQLite DB.
 etl=json.loads((ROOT/'contracts/backend-support/v0.4.0/etl-sample.json').read_text());etl['source']['format']='json'
 schema=export(etl['schema'],'journey-schema');bundle=export(etl,'journey-etl')
 view=json.loads((ROOT/'contracts/backend-support/v0.3.0/view-sample.json').read_text());view.update(schema=etl['schema'],name='item_totals',source={'tableId':'items','alias':'i'},joins=[],projections=[{'expression':{'kind':'aggregate','function':'count','argument':None},'name':'rows'}],groupBy=[]);view['where']=None
 vb=export(view,'journey-view')
 work=run/'journey';work.mkdir();dbpath=work/'database.db'
 with sqlite3.connect(dbpath) as db:db.executescript((schema/'schema.sql').read_text())
 rows=[dict(id=i,name='synthetic '+str(i),amount='12.30',active=True,day='2026-10-04',instant='2026-10-04T00:00:00Z',metadata={},parent_id=None) for i in range(1,1001)]
 # Generated default source path is input.csv even when selected JSON; inspect configuration.
 config=json.loads((bundle/'configuration.example.json').read_text());(run/'etl-config-shape.json').write_text(json.dumps({'keys':list(config)},indent=2))
 source=work/'input.json';source.write_text(json.dumps(rows));cfg=copy.deepcopy(config)
 def replace(value):
  if isinstance(value,dict):
   for k,v in value.items():
    if k=='path' and isinstance(v,str):value[k]=str(source)
    else:replace(v)
 replace(cfg)
 # CLI accepts explicit source path; copied config retains reviewed generated destination.
 (work/'configuration.json').write_text(json.dumps(cfg));environment=env.copy();environment['UT_ETL_CONNECTION']=str(dbpath)
 for dry in [True,False]:
  cmd=[sys.executable,str(bundle/'etl.py'),'--source',str(source),'--checkpoint',str(work/'checkpoint.json'),'--reject-report',str(work/'rejects.jsonl')]+(['--dry-run'] if dry else [])
  start=time.perf_counter();p=subprocess.run(cmd,cwd=work,env=environment,capture_output=True,text=True,timeout=120);commands.append(dict(command=cmd,cwd=str(work),exitCode=p.returncode));assert p.returncode==0,p.stderr[:300]+p.stdout[:300]
  with sqlite3.connect(dbpath) as db:assert db.execute('SELECT count(*) FROM items').fetchone()[0]==(0 if dry else 1000)
  if not dry:etl_elapsed=time.perf_counter()-start
 with sqlite3.connect(dbpath) as db:
  db.executescript((vb/'view.sql').read_text());assert db.execute('SELECT rows FROM item_totals').fetchone()==(1000,)
  backup=sqlite3.connect(work/'backup.db');db.backup(backup);backup.close();db.execute('DELETE FROM items');db.commit();assert db.execute('SELECT count(*) FROM items').fetchone()==(0,)
  with sqlite3.connect(work/'backup.db') as backup:backup.backup(db)
  assert db.execute('SELECT rows FROM item_totals').fetchone()==(1000,)
 return dict(rows=1000,etlSeconds=etl_elapsed,backupRestore='PASS',database=sqlite3.sqlite_version)

def performance():
 spec=twenty_tables();digest=json.loads(call({'request':spec},role='restfixture1',expected=200)[1])['data']['digest'];phases={}
 roles=['restfixture'+str(i) for i in range(20)]+['etlfixture'+str(i) for i in range(20)]
 def metrics():
  cmd=[str(Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/powershell.exe'),'-NoProfile','-File',str(ROOT/'scripts/b7/metrics.ps1'),'-TargetProcessId',str(process.pid)]
  result=subprocess.run(cmd,capture_output=True,text=True,timeout=30)
  commands.append(dict(command=cmd,cwd=str(ROOT),exitCode=result.returncode))
  assert result.returncode==0,'Host metrics unavailable'
  return json.loads(result.stdout)
 for endpoint in ['generate','export']:

  body={'request':spec};
  if endpoint=='export':body['expectedDigest']=digest
  for i in range(10):call(body,endpoint,roles[i],200)
  before=metrics();start=time.perf_counter();futures=[];lateness=[]
  with ThreadPoolExecutor(max_workers=2) as pool:
   for i in range(120):
    delay=start+i/4-time.perf_counter()
    if delay>0:time.sleep(delay)
    if len(futures)>=2:futures[-2].result()
    lateness.append(max(0,time.perf_counter()-(start+i/4)));futures.append(pool.submit(call,body,endpoint,roles[i%40]))
   results=[f.result() for f in futures]
  remaining=30-(time.perf_counter()-start)
  if remaining>0:time.sleep(remaining)
  seconds=time.perf_counter()-start;times=sorted(r[2] for r in results);statuses={str(s):sum(r[0]==s for r in results) for s in set(r[0] for r in results)}
  phases[endpoint]=dict(maxArrivalDelaySeconds=max(lateness),samples=120,durationSeconds=seconds,throughput=120/seconds,p50=times[59],p95=times[113],p99=times[118],statuses=statuses,proposedTargetPass=times[113]<2,before=before,after=metrics())
  (run/'performance-results.json').write_text(json.dumps(dict(environment='local Windows loopback; not staging',phases=phases),indent=2))
 assert all(p['statuses']=={'200':120} and p['proposedTargetPass'] for p in phases.values()),phases
 return phases

def maximum_specs():
 spec=twenty_tables();base=copy.deepcopy(spec['tables'][0]);spec['tables']=[]
 for i in range(100):
  t=copy.deepcopy(base);t['id']=t['name']=f'max_{i:03}'
  while len(t['columns'])<10:
   j=len(t['columns']);t['columns'].append(dict(id=f'field_{j}',name=f'field_{j}',type='text',nullable=True))
  spec['tables'].append(t)
 export(spec,'maximum-schema')
 bad=copy.deepcopy(spec);extra=copy.deepcopy(bad['tables'][0]);extra['id']=extra['name']='max_overflow';bad['tables'].append(extra);_,rejected,_=call({'request':bad},role='restfixture18',expected=422);assert b'/tables' in rejected
 call({'request':spec,'expectedDigest':'0'*64},'export','restfixture18',409)
 return '100 tables/1000 columns actual deterministic ZIP; oversized and stale export rejected'

def overload():
 spec=twenty_tables();u=urlsplit(sessions['origin']);sockets=[]
 try:
  i=sessions['boundary']
  for _ in range(2):
   s=socket.create_connection((u.hostname,u.port),timeout=5);sockets.append(s);s.sendall((f"POST /api/backend-support/generate HTTP/1.1\r\nHost: {u.netloc}\r\nOrigin: {sessions['origin']}\r\nCookie: JSESSIONID={i['session']}\r\nX-XSRF-TOKEN: {i['csrf']}\r\nContent-Type: application/json\r\nContent-Length: 1000\r\n\r\n{{").encode())
  time.sleep(.5);call({'request':spec},role='negative',expected=503)
 finally:
  for s in sockets:s.close()
 deadline=time.monotonic()+15
 while True:
  status,_,_=call({'request':spec},role='negative')
  if status==200:break
  assert status==503 and time.monotonic()<deadline;time.sleep(.5)
 call(b' '*1048577,role='negative',expected=413);call(b'{"request":{},"request":{}}',role='negative',expected=400)
 for n in range(40):call({'request':{}},'generate' if n%2==0 else 'export',role='rate',expected=422 if n%2==0 else 400)
 call({'request':spec},role='rate',expected=429)
 return 'Two slots reject503; disconnect frees capacity; shared endpoint throttle429; bounded400/413'

with (fixture/'server.log').open('w',encoding='utf-8') as log:
 process=subprocess.Popen(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
 try:
  deadline=time.monotonic()+90
  while not (fixture/'sessions.json').exists():
   assert process.poll() is None and time.monotonic()<deadline,'Server readiness';time.sleep(.2)
  sessions=json.loads((fixture/'sessions.json').read_text())
  with sqlite3.connect(fixture/'application.db') as db:db.execute("UPDATE tool_toggles SET enabled=1 WHERE tool_path='/backend-support'")
  if not args.only:
   check('schema-etl-view-backup-restore',integration);check('generator-performance',performance);check('maximum-specifications',maximum_specs);check('overload-disconnect-recovery',overload)
  p=subprocess.run([shutil.which('node'),str(ROOT/'scripts/b7/browser.mjs')],cwd=ROOT,env=env,capture_output=True,text=True,timeout=600);(run/'browser.log').write_text(p.stdout+p.stderr)
  def browser_result():
   failure=run/'browser-failure.json'
   if p.returncode and failure.exists():
    data=json.loads(failure.read_text())
    if any(n.get('failure') in ['net::ERR_INSUFFICIENT_RESOURCES','net::ERR_OUT_OF_MEMORY'] for n in data.get('network',[])):raise Blocked('Chrome host resource exhaustion; recorded failure retained')
   assert p.returncode==0,'Browser failure; inspect browser.log'
   return 'PASS'
  check('browser-accessibility',browser_result)
 finally:
  (fixture/'stop').touch()
  try:process.wait(timeout=30)
  except subprocess.TimeoutExpired:process.terminate();process.wait(timeout=15)
  (fixture/'sessions.json').unlink(missing_ok=True)
print(json.dumps({'status':verdict(),'run':str(run)}));sys.exit({'PASS':0,'FAIL':1,'BLOCKED':2}[verdict()])
