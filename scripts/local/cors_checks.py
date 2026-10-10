"""Read-only local preflight checks; no production endpoints or credentials."""
import http.client, json
from pathlib import Path

checks = []
for port in (5173, 8080):
    for origin, expected in [('http://localhost:5173', 200), ('https://untrusted.invalid', 403)]:
        connection = http.client.HTTPConnection('localhost', port, timeout=10)
        connection.request('OPTIONS', '/api/backend-support/validate', headers={
            'Origin': origin, 'Access-Control-Request-Method': 'POST',
            'Access-Control-Request-Headers': 'content-type,x-xsrf-token'})
        response = connection.getresponse()
        headers = {k.lower(): v for k, v in response.getheaders()}
        response.read(); connection.close()
        assert response.status == expected
        if expected == 200:
            assert headers.get('access-control-allow-origin') == origin
            assert headers.get('access-control-allow-credentials') == 'true'
            assert 'x-xsrf-token' in headers.get('access-control-allow-headers', '').lower()
        else:
            assert 'access-control-allow-origin' not in headers
        checks.append({'port': port, 'origin': origin, 'status': response.status})
output = Path(__file__).resolve().parents[2] / '.local/verification/cors-results.json'
output.write_text(json.dumps({'status': 'PASS', 'checks': checks}, indent=2))
print('PASS direct/backend and proxied allowed/denied credentialed preflights')
