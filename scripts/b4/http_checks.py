"""Real WAR B3 transport, policy and exported-byte verification; synthetic inputs only."""
import copy, hashlib, http.client, io, json, socket, sqlite3, sys, time, zipfile
from pathlib import Path
from urllib.parse import urlsplit
from fixtures import sample
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
    s=sample()
    for endpoint in ['generate','export']:
        b=dict(request=s)
        if endpoint=='export':b['expectedDigest']='0'*64
        call(None,b,endpoint,401);call('etluser',b,endpoint,403);call('etlguest',b,endpoint,403)
    p=call('etladmin',dict(request=s))['data'];assert p['adminPreview'];call('etladmin',dict(request=s,expectedDigest=p['digest']),'export')
    sql("delete from tool_toggles where tool_path='/backend-support'")
    for state,status in [('absent',403),('failed',503),('enabled',200)]:
        if state=='failed':sql("insert into tool_toggles values('/backend-support',1.5,'synthetic')")
        if state=='enabled':sql("update tool_toggles set enabled=1 where tool_path='/backend-support'")
        for role in ['etluser','etlguest','etladmin']:
            for endpoint in ['generate','export']:
                b=dict(request=s)
                if endpoint=='export':b['expectedDigest']='0'*64
                expected=(403 if role=='etlguest' else 409 if endpoint=='export' else 200) if state=='enabled' else status
                call(role,b,endpoint,expected)
    catalog=call('etluser',{},'catalog',method='GET')['data'];cap=next(m for m in catalog['generationModules'] if m['module']=='etl')
    assert cap['schemaVersion']=='0.4.0' and cap['templateVersion']=='etl-0.4.0-b4' and cap['canGenerate'] and cap['canExport']

def security():
    s=sample()
    for endpoint in ['generate','export']:
        b=dict(request=s)
        if endpoint=='export':b['expectedDigest']='0'*64
        call('etlnegative',b,endpoint,403,csrf=False);call('etlnegative',b,endpoint,403,origin_header='https://untrusted.invalid')
        call('etlnegative',b'{"request":{},"request":{}}',endpoint,400)
        call('etlnegative',dict(**b,unknown=True),endpoint,400)
        for field,value in [('schemaVersion','0.1.0'),('templateVersion','future'),('private-field','private-value-sentinel')]:
            bad=copy.deepcopy(b);bad['request'][field]=value;r=call('etlnegative',bad,endpoint,422);assert 'private-value-sentinel' not in json.dumps(r)
        call('etlnegative',b' '*1048577,endpoint,413);call('etlnegative',('['*34+'0'+']'*34).encode(),endpoint,413)
    call('etlnegative',dict(request=s,expectedDigest='0'*64),'export',409)
    for field,value in [('batchSize',0),('tableId','unknown'),('mode','replace'),('pythonCode','secret()')]:
        bad=copy.deepcopy(s);bad[field]=value;call('etlnegative',dict(request=bad),expected=422)

def exports():
    index=0
    for target in ['sqlite','postgresql']:
        export(sample(target)['schema'],target+'-schema','etlfixture'+str(index));index+=1
        for source in ['csv','json']:
            role='etlfixture'+str(index);index+=1
            for variant in ['strict','continue','upsert','cap','utc','ignore','types','case','composite']:
                export(sample(target,source,variant),f'{target}-{source}-{variant}',role)
        for variant in ['types','composite']:
            export(sample(target,'csv',variant)['schema'],f'{target}-schema-{variant}','etlfixture'+str(index))
        index+=1

def limits():
    s=sample()
    for _ in range(40):call('etlfixture19',dict(request=s,expectedDigest='0'*64),'export',409)
    call('etlfixture19',dict(request=s),expected=429)
    sockets=[]
    try:
        for _ in range(2):
            sock=socket.create_connection((url.hostname,url.port),timeout=10);sockets.append(sock);i=sessions['boundary']
            header=f"POST /api/backend-support/generate HTTP/1.1\r\nHost: {url.netloc}\r\nOrigin: {origin}\r\nCookie: JSESSIONID={i['session']}\r\nX-XSRF-TOKEN: {i['csrf']}\r\nContent-Type: application/json\r\nContent-Length: 1000\r\n\r\n{{"
            sock.sendall(header.encode())
        time.sleep(.5);call('etlfixture18',dict(request=s),expected=503)
    finally:
        for sock in sockets:sock.close()

try:
    check('ETL access and feature matrix',policy);check('ETL transport security and privacy',security);check('36 deterministic ETL exports and B2 DDL',exports);check('shared rate and concurrency limits',limits)
    result=dict(status='PASS',requests=requests,checks=checks,exports=36)
except Exception as error:
    result=dict(status='FAIL',requests=requests,checks=checks,failure=str(error));raise
finally:
    (run/'http-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
