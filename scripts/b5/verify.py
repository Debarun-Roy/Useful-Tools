"""B5 certification: one historical regression chain, actual exported WARs and unchanged inputs."""
import argparse,hashlib,json,os,re,shutil,subprocess,sys,zipfile
from datetime import datetime,timezone
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2];DOC=ROOT/'docs/backend-support/b5'
RUN=ROOT/'.b5'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ');RUN.mkdir(parents=True)
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--browser-channel',choices=['chrome','msedge']);args=parser.parse_args()
env=os.environ.copy();env.update(VITE_API_BASE='/api',VITE_RECAPTCHA_SITE_KEY='',RECAPTCHA_SECRET_KEY='',PYTHONUTF8='1');checks=[]
class Blocked(RuntimeError):pass
def git(*args):return subprocess.check_output(['git','-c',f'safe.directory={ROOT.as_posix()}',*args],cwd=ROOT,encoding='utf-8').strip()
def fingerprint():
    excluded={f'docs/backend-support/b{i}/{name}' for i in range(6) for name in ['sprint-pass.md','evidence.json','continuation.md']};h=hashlib.sha256()
    for name in sorted(set(git('ls-files','-c','-o','--exclude-standard').splitlines())):
        p=ROOT/name
        if name not in excluded and p.is_file():h.update(name.encode()+b'\0'+hashlib.sha256(p.read_bytes()).digest())
    return h.hexdigest()
def execute(name,command,cwd=ROOT,timeout=2400):
    command=list(map(str,command));command[0]=shutil.which(command[0]) or command[0];print('CHECK',name,flush=True)
    try:
        p=subprocess.run(command,cwd=cwd,env=env,capture_output=True,encoding='utf-8',errors='replace',timeout=timeout);output=p.stdout+p.stderr;code=p.returncode;status='PASS' if code==0 else 'FAIL'
        if code and re.search('Permission denied|WinError 10013|EPERM|Could not transfer artifact|Executable doesn.t exist|BLOCKED:',output):status='BLOCKED'
    except (OSError,subprocess.TimeoutExpired) as error:output=str(error);code=None;status='BLOCKED'
    output=re.sub(r'(?i)(JSESSIONID|XSRF-TOKEN|csrfToken|captchaToken|password)(\s*[=:]\s*)[^\s;,]+',r'\1\2[REDACTED]',output)
    log=RUN/(name+'.log');log.write_text(output,encoding='utf-8');checks.append(dict(name=name,status=status,command=command,cwd=str(cwd),exitCode=code,log=str(log.relative_to(ROOT))));print(status,name,flush=True)
def check(name,fn):
    try:detail=fn();checks.append(dict(name=name,status='PASS',exitCode=0,detail=detail));print('PASS',name,flush=True)
    except Blocked as error:checks.append(dict(name=name,status='BLOCKED',exitCode=None,detail=str(error)));print('BLOCKED',name,flush=True)
    except Exception as error:checks.append(dict(name=name,status='FAIL',exitCode=1,detail=str(error)));print('FAIL',name,str(error),flush=True)
def result(path):
    p=RUN/path
    if not p.exists():raise Blocked('Required evidence missing: '+path)
    value=json.loads(p.read_text());assert value['status']=='PASS';return value
def prior_bytes():
    prior=json.loads((RUN/'b4-regression-evidence.json').read_text());assert prior['verdict']=='PASS'
    pins=json.loads((ROOT/'scripts/b5/b4-bundle-sha256.json').read_text());directory=ROOT/prior['runDirectory']/'application'
    for name,digest in pins.items():assert hashlib.sha256((directory/name).read_bytes()).hexdigest()==digest,name
    return dict(byteIdenticalBundles=len(pins),priorRun=prior['runDirectory'])
def units():
    suite=ET.parse(ROOT/'Useful-Tools/target/surefire-reports/TEST-backendsupport.B5Test.xml').getroot();assert int(suite.attrib['tests'])>=6 and all(int(suite.attrib.get(k,0))==0 for k in ['failures','errors','skipped']);return dict(tests=int(suite.attrib['tests']),skipped=0)
def applications():
    value=result('applications/application-results.json');assert not value['diagnostic'] and len(value['applications'])==8
    names=set();requests=0
    for app in value['applications']:
        assert app['status']=='PASS' and len(app['groups'])>=11 and app['versions']['sessionCapacity']=='4096'
        assert any('actual WAR rejects' in s for s in app['groups']) and any('shared framework-neutral' in s for s in app['groups'])
        assert any('restart invalidates' in s for s in app['groups']);names.add(app['name']);requests+=app['requests']
        assert all(c['exitCode']==0 or c.get('expectedFailure') and c['exitCode']!=0 for c in app['commands'])
    assert names=={f'{database}-{captcha}-{module}' for database in ['sqlite','postgresql'] for captcha in ['off','recaptcha-v3'] for module in ['core','profile']}
    for name in names:
        if name.endswith('profile'):result('applications/'+name+'/restart/browser-results.json')
    return dict(projects=8,requests=requests,versions={a['name']:a['versions'] for a in value['applications']})
