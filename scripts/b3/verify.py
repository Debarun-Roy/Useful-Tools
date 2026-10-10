"""Certify B3 on Windows; exactly one nested B2/B1/B0 run, with historical reports restored."""
import argparse,hashlib,json,os,re,shutil,subprocess,sys,zipfile
from datetime import datetime,timezone
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2];DOC=ROOT/'docs/backend-support/b3'
RUN=ROOT/'.b3'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ');RUN.mkdir(parents=True)
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--browser-channel',choices=['chrome','msedge']);args=parser.parse_args()
env=os.environ.copy();env.update(VITE_API_BASE='/api',VITE_RECAPTCHA_SITE_KEY='',RECAPTCHA_SECRET_KEY='',PYTHONUTF8='1');checks=[]
class Blocked(RuntimeError):pass
def git(*args):return subprocess.check_output(['git','-c',f'safe.directory={ROOT.as_posix()}',*args],cwd=ROOT,encoding='utf-8').strip()
def fingerprint():
    excluded={f'docs/backend-support/{s}/{n}' for s in ['b0','b1','b2','b3'] for n in ['sprint-pass.md','evidence.json','continuation.md']};h=hashlib.sha256()
    for name in sorted(set(git('ls-files','-c','-o','--exclude-standard').splitlines())):
        p=ROOT/name
        if name not in excluded and p.is_file():h.update(name.encode()+b'\0'+hashlib.sha256(p.read_bytes()).digest())
    return h.hexdigest()
def execute(name,command,cwd=ROOT,timeout=1800):
    command=list(map(str,command));command[0]=shutil.which(command[0]) or command[0];print('CHECK',name,flush=True)
    try:
        p=subprocess.run(command,cwd=cwd,env=env,capture_output=True,encoding='utf-8',errors='replace',timeout=timeout);output=p.stdout+p.stderr;code=p.returncode;status='PASS' if code==0 else 'FAIL'
        if code and re.search('Permission denied|WinError 10013|EPERM|Could not transfer artifact|Executable doesn.t exist|BLOCKED:',output):status='BLOCKED'
    except (OSError,subprocess.TimeoutExpired) as error:output=str(error);code=None;status='BLOCKED'
    output=re.sub(r'(?i)(JSESSIONID|XSRF-TOKEN|csrfToken|password)(\s*[=:]\s*)[^\s;,]+',r'\1\2[REDACTED]',output)
    log=RUN/(name+'.log');log.write_text(output,encoding='utf-8');checks.append(dict(name=name,status=status,command=command,cwd=str(cwd),exitCode=code,log=str(log.relative_to(ROOT))));print(status,name,flush=True)
def check(name,fn):
    try:detail=fn();checks.append(dict(name=name,status='PASS',exitCode=0,detail=detail));print('PASS',name,flush=True)
    except Blocked as e:checks.append(dict(name=name,status='BLOCKED',exitCode=None,detail=str(e)));print('BLOCKED',name,str(e),flush=True)
    except Exception as e:checks.append(dict(name=name,status='FAIL',exitCode=1,detail=str(e)));print('FAIL',name,str(e),flush=True)
def result(name):
    path=RUN/'application'/name
    if not path.exists():raise Blocked('Required evidence missing: '+name)
    return json.loads(path.read_text(encoding='utf-8'))
def passed(name):
    r=result(name);assert r['status']=='PASS',r;return r
def database(target):
    r=result('database-results.json')
    if 'BLOCKED:' in r.get('failure',''):raise Blocked(r['failure'])
    assert r.get(target,{}).get('status')=='PASS',r
    assert r[target]['migrations']==4 and r[target]['views']==6 and r[target]['preconditionFailure'] and r[target]['atomicRollback'] and r[target]['proceduresExecuted'];return r[target]
def units():
    node=ET.parse(ROOT/'Useful-Tools/target/surefire-reports/TEST-backendsupport.B3Test.xml').getroot();assert int(node.attrib['tests'])>=12 and all(int(node.attrib.get(k,0))==0 for k in ['failures','errors','skipped']);return dict(tests=int(node.attrib['tests']),failures=0,errors=0,skipped=0)
