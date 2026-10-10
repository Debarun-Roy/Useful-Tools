"""External disposable ASGI launcher: real exported source, clock/provider injection only."""
import asyncio,importlib,importlib.metadata,json,os,sys,time
from datetime import datetime,timezone
from pathlib import Path
from urllib.parse import parse_qs,urlsplit
import httpx,uvicorn

async def main():
    run,project,cert,key=map(Path,sys.argv[1:5]);run=run.resolve();project=project.resolve()
    root=Path(__file__).resolve().parents[2]
    if not run.is_relative_to(root/'.b6') or not project.is_relative_to(root/'.b6'):raise ValueError('Owned B6 paths required')
    spec=json.loads((project/'auth-spec.json').read_text());sys.path.insert(0,str(project));module=spec['pythonModule']
    app_module=importlib.import_module(module+'.app');config_module=importlib.import_module(module+'.config');captcha_module=importlib.import_module(module+'.captcha')
    now=[time.time()];used=set();calls=[0];provider_failures=[]
    async def provider(reader,writer):
        try:
            head=await reader.readuntil(b'\r\n\r\n');headers=dict(line.split(':',1) for line in head.decode().split('\r\n')[1:] if ':' in line)
            length=int(next(v for k,v in headers.items() if k.lower()=='content-length'));form=parse_qs((await reader.readexactly(length)).decode(),keep_blank_values=True)
            calls[0]+=1
            if form.get('secret')!=['synthetic-provider-secret'] or set(form)!={'secret','response'}:raise ValueError('Provider form mismatch')
            token=form['response'][0];status=200;extra=''
            if token=='timeout':await asyncio.sleep(3.5);return
            if token=='unavailable':status=503
            if token=='redirect':status=302;extra='Location: http://127.0.0.1:1/not-followed\r\n'
            result={'success':token not in used and 'rejected' not in token,'action':'other' if 'wrong-action' in token else 'register' if token.startswith('register:') else 'login',
                    'hostname':'other.invalid' if 'wrong-host' in token else 'localhost','score':'0.9' if 'wrong-type' in token else 0.1 if 'low-score' in token else 0.9,
                    'challenge_ts':datetime.fromtimestamp(now[0]-(121 if 'expired' in token else -11 if 'future' in token else 0),timezone.utc).isoformat().replace('+00:00','Z')}
            used.add(token);body=(b'x'*8193 if token=='oversize' else b'{' if 'malformed' in token else json.dumps(result).encode())
            writer.write(f'HTTP/1.1 {status} Result\r\nContent-Length: {len(body)}\r\nContent-Type: application/json\r\nConnection: close\r\n{extra}\r\n'.encode()+body);await writer.drain()
        except (ConnectionError,asyncio.IncompleteReadError):pass
        except Exception as error:provider_failures.append(type(error).__name__)
        finally:writer.close();await writer.wait_closed()
    provider_server=await asyncio.start_server(provider,'127.0.0.1',0);provider_port=provider_server.sockets[0].getsockname()[1]
    class LocalConnections(httpx.AsyncHTTPTransport):
        async def handle_async_request(self,request):
            if str(request.url)!='https://www.google.com/recaptcha/api/siteverify':raise ValueError('Unexpected provider destination')
            request.url=httpx.URL(f'http://127.0.0.1:{provider_port}/verify')
            return await super().handle_async_request(request)
    app=app_module.create_app(config_module.Config.load(),clock=lambda:now[0],transport=captcha_module.Google(LocalConnections()))
    runtime=app.state.runtime
    async def wrapper(scope,receive,send):
        if scope['type']=='http' and scope['path']=='/client/':
            body=b"<!doctype html><html><head><title>External browser verification</title></head><body><h1>Exported auth application browser client</h1><p id='status'>Ready</p></body></html>"
            await send({'type':'http.response.start','status':200,'headers':[(b'content-type',b'text/html; charset=utf-8')]});await send({'type':'http.response.body','body':body});return
        await app(scope,receive,send)
    origin=spec['origins'][0];port=urlsplit(origin).port
    server=uvicorn.Server(uvicorn.Config(wrapper,host='127.0.0.1',port=port,ssl_certfile=str(cert),ssl_keyfile=str(key),
        proxy_headers=False,access_log=False,log_level='critical',server_header=False,limit_concurrency=64,timeout_graceful_shutdown=10))
    task=asyncio.create_task(server.serve())
    try:
        while not server.started:
            if task.done():await task;raise RuntimeError('EXPORTED_ASGI_STARTUP_FAILED')
            await asyncio.sleep(.05)
        def dbversion():
            with runtime.users.connection() as c:return str(c.execute('SELECT version()' if runtime.config.postgres else 'SELECT sqlite_version()').fetchone()[0])
        versions={'origin':origin,'python':sys.version.split()[0],'database':await runtime.users.work.run(dbversion),
                  'sessionCapacity':'4096','redis':(await runtime.sessions.client.info('server'))['redis_version'],
                  **{k:importlib.metadata.version(k) for k in ['fastapi','starlette','pydantic','uvicorn','starsessions','redis','psycopg','argon2-cffi','httpx']}}
        # Avoid collision between Redis client package and actual server version.
        versions['redisServer']=(await runtime.sessions.client.info('server'))['redis_version']
        def publish(path,value):
            temp=path.with_suffix('.tmp');temp.write_text(json.dumps(value));temp.replace(path)
        publish(run/'ready.json',versions)
        while not (run/'stop').exists():
            path=run/'control.json'
            if path.exists():
                action=json.loads(path.read_text());path.unlink()
                now[0]+=action.get('advance',0)/1000
                if action.get('clearCsrf'):
                    for sid in list(runtime.sessions.active):
                        async with runtime.sessions.transaction(sid):
                            state=await runtime.sessions.current(sid)
                            if state is not None:state.pop('csrf',None);await runtime.sessions.save(sid,state)
                details={}
                if action.get('fillSessions'):
                    while len(runtime.sessions.active)<4096:
                        await runtime.sessions.bootstrap('',None)
                    details['capacityReached']=len(runtime.sessions.active)
                if action.get('fillLimiter'):
                    for index in range(4096):runtime.limiter.take('synthetic:'+str(index),1)
                    details['limiterKeys']=len(runtime.limiter.windows)
                if action.get('holdHash'):
                    import threading
                    release=threading.Event()
                    held=[asyncio.create_task(runtime.passwords.work.run(release.wait)) for _ in range(2)]
                    await asyncio.sleep(.05)
                    details['hashSlots']=runtime.passwords.work.active
                    asyncio.get_running_loop().call_later(2,release.set)
                if action.get('holdDatabase'):
                    import threading
                    release_db=threading.Event()
                    held_db=[asyncio.create_task(runtime.users.work.run(release_db.wait)) for _ in range(8)]
                    await asyncio.sleep(.05)
                    details['databaseSlots']=runtime.users.work.active
                    asyncio.get_running_loop().call_later(2,release_db.set)
                if action.get('pauseRedis'):
                    await runtime.sessions.client.execute_command('CLIENT','PAUSE',3000,'ALL')
                if action.get('inspectCookie'):
                    sid=action['inspectCookie'];raw=await runtime.sessions.store.read(sid,runtime.config.idle)
                    state=json.loads(raw) if raw else None
                    details['recordPresent']=bool(raw)
                    details['recordKeys']=sorted(state) if state else []
                if action.get('inspectSessions'):
                    ttls=[]
                    for sid in list(runtime.sessions.active):
                        ttl=await runtime.sessions.client.ttl(runtime.sessions.store.prefix(sid))
                        if ttl>0:ttls.append(ttl)
                    details={'liveRecords':len(ttls),'ttls':ttls,'capacity':4096}
                if action.get('expireSessions'):
                    for sid in list(runtime.sessions.active):
                        await runtime.sessions.client.expire(runtime.sessions.store.prefix(sid),1)
                if provider_failures:raise RuntimeError('Provider assertion failed')
                publish(run/f"control-result-{action['id']}.json",{'id':action['id'],'providerCalls':calls[0],**details})
            if task.done():await task;raise RuntimeError('Server exited before stop')
            await asyncio.sleep(.025)
    finally:
        server.should_exit=True
        await task
        provider_server.close();await provider_server.wait_closed()
        (run/'ready.json').unlink(missing_ok=True)
if __name__=='__main__':asyncio.run(main())
