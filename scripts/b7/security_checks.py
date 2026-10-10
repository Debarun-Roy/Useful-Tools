"""Public dependency coordinates only sent to OSV; findings never imply exploitability."""
import argparse,hashlib,json,re,subprocess,sys,urllib.request,zipfile,shutil
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def main():
 p=argparse.ArgumentParser();p.add_argument('run');a=p.parse_args();run=Path(a.run).resolve();run.mkdir(parents=True,exist_ok=True);packages=set();coverage=[]
 lock=json.loads((ROOT/'usefultools-frontend/package-lock.json').read_text())
 for n,v in lock['packages'].items():
  if n and v.get('version'):packages.add(('npm',n.split('node_modules/')[-1],v['version']))
 for file in [ROOT/'Useful-Tools/target/UsefulTools.war']:
  with zipfile.ZipFile(file) as z:
   import io
   for n in z.namelist():
    if n.startswith('WEB-INF/lib/') and n.endswith('.jar'):
     with zipfile.ZipFile(io.BytesIO(z.read(n))) as jar:
      for name in jar.namelist():
       if name.endswith('/pom.properties'):
        values=dict(re.findall(r'^(groupId|artifactId|version)=(.+)$',jar.read(name).decode(),re.M));
        if len(values)==3:packages.add(('Maven',values['groupId']+':'+values['artifactId'],values['version']))
 for file in [ROOT/'Useful-Tools/src/main/resources/backendsupport/b6/requirements.txt']:
  for name,version in re.findall(r'^([A-Za-z0-9_.-]+)==([^\s]+)',file.read_text(),re.M):packages.add(('PyPI',name,version))
 # Generated Java dependencies are literal pinned XML template entries.
 import xml.etree.ElementTree as ET
 for file in (ROOT/'Useful-Tools/src/main/resources/backendsupport/b5').glob('*pom*'):
  for group,name,version in re.findall(r'<groupId>([^<]+)</groupId>\s*<artifactId>([^<]+)</artifactId>\s*<version>([^<]+)</version>',file.read_text()):
   if '${' not in version:packages.add(('Maven',group+':'+name,version))
 inventory=[{'ecosystem':e,'name':n,'version':v} for e,n,v in sorted(packages)];(run/'dependency-inventory.json').write_text(json.dumps(inventory,indent=2))
 result={'scanner':'B7 OSV query API v1 wrapper','retrievedAt':datetime.now(timezone.utc).isoformat(),'databaseDate':'Live response; per-advisory modified dates recorded; no global snapshot date supplied by OSV','inventorySha256':hashlib.sha256((run/'dependency-inventory.json').read_bytes()).hexdigest(),'coverage':['frontend locked direct/transitive','assembled WAR Maven metadata','Python generated pins','Java generated literal pins'],'limitations':['Runtime OS/JDK/native DB/container advisories require vendor review','Absent Maven metadata and dependencies not literally pinned are not automatically covered'],'status':'BLOCKED'}
 try:
  query={'queries':[{'package':{'ecosystem':x['ecosystem'],'name':x['name']},'version':x['version']} for x in inventory]}
  request=urllib.request.Request('https://api.osv.dev/v1/querybatch',data=json.dumps(query).encode(),headers={'Content-Type':'application/json'})
  with urllib.request.urlopen(request,timeout=45) as response:data=json.load(response)
  assert len(data['results'])==len(inventory);findings=[]
  for pkg,entry in zip(inventory,data['results']):
   if entry.get('vulns'):findings.append({'package':pkg,'advisories':entry['vulns']})
  result.update(status='FAIL' if findings else 'PASS',findings=findings,packages=len(inventory))
 except Exception as error:result['error']=type(error).__name__+': '+str(error)
 dispositions=json.loads((ROOT/'scripts/b7/advisory-dispositions.json').read_text())
 def assessed(pkg,advisory):
  return next((d for d in dispositions if all(d[k]==pkg[k] for k in ['ecosystem','name','version']) and d['advisory']==advisory),None)
 unresolved=[]
 for finding in result.get('findings',[]):
  for advisory in finding['advisories']:
   disposition=assessed(finding['package'],advisory['id'])
   advisory['localDisposition']=disposition
   if not disposition:unresolved.append({'package':finding['package'],'advisory':advisory['id']})
 result['rawStatus']=result['status']
 if result['status']!='BLOCKED':result['status']='FAIL' if unresolved else 'PASS'
 result['unresolved']=unresolved;result['releaseAcceptance']=False
 (run/'dependency-scan.json').write_text(json.dumps(result,indent=2))
 npm=[shutil.which('npm') or 'npm','audit','--json'];audit=subprocess.run(npm,cwd=ROOT/'usefultools-frontend',capture_output=True,text=True,timeout=180)
 (run/'npm-audit.json').write_text(audit.stdout);npm_unresolved=[]
 try:
  parsed=json.loads(audit.stdout);assert 'vulnerabilities' in parsed and not parsed.get('error')
  for name,v in parsed['vulnerabilities'].items():
   # Only exact direct advisory dispositions; no blanket package/severity suppression.
   for item in v['via']:
    if not isinstance(item,dict):npm_unresolved.append({'name':name,'via':item});continue
    pkg={'ecosystem':'npm','name':name,'version':lock['packages']['node_modules/'+name]['version']}
    if not assessed(pkg,item['url'].rsplit('/',1)[-1]):npm_unresolved.append({'name':name,'url':item['url']})
  npm_status='FAIL' if npm_unresolved else 'PASS'
 except Exception:npm_status='BLOCKED'
 (run/'npm-assessment.json').write_text(json.dumps({'status':npm_status,'command':npm,'cwd':str(ROOT/'usefultools-frontend'),'exitCode':audit.returncode,'unresolved':npm_unresolved,'rawScanRetained':True,'releaseAcceptance':False},indent=2))
 git=subprocess.check_output(['git','-c',f'safe.directory={ROOT.as_posix()}','ls-files','-c','-o','--exclude-standard'],cwd=ROOT,text=True)
 findings=[];scanned=0
 patterns={'private-key':r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----','aws-access-key':r'\bAKIA[A-Z0-9]{16}\b','github-token':r'\bgh[pousr]_[A-Za-z0-9]{30,}\b'}
 for name in sorted(set(git.splitlines())):
  path=ROOT/name
  if not path.is_file() or path.suffix.lower() not in {'.java','.py','.jsx','.js','.json','.properties','.xml','.yml','.yaml','.sql','.txt','.md'}:continue
  if path.stat().st_size>5*1024*1024:continue
  text=path.read_text(encoding='utf-8',errors='replace');scanned+=1
  for rule,pattern in patterns.items():
   for match in re.finditer(pattern,text):findings.append({'file':name,'line':text.count('\n',0,match.start())+1,'rule':rule})
 (run/'secret-scan.json').write_text(json.dumps({'status':'FAIL' if findings else 'PASS','scanner':'B7 narrow credential-pattern scan v1','files':scanned,'findings':findings,'limitations':['Pattern-only scan; no entropy/history scan; values never recorded']},indent=2))
 print(json.dumps({'dependencyStatus':result['status'],'packages':len(inventory),'secretFindings':len(findings)}));return 1 if result['status']!='PASS' or npm_status!='PASS' or findings else 0
if __name__=='__main__':sys.exit(main())
