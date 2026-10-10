"""Real WAR B3 transport, policy and exported-byte verification; synthetic inputs only."""
import copy, hashlib, http.client, io, json, socket, sqlite3, sys, time, zipfile
from pathlib import Path
from urllib.parse import urlsplit
from fixtures import sample,migrations,views
run=Path(sys.argv[1]).resolve();sessions=json.loads((run/'sessions.json').read_text());origin=sessions['origin'];url=urlsplit(origin)
checks=[];requests=0
def sql(statement):
    with sqlite3.connect(run/'application.db') as db:return db.execute(statement).fetchall()
def call(role,body,endpoint='generate',expected=200,csrf=True,origin_header=True,method='POST'):
    global requests
    requests+=1;headers={'Content-Type':'application/json'}
    if role:
        headers['Cookie']='JSESSIONID='+sessions[role]['session']
        if csrf:headers['X-XSRF-TOKEN']=sessions[role]['csrf'] if csrf is True else csrf
    if origin_header:headers['Origin']=origin if origin_header is True else origin_header
    connection=http.client.HTTPConnection(url.hostname,url.port,timeout=30)
    try:
        connection.request(method,'/api/backend-support/'+endpoint,body if isinstance(body,bytes) else json.dumps(body).encode(),headers)
        response=connection.getresponse();raw=response.read();assert response.status==expected,(endpoint,response.status,expected,raw[:300])
        assert response.getheader('Cache-Control')=='no-store'
        if endpoint=='export' and expected==200:
            assert response.getheader('Content-Type')=='application/zip'
            assert response.getheader('Content-Disposition')=='attachment; filename="usefultools-'+body['request']['module']+'.zip"'
            return raw
        assert response.getheader('Content-Type').startswith('application/json');data=json.loads(raw)
        if expected>=400:assert data['success'] is False and len(raw)<=262144
        return data
    finally:connection.close()
def export(s,name,role):
    p=call(role,dict(request=s))['data'];raw=call(role,dict(request=s,expectedDigest=p['digest']),'export')
    assert raw==call(role,dict(request=s,expectedDigest=p['digest']),'export')
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        assert sorted(z.namelist())==z.namelist() and len(z.namelist())<=100
        assert sum(i.file_size for i in z.infolist())<=5*1024*1024
        assert all(i.date_time==(1980,1,2,0,0,0) and not i.extra and '/' not in i.filename and '\\' not in i.filename and '..' not in i.filename for i in z.infolist())
        for f in p['files']:assert z.read(f['path'])==f['content'].encode()
        for f in p['manifest']['core']['files']:assert hashlib.sha256(z.read(f['path'])).hexdigest()==f['sha256']
    (run/(name+'.zip')).write_bytes(raw);(run/(name+'-preview.json')).write_text(json.dumps(p),encoding='utf-8')
    return p
def check(name,fn):fn();checks.append(name);print('PASS',name,flush=True)
def policy():
    for module in ['migration','view','evaluator']:
        s=sample(module);r=module+'user'
        for endpoint in ['generate','export']:
            body=dict(request=s)
            if endpoint=='export':body['expectedDigest']='0'*64
            call(None,body,endpoint,401);call(r,body,endpoint,403);call(module+'guest',body,endpoint,403)
        p=call(module+'admin',dict(request=s))['data'];assert p['adminPreview'];call(module+'admin',dict(request=s,expectedDigest=p['digest']),'export')
    sql("delete from tool_toggles where tool_path='/backend-support'")
    for state,expected in [('absent',403),('failed',503),('enabled',200)]:
        if state=='failed':sql("insert into tool_toggles values('/backend-support',1.5,'synthetic')")
        if state=='enabled':sql("update tool_toggles set enabled=1 where tool_path='/backend-support'")
        for module in ['migration','view','evaluator']:
            for role in ['user','guest','admin']:
                for endpoint in ['generate','export']:
                    s=sample(module);body=dict(request=s)
                    if endpoint=='export':body['expectedDigest']='0'*64
                    status=(403 if role=='guest' else 409 if endpoint=='export' else 200) if state=='enabled' else expected
                    call(module+role,body,endpoint,status)
    catalog=call('user',{},'catalog',method='GET')['data'];assert {'migration','view','evaluator'} <= {m['module'] for m in catalog['generationModules']}
    assert all(m['canGenerate'] and m['canExport'] for m in catalog['generationModules'])