def packaging():
    app=RUN/'exports';preview=json.loads((app/'browser-auth-preview.json').read_text())
    with zipfile.ZipFile(app/'browser-auth.zip') as archive:
        assert set(archive.namelist())=={f['path'] for f in preview['files']}
        for f in preview['files']:assert archive.read(f['path'])==f['content'].encode()
    log=(app/'generator.log').read_text();assert 'private-b5-sentinel' not in log and not (app/'sessions.json').exists()
    for p in app.glob('*-preview.json'):assert json.loads(p.read_text())['digest'] not in log
    with zipfile.ZipFile(ROOT/'Useful-Tools/target/UsefulTools.war') as archive:
        names=archive.namelist();assert 'WEB-INF/classes/backendsupport/AuthGeneration.class' in names
        assert 'WEB-INF/classes/backendsupport/b5/AuthServlet.java.txt' in names
        assert not any('example/auth/' in n or 'b5/StarterServer' in n or 'b1/Server.class' in n or '/B5Test.class' in n for n in names)
    return 'Actual browser ZIP matches preview; no source/digest logging or compiled generated runtime/test fixture deployed'
def review():
    for name in ['README.md','decisions.md','requirements-to-tests.md','continuation.md','review.md','threat-model.md','b6-handoff.md']:assert (DOC/name).stat().st_size>200,name
    assert 'python -X utf8 scripts/b5/verify.py' in (ROOT/'.github/workflows/b5.yml').read_text()
    for name,digest in json.loads((ROOT/'scripts/b5/frozen-contracts.json').read_text()).items():assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest,name
    return 'Frozen contracts/templates, traceability, self-review, threat model, conditional B6 handoff and matching CI'
def cleanup_owned():
    # Only paths created beneath this verifier's fresh run are eligible for cleanup.
    for log in list(RUN.rglob('server.log'))+list(RUN.rglob('generator.log')):
        directory=log.parent.resolve();assert directory.is_relative_to(RUN.resolve());(directory/'stop').touch()
    for pid in RUN.rglob('postmaster.pid'):
        directory=pid.parent.resolve();assert directory.is_relative_to(RUN.resolve()) and directory.name=='pgdata'
        binary=ROOT/'.b2/postgresql/pgsql/bin/pg_ctl.exe'
        if binary.exists():
            result=subprocess.run([binary,'-D',directory,'-m','fast','-w','stop'],capture_output=True,timeout=30)
            checks.append(dict(name='owned-postgres-cleanup',status='PASS' if result.returncode==0 else 'FAIL',command=[str(binary),'-D',str(directory),'-m','fast','-w','stop'],cwd=str(ROOT),exitCode=result.returncode))
    return 'Owned stop markers issued; no foreign process or database touched'
start=fingerprint();originals={}
for sprint in range(5):
    for name in ['evidence.json','sprint-pass.md']:
        p=ROOT/f'docs/backend-support/b{sprint}/{name}';originals[p]=p.read_bytes();(RUN/f'historical-b{sprint}-{name}').write_bytes(originals[p])
try:
    command=[sys.executable,'-X','utf8','scripts/b4/verify.py']
    if args.browser_channel:command+=['--browser-channel',args.browser_channel]
    try:
        execute('b4-b3-b2-b1-b0-regressions',command,timeout=3600)
        for name in ['evidence.json','sprint-pass.md']:shutil.copy2(ROOT/'docs/backend-support/b4'/name,RUN/('b4-regression-'+name))
    finally:
        for p,data in originals.items():p.write_bytes(data)
    check('released-bundle-bytes',prior_bytes);check('b5-units',units)
    execute('verification-environment',[sys.executable,'-m','venv',RUN/'venv'])
    vpy=RUN/'venv/Scripts/python.exe';execute('verification-dependencies',[vpy,'-m','pip','install','-r',ROOT/'scripts/b5/requirements.txt'])
    execute('verification-inventory',[vpy,'-m','pip','freeze']);execute('java-version',['java','-version']);execute('maven-version',['mvn','-version']);execute('node-version',['node','--version']);execute('python-version',[vpy,'--version'])
    command=[sys.executable,'-X','utf8','scripts/b5/export_checks.py',RUN/'exports','--browser']
    if args.browser_channel:command+=['--browser-channel',args.browser_channel]
    execute('generator-http-browser',command,timeout=600)
    check('generator-http',lambda:result('exports/export-results.json'));check('generator-browser',lambda:result('exports/browser-results.json'))
    execute('independent-contracts',[vpy,'-X','utf8','scripts/b5/contracts.py',RUN/'exports'])
    command=[vpy,'-X','utf8','scripts/b5/application_checks.py',RUN/'exports',RUN/'applications']
    if args.browser_channel:command+=['--browser-channel',args.browser_channel]
    execute('exported-applications',command,timeout=2400);check('complete-application-matrix',applications)
    check('packaging-privacy',packaging);check('documentation-review',review)
    execute('diff-check',['git','-c',f'safe.directory={ROOT.as_posix()}','diff','--check'])
    check('inputs-unchanged',lambda:'stable' if start==fingerprint() else (_ for _ in ()).throw(AssertionError('Inputs changed during certification')))
    check('history-preserved',lambda:all(p.read_bytes()==data for p,data in originals.items()) or (_ for _ in ()).throw(AssertionError('Historical reports changed')))
