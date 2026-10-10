"""Owned disposable actual-WAR HTTP/browser run. --http-only is diagnostic, not certification."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('run')
parser.add_argument('--http-only', action='store_true')
parser.add_argument('--browser-channel', choices=['chrome','msedge'])
args = parser.parse_args()
run = Path(args.run).resolve()
assert run.is_relative_to(ROOT/'.b1'), 'Disposable B1 directory required'
run.mkdir(parents=True,exist_ok=True)
runtime = ROOT/'scripts/b1/runtime'
cp = os.pathsep.join([str(runtime/'target/test-classes'), (runtime/'classpath.txt').read_text().strip()])
env = os.environ.copy()
env.update(SQLITE_DB_PATH=str(run/'application.db'),SQLITE_DB_URL='', RECAPTCHA_SECRET_KEY='', B1_RUN=str(run), PLAYWRIGHT_BROWSERS_PATH=str(ROOT/'.b0/browsers'))
if args.browser_channel: env['B0_BROWSER_CHANNEL'] = args.browser_channel
else: env.pop('B0_BROWSER_CHANNEL',None)
command = [shutil.which('java'),'-cp',cp,'b1.Server',str(run),str(ROOT/'Useful-Tools/target/UsefulTools.war'),str(ROOT/'usefultools-frontend/dist')]
log = open(run/'server.log','w',encoding='utf-8')
process = subprocess.Popen(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
code = 1
try:
    deadline = time.monotonic()+60
    while not (run/'sessions.json').exists():
        if process.poll() is not None: raise RuntimeError('Container exited before readiness; inspect server.log')
        if time.monotonic()>deadline: raise RuntimeError('Container readiness timeout')
        time.sleep(.2)
    code = subprocess.call([sys.executable,'-X','utf8',str(ROOT/'scripts/b1/http_checks.py'),str(run)],cwd=ROOT,env=env)
    if not args.http_only:
        browser_code = subprocess.call([shutil.which('node'),'tests/b1-browser.mjs'],cwd=ROOT/'usefultools-frontend',env=env)
        code = max(code,browser_code)
finally:
    (run/'stop').touch()
    try: process.wait(timeout=20)
    except subprocess.TimeoutExpired: process.terminate(); process.wait(timeout=10)
    log.close()
    # Test sessions are ephemeral credentials. Never publish them or cookie values in logs.
    session_file=run/'sessions.json'
    session_file.unlink(missing_ok=True)
    text=(run/'server.log').read_text(encoding='utf-8',errors='replace')
    text=re.sub(r'(?i)(JSESSIONID|XSRF-TOKEN|csrfToken)(\s*[=:]\s*)[^\s;,]+',r'\1\2[REDACTED]',text)
    (run/'server.log').write_text(text,encoding='utf-8')
sys.exit(code)
