"""Certify B0. Run from repository root: python scripts/b0/verify.py.
Every required check runs or is recorded as blocked; no diagnostic/pass shortcut.
"""
import hashlib
import argparse
import json
import os
import platform
import re
import shutil
import socket
import sqlite3
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[2]
RUN = ROOT / '.b0' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
RUN.mkdir(parents=True)
ENV = os.environ.copy()
ENV.update(SQLITE_DB_PATH=str(RUN / 'application.db'), SQLITE_DB_URL='', RECAPTCHA_SECRET_KEY='', VITE_API_BASE='/api', VITE_RECAPTCHA_SITE_KEY='', PLAYWRIGHT_BROWSERS_PATH=str(ROOT / '.b0/browsers'))
FRONT = ROOT / 'usefultools-frontend'
JAVA = ROOT / 'spikes/backend-support/java-servlet'
PY = ROOT / 'spikes/backend-support/python-fastapi'
VENV = RUN / 'venv'
VPY = VENV / ('Scripts/python.exe' if os.name == 'nt' else 'bin/python')
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--browser-channel', choices=['chrome','msedge'], help='Use an explicitly installed browser; otherwise install pinned Playwright Chromium')
OPTIONS = parser.parse_args()
if OPTIONS.browser_channel: ENV['B0_BROWSER_CHANNEL'] = OPTIONS.browser_channel
else: ENV.pop('B0_BROWSER_CHANNEL', None)
checks = []


def git(*args):
    result = subprocess.run(['git', '-c', f'safe.directory={ROOT.as_posix()}', *args], cwd=ROOT, capture_output=True, text=True)
    if result.returncode: raise RuntimeError('Git inspection failed')
    return result.stdout.strip()


def fingerprint():
    files = sorted(set(git('ls-files', '-c', '-o', '--exclude-standard').splitlines()))
    # Generated reports are outputs, not certification inputs.
    excluded = {'docs/backend-support/b0/sprint-pass.md', 'docs/backend-support/b0/evidence.json'}
    digest = hashlib.sha256()
    for name in files:
        p = ROOT / name
        if name in excluded or not p.is_file(): continue
        digest.update(name.encode() + b'\0' + hashlib.sha256(p.read_bytes()).digest())
    return digest.hexdigest()


