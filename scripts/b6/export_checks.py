"""Export actual Python projects through UsefulTools HTTP; own generator process only."""
import argparse,copy,hashlib,http.client,io,json,os,re,shutil,socket,sqlite3,subprocess,sys,time,zipfile
from pathlib import Path
from urllib.parse import urlsplit
ROOT=Path(__file__).resolve().parents[2]
def execute(run,browser_channel=None,browser=False):
    run=run.resolve();assert run.is_relative_to(ROOT/'.b6');run.mkdir(parents=True,exist_ok=True)
    runtime=ROOT/'scripts/b1/runtime';cp=os.pathsep.join([str(runtime/'target/test-classes'),(runtime/'classpath.txt').read_text().strip()])
    env=os.environ.copy();env.update(SQLITE_DB_PATH=str(run/'application.db'),SQLITE_DB_URL='',RECAPTCHA_SECRET_KEY='',B1_RUN=str(run),B5_RUN=str(run))
    command=[shutil.which('java'),'-cp',cp,'b1.Server',str(run),str(ROOT/'Useful-Tools/target/UsefulTools.war'),str(ROOT/'usefultools-frontend/dist')]
    requests=0;checks=[];exports=[];result={'status':'FAIL'}
    with (run/'generator.log').open('w',encoding='utf-8') as log:
        process=subprocess.Popen(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
        try:
            deadline=time.monotonic()+90
            while not (run/'sessions.json').exists():
                if process.poll() is not None:raise RuntimeError('Generator startup failed; inspect generator.log')
                if time.monotonic()>deadline:raise RuntimeError('Generator startup timed out')
                time.sleep(.2)
            sessions=json.loads((run/'sessions.json').read_text());origin=sessions['origin'];url=urlsplit(origin)
            def call(role,payload,endpoint='generate',expected=200,csrf=True,origin_value=True,method='POST'):
                nonlocal requests
                requests+=1;headers={'Content-Type':'application/json'}
                if role:
                    headers['Cookie']='JSESSIONID='+sessions[role]['session']
                    if csrf:headers['X-XSRF-TOKEN']=sessions[role]['csrf'] if csrf is True else csrf
                if origin_value:headers['Origin']=origin if origin_value is True else origin_value
                connection=http.client.HTTPConnection(url.hostname,url.port,timeout=30)
                try:
                    connection.request(method,'/api/backend-support/'+endpoint,payload if isinstance(payload,bytes) else json.dumps(payload).encode(),headers)
                    response=connection.getresponse();raw=response.read();assert response.status==expected,(endpoint,response.status,expected,raw[:200])
                    assert response.getheader('Cache-Control')=='no-store'
                    if endpoint=='export' and expected==200:
                        assert response.getheader('Content-Disposition')=='attachment; filename="usefultools-rest.zip"';return raw
                    assert response.getheader('Content-Type').startswith('application/json');return json.loads(raw)
                finally:connection.close()
            sample=json.loads((ROOT/'contracts/backend-support/v0.6.0/rest-sample.json').read_text())
            for endpoint in ['generate','export']:
                body={'request':sample}
                if endpoint=='export':body['expectedDigest']='0'*64
                call(None,body,endpoint,401);call('restuser',body,endpoint,403);call('restguest',body,endpoint,403)
            call('restadmin',{'request':sample})
            def sql(statement):
                with sqlite3.connect(run/'application.db') as db:db.execute(statement)
            sql("DELETE FROM tool_toggles WHERE tool_path='/backend-support'")
            for state,status in [('absent',403),('failed',503),('enabled',200)]:
                if state=='failed':sql("INSERT INTO tool_toggles VALUES('/backend-support',1.5,'synthetic')")
                if state=='enabled':sql("UPDATE tool_toggles SET enabled=1 WHERE tool_path='/backend-support'")
                for role in ['restuser','restguest','restadmin']:
                    for endpoint in ['generate','export']:
                        body={'request':sample}
                        if endpoint=='export':body['expectedDigest']='0'*64
                        expected=(403 if role=='restguest' else 409 if endpoint=='export' else 200) if state=='enabled' else status
                        call(role,body,endpoint,expected)
            capability=next(m for m in call('restuser',{},'catalog',method='GET')['data']['generationModules'] if m['module']=='rest')
            assert 'python' in capability['targets'] and capability['pythonAvailable'] and capability['python']['schemaVersion']=='0.6.0';checks.append('catalog and disabled/user/guest/admin protection')
            for endpoint in ['generate','export']:
                body={'request':sample}
                if endpoint=='export':body['expectedDigest']='0'*64
                call('restnegative',body,endpoint,403,csrf=False);call('restnegative',body,endpoint,403,origin_value='https://untrusted.invalid')
                call('restnegative',b'{"request":{},"request":{}}',endpoint,400)
                call('restnegative',b' '*1048577,endpoint,413)
                for field,value in [('target','java'),('schemaVersion','0.1.0'),('templateVersion','future'),('secret','private-b5-sentinel')]:
                    bad=copy.deepcopy(body);bad['request'][field]=value;assert 'private-b5-sentinel' not in json.dumps(call('restnegative',bad,endpoint,422))
            checks.append('strict transport, versions, target, CSRF/Origin and privacy')
            for database in ['sqlite','postgresql']:
                for captcha in ['off','recaptcha-v3']:
                    for profile in [False,True]:
                        spec=copy.deepcopy(sample);spec['database']=database;spec['captcha']['mode']=captcha
                        if not profile:spec['modules']=['core']
                        with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
                        spec['origins']=[f'https://127.0.0.1:{port}'];spec['captcha']['hostname']='localhost'
                        name=f'{database}-{captcha}-{ "profile" if profile else "core"}'
                        role='restfixture'+str(len(exports));preview=call(role,{'request':spec})['data']
                        body={'request':spec,'expectedDigest':preview['digest']};raw=call(role,body,'export');assert raw==call(role,body,'export')
                        call(role,{'request':spec,'expectedDigest':'0'*64},'export',409)
                        destination=(run/'projects'/name).resolve();destination.mkdir(parents=True)
                        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
                            assert archive.namelist()==sorted(archive.namelist()) and len(archive.namelist())<=100
                            assert sum(i.file_size for i in archive.infolist())<=5*1024*1024
                            seen=set()
                            for entry in archive.infolist():
                                assert entry.date_time==(1980,1,2,0,0,0) and not entry.extra and entry.compress_type==zipfile.ZIP_STORED
                                assert not entry.is_dir() and not (entry.external_attr>>16)&0o170000==0o120000
                                assert '\\' not in entry.filename and all(s not in ('','.','..') for s in entry.filename.split('/'))
                                assert entry.filename.lower() not in seen;seen.add(entry.filename.lower())
                                path=(destination/entry.filename).resolve();assert path.is_relative_to(destination);path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(archive.read(entry))
                            for item in preview['files']:assert archive.read(item['path'])==item['content'].encode()
                            for item in preview['manifest']['core']['files']:assert hashlib.sha256(archive.read(item['path'])).hexdigest()==item['sha256']
                        (run/(name+'.zip')).write_bytes(raw);(run/(name+'-preview.json')).write_text(json.dumps(preview),encoding='utf-8')
                        exports.append(dict(name=name,database=database,captcha=captcha,profile=profile,port=port,directory=str(destination.relative_to(ROOT)),digest=preview['digest'],zipSha256=hashlib.sha256(raw).hexdigest()))
            checks.append('eight deterministic HTTP project exports with safe extraction and digest revalidation')
            historical=json.loads((ROOT/'scripts/b6/b5-bundles.json').read_text())
            for index,pin in enumerate(historical):
                role='restfixture'+str(index);preview=call(role,{'request':pin['spec']})['data']
                raw=call(role,{'request':pin['spec'],'expectedDigest':preview['digest']},'export')
                assert hashlib.sha256(raw).hexdigest()==pin['sha256'],pin['name']
            checks.append('eight released B5 Java ZIPs regenerated byte-identically from certified specifications')
            for _ in range(40):call('restfixture19',{'request':sample,'expectedDigest':'0'*64},'export',409)
            call('restfixture19',{'request':sample},expected=429)
            sockets=[]
            try:
                for _ in range(2):
                    sock=socket.create_connection((url.hostname,url.port),timeout=10);sockets.append(sock);identity=sessions['boundary']
                    header=f"POST /api/backend-support/generate HTTP/1.1\r\nHost: {url.netloc}\r\nOrigin: {origin}\r\nCookie: JSESSIONID={identity['session']}\r\nX-XSRF-TOKEN: {identity['csrf']}\r\nContent-Type: application/json\r\nContent-Length: 1000\r\n\r\n{{";sock.sendall(header.encode())
                time.sleep(.5);call('restfixture18',{'request':sample},expected=503)
            finally:
                for sock in sockets:sock.close()
            checks.append('shared rate and two concurrent assembly slots')
            if browser:
                env['B5_RUN']=str(run);env['PLAYWRIGHT_BROWSERS_PATH']=str(ROOT/'.b0/browsers')
                if browser_channel:env['B0_BROWSER_CHANNEL']=browser_channel
                else:env.pop('B0_BROWSER_CHANNEL',None)
                subprocess.run([shutil.which('node'),'tests/b6-browser.mjs'],cwd=ROOT/'usefultools-frontend',env=env,check=True)
            result=dict(status='PASS',requests=requests,checks=checks,exports=exports,command=command,cwd=str(ROOT))
        finally:
            (run/'stop').touch()
            try:process.wait(timeout=20)
            except subprocess.TimeoutExpired:process.terminate();process.wait(timeout=10)
            (run/'sessions.json').unlink(missing_ok=True)
            (run/'export-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(result['status'],'HTTP exports',len(exports),flush=True)
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('run',type=Path);parser.add_argument('--browser',action='store_true');parser.add_argument('--browser-channel',choices=['chrome','msedge']);args=parser.parse_args();execute(args.run,args.browser_channel,args.browser)
