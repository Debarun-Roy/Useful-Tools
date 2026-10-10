"""Certify B1 from repository root: python -X utf8 scripts/b1/verify.py [--browser-channel chrome]."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
DOC = ROOT/'docs/backend-support/b1'
RUN = ROOT/'.b1'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
RUN.mkdir(parents=True)
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--browser-channel',choices=['chrome','msedge'])
args = parser.parse_args()
env = os.environ.copy()
env.update(VITE_API_BASE='/api',VITE_RECAPTCHA_SITE_KEY='',RECAPTCHA_SECRET_KEY='',PYTHONUTF8='1')
checks=[]

def git(*args):
    return subprocess.check_output(['git','-c',f'safe.directory={ROOT.as_posix()}',*args],cwd=ROOT,text=True,encoding='utf-8').strip()
def fingerprint():
    excluded={'docs/backend-support/b0/sprint-pass.md','docs/backend-support/b0/evidence.json',
              'docs/backend-support/b1/sprint-pass.md','docs/backend-support/b1/evidence.json','docs/backend-support/b1/continuation.md'}
    digest=hashlib.sha256()
    for name in sorted(set(git('ls-files','-c','-o','--exclude-standard').splitlines())):
        file=ROOT/name
        if name in excluded or not file.is_file():continue
        digest.update(name.encode()+b'\0'+hashlib.sha256(file.read_bytes()).digest())
    return digest.hexdigest()
def run(name,command,cwd=ROOT,timeout=1200):
    command=[str(c) for c in command];command[0]=shutil.which(command[0]) or command[0]
    print('CHECK',name,flush=True)
    try:
        proc=subprocess.run(command,cwd=cwd,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=timeout,encoding='utf-8',errors='replace')
        output=proc.stdout;code=proc.returncode;status='PASS' if code==0 else 'FAIL'
        if code and re.search('Permission denied|WinError 10013|EPERM|Could not transfer artifact|Executable doesn.t exist',output):status='BLOCKED'
    except (OSError,subprocess.TimeoutExpired) as error:output=str(error);code=None;status='BLOCKED'
    output=re.sub(r'(?i)(secret|password|token)(\s*[=:]\s*)[^\s,;]+',r'\1\2[REDACTED]',output)
    logfile=RUN/(name+'.log');logfile.write_text(output,encoding='utf-8')
    checks.append(dict(name=name,status=status,exitCode=code,command=command,cwd=str(cwd),log=str(logfile.relative_to(ROOT))))
    print(status,name,flush=True)
    return status=='PASS'
def check(name,fn):
    try:detail=fn();checks.append(dict(name=name,status='PASS',exitCode=0,detail=detail))
    except Exception as error:checks.append(dict(name=name,status='FAIL',exitCode=1,detail=str(error)))
def documents():
    for name in ['README.md','decisions.md','b2-handoff.md','continuation.md']:
        assert (DOC/name).stat().st_size>200,name
    assert (ROOT/'.github/workflows/b1.yml').is_file()
    transport=json.loads((ROOT/'contracts/backend-support/b1/validation-transport.schema.json').read_text())
    assert len(transport['oneOf'])==2
    with zipfile.ZipFile(ROOT/'Useful-Tools/target/UsefulTools.war') as war:
        assert not any('b1/Server' in name or 'spike/LocalServer' in name for name in war.namelist())
        assert 'WEB-INF/classes/backendsupport/BackendSupportController.class' in war.namelist()
    return 'Docs, separate transport, production WAR excludes test session/SPA fixtures'
def test_counts():
    results={}
    for name,minimum in [('backendsupport.ValidationTest',8),('backendsupport.AvailabilityTest',1),('baseline.BaselineTest',3)]:
        suite=ET.parse(ROOT/f'Useful-Tools/target/surefire-reports/TEST-{name}.xml').getroot()
        assert int(suite.attrib['tests'])>=minimum
        assert all(int(suite.attrib.get(k,0))==0 for k in ['failures','errors','skipped'])
        results[name]=int(suite.attrib['tests'])
    return results

start=fingerprint()
originals={name:(ROOT/'docs/backend-support/b0'/name).read_bytes() for name in ['evidence.json','sprint-pass.md']}
try:
    b0command=[sys.executable,'-X','utf8','scripts/b0/verify.py']
    if args.browser_channel:b0command+=['--browser-channel',args.browser_channel]
    try:
        run('b0-regression',b0command,timeout=1800)
        for name in originals:shutil.copy2(ROOT/'docs/backend-support/b0'/name,RUN/('b0-regression-'+name))
    finally:
        for name,content in originals.items():(ROOT/'docs/backend-support/b0'/name).write_bytes(content)
    run('b1-frontend-tests',['npm','run','test:b1'],ROOT/'usefultools-frontend')
    run('b1-runtime-build',['mvn','-B','-ntp','test-compile','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath','-Dmdep.outputFile=classpath.txt'],ROOT/'scripts/b1/runtime')
    runtime=[sys.executable,'-X','utf8','scripts/b1/runtime_checks.py',RUN/'application']
    if args.browser_channel:runtime+=['--browser-channel',args.browser_channel]
    run('b1-http-browser',runtime,timeout=600)
    check('test-counts',test_counts)
    check('documentation-and-production-isolation',documents)
    run('diff-check',['git','-c',f'safe.directory={ROOT.as_posix()}','diff','--check'])
    check('inputs-unchanged',lambda: 'stable' if fingerprint()==start else (_ for _ in ()).throw(AssertionError('Certification inputs changed')))
except Exception as error:checks.append(dict(name='verification-runtime',status='BLOCKED',detail=str(error),exitCode=None))

groups={'G1':['b0-regression'],'G2':['b1-http-browser','documentation-and-production-isolation'],
        'G3':['test-counts','b1-http-browser'],'G4':['b1-http-browser'],'G5':['test-counts','b1-http-browser'],
        'G6':['b1-frontend-tests','b1-http-browser'],'G7':['b1-http-browser'],'G8':['b1-http-browser'],
        'G9':['b0-regression','b1-runtime-build','b1-http-browser'],'G10':['diff-check','documentation-and-production-isolation','inputs-unchanged']}
lookup={c['name']:c['status'] for c in checks}
gates={key:('PASS' if all(lookup.get(n)=='PASS' for n in names) else 'FAIL' if any(lookup.get(n)=='FAIL' for n in names) else 'BLOCKED') for key,names in groups.items()}
verdict='FAIL' if any(c['status']=='FAIL' for c in checks) else 'BLOCKED' if any(c['status']=='BLOCKED' for c in checks) or 'BLOCKED' in gates.values() else 'PASS'
evidence=dict(sprint='B1',verdict=verdict,timestamp=datetime.now(timezone.utc).isoformat(),head=git('rev-parse','HEAD'),branch=git('branch','--show-current'),dirty=git('status','--porcelain'),inputFingerprint=start,finalInputFingerprint=fingerprint(),browserChannel=args.browser_channel or 'pinned-chromium',checks=checks,gates=gates,runDirectory=str(RUN.relative_to(ROOT)),limitations=['Hosted CI not executed','No production deployment, generated code, live PostgreSQL/Redis or Java 17 runtime execution','Synthetic sessions bypass account login only in external test container; real application filters execute','External fonts/CAPTCHA inert; error scenarios use explicit browser fault injection'])
for name in ['http-results.json','browser-results.json']:
    file=RUN/'application'/name
    if file.exists():evidence[name]=json.loads(file.read_text())
regression_file=RUN/'b0-regression-evidence.json'
if regression_file.exists():
    regression=json.loads(regression_file.read_text())
    evidence['b0Regression']=regression
    evidence['versions']={}
    for c in regression.get('checks',[]):
        if c['name'].endswith('-version') and c.get('log'):
            evidence['versions'][c['name']]=(ROOT/c['log']).read_text(encoding='utf-8').strip()
(DOC/'evidence.json').write_text(json.dumps(evidence,indent=2)+'\n',encoding='utf-8')
lines=['# B1 sprint report','',f'Verdict: **{verdict}**',f'Tested: {evidence["timestamp"]}',f'HEAD: `{evidence["head"]}` (uncommitted working tree)',f'Input fingerprint: `{start}`','', '| Gate | Result |','|---|---|']
lines += [f'| {g} | {s} |' for g,s in gates.items()]
lines += ['',f'Exact command: `python -X utf8 scripts/b1/verify.py'+(f' --browser-channel {args.browser_channel}' if args.browser_channel else '')+'`',f'Logs and screenshots: `{RUN.relative_to(ROOT)}`. Commands, counts, environment/version logs and exit codes are linked in evidence.json and the preserved B0 regression report.','', '## Limitations','']+['- '+s for s in evidence['limitations']]
lines += ['','See decisions.md for policy and validation coverage, continuation.md for recovery, and b2-handoff.md for next-sprint boundaries. B2 prerequisites are satisfied only when every gate above is PASS.']
(DOC/'sprint-pass.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
print(verdict, 'B1',str(RUN),flush=True)
sys.exit(0 if verdict=='PASS' else 1)