def security():
    for module in ['migration','view','evaluator']:
        role=module+'negative';s=sample(module)
        for endpoint in ['generate','export']:
            b=dict(request=s)
            if endpoint=='export':b['expectedDigest']='0'*64
            call(role,b,endpoint,403,csrf=False);call(role,b,endpoint,403,origin_header='https://untrusted.invalid')
            call(role,b'{"request":{},"request":{}}',endpoint,400)
            call(role,dict(**b,unknown=True),endpoint,400)
            bad=copy.deepcopy(b);bad['request']['templateVersion']='future';call(role,bad,endpoint,422)
            bad=copy.deepcopy(b);bad['request']['schemaVersion']='0.1.0';call(role,bad,endpoint,422)
            bad=copy.deepcopy(b);bad['request']['privacy-sentinel']='private-value-sentinel';data=call(role,bad,endpoint,422);assert 'private-value-sentinel' not in json.dumps(data)
            call(role,b' '*1048577,endpoint,413);call(role,('['*34+'0'+']'*34).encode(),endpoint,413)
        call(role,dict(request=s,expectedDigest='0'*64),'export',409)
        bad=sample(module);bad['module']='unknown';call(role,dict(request=bad),expected=422)
        bad=sample(module);bad['rawSql']='SELECT secret';call(role,dict(request=bad,expectedDigest='0'*64),'export',422)
def exports():
    for target in ['sqlite','postgresql']:
        # Separate existing fixture sessions keep export checks within the shared per-session budget.
        for module,fixtures in [('migration',migrations(target)),('view',views(target)),('evaluator',{'errors':sample('evaluator',target)})]:
            role=module+('exports' if target=='sqlite' else 'user')
            for name,s in fixtures.items():
                p=export(s,f'{target}-{module}-{name}',role)
                if module=='evaluator':assert p['details']['evaluationCompleted'] and p['details']['hasErrors'] and not p['details']['executableSql'];assert not any(f['path'].endswith('.sql') for f in p['files'])
        # Initial DDL also comes from the real released B2 endpoint.
        s=sample('migration',target)['before'];export(s,f'{target}-schema','admin')
def negatives():
    for variant in ['drop','mixed','type','nullable','unique','rename','fk','check','index','tableadd']:
        s=sample('migration');t=s['after']['tables'][0]
        if variant in ['drop','mixed']:t['columns'].pop(2);s['acknowledgeDestructive']=True
        if variant=='type':t['columns'][1]['length']=40
        if variant=='nullable':t['columns'][1]['nullable']=True
        if variant=='unique':t['indexes']=[dict(id='u',name='u',columns=['email'],unique=True)]
        if variant=='rename':t['name']='clients'
        if variant=='fk':s['after']['tables'][1]['foreignKeys']=[]
        if variant=='check':s['after']['tables'][1]['checks']=[]
        if variant=='index':s['after']['tables'][1]['indexes']=[]
        if variant=='tableadd':new=copy.deepcopy(t);new['id']='new';new['name']='new';s['after']['tables'].append(new)
        p=call('migrationadmin',dict(request=s),expected=422);assert 'files' not in p['data']
        call('migrationadmin',dict(request=s,expectedDigest='0'*64),'export',422)
    for variant in ['group','alias','null','collision','type']:
        s=sample('view')
        if variant=='group':s['groupBy']=[]
        if variant=='alias':s['projections'][0]['expression']['sourceAlias']='missing'
        if variant=='null':s['where']=dict(op='eq',left=s['projections'][0]['expression'],right=dict(kind='literal',value=None))
        if variant=='collision':s['name']='customers'
        if variant=='type':s['joins'][0]['right']['columnId']='amount'
        call('viewadmin',dict(request=s),expected=422);call('viewadmin',dict(request=s,expectedDigest='0'*64),'export',422)
def budgets():
    sockets=[]
    try:
        for _ in range(2):
            sock=socket.create_connection((url.hostname,url.port),timeout=10);sockets.append(sock);i=sessions['boundary']
            sock.sendall(f'POST /api/backend-support/generate HTTP/1.1\r\nHost: {url.netloc}\r\nCookie: JSESSIONID={i["session"]}\r\nX-XSRF-TOKEN: {i["csrf"]}\r\nOrigin: {origin}\r\nContent-Type: application/json\r\nContent-Length: 100\r\n\r\n{{'.encode())
        time.sleep(.7);call('boundary',dict(request=sample('view')),expected=503)
    finally:
        for sock in sockets:sock.close()
    for i in range(40):call('rate',{},['validate','generate','export'][i%3],400)
    call('rate',dict(request=sample('evaluator')),expected=429)
try:
    for name,fn in [('module role and state matrices',policy),('transport security strict bounds and revalidation',security),('deterministic actual exported fixtures',exports),('unsupported complete plans and views blocked',negatives),('shared concurrency and rate budgets',budgets)]:check(name,fn)
    sql("create table if not exists user_table(username text primary key,role text,created_date text)")
    result=dict(status='PASS',checks=checks,requests=requests)
except Exception as e:result=dict(status='FAIL',checks=checks,requests=requests,failure=str(e));raise
finally:(run/'http-results.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
