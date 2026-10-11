"""Local Guest Login source-policy regression. Requires frontend and backend.

Run from repository root: python scripts/local/guest_login_checks.py
Does not start/stop services. Saves no session IDs or CSRF tokens in evidence.
"""
import http.client
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
results = []


def call(port, path, method='POST', headers=None, body=None):
    # Match the launcher's IPv4 bind without Windows IPv6 connection fallback.
    # HTTP authority remains localhost, including cookies and source policy.
    connection = http.client.HTTPConnection('127.0.0.1', port, timeout=10)
    try:
        connection.request(method, '/api/' + path, body=body,
                           headers={'Host': f'localhost:{port}'} | (headers or {}))
        response = connection.getresponse()
        raw = response.read()
        try:
            data = json.loads(raw)
        except ValueError:
            data = {}
        return response.status, data, response.getheaders()
    finally:
        connection.close()


for port in (5173, 8080):
    cases = [
        ('trusted-origin', {'Origin': 'http://localhost:5173'}, 200),
        ('trusted-referer', {'Referer': 'http://localhost:5173/login'}, 200),
        ('missing-source', {}, 403),
        ('opaque-origin', {'Origin': 'null', 'Referer': 'http://localhost:5173/login'}, 403),
        ('untrusted-origin', {'Origin': 'https://untrusted.invalid', 'Referer': 'http://localhost:5173/login'}, 403),
        ('wrong-host', {'Host': '127.0.0.1:5173', 'Origin': 'http://localhost:5173'}, 403),
        ('referer-prefix-attack', {'Referer': 'http://localhost.evil.invalid:5173/login'}, 403),
        ('referer-userinfo-attack', {'Referer': 'http://localhost:5173@evil.invalid/login'}, 403),
        ('wrong-referer-port', {'Referer': 'http://localhost:5174/login'}, 403),
    ]
    for name, headers, expected in cases:
        status, data, response_headers = call(port, 'auth/login-guest', headers=headers)
        assert status == expected, (port, name, status, data.get('errorCode'))
        if expected == 403:
            assert data.get('errorCode') == 'LOCAL_REQUEST_REJECTED', (name, data)
            assert 'json' in {k.lower(): v for k, v in response_headers}.get('content-type', '')
        else:
            assert data['success'] and data['data']['role'] == 'guest'
        results.append({'port': port, 'case': name, 'status': status})
        if name == 'trusted-referer':
            session = next(value.split(';')[0] for key, value in response_headers
                           if key.lower() == 'set-cookie' and value.startswith('JSESSIONID='))
            token = data['data']['csrfToken']
            auth = headers | {'Cookie': session, 'Content-Type': 'application/json'}
            status, catalog, _ = call(port, 'backend-support/catalog', 'GET', auth)
            assert status == 200
            sample = json.dumps({'sampleId': catalog['data']['modules'][0]['sampleIds'][0]})
            assert call(port, 'backend-support/validate', headers=auth, body=sample)[0] == 403
            bad = auth | {'X-XSRF-TOKEN': 'deliberately-invalid'}
            assert call(port, 'backend-support/validate', headers=bad, body=sample)[0] == 403
            auth['X-XSRF-TOKEN'] = token
            status, validation, _ = call(port, 'backend-support/validate', headers=auth, body=sample)
            assert status == 200, (port, 'sample', status, validation)
            assert call(port, 'backend-support/generate', headers=auth, body='{}')[0] == 403
            assert call(port, 'auth/logout', headers=auth, body='{}')[0] == 200
            assert call(port, 'backend-support/catalog', 'GET', auth)[0] == 401
            results.append({'port': port, 'case': 'referer-session-csrf-sample-permissions-logout', 'status': 'PASS'})

output = ROOT / '.local/verification/guest-login-results.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps({'status': 'PASS', 'checks': results}, indent=2))
print('PASS Guest Login through proxy and direct backend; source checks, CSRF, sample, permissions, logout')
