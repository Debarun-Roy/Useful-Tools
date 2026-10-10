"""B7 release qualification. Required external gates remain nonzero when unavailable."""
import argparse,hashlib,json,os,platform,shutil,subprocess,sys
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];DOC=ROOT/'docs/backend-support/b7'
OUTPUTS={'evidence.json','sprint-pass.md','continuation.md','performance-report.md','accessibility-report.md','pilot-results.md'}
def git(*args):return subprocess.check_output(['git','-c',f'safe.directory={ROOT.as_posix()}',*args],cwd=ROOT,text=True).strip()
def fingerprint(manifest=None):
 h=hashlib.sha256();files={}
 for name in sorted(set(git('ls-files','-c','-o','--exclude-standard').splitlines())):
  p=ROOT/name
  if name.startswith('docs/backend-support/') and p.name in OUTPUTS:continue
  if p.is_file():
   digest=hashlib.sha256(p.read_bytes());files[name]=digest.hexdigest();h.update(name.encode()+b'\0'+digest.digest())
 if manifest:manifest.write_text(json.dumps(files,indent=2)+'\n')
 return h.hexdigest()
def restore_originals(originals):
 # Replace only after a complete sibling copy exists; ENOSPC must not truncate history.
 for p,data in originals.items():
  if p.exists() and p.read_bytes()==data:continue
  temporary=p.with_name(p.name+'.b7-restore.tmp')
  try:
   temporary.write_bytes(data)
   os.replace(temporary,p)
  finally:
   if temporary.exists():temporary.unlink()

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--browser-channel',choices=['chrome','msedge'],default='chrome');parser.add_argument('--engineering-only',action='store_true',help='Diagnostic result only, never release certification');parser.add_argument('--resume',type=Path,help='Reuse only a strictly validated PASS prior-sprint chain; rerun all B7 checks');args=parser.parse_args()
 run=ROOT/'.b7'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir(parents=True);checks=[];start=fingerprint(run/'input-files.json');env=os.environ.copy();env.update(JAVA_TOOL_OPTIONS='-Xms32m -Xmx512m',PYTHONUTF8='1')
 def execute(name,command,cwd=ROOT,timeout=10800):
  command=list(map(str,command));command[0]=shutil.which(command[0]) or command[0];print('CHECK',name,flush=True)
  try:
   with (run/(name+'.log')).open('w',encoding='utf-8') as log:r=subprocess.run(command,cwd=cwd,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=timeout)
   c=dict(name=name,status='PASS' if r.returncode==0 else 'BLOCKED' if r.returncode==2 else 'FAIL',command=command,cwd=str(cwd),exitCode=r.returncode,log=str(run/(name+'.log')))
  except (OSError,subprocess.TimeoutExpired) as e:c=dict(name=name,status='BLOCKED',command=command,cwd=str(cwd),exitCode=None,error=str(e))
  checks.append(c);(run/'progress.json').write_text(json.dumps(checks,indent=2));print(c['status'],name,flush=True);return c
 originals={ROOT/f'docs/backend-support/b{i}/{n}':(ROOT/f'docs/backend-support/b{i}/{n}').read_bytes() for i in range(7) for n in ['evidence.json','sprint-pass.md']}
 for p,data in originals.items():(run/(p.parent.name+'-'+p.name)).write_bytes(data)
 try:
  if args.resume:
   from resume import validate
   try:
    proof=validate(args.resume,run,args.browser_channel,env)
    checks.append(dict(name='b0-b6-regression-chain',status='PASS',exitCode=0,reused=True,validation=proof))
    for n in ['evidence.json','sprint-pass.md']:shutil.copy2(args.resume/('current-b6-'+n),run/('current-b6-'+n))
    print('PASS validated prior-chain reuse',flush=True)
   except Exception as error:
    checks.append(dict(name='b0-b6-regression-chain',status='BLOCKED',exitCode=None,reused=False,error=type(error).__name__+': '+str(error)))
    print('BLOCKED prior-chain reuse:',str(error),flush=True)
  else:
   free=shutil.disk_usage(ROOT).free;required=8*1024**3
   if free<required:raise OSError(f'BLOCKED: full regression requires at least8GiB free; available={free} bytes')
   execute('b0-b6-regression-chain',[sys.executable,'-X','utf8','scripts/b6/verify.py','--browser-channel',args.browser_channel])
   for n in ['evidence.json','sprint-pass.md']:shutil.copy2(ROOT/'docs/backend-support/b6'/n,run/('current-b6-'+n))
 except (OSError,subprocess.TimeoutExpired) as error:
  checks.append(dict(name='b0-b6-regression-chain',status='BLOCKED',exitCode=None,error=str(error)))
 finally:
  restore_originals(originals)
 execute('accessibility-tooling',['npm','install','--prefix',ROOT/'.b7/tools','--no-audit','--no-fund','--save-exact','axe-core@4.13.0'],timeout=300)
 execute('integrated-load-browser',[sys.executable,'-X','utf8','scripts/b7/local_checks.py',run/'local','--browser-channel',args.browser_channel],timeout=1200)
 execute('dependency-secret-security',[sys.executable,'-X','utf8','scripts/b7/security_checks.py',run/'security'],timeout=180)
 execute('actual-login-privacy',[sys.executable,'-X','utf8','scripts/b7/login_checks.py',run/'login'],timeout=300)
 execute('delivery-package',[sys.executable,'-X','utf8','scripts/b7/package_checks.py',run/'package'],timeout=60)
 execute('diff-check',['git','-c',f'safe.directory={ROOT.as_posix()}','diff','--check'],timeout=30)
 checks.append(dict(name='unchanged-inputs',status='PASS' if fingerprint()==start else 'FAIL',exitCode=0 if fingerprint()==start else 1))
 checks.append(dict(name='historical-certificates',status='PASS' if all(p.read_bytes()==data for p,data in originals.items()) else 'FAIL'))
 # No fixture/automated result may manufacture human or target-environment acceptance.
 external=json.loads((DOC/'external-gates.json').read_text());checks.append(dict(name='external-release-evidence',status='BLOCKED',detail=external))
 lookup={c['name']:c['status'] for c in checks}
 descriptions={'G1':'Inventory/traceability and B0–B6 regressions','G2':'Six-module exports, integration and auth parity','G3':'Security remediation and independent sign-off','G4':'Accepted staging performance/resource/fault criteria','G5':'Browser/accessibility and existing-tool acceptance','G6':'Five-developer real pilot/UAT acceptance','G7':'Intended deployment and rollback/restore qualification','G8':'Monitoring/support/maintenance procedures','G9':'Reproducible evidence and required hosted CI','G10':'Product-owner staged rollout approval'}
 gates={}
 for gate,title in descriptions.items():
  deps=['b0-b6-regression-chain'] if gate=='G1' else ['b0-b6-regression-chain','integrated-load-browser'] if gate=='G2' else ['dependency-secret-security'] if gate=='G3' else ['integrated-load-browser'] if gate=='G4' else ['accessibility-tooling','integrated-load-browser','actual-login-privacy','b0-b6-regression-chain'] if gate=='G5' else ['integrated-load-browser','delivery-package'] if gate=='G7' else ['actual-login-privacy','dependency-secret-security','delivery-package'] if gate=='G8' else ['diff-check','unchanged-inputs','historical-certificates'] if gate=='G9' else []
  status='FAIL' if any(lookup.get(n)=='FAIL' for n in deps) else 'BLOCKED' if any(lookup.get(n)!='PASS' for n in deps) or gate in ['G3','G4','G5','G6','G7','G8','G9','G10'] else 'PASS'
  gates[gate]=dict(title=title,status=status,source='Original requirements/verification plan and B7 implementation prompt',evidence=deps,environment='local Windows; external target unavailable',owner='Engineering' if gate in ['G1','G2','G8','G9'] else 'QA/security/product/deployment owners (unassigned)',remainingAction=external.get(gate,'Resolve failed checks; final review required') if status!='PASS' else 'None')
 engineering='FAIL' if any(c['status']=='FAIL' for c in checks) else 'BLOCKED' if any(c['status']=='BLOCKED' for c in checks if c['name']!='external-release-evidence') else 'PASS'
 verdict='FAIL' if any(g['status']=='FAIL' for g in gates.values()) else 'BLOCKED';e=dict(sprint='B7',timestamp=datetime.now(timezone.utc).isoformat(),head=git('rev-parse','HEAD'),branch=git('branch','--show-current'),dirty=git('status','--porcelain'),inputFingerprint=start,finalInputFingerprint=fingerprint(),engineeringQualification=engineering,verdict=verdict,releaseApproval='NOT APPROVED',deployment='NOT EXECUTED',runDirectory=str(run.relative_to(ROOT)),host={'os':platform.platform(),'processor':platform.processor(),'logicalCpus':os.cpu_count(),'python':platform.python_version()},command=sys.argv,cwd=str(ROOT),checks=checks,gates=gates,mode='engineering diagnostic' if args.engineering_only else 'full certification',limitations=['Independent security review and real UAT unavailable','Target deployment/staging and hosted CI unexecuted','No production access or deployment'])
 e['checkGraph']={'b0-b6-regression-chain':[],'accessibility-tooling':[],'integrated-load-browser':['b0-b6-regression-chain','accessibility-tooling'],'dependency-secret-security':['b0-b6-regression-chain'],'actual-login-privacy':['b0-b6-regression-chain'],'delivery-package':['b0-b6-regression-chain'],'diff-check':[],'unchanged-inputs':[],'historical-certificates':[],'external-release-evidence':[]}
 e['resultFiles']={p.relative_to(run).as_posix():{'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size} for p in run.rglob('*.json') if p.name not in ['evidence.json','sessions.json','ready.json'] and 'node_modules' not in p.parts and 'tomcat' not in p.parts}
 for p in [run/'evidence.json',DOC/'evidence.json']:p.write_text(json.dumps(e,indent=2)+'\n')
 # Report generation occurs after the entire chain; it cannot mutate nested certification inputs.
 performance=run/'local/performance-results.json';accessibility=run/'local/accessibility-results.json'
 if performance.exists():
  measured=json.loads(performance.read_text());lines=['# B7 performance report','','Local Windows loopback only. Proposed staging target remains unaccepted.','', '| Operation | Samples | p50 / p95 / p99 seconds | Requests/sec | Statuses |','|---|---|---|---|---|']
  for name,p in measured['phases'].items():lines.append(f"| {name} | {p['samples']} | {p['p50']:.4f} / {p['p95']:.4f} / {p['p99']:.4f} | {p['throughput']:.2f} | {p['statuses']} |")
  lines += ['',f'Exact host/CPU/memory/connection boundary samples: `{performance.relative_to(ROOT)}`. These are not peak measurements.', 'ETL runtime and generator resource/recovery details: local-results.json. Java/Python saturation and recovery are in the current B0–B6 chain; no generated-auth throughput SLO is claimed.']
  (DOC/'performance-report.md').write_text('\n'.join(lines)+'\n')
 if accessibility.exists():
  a=json.loads(accessibility.read_text());(DOC/'accessibility-report.md').write_text(f"# B7 accessibility report\n\nAutomated result: {a['status']}; browser {a['browser']}. Details: `{accessibility.relative_to(ROOT)}`.\n\nSeven module/target surfaces across ten themes, keyboard focus, mobile layout, viewport reflow, deep-link/Back and existing REST tester are exercised. Prior chain covers drafts, error/stale responses, clipboard/download fidelity and existing admin workflows. Viewport reflow is not browser-native zoom. Incomplete axe findings require human assessment. No screen-reader, Safari, Firefox, Edge, or real human UAT claim.\n")
 report=['# B7 qualification report','',f'Engineering: **{engineering}**. Overall B7: **{verdict}**. Release: **NOT APPROVED**. Deployment: **NOT EXECUTED**.',f'Inputs `{start}`; HEAD `{e["head"]}`; dirty tree.','', '| Gate | Status |','|---|---|']+[f'| {n}: {g["title"]} | {g["status"]} |' for n,g in gates.items()]+['',f'Evidence: `{run.relative_to(ROOT)}`. Commands/exits and external actions in evidence.json. Historical B0–B6 reports restored. No ignored recovery driver required.','See continuation.md and release-handoff.md before resuming.']
 for p in [run/'sprint-pass.md',DOC/'sprint-pass.md']:p.write_text('\n'.join(report)+'\n')
 print(verdict,'B7',run,flush=True);return 0 if args.engineering_only and engineering=='PASS' else 1
if __name__=='__main__':sys.exit(main())
