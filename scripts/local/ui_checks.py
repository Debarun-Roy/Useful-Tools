"""Run focused UI checks against an owned loopback WAR with synthetic sessions."""
import os, json, subprocess, shutil, sys, time, sqlite3
from pathlib import Path
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
run = ROOT / '.b2' / ('ui-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'))
run.mkdir(parents=True)
runtime = ROOT / 'scripts/b1/runtime'
cp = str(runtime / 'target/test-classes') + os.pathsep + (runtime / 'classpath.txt').read_text().strip()
env = os.environ.copy()
for key in ['UT_PROFILE', 'UT_LOCAL_DIR', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS']:
    env.pop(key, None)
env.update(SQLITE_DB_PATH=str(run / 'application.db'), SQLITE_DB_URL='', RECAPTCHA_SECRET_KEY='', B1_RUN=str(run))
with (run / 'server.log').open('w', encoding='utf8') as log:
    process = subprocess.Popen([shutil.which('java'), '-cp', cp, 'b1.Server', str(run),
                               str(ROOT / 'Useful-Tools/target/UsefulTools.war'),
                               str(ROOT / 'usefultools-frontend/dist')], cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
    try:
        deadline = time.monotonic() + 90
        while not (run / 'sessions.json').exists():
            if process.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError('Owned UI fixture failed startup; inspect ' + str(run))
            time.sleep(.2)
        with sqlite3.connect(run / 'application.db') as db:
            db.execute("UPDATE tool_toggles SET enabled=1 WHERE tool_path='/backend-support'")
        command = [shutil.which('node'), str(ROOT / 'scripts/local/ui-checks.mjs'), str(run)]
        result = subprocess.run(command, cwd=ROOT, env=env)
        output = Path((ROOT / '.local/ui-current.txt').read_text())
        (output / 'ui-command.json').write_text(json.dumps({'command': command, 'exitCode': result.returncode, 'fixture': str(run)}, indent=2))
        sys.exit(result.returncode)
    finally:
        (run / 'stop').touch()
        try: process.wait(timeout=20)
        except subprocess.TimeoutExpired: process.terminate(); process.wait(timeout=10)
