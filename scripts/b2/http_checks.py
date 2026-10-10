"""Actual WAR/filter/export checks; all submitted values are synthetic."""
import copy
import hashlib
import http.client
import io
import json
from pathlib import Path
import socket
import sqlite3
import statistics
import sys
import time
from urllib.parse import urlsplit
import zipfile
from fixtures import sample, complex_schema, twenty_tables

run=Path(sys.argv[1]).resolve();sessions=json.loads((run/'sessions.json').read_text());origin=sessions['origin'];url=urlsplit(origin)
checks=[];requests=0
def sql(statement):
    with sqlite3.connect(run/'application.db') as db:return db.execute(statement).fetchall()
def call(role='user',body=None,endpoint='generate',expected=200,code=None,csrf=True,origin_header=True,media='application/json',method='POST'):
    global requests
    requests+=1;headers={'Content-Type':media}
    if role:
        headers['Cookie']='JSESSIONID='+sessions[role]['session']
        if csrf:headers['X-XSRF-TOKEN']=sessions[role]['csrf'] if csrf is True else csrf
    if origin_header:headers['Origin']=origin if origin_header is True else origin_header
    payload=body if isinstance(body,bytes) else json.dumps(body).encode()
    connection=http.client.HTTPConnection(url.hostname,url.port,timeout=30)
    try:
        connection.request(method,'/api/backend-support/'+endpoint,payload,headers)
        response=connection.getresponse();raw=response.read();assert response.status==expected,f'{endpoint}: {response.status} expected {expected}: {raw[:250]}'
        assert response.getheader('Cache-Control')=='no-store'
        if expected==200 and endpoint=='export':
            assert response.getheader('Content-Type')=='application/zip'
            assert response.getheader('Content-Disposition')=='attachment; filename="usefultools-schema.zip"'
            return raw
        assert response.getheader('Content-Type').startswith('application/json')
        data=json.loads(raw)
        if code:assert data.get('errorCode')==code,(code,data)
        if expected>=400:assert data['success'] is False and len(raw)<=262144
        else:assert len(raw)<=32*1024*1024
        return data
    finally:connection.close()
def check(name,fn):fn();checks.append(name);print('PASS',name,flush=True)
def transport(s=None):return {'request':s or sample()}

def policy():
    for endpoint in ['generate','export']:
        call(None,{},endpoint,401,'UNAUTHENTICATED')
        call('user',{},endpoint,403,'TOOL_DISABLED')
        call('guest',{},endpoint,403,'TOOL_DISABLED')
    preview=call('admin',transport())['data'];assert preview['adminPreview']
    assert call('admin',dict(request=sample(),expectedDigest=preview['digest']),endpoint='export').startswith(b'PK')
    sql("delete from tool_toggles where tool_path='/backend-support'")
    for role in ['user','guest','admin']:
        for endpoint in ['generate','export']:call(role,{},endpoint,403,'TOOL_UNCONFIGURED')
    sql("insert into tool_toggles values('/backend-support',1.5,'synthetic')")
    for role in ['user','guest','admin']:
        for endpoint in ['generate','export']:call(role,{},endpoint,503,'TOOL_UNAVAILABLE')
    sql("update tool_toggles set enabled=1 where tool_path='/backend-support'")
    for endpoint in ['generate','export']:
        call('guest',{'sampleId':'customers-orders'},endpoint,403,'GUEST_GENERATION_DENIED')
        call('guest',transport(),endpoint,403,'GUEST_GENERATION_DENIED')
check('no identity, guest, user, admin and all feature states',policy)

def security():
    for endpoint in ['generate','export']:
        for role,csrf,origin_header,code in [('missingcsrf',True,True,'CSRF_INVALID'),('negative',False,True,'CSRF_INVALID'),('negative','wrong',True,'CSRF_INVALID'),('negative',True,False,'ORIGIN_REJECTED'),('negative',True,'https://untrusted.invalid','ORIGIN_REJECTED')]:
            call(role,{},endpoint,403,code,csrf,origin_header)
        call('negative',{},endpoint,415,'UNSUPPORTED_MEDIA_TYPE',media='text/plain')
        call('negative',b'{',endpoint,400,'MALFORMED_JSON')
        call('negative',b'{"request":{},"request":{}}',endpoint,400,'DUPLICATE_KEY')
        call('negative',{'request':{},'files':[]},endpoint,400,'INVALID_TRANSPORT')
        call('boundary',b' '*1048577,endpoint,413,'BODY_TOO_LARGE')
        call('boundary',('['*34+'0'+']'*34).encode(),endpoint,413,'COMPLEXITY_LIMIT')
    call('negative',{},'generate',405,'METHOD_NOT_ALLOWED',method='GET')
check('real filters: CSRF Origin media JSON duplicate transport request limits',security)