def b2_bytes():
    regression=json.loads((RUN/'b2-regression-evidence.json').read_text());assert regression['verdict']=='PASS'
    path=ROOT/regression['runDirectory']/'application';pins=json.loads((ROOT/'scripts/b3/b2-bundle-sha256.json').read_text())
    for name,digest in pins.items():assert hashlib.sha256((path/name).read_bytes()).hexdigest()==digest,name
    return 'Four actual B2 HTTP-export ZIPs byte-identical to the final released B2 certificate'
def artifacts():
    app=RUN/'application'
    for module in ['migration','view','evaluator']:
        p=json.loads((app/f'browser-{module}-preview.json').read_text(encoding='utf-8'))
        with zipfile.ZipFile(app/f'browser-{module}.zip') as z:
            assert set(z.namelist())=={f['path'] for f in p['files']}
            for f in p['files']:assert z.read(f['path'])==f['content'].encode()
            for f in p['manifest']['core']['files']:assert hashlib.sha256(z.read(f['path'])).hexdigest()==f['sha256']
    log=(app/'server.log').read_text(encoding='utf-8');assert not any(v in log for v in ['privacy-sentinel','private-value-sentinel','browser_note','browser_view','CREATE VIEW','ALTER TABLE','O\'Reilly'])
    for file in app.glob('*-preview.json'):
        p=json.loads(file.read_text(encoding='utf-8'));assert p['digest'] not in log
    assert not (app/'sessions.json').exists()
    with zipfile.ZipFile(ROOT/'Useful-Tools/target/UsefulTools.war') as z:
        names=z.namelist();assert all('WEB-INF/classes/backendsupport/'+c+'.class' in names for c in ['MigrationGeneration','ViewGeneration','SchemaEvaluation'])
        assert not any('b1/Server' in n or 'postgres.exe' in n or '/B3Test' in n or 'scripts/b3/' in n for n in names)
    return 'Three browser ZIPs match previews/manifests; submitted synthetic values and digests absent from application logs; no test harness in WAR'
def review():
    for name in ['README.md','decisions.md','requirements-to-tests.md','continuation.md','review.md','b4-handoff.md']:assert (DOC/name).stat().st_size>200,name
    assert 'python -X utf8 scripts/b3/verify.py' in (ROOT/'.github/workflows/b3.yml').read_text()
    for name,digest in json.loads((ROOT/'scripts/b3/frozen-contracts.json').read_text()).items():assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest,name
    return 'Frozen B0/B2 contracts retained; B3 scope/limits/review/handoff and matching CI command present'
start=fingerprint();originals={}
for sprint in ['b0','b1','b2']:
    for name in ['evidence.json','sprint-pass.md']:
        p=ROOT/f'docs/backend-support/{sprint}/{name}';originals[p]=p.read_bytes();(RUN/f'historical-{sprint}-{name}').write_bytes(originals[p])
try:
    command=[sys.executable,'-X','utf8','scripts/b2/verify.py']
    if args.browser_channel:command+=['--browser-channel',args.browser_channel]
    try:
        execute('b2-b1-b0-regressions',command)
        for name in ['evidence.json','sprint-pass.md']:shutil.copy2(ROOT/'docs/backend-support/b2'/name,RUN/('b2-regression-'+name))
    finally:
        for p,data in originals.items():p.write_bytes(data)
    check('released-b2-bytes',b2_bytes)
    command=[sys.executable,'-X','utf8','scripts/b3/runtime_checks.py',RUN/'application']
    if args.browser_channel:command+=['--browser-channel',args.browser_channel]
    execute('b3-runtime',command,timeout=900)
    check('b3-units',units);check('http-security',lambda:passed('http-results.json'))
    check('sqlite-execution',lambda:database('sqlite'));check('postgresql-execution',lambda:database('postgresql'))
    check('browser-workflows',lambda:passed('browser-results.json'));check('export-privacy-packaging',artifacts)
    regression=json.loads((RUN/'b2-regression-evidence.json').read_text());b0=regression['b1Regression']['b0Regression'];vpy=ROOT/b0['runDirectory']/('venv/Scripts/python.exe' if os.name=='nt' else 'venv/bin/python')
    execute('independent-contracts',[vpy,'-X','utf8','scripts/b3/contracts.py',RUN/'application'])
    check('documentation-review',review);execute('diff-check',['git','-c',f'safe.directory={ROOT.as_posix()}','diff','--check'])
    check('inputs-unchanged',lambda:'stable' if start==fingerprint() else (_ for _ in ()).throw(AssertionError('Inputs changed during certification')))
    check('history-preserved',lambda:all(p.read_bytes()==data for p,data in originals.items()) or (_ for _ in ()).throw(AssertionError('Historical reports changed')))