def run(name, args, cwd=ROOT, timeout=600):
    args = [str(a) for a in args]
    executable = shutil.which(args[0])
    if executable: args[0] = executable
    path = RUN / (name + '.log')
    print(f'CHECK {name}', flush=True)
    try:
        result = subprocess.run(args, cwd=cwd, env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace', timeout=timeout)
        output = result.stdout
        # Known env overrides are synthetic; redact credential-shaped log assignments defensively.
        output = re.sub(r'(?i)(secret|password|token)(\s*[=:]\s*)[^\s,;]+', r'\1\2[REDACTED]', output)
        path.write_text(output, encoding='utf-8')
        status = 'PASS' if result.returncode == 0 else 'FAIL'
        if result.returncode and re.search(r'Permission denied|WinError 10013|EPERM|ENOTFOUND|Could not transfer artifact|No matching distribution', output): status = 'BLOCKED'
        code = result.returncode
    except (OSError, subprocess.TimeoutExpired) as error:
        path.write_text(str(error), encoding='utf-8'); status, code = 'BLOCKED', None
    checks.append({'name':name, 'command':args, 'cwd':str(cwd), 'exitCode':code, 'status':status, 'log':str(path.relative_to(ROOT))})
    print(f'{status} {name}', flush=True)
    return status == 'PASS'


def assertion(name, action):
    try:
        action()
        checks.append({'name':name,'status':'PASS','exitCode':0})
        return True
    except Exception as error:
        checks.append({'name':name,'status':'FAIL','exitCode':1,'detail':str(error)})
        return False


def documentation():
    for name in ['README.md','repository-baseline.md','compatibility-matrix.md','decisions.md','b1-handoff.md','continuation.md']:
        assert (ROOT/'docs/backend-support/b0'/name).stat().st_size > 200, name
    assert (ROOT/'.github/workflows/b0.yml').is_file()
    assert (ROOT/'Useful-Tools/src/main/webapp/WEB-INF/web.xml').is_file()
    assert (ROOT/'Useful-Tools/src/main/java/common/ApiResponse.java').is_file()


def database():
    with sqlite3.connect(ENV['SQLITE_DB_PATH']) as db:
        assert db.execute('select count(*) from units').fetchone()[0] > 100
        tables = {r[0] for r in db.execute("select name from sqlite_master where type='table'")}
        assert {'regex_patterns', 'schema_templates', 'tool_recommendations', 'units'} <= tables



def test_reports():
    for path, minimum in [(ROOT/'Useful-Tools/target/surefire-reports/TEST-baseline.BaselineTest.xml', 3), (JAVA/'target/surefire-reports/TEST-spike.AdapterTest.xml', 1), (JAVA/'target/failsafe-reports/TEST-spike.RuntimeIT.xml', 2)]:
        suite = ET.parse(path).getroot()
        assert int(suite.attrib['tests']) >= minimum
        assert all(int(suite.attrib.get(k, 0)) == 0 for k in ['errors','failures','skipped']), str(path)


def startup_logs():
    log = (RUN/'java-reference.log').read_text(encoding='utf-8')
    assert not re.search(r'SEVERE:|Exception fixing docBase|NoSuchFileException|ERROR during initialization|ERROR: Could not obtain', log), 'Container initialization errors; inspect java-reference.log'


def browser():
    classpath_file = RUN/'classpath.txt'
    if not run('java-classpath', ['mvn','-B','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath',f'-Dmdep.outputFile={classpath_file}'], JAVA):
        raise RuntimeError('Cannot start browser backend without its resolved classpath')
    classpath = os.pathsep.join([str(JAVA/'target/test-classes'), str(JAVA/'target/classes'), classpath_file.read_text().strip()])
    with socket.socket() as sock:
        sock.bind(('127.0.0.1',0)); port = sock.getsockname()[1]
    command = ['java', f'-Db0.war={ROOT / "Useful-Tools/target/UsefulTools.war"}', f'-Db0.port={port}', '-cp', classpath, 'spike.LocalServer']
    log = open(RUN/'browser-backend.log','w',encoding='utf-8')
    process = subprocess.Popen(command, cwd=ROOT, env=ENV, stdout=log, stderr=subprocess.STDOUT)
    try:
        deadline = time.monotonic()+60
        while True:
            if process.poll() is not None: raise RuntimeError('Application exited before browser smoke')
            try:
                urlopen(f'http://127.0.0.1:{port}/api/auth/session-status', timeout=1)
            except HTTPError as error:
                if error.code == 401: break
            except (URLError, TimeoutError): pass
            if time.monotonic()>deadline: raise RuntimeError('Application startup timed out')
            time.sleep(.2)
        ENV['B0_BACKEND_URL'] = f'http://127.0.0.1:{port}'
        ENV['B0_SCREENSHOT'] = str(RUN/'frontend.png')
        run('browser-smoke',['node','tests/b0-browser.mjs'],FRONT,120)
    finally:
        process.terminate()
        try: process.wait(timeout=10)
        except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=5)
        log.close()


