"""Negative configuration tests in fresh JVMs; resolves paths without opening databases."""
import json, os, shutil, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.local/verification/config'
OUT.mkdir(parents=True, exist_ok=True)
cp = os.pathsep.join([str(ROOT / 'Useful-Tools/target/classes'),
                     (ROOT / '.local/classpath.txt').read_text().strip()])
subprocess.run([shutil.which('javac'), '--release', '17', '-cp', cp, '-d', str(OUT),
                str(ROOT / 'scripts/local/ConfigProbe.java')], check=True)
cp = str(OUT) + os.pathsep + cp
base = os.environ.copy()
for key in ['UT_PROFILE', 'UT_LOCAL_DIR', 'SQLITE_DB_URL', 'SQLITE_DB_PATH', 'CORS_ALLOWED_ORIGINS',
            'RAILWAY_ENVIRONMENT_ID', 'VERCEL', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS']:
    base.pop(key, None)
local = {'UT_PROFILE': 'local', 'UT_LOCAL_DIR': str(ROOT / '.local'),
         'SQLITE_DB_PATH': str(ROOT / '.local/data/UsefulTools.db')}
checks = []
def check(name, additions, mode='config', success=True, marker=True, reason=None):
    command = [shutil.which('java')] + (['-Dusefultools.local.launcher=true'] if marker else [])
    command += ['-cp', cp, 'local.ConfigProbe', mode]
    result = subprocess.run(command, env=base | additions, capture_output=True, text=True)
    assert (result.returncode == 0) == success, (name, result.stderr)
    if not success:
        assert 'Exception' in result.stderr and 'Could not find or load main class' not in result.stderr, name
    if reason:
        assert reason in result.stderr, (name, 'wrong rejection reason')
    checks.append({'name': name, 'result': 'PASS', 'expectedSuccess': success, 'exitCode': result.returncode})

check('local database exact isolated path', local, 'database')
check('local cannot use inherited JDBC URL', local | {'SQLITE_DB_URL': 'jdbc:sqlite:/never-open-this.db'}, 'database', False)
check('local cannot use another database path', local | {'SQLITE_DB_PATH': str(OUT / 'not-owned.db')}, 'database', False)
check('local requires launcher marker', local, success=False, marker=False)
check('local rejects Railway', local | {'RAILWAY_ENVIRONMENT_ID': 'synthetic'}, success=False)
check('local rejects Vercel', local | {'VERCEL': '1'}, success=False)
check('local requires config directory', local | {'UT_LOCAL_DIR': str(OUT / 'missing')}, success=False)
isolated = OUT / 'overlay-probe' / '.local'
isolated.mkdir(parents=True, exist_ok=True)
overlay = isolated / 'local.properties'
try:
    if overlay.exists(): overlay.unlink()  # Owned disposable probe only.
    check('missing local overlay fails closed', local | {'UT_LOCAL_DIR': str(isolated)}, success=False, reason='NoSuchFileException')
    overlay.write_text('recaptcha_verify_url=https://untrusted.invalid/verify\n')
    check('local provider override rejected', local | {'UT_LOCAL_DIR': str(isolated)}, success=False, reason='Unsupported local overlay key')
finally:
    overlay.unlink(missing_ok=True)
check('unknown profile fails closed', {'UT_PROFILE': 'typo'}, success=False)
check('staging cannot use bundled defaults', {'UT_PROFILE': 'staging'}, success=False)
check('wildcard credentialed CORS rejected', {'CORS_ALLOWED_ORIGINS': '*'}, success=False)
check('explicit staging settings accepted', {'UT_PROFILE': 'staging', 'CORS_ALLOWED_ORIGINS': 'https://staging.example.invalid', 'SQLITE_DB_PATH': str(OUT / 'not-opened.db')})
check('production defaults remain available', {'UT_PROFILE': 'production'})
check('local cookie API and raw-header paths', local, 'cookies')
check('non-local cookie API and raw-header paths', {}, 'cookies')
(OUT / 'results.json').write_text(json.dumps({'status': 'PASS', 'checks': checks}, indent=2))
print('PASS', len(checks), 'isolated configuration/cookie checks; no database opened')
