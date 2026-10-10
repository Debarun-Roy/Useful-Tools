"""Run existing schema/migration/view/evaluator/ETL HTTP suites with isolated owned fixtures.
Does not run generated database-engine qualification or replace B0-B7 release gates.
"""
import json, os, shutil, subprocess, sys, time
from pathlib import Path
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
runtime = ROOT / 'scripts/b1/runtime'
cp = os.pathsep.join([str(runtime/'target/test-classes'), (runtime/'classpath.txt').read_text().strip()])
stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
results = []
for family in ['b2','b3','b4']:
    run = ROOT / ('.' + family) / ('localhost-' + stamp)
    run.mkdir(parents=True)
    env = os.environ.copy()
    for key in ['UT_PROFILE','UT_LOCAL_DIR','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS']:
        env.pop(key, None)
    env.update(SQLITE_DB_PATH=str(run/'application.db'), SQLITE_DB_URL='', RECAPTCHA_SECRET_KEY='')
    command = [shutil.which('java'), '-cp', cp, 'b1.Server', str(run),
               str(ROOT/'Useful-Tools/target/UsefulTools.war'), str(ROOT/'usefultools-frontend/dist')]
    with (run/'server.log').open('w') as log:
        process = subprocess.Popen(command, cwd=run, env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 90
            while not (run/'sessions.json').exists():
                assert process.poll() is None and time.monotonic() < deadline, 'Fixture startup failed'
                time.sleep(.2)
            test = [sys.executable, '-X', 'utf8', str(ROOT/'scripts'/family/'http_checks.py'), str(run)]
            with (run/'http.log').open('w') as output:
                completed = subprocess.run(test, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT, timeout=180)
            results.append({'family':family, 'exitCode':completed.returncode, 'run':str(run.relative_to(ROOT)), 'command':test})
            assert completed.returncode == 0, str(run/'http.log')
            print('PASS', family, 'existing HTTP suite', flush=True)
        finally:
            (run/'stop').touch()
            try: process.wait(timeout=20)
            except subprocess.TimeoutExpired: process.terminate(); process.wait(timeout=10)
            (run/'sessions.json').unlink(missing_ok=True)
            (ROOT/'.local/verification/support-http-results.json').write_text(json.dumps(results,indent=2))
