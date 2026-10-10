"""Inspect actual delivery packages and deployment inputs; not a container-runtime claim."""
import hashlib,json,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
run=Path(sys.argv[1]).resolve();run.mkdir(parents=True,exist_ok=True)
checks=[]
def check(name,fn):
 try:detail=fn();checks.append(dict(name=name,status='PASS',detail=detail))
 except Exception as e:checks.append(dict(name=name,status='FAIL',detail=str(e)))
def packages():
 war=ROOT/'Useful-Tools/target/UsefulTools.war'
 with zipfile.ZipFile(war) as z:
  names=z.namelist();assert not any(n.endswith(('StarterServer.class','RuntimeTest.class','Server.class')) or '/b7/' in n for n in names)
  files=[]
  import xml.etree.ElementTree as ET
  pom=ET.parse(ROOT/'Useful-Tools/pom.xml');ns={'m':'http://maven.apache.org/POM/4.0.0'}
  patterns=[n.text for n in pom.findall('.//m:resources/m:resource/m:includes/m:include',ns) if n.text.startswith('v0.')]
  sources={p for pattern in patterns for p in (ROOT/'contracts/backend-support').glob(pattern)}
  assert len(sources)==27
  for source in sorted(sources):
   if source.is_file():
    target='WEB-INF/classes/backendsupport/contracts/'+source.relative_to(ROOT/'contracts/backend-support').as_posix()
    assert z.read(target)==source.read_bytes(),target;files.append(target)
  for family in ['b5','b6']:
   for source in (ROOT/'Useful-Tools/src/main/resources/backendsupport'/family).rglob('*'):
    if source.is_file():
     target='WEB-INF/classes/backendsupport/'+family+'/'+source.relative_to(ROOT/'Useful-Tools/src/main/resources/backendsupport'/family).as_posix()
     assert z.read(target)==source.read_bytes(),target;files.append(target)
 return dict(warSha256=hashlib.sha256(war.read_bytes()).hexdigest(),sourceMatchedResources=len(files),testHarnessExcluded=True)
def deployment():
 configuration=json.loads((ROOT/'railway.json').read_text());assert configuration['build']['dockerContextDir']=='.'
 docker=(ROOT/'Useful-Tools/Dockerfile').read_text();assert 'COPY contracts/backend-support /contracts/backend-support' in docker
 assert 'usefultools_dump.sql' not in docker and 'test -s' in docker and 'SQLITE_DB_PATH' in docker
 # Contract source bytes are verified in the assembled WAR above; root context must include them.
 ignore=ROOT/'.dockerignore'
 if ignore.exists():assert not any(line.strip() in ['contracts','contracts/','*','**'] for line in ignore.read_text().splitlines())
 return dict(context='repository root',database='operator-provisioned persistent database required',containerExecution='BLOCKED: Docker and intended target unavailable')
check('war-resource-integrity-and-harness-exclusion',packages);check('deployment-build-inputs',deployment)
result=dict(status='PASS' if all(c['status']=='PASS' for c in checks) else 'FAIL',checks=checks,limitations=['Static context/package checks do not execute JDK17/container/TLS/persistent-volume deployment'])
(run/'package-results.json').write_text(json.dumps(result,indent=2));print(json.dumps(result));sys.exit(0 if result['status']=='PASS' else 1)
