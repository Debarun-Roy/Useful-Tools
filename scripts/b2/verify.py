"""Certify B2, including exactly one nested B1/B0 regression run. Windows local/CI."""
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

ROOT=Path(__file__).resolve().parents[2];DOC=ROOT/'docs/backend-support/b2'
RUN=ROOT/'.b2'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ');RUN.mkdir(parents=True)
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--browser-channel',choices=['chrome','msedge']);args=parser.parse_args()
env=os.environ.copy();env.update(VITE_API_BASE='/api',VITE_RECAPTCHA_SITE_KEY='',RECAPTCHA_SECRET_KEY='',PYTHONUTF8='1')
checks=[]
class Blocked(RuntimeError): pass
def git(*args):return subprocess.check_output(['git','-c',f'safe.directory={ROOT.as_posix()}',*args],cwd=ROOT,encoding='utf-8').strip()
def fingerprint():
    excluded={f'docs/backend-support/{s}/{n}' for s in ['b0','b1','b2'] for n in ['sprint-pass.md','evidence.json','continuation.md']}
    h=hashlib.sha256()
    for name in sorted(set(git('ls-files','-c','-o','--exclude-standard').splitlines())):
        p=ROOT/name
        if name not in excluded and p.is_file():h.update(name.encode()+b'\0'+hashlib.sha256(p.read_bytes()).digest())
    return h.hexdigest()
def run(name,command,cwd=ROOT,timeout=1800):
    command=list(map(str,command));command[0]=shutil.which(command[0]) or command[0];print('CHECK',name,flush=True)
    try:
        p=subprocess.run(command,cwd=cwd,env=env,capture_output=True,encoding='utf-8',errors='replace',timeout=timeout)
        output=p.stdout+p.stderr;code=p.returncode;status='PASS' if code==0 else 'FAIL'
        if code and re.search('Permission denied|WinError 10013|EPERM|Could not transfer artifact|Executable doesn.t exist|BLOCKED:',output):status='BLOCKED'
    except (OSError,subprocess.TimeoutExpired) as error:output=str(error);code=None;status='BLOCKED'
    output=re.sub(r'(?i)(JSESSIONID|XSRF-TOKEN|csrfToken|password)(\s*[=:]\s*)[^\s;,]+',r'\1\2[REDACTED]',output)
    log=RUN/(name+'.log');log.write_text(output,encoding='utf-8')
    checks.append(dict(name=name,status=status,command=command,cwd=str(cwd),exitCode=code,log=str(log.relative_to(ROOT))))
    print(status,name,flush=True);return status=='PASS'
def check(name,fn):
    try:detail=fn();checks.append(dict(name=name,status='PASS',exitCode=0,detail=detail));print('PASS',name,flush=True)
    except Blocked as error:checks.append(dict(name=name,status='BLOCKED',exitCode=None,detail=str(error)));print('BLOCKED',name,str(error),flush=True)
    except Exception as error:checks.append(dict(name=name,status='FAIL',exitCode=1,detail=str(error)));print('FAIL',name,str(error),flush=True)
def result(name):
    file=RUN/'application'/name
    assert file.exists(),f'Missing required {name}'
    return json.loads(file.read_text(encoding='utf-8'))
def status(name):
    r=result(name);assert r['status']=='PASS',r;return r
def database(target):
    r=result('database-results.json')
    if r.get(target,{}).get('status')!='PASS' and ('BLOCKED:' in r.get('failure','') or any(c['name']=='b2-runtime' and c['status']=='BLOCKED' for c in checks)):
        raise Blocked(r.get('failure','Database execution prerequisite unavailable'))
    assert r.get(target,{}).get('status')=='PASS',r;return r[target]
def units():
    counts={}
    for name,minimum in [('backendsupport.GenerationTest',10),('backendsupport.ValidationTest',11),('backendsupport.AvailabilityTest',1),('baseline.BaselineTest',3)]:
        node=ET.parse(ROOT/f'Useful-Tools/target/surefire-reports/TEST-{name}.xml').getroot()
        assert int(node.attrib['tests'])>=minimum and all(int(node.attrib.get(k,0))==0 for k in ['failures','errors','skipped'])
        counts[name]=int(node.attrib['tests'])
    return counts