except Exception as error:checks.append(dict(name='verification-infrastructure',status='BLOCKED',exitCode=None,detail=str(error)))
finally:
    for p,data in originals.items():p.write_bytes(data)
    check('owned-cleanup',cleanup_owned)
groups={'G1':['b4-b3-b2-b1-b0-regressions','released-bundle-bytes'],'G2':['b5-units','independent-contracts','generator-http'],
 'G3':['exported-applications','complete-application-matrix'],'G4':['complete-application-matrix'],'G5':['complete-application-matrix','b5-units'],
 'G6':['complete-application-matrix'],'G7':['independent-contracts','complete-application-matrix'],'G8':['packaging-privacy','generator-http','generator-browser'],
 'G9':['generator-http-browser','exported-applications','history-preserved','documentation-review','owned-cleanup'],'G10':['documentation-review','diff-check','inputs-unchanged']}
lookup={c['name']:c['status'] for c in checks};gates={g:'PASS' if all(lookup.get(n)=='PASS' for n in names) else 'FAIL' if any(lookup.get(n)=='FAIL' for n in names) else 'BLOCKED' for g,names in groups.items()}
verdict='FAIL' if any(c['status']=='FAIL' for c in checks) or 'FAIL' in gates.values() else 'BLOCKED' if any(c['status']=='BLOCKED' for c in checks) or 'BLOCKED' in gates.values() else 'PASS'
evidence=dict(sprint='B5',module='BS07–BS08 Java auth starter',verdict=verdict,timestamp=datetime.now(timezone.utc).isoformat(),head=git('rev-parse','HEAD'),branch=git('branch','--show-current'),dirty=git('status','--porcelain'),inputFingerprint=start,finalInputFingerprint=fingerprint(),checks=checks,gates=gates,runDirectory=str(RUN.relative_to(ROOT)),browserChannel=args.browser_channel or 'pinned-chromium',limitations=['Windows certification host; actual JDK/container/JDBC versions recorded by exported applications','Release-17 compilation does not prove Java 17 execution','Single-instance volatile sessions/throttling; no distributed revocation','No real CAPTCHA traffic, hosted CI, deployment or production database access','Self-review only; independent security review required before external auth-template release','B6 Python implementation not started'])
for path in ['b4-regression-evidence.json','exports/export-results.json','exports/browser-results.json','exports/contract-results.json','applications/application-results.json']:
    if (RUN/path).exists():evidence[path]=json.loads((RUN/path).read_text())
(DOC/'evidence.json').write_text(json.dumps(evidence,indent=2)+'\n',encoding='utf-8');(RUN/'evidence.json').write_bytes((DOC/'evidence.json').read_bytes())
lines=['# B5 sprint report','',f'Verdict: **{verdict}**',f'Tested: {evidence["timestamp"]}',f'HEAD: `{evidence["head"]}` (dirty; no commit)',f'Input fingerprint: `{start}`','', '| Gate | Result |','|---|---|']+[f'| {g} | {s} |' for g,s in gates.items()]
lines+=['','Command: `python -X utf8 scripts/b5/verify.py'+(f' --browser-channel {args.browser_channel}' if args.browser_channel else '')+'`',f'Evidence: `{RUN.relative_to(ROOT)}`. Exact commands, cwd, exit codes, counts, versions and matrix results are in evidence.json. Historical B0–B4 report bytes restored.','']+['- '+s for s in evidence['limitations']]
lines+=['','B6 prerequisites are satisfied only when every B5 gate PASS against unchanged inputs. Follow b6-handoff.md; B6 is not started.']
(DOC/'sprint-pass.md').write_text('\n'.join(lines)+'\n',encoding='utf-8');(RUN/'sprint-pass.md').write_bytes((DOC/'sprint-pass.md').read_bytes());print(verdict,'B5',RUN,flush=True);sys.exit(0 if verdict=='PASS' else 1)
