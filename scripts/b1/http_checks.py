"""Real HTTP security/contract checks against the actual WAR, synthetic sessions only."""
import http.client
import json
import sqlite3
import sys
from pathlib import Path
from urllib.parse import urlsplit

run = Path(sys.argv[1]).resolve()
root = Path(__file__).resolve().parents[2]
sessions = json.loads((run / 'sessions.json').read_text())
origin = sessions['origin']
url = urlsplit(origin)
checks = []
request_count = 0

def call(role=None, body=None, path='/api/backend-support/catalog', expected=200, code=None,
         csrf=True, origin_header=True, media='application/json', chunked=False, method=None):
    global request_count
    request_count += 1
    headers = {}
    if role:
        headers['Cookie'] = 'JSESSIONID=' + sessions[role]['session']
        if csrf: headers['X-XSRF-TOKEN'] = sessions[role]['csrf'] if csrf is True else csrf
    if origin_header: headers['Origin'] = origin if origin_header is True else origin_header
    if body is not None:
        headers['Content-Type'] = media
        body = json.dumps(body).encode() if not isinstance(body, bytes) else body
    connection = http.client.HTTPConnection(url.hostname, url.port, timeout=20)
    try:
        connection.request(method or ('POST' if body is not None else 'GET'), path,
                           body=iter([body]) if chunked else body, headers=headers, encode_chunked=chunked)
        response = connection.getresponse()
        raw = response.read()
        assert response.status == expected, f'{path}: expected {expected}, got {response.status}'
        assert len(raw) <= 262144, 'response cap'
        data = json.loads(raw)
        if code: assert data.get('errorCode') == code, f'Expected code {code}'
        if expected >= 400: assert data['success'] is False
        return data
    finally: connection.close()

def post(role, body, **kw): return call(role, body, '/api/backend-support/validate', **kw)
def sql(statement, args=()):
    with sqlite3.connect(run/'application.db') as db: return db.execute(statement, args).fetchall()
def check(name, fn):
    fn(); checks.append(name); print('PASS', name, flush=True)
sample = {'sampleId':'customers-orders'}
custom = {'request':json.loads((root/'Useful-Tools/src/main/resources/backendsupport/samples.json').read_text())['customers-orders']}

def catalog_access():
    call(expected=401, code='UNAUTHENTICATED')
    for role in ['guest','user','admin']:
        result = call(role)['data']
        assert len(result['modules']) == 6 and result['catalogVersion'] == '1.0.0-b1'
        assert result['operations']['generate'] is False and result['operations']['export'] is False
        assert result['access']['canValidate'] == (role == 'admin')
        assert result['access']['adminPreview'] == (role == 'admin')
        # Capability metadata such as sessionStore is public; reject actual data fields/values.
        def no_private_fields(value):
            if isinstance(value, dict):
                assert not ({str(k).lower() for k in value} & {'spec','specification','session','sessionid','jsessionid','csrf','csrftoken','password','secret'})
                for child in value.values():no_private_fields(child)
            elif isinstance(value, list):
                for child in value:no_private_fields(child)
        no_private_fields(result)
        serialized=json.dumps(result)
        for identity in sessions.values():
            if isinstance(identity,dict):
                for key in ['session','csrf']:
                    if identity.get(key):assert identity[key] not in serialized
    assert sql("select enabled from tool_toggles where tool_path='/backend-support'") == [(0,)]
    post('user', sample, expected=403, code='TOOL_DISABLED')
    post('guest', sample, expected=403, code='TOOL_DISABLED')
    assert post('admin', sample)['data']['adminPreview'] is True
check('catalog roles, default disabled, admin preview',catalog_access)

def unavailable():
    sql("delete from tool_toggles where tool_path='/backend-support'")
    for role in ['user','guest','admin']:
        assert call(role)['data']['access']['canValidate'] is False
        post(role,sample,expected=403,code='TOOL_UNCONFIGURED')
    sql("insert into tool_toggles values('/backend-support',0,'synthetic')")
    sql("update tool_toggles set enabled=1.5 where tool_path='/backend-support'")
    call('admin',expected=503,code='TOOL_UNAVAILABLE')
    post('admin',sample,expected=503,code='TOOL_UNAVAILABLE')
    sql("update tool_toggles set enabled=0 where tool_path='/backend-support'")
    sql('alter table tool_toggles rename to b1_saved_toggles')
    try:
        for role in ['user','guest','admin']:
            call(role,expected=503,code='TOOL_UNAVAILABLE')
            post(role,sample,expected=503,code='TOOL_UNAVAILABLE')
    finally: sql('alter table b1_saved_toggles rename to tool_toggles')
check('absent and failed availability fail closed for every role', unavailable)