def artifacts():
    app=RUN/'application';preview=json.loads((app/'browser-preview.json').read_text(encoding='utf-8'))
    with zipfile.ZipFile(app/'browser-export.zip') as z:
        assert set(z.namelist())=={f['path'] for f in preview['files']}
        assert sum(i.file_size for i in z.infolist())<=5*1024*1024
        for file in preview['files']:assert z.read(file['path'])==file['content'].encode()
        for file in preview['manifest']['core']['files']:assert hashlib.sha256(z.read(file['path'])).hexdigest()==file['sha256']
    log=(app/'server.log').read_text(encoding='utf-8')
    assert all(v not in log for v in ['privacy-sentinel','private-value-sentinel','customers_renamed','CREATE TABLE','O\'Brien'])
    assert not (app/'sessions.json').exists()
    with zipfile.ZipFile(ROOT/'Useful-Tools/target/UsefulTools.war') as z:
        names=z.namelist();assert 'WEB-INF/classes/backendsupport/GenerationController.class' in names
        assert not any('b1/Server' in n or 'spike/LocalServer' in n or 'postgres.exe' in n for n in names)
    return 'Browser ZIP matches preview and manifest; no schema sentinels in application logs; ephemeral sessions removed; test runtime absent from WAR'
def review():
    for name in ['README.md','decisions.md','requirements-to-tests.md','continuation.md','b3-handoff.md','review.md']:
        assert (DOC/name).stat().st_size>200,name
    assert (ROOT/'.github/workflows/b2.yml').exists()
    frozen=json.loads((ROOT/'scripts/b2/frozen-contracts.json').read_text())
    for name,digest in frozen.items():assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest,name
    assert json.loads((DOC/'historical-b1/evidence.json').read_text())['inputFingerprint']=='32750477c5a016426752df6036b75042107017f822ed91a8d53c92f2b1e0448e'
    return 'Frozen B0 contracts unchanged; historical B1 certificate retained; docs and CI present'

start=fingerprint();originals={}
for sprint in ['b0','b1']:
    for name in ['evidence.json','sprint-pass.md']:
        p=ROOT/f'docs/backend-support/{sprint}/{name}';originals[p]=p.read_bytes()
        (RUN/f'historical-{sprint}-{name}').write_bytes(originals[p])
try:
    command=[sys.executable,'-X','utf8','scripts/b1/verify.py']
    if args.browser_channel:command+=['--browser-channel',args.browser_channel]
    try:
        run('b1-and-b0-regressions',command)
        for name in ['evidence.json','sprint-pass.md']:shutil.copy2(ROOT/'docs/backend-support/b1'/name,RUN/('b1-regression-'+name))
    finally:
        for p,content in originals.items():p.write_bytes(content)
    run('b2-frontend-units',['npm','run','test:b2'],ROOT/'usefultools-frontend')
    command=[sys.executable,'-X','utf8','scripts/b2/runtime_checks.py',RUN/'application']
    if args.browser_channel:command+=['--browser-channel',args.browser_channel]
    run('b2-runtime',command,timeout=900)
    check('java-unit-counts',units)
    check('http-security',lambda:status('http-results.json'))
    check('sqlite-execution',lambda:database('sqlite'))
    check('postgresql-execution',lambda:database('postgresql'))
    check('browser-workflow',lambda:status('browser-results.json'))
    check('artifact-and-privacy-review',artifacts)
    regression=json.loads((RUN/'b1-regression-evidence.json').read_text())
    b0=regression['b0Regression'];vpy=ROOT/b0['runDirectory']/('venv/Scripts/python.exe' if os.name=='nt' else 'venv/bin/python')
    run('b2-contracts',[vpy,'-X','utf8','scripts/b2/contracts.py',RUN/'application'])
    check('documentation-frozen-contracts-review',review)
    run('diff-check',['git','-c',f'safe.directory={ROOT.as_posix()}','diff','--check'])
    check('inputs-unchanged',lambda:'stable' if fingerprint()==start else (_ for _ in ()).throw(AssertionError('Inputs changed during certification')))
    check('historical-reports-preserved',lambda:all(p.read_bytes()==content for p,content in originals.items()) or (_ for _ in ()).throw(AssertionError('Historical report changed')))