except Exception as e:checks.append(dict(name='verification-infrastructure',status='BLOCKED',exitCode=None,detail=str(e)))
finally:
    for p,data in originals.items():p.write_bytes(data)
groups={'G1':['b2-b1-b0-regressions','released-b2-bytes'],'G2':['independent-contracts','http-security'],'G3':['b3-units','http-security'],
 'G4':['sqlite-execution','postgresql-execution'],'G5':['sqlite-execution','postgresql-execution'],'G6':['b3-units','http-security','independent-contracts'],
 'G7':['http-security','export-privacy-packaging'],'G8':['browser-workflows','export-privacy-packaging'],'G9':['b2-b1-b0-regressions','b3-runtime','independent-contracts','history-preserved','documentation-review'],
 'G10':['documentation-review','diff-check','inputs-unchanged']}
lookup={c['name']:c['status'] for c in checks};gates={g:'PASS' if all(lookup.get(n)=='PASS' for n in names) else 'FAIL' if any(lookup.get(n)=='FAIL' for n in names) else 'BLOCKED' for g,names in groups.items()}
verdict='FAIL' if 'FAIL' in gates.values() or any(c['status']=='FAIL' for c in checks) else 'BLOCKED' if 'BLOCKED' in gates.values() or any(c['status']=='BLOCKED' for c in checks) else 'PASS'
evidence=dict(sprint='B3',verdict=verdict,timestamp=datetime.now(timezone.utc).isoformat(),head=git('rev-parse','HEAD'),branch=git('branch','--show-current'),dirty=git('status','--porcelain'),inputFingerprint=start,finalInputFingerprint=fingerprint(),checks=checks,gates=gates,runDirectory=str(RUN.relative_to(ROOT)),browserChannel=args.browser_channel or 'pinned-chromium',limitations=['Windows certification host; pinned PostgreSQL/psql 17.11 only','JDK 25 release-17 compilation; Java 17 runtime not executed','No hosted CI, deployment, production database or B7 load qualification','Schema shape prechecks do not prove default/FK/CHECK/collation equivalence; maintenance and operator review required','Column additions have no lossless automatic down script; unsupported operations block complete plans','Synthetic session fixture is test-only; actual production security filters execute'])
if (RUN/'b2-regression-evidence.json').exists():evidence['b2Regression']=json.loads((RUN/'b2-regression-evidence.json').read_text());evidence['versions']=evidence['b2Regression'].get('versions',{})
for name in ['http-results.json','database-results.json','browser-results.json','runtime-results.json','contract-results.json']:
    if (RUN/'application'/name).exists():evidence[name]=result(name)
(DOC/'evidence.json').write_text(json.dumps(evidence,indent=2)+'\n',encoding='utf-8');(RUN/'evidence.json').write_bytes((DOC/'evidence.json').read_bytes())
lines=['# B3 sprint report','',f'Verdict: **{verdict}**',f'Tested: {evidence["timestamp"]}',f'HEAD: `{evidence["head"]}` (dirty; no commit)',f'Input fingerprint: `{start}`','', '| Gate | Result |','|---|---|']+[f'| {g} | {s} |' for g,s in gates.items()]
lines+=['','Command: `python -X utf8 scripts/b3/verify.py'+(f' --browser-channel {args.browser_channel}' if args.browser_channel else '')+'`',f'Evidence directory: `{RUN.relative_to(ROOT)}`. Exact commands, exits, versions and counts are in evidence.json. Historical B0/B1/B2 reports restored byte-for-byte.','']+['- '+v for v in evidence['limitations']]
lines+=['','B4 prerequisites are satisfied only when all B3 gates PASS. Follow b4-handoff.md; B4 has not started.']
(DOC/'sprint-pass.md').write_text('\n'.join(lines)+'\n',encoding='utf-8');(RUN/'sprint-pass.md').write_bytes((DOC/'sprint-pass.md').read_bytes());print(verdict,'B3',RUN,flush=True);sys.exit(0 if verdict=='PASS' else 1)