def csrf_origin():
    post(None,sample,expected=401,code='UNAUTHENTICATED')
    for token in [False,'wrong']:
        post('admin',sample,csrf=token,expected=403,code='CSRF_INVALID')
    post('missingcsrf',sample,expected=403,code='CSRF_INVALID')
    post('admin',sample,origin_header=False,expected=403,code='ORIGIN_REJECTED')
    post('admin',sample,origin_header='https://untrusted.invalid',expected=403,code='ORIGIN_REJECTED')
    post('admin',sample)
check('real filter authentication, stored/header CSRF and Origin',csrf_origin)

def enabled():
    call('admin', {'toolPath':'/backend-support','enabled':True}, '/api/admin/tool-toggles', method='PUT')
    for role in ['user','guest','admin']:
        assert call(role)['data']['access']['enabled'] is True
        post(role,sample)
    for role in ['user','admin']: assert post(role,custom)['data']['result']['valid'] is True
    for payload in [custom,dict(sample,request=custom['request']),dict(sample,target={'dialect':'postgresql'}),dict(sample,isSample=True)]:
        post('guest',payload,expected=403,code='GUEST_SAMPLE_ONLY')
    post('guest',{'sampleId':'unknown'},expected=400,code='UNKNOWN_SAMPLE')
    for sid in json.loads((root/'Useful-Tools/src/main/resources/backendsupport/samples.json').read_text()):
        assert post('user',{'sampleId':sid})['data']['result']['valid']
check('admin toggle, enabled roles, immutable samples and guest bypass attempts',enabled)

def malformed():
    for raw,code in [(b'{','MALFORMED_JSON'),(b'{"sampleId":"x","sampleId":"y"}','DUPLICATE_KEY'),(b'{} {}','MALFORMED_JSON'),(b'{"sampleId":"x","isSample":true}','INVALID_TRANSPORT')]:
        post('negative',raw,expected=400,code=code)
    post('negative',sample,media='text/plain',expected=415,code='UNSUPPORTED_MEDIA_TYPE')
    for edit in ['version','unknown','target','reference','type']:
        c=json.loads(json.dumps(custom));r=c['request']
        if edit=='version':r['schemaVersion']='999'
        if edit=='unknown':r['sensitive-sentinel']='hidden-value'
        if edit=='target':r['target']['framework']='fastapi'
        if edit=='reference':r['spec']['tables'][0]['primaryKey']=['missing']
        if edit=='type':r['spec']=False
        data=post('negative',c,expected=422,code='INVALID_SPECIFICATION')
        assert data['data']['result']['valid'] is False
        assert 'sensitive-sentinel' not in json.dumps(data) and 'hidden-value' not in json.dumps(data)
    c=json.loads(json.dumps(custom));c['request']['spec']['tables']=[{}]*101
    data=post('negative',c,expected=422)
    assert len(data['data']['result']['findings'])==100 and data['data']['truncated'] is True
check('malformed, duplicate, media, version, unknown fields, target, references and bounded findings',malformed)

def bounds():
    for chunked in [False,True]: post('boundary',b' '*1048577,chunked=chunked,expected=413,code='BODY_TOO_LARGE')
    post('boundary',('['*34+'0'+']'*34).encode(),expected=413,code='COMPLEXITY_LIMIT')
    post('boundary',b'"'+b'x'*4097+b'"',expected=413,code='STRING_LIMIT')
    post('boundary',b'['+b'0,'*1000+b'0]',expected=413,code='COLLECTION_LIMIT')
check('raw body cap with Content-Length and chunked transfer, depth/string/collection caps',bounds)

def rate():
    for _ in range(40): post('rate',b'{',expected=400)
    post('rate',sample,expected=429,code='RATE_LIMITED')
check('explicit 40 per session rate limit counts rejected attempts',rate)

def parity():
    sql("create table if not exists user_table(username text primary key, role text, created_date text)")
    search=call('user',path='/api/search/tools?q=backend')['data']
    assert '/backend-support' in json.dumps(search)
    call('user',{'toolPath':'/backend-support'},'/api/favorites/toggle')
    assert '/backend-support' in json.dumps(call('user',path='/api/favorites/list'))
    call('user',{'toolName':'backend-support.validate','summary':'sensitive-sentinel','payload':{'secret':'hidden-value'}},'/api/activity/log')
    rows=sql("select summary,payload from user_activity where tool_name='backend-support.validate'")
    assert rows and all(row==('Validated a backend specification',None) for row in rows)
    assert 'BackendSupportSecurityFilter' in (root/'Useful-Tools/src/main/webapp/WEB-INF/web.xml').read_text(encoding='utf-8')
check('actual search, favorites and metadata-only activity',parity)
(run/'http-results.json').write_text(json.dumps({'status':'PASS','checks':checks,'count':len(checks),'requestCount':request_count},indent=2))
