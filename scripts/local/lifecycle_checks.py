"""Local API isolation, request failures, persistence and owned-process lifecycle."""
import http.client, json, socket, sqlite3, subprocess, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
LOCAL = ROOT / '.local'
checks = []
session = csrf = None

def request(path, body=None, origin='http://localhost:5173', host='localhost:5173', token=True, method=None):
    client = http.client.HTTPConnection('localhost', 5173, timeout=15)
    headers = {'Host': host}
    if origin is not None: headers['Origin'] = origin
    if session: headers['Cookie'] = session
    if token and csrf: headers['X-XSRF-TOKEN'] = csrf
    if body is not None: headers['Content-Type'] = 'application/json'
    try:
        client.request(method or ('POST' if body is not None else 'GET'), '/api/' + path,
                       json.dumps(body) if body is not None else None, headers)
        response = client.getresponse(); raw = response.read()
        try: data = json.loads(raw)
        except ValueError: data = None
        return response.status, data, response.getheaders()
    finally: client.close()

def login():
    global session, csrf
    status, data, headers = request('auth/login-guest', {})
    assert status == 200
    session = next(v.split(';')[0] for k,v in headers if k.lower() == 'set-cookie' and v.startswith('JSESSIONID='))
    csrf = data['data']['csrfToken']

def counts():
    with sqlite3.connect((LOCAL/'data/UsefulTools.db').as_uri()+'?mode=ro', uri=True) as db:
        assert db.execute('pragma integrity_check').fetchone()[0] == 'ok'
        return {name: db.execute('select count(*) from '+name).fetchone()[0]
                for name in ['units','tool_toggles','local_seed','user_favorites']}

def dev(command, expected=0):
    result = subprocess.run([sys.executable, str(ROOT/'scripts/local/dev.py'), command],
                            cwd=ROOT, capture_output=True, text=True, timeout=120)
    assert (result.returncode == 0) == (expected == 0), (command, result.stdout, result.stderr)
    return result

assert request('backend-support/catalog')[0] == 401
login()
assert request('backend-support/catalog', host='untrusted.invalid')[0] == 403
assert request('backend-support/validate', {}, origin=None)[0] == 403
assert request('backend-support/validate', {}, origin='https://untrusted.invalid')[0] == 403
assert request('backend-support/validate', {}, token=False)[0] == 403
status, _, headers = request('backend-support/validate', method='OPTIONS')
headers = {key.lower(): value for key, value in headers}
assert status == 200 and headers.get('access-control-allow-origin') == 'http://localhost:5173'
assert headers.get('access-control-allow-credentials') == 'true'
assert request('backend-support/validate', method='OPTIONS', origin='https://untrusted.invalid')[0] == 403
checks.append('unauthenticated, spoofed Host, missing/untrusted Origin, CSRF and credentialed preflight')

status, existing, _ = request('favorites/list'); assert status == 200
if '/backend-support' not in json.dumps(existing):
    assert request('favorites/toggle', {'toolPath':'/backend-support'})[0] == 200
assert '/backend-support' in json.dumps(request('favorites/list')[1])
before = counts()
with sqlite3.connect((LOCAL/'data/UsefulTools.db').as_uri()+'?mode=ro', uri=True) as db:
    assert db.execute("select count(*) from user_favorites where username=? and tool_path=?", ('Guest User','/backend-support')).fetchone()[0] == 1
checks.append('supported favorites API writes identifiable synthetic row to exact local SQLite file')
state = (LOCAL/'active.json').read_bytes()
dev('start', expected=1)
assert (LOCAL/'active.json').read_bytes() == state
checks.append('repeat start fails without replacing owned process state')
dev('stop'); dev('stop')
with socket.socket() as blocker:
    blocker.bind(('127.0.0.1', 8080)); blocker.listen()
    dev('start', expected=1)
    assert blocker.getsockname()[1] == 8080 and (LOCAL/'active.json').read_bytes() == state
checks.append('occupied port fails without killing owner or changing state; stop is idempotent')
dev('start')
assert request('backend-support/catalog')[0] == 401  # Old session never gains access after restart.
session = csrf = None
login()
assert '/backend-support' in json.dumps(request('favorites/list')[1])
assert counts() == before
checks.append('restart persists synthetic favorite and exact seed/reference counts; old session rejected')
assert request('auth/logout', {})[0] == 200
assert request('backend-support/catalog')[0] == 401
checks.append('logout invalidates session and denies replay')
(LOCAL/'verification/lifecycle-results.json').write_text(json.dumps({'status':'PASS','checks':checks,'counts':before},indent=2))
print(json.dumps({'status':'PASS','checks':checks}))