def exports():
    for target in ['sqlite','postgresql']:
        for name,s in [('sample',sample(target)),('complex',complex_schema(target))]:
            data=call(body=transport(s))['data'];(run/f'{target}-{name}-preview.json').write_text(json.dumps({'success':True,'data':data}),encoding='utf-8');digest=data['digest'];preview={f['path']:f['content'].encode() for f in data['files']}
            export=dict(request=s,expectedDigest=digest);raw=call(body=export,endpoint='export')
            assert raw==call(body=export,endpoint='export')
            with zipfile.ZipFile(io.BytesIO(raw)) as z:
                assert set(z.namelist())==set(preview)
                for path,content in preview.items():assert z.read(path)==content
                for f in data['manifest']['core']['files']:
                    content=z.read(f['path']);assert len(content)==f['bytes'] and hashlib.sha256(content).hexdigest()==f['sha256']
            (run/f'{target}-{name}.zip').write_bytes(raw)
            call(body=dict(request=s,expectedDigest='0'*64),endpoint='export',expected=409,code='PREVIEW_DIGEST_MISMATCH')
    s=sample();s['tables'][0]['primaryKey']=['missing']
    call('negative',dict(request=s,expectedDigest='0'*64),'export',422,'INVALID_GENERATION_SPECIFICATION')
    s=sample();s['templateVersion']='unreleased'
    for endpoint in ['generate','export']:
        body=transport(s)
        if endpoint=='export':body['expectedDigest']='0'*64
        call('negative',body,endpoint,422,'UNAVAILABLE_TEMPLATE')
    s=sample();s['tables'][0]['name']='privacy-sentinel';s['unknown']='private-value-sentinel'
    data=call('negative',transport(s),expected=422)
    assert 'privacy-sentinel' not in json.dumps(data) and 'private-value-sentinel' not in json.dumps(data)
    if sql("select name from sqlite_master where name='user_activity'"):
        assert sql("select count(*) from user_activity where tool_name like 'backend-support.%'")==[(0,)]
check('actual binary exports deterministic, preview hashes match, independent revalidation, privacy',exports)

def constraints():
    for modification in ['cycle','identifier','check','default','target','fk']:
        s=sample()
        if modification=='cycle':s['tables'][0]['foreignKeys']=[dict(columns=['id'],tableId='orders',targetColumns=['id'],onDelete='NO ACTION',onUpdate='NO ACTION')]
        if modification=='identifier':s['tables'][0]['name']='x\nunsafe'
        if modification=='check':s['tables'][1]['checks'][0]['op']='raw SQL'
        if modification=='default':s['tables'][0]['columns'][0]['default']={'kind':'literal','value':'0; DROP'}
        if modification=='target':s['target']='mysql'
        if modification=='fk':s['tables'][1]['foreignKeys'][0]['targetColumns']=['active']
        call('negative',transport(s),expected=422,code='INVALID_GENERATION_SPECIFICATION')
check('unsupported expressions, types, targets, identifiers, FK and cycles rejected',constraints)

def concurrency():
    sockets=[]
    try:
        for _ in range(2):
            sock=socket.create_connection((url.hostname,url.port),timeout=10);sockets.append(sock)
            identity=sessions['boundary']
            headers=f'POST /api/backend-support/generate HTTP/1.1\r\nHost: {url.netloc}\r\nCookie: JSESSIONID={identity["session"]}\r\nX-XSRF-TOKEN: {identity["csrf"]}\r\nOrigin: {origin}\r\nContent-Type: application/json\r\nContent-Length: 100\r\n\r\n{{'
            sock.sendall(headers.encode())
        time.sleep(.7)
        call('boundary',transport(),expected=503,code='GENERATION_BUSY')
    finally:
        for sock in sockets:sock.close()
    time.sleep(.2)
check('two-slot concurrency cap rejects before response commit',concurrency)

def rate():
    for i in range(40):call('rate',b'{',endpoint=['generate','export','validate'][i%3],expected=400)
    for endpoint in ['generate','export']:call('rate',transport(),endpoint,429,'RATE_LIMITED')
check('shared 40-attempt budget across validate generate export',rate)

times=[]
for _ in range(5):
    start=time.perf_counter();call(body=transport(twenty_tables()));times.append(round((time.perf_counter()-start)*1000,3))
(run/'http-results.json').write_text(json.dumps(dict(status='PASS',checks=checks,count=len(checks),requestCount=requests,
    performance=dict(tables=20,iterations=5,milliseconds=times,medianMilliseconds=statistics.median(times),method='Sequential real loopback HTTP complete generation responses after warmup; no staging p95 claim')),indent=2)+'\n',encoding='utf-8')
sql("create table if not exists user_table(username text primary key, role text, created_date text)")