except Exception as error:checks.append(dict(name='verifier-runtime',status='BLOCKED',exitCode=None,detail=str(error)))
finally:
    for p,content in originals.items():p.write_bytes(content)

groups={'G1':['b1-and-b0-regressions'],'G2':['b2-contracts','documentation-frozen-contracts-review'],
 'G3':['java-unit-counts','http-security'],'G4':['sqlite-execution'],'G5':['postgresql-execution'],
 'G6':['java-unit-counts','artifact-and-privacy-review'],'G7':['http-security','artifact-and-privacy-review'],
 'G8':['browser-workflow','b2-frontend-units','artifact-and-privacy-review'],
 'G9':['b1-and-b0-regressions','b2-runtime','b2-contracts','historical-reports-preserved'],
 'G10':['documentation-frozen-contracts-review','diff-check','inputs-unchanged']}
lookup={c['name']:c['status'] for c in checks}
gates={g:'PASS' if all(lookup.get(n)=='PASS' for n in names) else 'FAIL' if any(lookup.get(n)=='FAIL' for n in names) else 'BLOCKED' for g,names in groups.items()}
verdict='FAIL' if 'FAIL' in gates.values() or any(c['status']=='FAIL' for c in checks) else 'BLOCKED' if 'BLOCKED' in gates.values() or any(c['status']=='BLOCKED' for c in checks) else 'PASS'
evidence=dict(sprint='B2',verdict=verdict,timestamp=datetime.now(timezone.utc).isoformat(),head=git('rev-parse','HEAD'),branch=git('branch','--show-current'),dirty=git('status','--porcelain'),inputFingerprint=start,finalInputFingerprint=fingerprint(),checks=checks,gates=gates,runDirectory=str(RUN.relative_to(ROOT)),browserChannel=args.browser_channel or 'pinned-chromium',limitations=['Hosted CI and deployment not executed','Java 17 runtime not executed; release 17 compilation on JDK 25','PostgreSQL 17.11 only; Windows certification host','No B3 or B7 load qualification; 20-table times are local sequential HTTP observations','External CAPTCHA inert in verification; account login replaced by external synthetic sessions, actual production filters execute'])
if (RUN/'b1-regression-evidence.json').exists():
    evidence['b1Regression']=json.loads((RUN/'b1-regression-evidence.json').read_text());evidence['versions']=evidence['b1Regression'].get('versions',{})
for name in ['http-results.json','database-results.json','browser-results.json','runtime-results.json']:
    if (RUN/'application'/name).exists():evidence[name]=result(name)
(DOC/'evidence.json').write_text(json.dumps(evidence,indent=2)+'\n',encoding='utf-8')
(RUN/'evidence.json').write_bytes((DOC/'evidence.json').read_bytes())
lines=['# B2 sprint report','',f'Verdict: **{verdict}**',f'Tested: {evidence["timestamp"]}',f'HEAD: `{evidence["head"]}` (uncommitted)',f'Input fingerprint: `{start}`','', '| Gate | Result |','|---|---|']+[f'| {g} | {s} |' for g,s in gates.items()]
lines+=['', 'Exact command: `python -X utf8 scripts/b2/verify.py'+(f' --browser-channel {args.browser_channel}' if args.browser_channel else '')+'`',f'Logs, exported synthetic fixtures and screenshots: `{RUN.relative_to(ROOT)}`.', 'Commands, working directories, exit codes, counts, database/browser versions and B1/B0 regression evidence are in evidence.json. Historical B0/B1 reports were restored byte-for-byte.','']+['- '+s for s in evidence['limitations']]
lines+=['','B3 readiness requires every gate PASS. See requirements-to-tests.md, decisions.md, continuation.md, review.md and b3-handoff.md. No B3 work was started.']
(DOC/'sprint-pass.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
(RUN/'sprint-pass.md').write_bytes((DOC/'sprint-pass.md').read_bytes())
print(verdict,'B2',RUN,flush=True);sys.exit(0 if verdict=='PASS' else 1)