def main():
    start = fingerprint()
    for name, args in [('node-version',['node','--version']),('npm-version',['npm','--version']),('maven-version',['mvn','--version']),('java-version',['java','-version']),('python-version',[sys.executable,'--version'])]: run(name,args)
    assertion('layout-and-docs', documentation)
    run('frontend-install',['npm','ci','--no-audit','--no-fund'],FRONT)
    run('frontend-tests',['npm','test'],FRONT)
    run('frontend-build',['npm','run','build'],FRONT)
    run('backend-build',['mvn','-B','clean','verify'],ROOT/'Useful-Tools')
    run('war-integrity',[sys.executable,'scripts/b0/war.py','Useful-Tools/target/UsefulTools.war'])
    run('java-reference',['mvn','-B','clean','verify',f'-Db0.applicationWar={ROOT / "Useful-Tools/target/UsefulTools.war"}'],JAVA)
    assertion('test-execution-counts', test_reports)
    assertion('initialization-logs', startup_logs)
    assertion('database-initialization', database)
    run('python-venv',[sys.executable,'-m','venv',VENV])
    run('python-download',[VPY,'-m','pip','download','--disable-pip-version-check','--retries','2','--timeout','20','-r',PY/'requirements.txt','-d',ROOT/'.b0/wheels'])
    run('python-install',[VPY,'-m','pip','install','--disable-pip-version-check','--no-index','--find-links',ROOT/'.b0/wheels','-r',PY/'requirements.txt'])
    run('python-dependencies',[VPY,'-m','pip','check'])
    run('python-reference',[VPY,'-m','pytest','-q','-p','no:cacheprovider',PY],timeout=120)
    run('contracts',[VPY,ROOT/'scripts/b0/contracts.py'])
    if not OPTIONS.browser_channel:
        run('browser-install',['npx','--no-install','playwright','install','chromium'],FRONT)
    assertion('browser-startup', browser)
    run('diff-review',['git','-c',f'safe.directory={ROOT.as_posix()}','diff','--check'])
    assertion('inputs-unchanged', lambda: (_ for _ in ()).throw(AssertionError('Inputs changed during certification')) if fingerprint()!=start else None)
    groups = {
        'G1':['layout-and-docs'], 'G2':['frontend-install','frontend-build'],
        'G3':['backend-build','war-integrity'], 'G4':['frontend-tests','backend-build','test-execution-counts'],
        'G5':['initialization-logs','database-initialization','browser-startup','browser-smoke'],
        'G6':['node-version','npm-version','maven-version','java-version','python-version','java-reference','python-reference','python-dependencies','layout-and-docs'],
        'G7':['contracts'], 'G8':['java-reference','test-execution-counts'], 'G9':['python-venv','python-download','python-install','python-dependencies','python-reference'],
        'G10':['layout-and-docs','diff-review','inputs-unchanged']}
    by_name = {c['name']:c['status'] for c in checks}
    gates = {}
    for gate,names in groups.items():
        statuses = [by_name.get(name,'BLOCKED') for name in names]
        gates[gate] = 'FAIL' if 'FAIL' in statuses else 'BLOCKED' if 'BLOCKED' in statuses else 'PASS'
    statuses = list(gates.values()) + [c['status'] for c in checks]
    status = 'FAIL' if 'FAIL' in statuses else 'BLOCKED' if 'BLOCKED' in statuses else 'PASS'
    evidence = {'status':status,'utc':datetime.now(timezone.utc).isoformat(),'head':git('rev-parse','HEAD'),'branch':git('branch','--show-current'),'dirty':git('status','--short'),'inputFingerprint':start,'fingerprintAlgorithm':'SHA256(sorted path NUL SHA256(bytes)); tracked and nonignored untracked files excluding generated sprint-pass.md and evidence.json','platform':platform.platform(),'verificationCommand':[sys.executable,'scripts/b0/verify.py',*sys.argv[1:]],'browserChannel':OPTIONS.browser_channel or 'pinned-chromium','checks':checks,'gates':gates,'runDirectory':str(RUN.relative_to(ROOT))}
    report = ROOT/'docs/backend-support/b0'
    (report/'evidence.json').write_text(json.dumps(evidence,indent=2)+'\n',encoding='utf-8')
    lines = [f'# Sprint B0 {status}', '', f'Tested {evidence["utc"]} on `{evidence["branch"]}`, HEAD `{evidence["head"]}`.', ('The working tree is dirty. HEAD alone does not identify these changes.' if evidence['dirty'] else 'The tested working tree was clean.'), f'Input fingerprint: `{start}`.', '', 'Fingerprint covers tracked and nonignored untracked files, excluding this generated report and evidence.json.', f'Full commands, working directories, exit codes, dirty state and evidence paths: [evidence.json](evidence.json). Run logs: `{evidence["runDirectory"]}`.', '', '| Gate | Result |', '|---|---|']
    lines += [f'| {g} | {s} |' for g,s in gates.items()]
    lines += ['', '## Checks', ''] + [f'- {c["name"]}: {c["status"]} (exit {c["exitCode"]})' for c in checks]
    lines += ['', '## Scope and limitations', '', 'Health/startup and bounded adapter tests do not certify production auth or generated SQL. Python RedisStore is exercised against fakeredis; live shared Redis and concurrent auth are later gates. Browser smoke blocks third-party fonts/CAPTCHA scripts and does not verify CAPTCHA or login. Java 17 API/bytecode is enforced; actual executing JDK is recorded in java-version.log. Docker deployment, real PostgreSQL and production services are outside this run.', '', 'B1 readiness: '+('Ready for the bounded B1 shell/catalog/validation work; follow b1-handoff.md.' if status=='PASS' else 'Not certified. Resolve failed/blocked checks and rerun python scripts/b0/verify.py.'), '']
    (report/'sprint-pass.md').write_text('\n'.join(lines),encoding='utf-8')
    print(f'B0 {status}: {RUN}',flush=True)
    return 0 if status=='PASS' else 1

if __name__=='__main__':
    sys.exit(main())
