"""Supplement actual HTTPS checks with exact ASGI scopes not expressible in valid HTTP."""
import asyncio,copy,importlib,json,os,sys
from pathlib import Path
project=Path(sys.argv[1]).resolve();sys.path.insert(0,str(project))
spec=json.loads((project/'auth-spec.json').read_text());module=spec['pythonModule']
Config=importlib.import_module(module+'.config').Config
create_app=importlib.import_module(module+'.app').create_app
async def probe(app,scheme='https',address='127.0.0.1',root='',method='GET',headers=(),chunks=()):
    scope={'type':'http','asgi':{'version':'3.0'},'http_version':'1.1','method':method,'scheme':scheme,
           'path':'/api/auth/session' if method=='GET' else '/api/auth/login','raw_path':b'/api/auth/session' if method=='GET' else b'/api/auth/login',
           'root_path':root,'query_string':b'','headers':[(b'host',b'localhost'),*headers],
           'client':(address,12345),'server':('localhost',443)}
    events=[{'type':'http.request','body':chunk,'more_body':i<len(chunks)-1} for i,chunk in enumerate(chunks)] or [{'type':'http.request','body':b'','more_body':False}]
    sent=[];done=asyncio.Event()
    async def receive():
        if events:return events.pop(0)
        await done.wait();return {'type':'http.disconnect'}
    async def send(event):
        sent.append(event)
        if event['type']=='http.response.body' and not event.get('more_body'):done.set()
    await app(scope,receive,send)
    start=next(e for e in sent if e['type']=='http.response.start');raw=b''.join(e.get('body',b'') for e in sent if e['type']=='http.response.body')
    assert dict(start['headers'])[b'cache-control']==b'no-store'
    return start['status'],json.loads(raw)
async def main():
    app=create_app(Config(spec,os.environ))
    async with app.router.lifespan_context(app):
        status,value=await probe(app,root='/unsupported');assert (status,value['error'])==(400,'INVALID_REQUEST_TARGET')
        status,value=await probe(app,scheme='http',headers=[(b'x-forwarded-proto',b'https'),(b'x-forwarded-for',b'127.0.0.1')]);assert (status,value['error'])==(400,'HTTPS_REQUIRED')
        for length in [None,b'1']:
            headers=[(b'content-type',b'application/json')]+([(b'content-length',length)] if length else [])
            status,value=await probe(app,method='POST',headers=headers,chunks=[b' '*8192,b' '*8193]);assert (status,value['error'])==(413,'BODY_TOO_LARGE')
    dev=copy.deepcopy(spec);dev['mode']='development';dev['origins']=['http://127.0.0.1:8080']
    app=create_app(Config(dev,os.environ))
    async with app.router.lifespan_context(app):
        assert (await probe(app,scheme='http'))==(200,{'authenticated':False})
        status,value=await probe(app,scheme='http',address='192.0.2.8',headers=[(b'x-forwarded-for',b'127.0.0.1')]);assert (status,value['error'])==(400,'LOOPBACK_REQUIRED')
    print('PASS six ASGI boundary cases; real exported factory, database and Redis')
asyncio.run(main())
