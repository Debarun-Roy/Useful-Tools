"""Strict reuse of a completed ordinary B0–B6 chain; every B7 check reruns.
Changes outside B7-only tooling/dossier/CI reject reuse. No failed check is reused.
"""
import hashlib,json,os,platform,shutil,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def validate(source,run,channel,env):
 source=source.resolve();assert source.is_relative_to(ROOT/'.b7') and source!=run
 certificate=source/'evidence.json';old=json.loads(certificate.read_text());original=json.loads((source/'input-files.json').read_text());current=json.loads((run/'input-files.json').read_text())
 assert old['inputFingerprint']==old['finalInputFingerprint'],'Source inputs drifted'
 check=next(c for c in old['checks'] if c['name']=='b0-b6-regression-chain');assert check['status']=='PASS' and check['exitCode']==0,'Prior chain did not pass'
 assert sha(source/'current-b6-evidence.json')==old['resultFiles']['current-b6-evidence.json']['sha256'],'Prior chain evidence changed'
 prior=json.loads((source/'current-b6-evidence.json').read_text());assert prior['verdict']=='PASS' and set(prior['gates'].values())=={'PASS'}
 assert prior['inputFingerprint']==prior['finalInputFingerprint'] and prior['browserChannel']==channel
 changed=sorted(n for n in original.keys()|current.keys() if original.get(n)!=current.get(n))
 allowed=lambda n:n.startswith(('scripts/b7/','docs/backend-support/b7/')) or n=='.github/workflows/b7.yml'
 assert all(allowed(n) for n in changed),'Non-B7 inputs changed: '+str([n for n in changed if not allowed(n)])
 # Prove the previous fingerprint manifest itself belongs to the recorded input.
 digest=hashlib.sha256()
 for name,value in sorted(original.items()):digest.update(name.encode()+b'\0'+bytes.fromhex(value))
 assert digest.hexdigest()==old['inputFingerprint'],'Source manifest mismatch'
 head=subprocess.check_output(['git','-c',f'safe.directory={ROOT.as_posix()}','rev-parse','HEAD'],cwd=ROOT,text=True).strip();assert head==old['head']==prior['head']
 host=dict(os=platform.platform(),processor=platform.processor(),logicalCpus=os.cpu_count(),python=platform.python_version());assert host==old['host'],'Host/runtime changed'
 current_war=ROOT/'Useful-Tools/target/UsefulTools.war';package=json.loads((source/'package/package-results.json').read_text());assert package['status']=='PASS'
 assert sha(source/'package/package-results.json')==old['resultFiles']['package/package-results.json']['sha256']
 assert sha(current_war)==package['checks'][0]['detail']['warSha256'],'Assembled WAR changed'
 # The previous B7 fixture copied exactly the frontend used after the successful chain.
 fixture=ROOT/'.b6'/('b7-'+hashlib.sha256(str((source/'local').resolve()).encode()).hexdigest()[:16])/'tomcat/webapps/ROOT'
 assets={p.relative_to(ROOT/'usefultools-frontend/dist').as_posix():sha(p) for p in (ROOT/'usefultools-frontend/dist').rglob('*') if p.is_file()}
 assert assets and all((fixture/n).is_file() and sha(fixture/n)==h for n,h in assets.items()),'Frontend build changed'
 old_assets={p.relative_to(fixture).as_posix() for p in (fixture/'assets').rglob('*') if p.is_file()}
 assert old_assets=={n for n in assets if n.startswith('assets/')},'Frontend asset inventory changed'
 prior_run=ROOT/prior['runDirectory'];versions=[]
 for name,command in [('java-version',['java','-version']),('maven-version',['mvn','-version']),('node-version',['node','--version']),('python-version',['python','--version'])]:
  command[0]=shutil.which(command[0]);result=subprocess.run(command,cwd=ROOT,env=env,capture_output=True,text=True,timeout=45)
  actual=result.stdout+result.stderr;expected=(prior_run/(name+'.log')).read_text()
  normalized=lambda s:'\n'.join(line.strip() for line in s.splitlines() if line.strip())
  assert result.returncode==0 and normalized(actual)==normalized(expected),name+' changed'
  versions.append(dict(command=command,cwd=str(ROOT),exitCode=result.returncode,sha256=hashlib.sha256(actual.encode()).hexdigest()))
 command=[shutil.which('node'),'--input-type=module','-e',"import {chromium} from './usefultools-frontend/node_modules/@playwright/test/index.mjs';const b=await chromium.launch({headless:true,channel:process.argv[1]});try{console.log(b.version())}finally{await b.close()}",channel]
 result=subprocess.run(command,cwd=ROOT,env=env,capture_output=True,text=True,timeout=60)
 assert result.returncode==0 and result.stdout.strip()==prior['exports/browser-results.json']['browserVersion'],'Browser changed/unavailable'
 versions.append(dict(command=command,cwd=str(ROOT),exitCode=result.returncode,version=result.stdout.strip()))
 record=dict(status='PASS',sourceCertificate=str(certificate.relative_to(ROOT)),sourceCertificateSha256=sha(certificate),sourceInputFingerprint=old['inputFingerprint'],priorChainFingerprint=prior['inputFingerprint'],changedB7OnlyInputs=changed,matchingPriorInputs=len(current)-len(changed),host=host,versions=versions,warSha256=sha(current_war),frontendAssets=assets,reason='Completed ordinary B0–B6 chain PASS; non-B7 sources/config/dependencies, built WAR/frontend, host/JDK/Maven/Node/Python/Chrome unchanged. Only B7 checks are affected and all rerun. Transient host pressure is not reused as success.')
 (run/'resume-validation.json').write_text(json.dumps(record,indent=2)+'\n')
 return record
