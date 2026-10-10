"""Diagnostic only: live Redis lifecycle plus FastAPI TestClient, never certification."""
import asyncio,json,sys,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'.b6/prototype'))
from redis_service import Server
from auth_app.config import Config
from auth_app.sessions import Sessions
from auth_app.app import create_app
from fastapi.testclient import TestClient

def main():
    run=ROOT/'.b6'/('prototype-'+str(time.time_ns()));run.mkdir()
    result={'status':'FAIL','diagnostic':True,'checks':[]}
    try:
        with Server(run/'redis') as redis:
            spec=json.loads((ROOT/'contracts/backend-support/v0.6.0/rest-sample.json').read_text());spec['origins']=['https://127.0.0.1:8443']
            env={'AUTH_DATABASE':str(run/'users.db'),'AUTH_INITIALIZE':'true','AUTH_REDIS_URL':f'redis://127.0.0.1:{redis.port}/0'}
            config=Config(spec,env)
            async def lifecycle():
                now=[time.time()];s=Sessions(config,lambda:now[0]);await s.start()
                try:
                    sid,state=await s.bootstrap('',None);key=s.store.prefix(sid)
                    assert 0<await s.client.ttl(key)<=config.idle
                    assert json.loads(await s.store.read(sid,config.idle))==state
                    await s.client.expire(key,1);await asyncio.sleep(1.1);assert await s.current(sid) is None
                    sid,state=await s.bootstrap('',None);new,state=await s.rotate(sid,state,{'id':'synthetic'})
                    assert await s.current(sid) is None and not await s.client.exists(s.store.prefix(sid))
                    entered=asyncio.Event();release=asyncio.Event()
                    async def earlier():
                        async with s.transaction(new):
                            current=await s.current(new);entered.set();await release.wait();await s.save(new,current)
                    async def logout():
                        async with s.transaction(new):await s.remove(new)
                    a=asyncio.create_task(earlier());await entered.wait();b=asyncio.create_task(logout());await asyncio.sleep(.01);release.set();await asyncio.gather(a,b)
                    assert await s.current(new) is None and not await s.client.exists(s.store.prefix(new))
                    sid,state=await s.bootstrap('',None);other=Sessions(config,lambda:now[0]);await other.start()
                    try:assert await other.current(sid) is None and other.namespace!=s.namespace
                    finally:await other.close()
                    assert await s.client.exists(s.store.prefix(sid))
                    await s.remove(sid)
                    result['checks'].append('live Redis TTL/serialization/rotation/delete/concurrent logout/restart namespace')
                finally:await s.close()
            asyncio.run(lifecycle())
            app=create_app(config)
            with TestClient(app,base_url='https://127.0.0.1:8443') as client:
                def call(path,method='GET',value=None,expected=200,token=None):
                    headers={'Origin':spec['origins'][0]}
                    if token:headers['X-CSRF-Token']=token
                    r=client.request(method,path,json=value,headers=headers)
                    assert r.status_code==expected,(path,r.status_code,r.text)
                    assert r.headers['cache-control']=='no-store'
                    return r
                token=call('/api/auth/csrf-token').json()['csrfToken'];old=client.cookies.get('JSESSIONID')
                credentials={'username':'Example_USER','password':' unchanged synthetic password '}
                call('/api/auth/register','POST',credentials,201,token)
                assert call('/api/auth/session').json()=={'authenticated':False}
                call('/api/auth/login','POST',credentials,200,token)
                assert client.cookies.get('JSESSIONID')!=old
                call('/api/user/profile','PATCH',{'displayName':'Alice'},403,token)
                token=call('/api/auth/csrf-token').json()['csrfToken']
                assert call('/api/user/profile','PATCH',{'displayName':'Alice'},200,token).json()['displayName']=='Alice'
                call('/api/auth/logout','POST',{},204,token)
                assert call('/api/auth/session').json()=={'authenticated':False}
                call('/docs',expected=404);call('/openapi.json',expected=404)
                result['checks'].append('FastAPI actual handler lifecycle and explicit disabled docs')
        result['status']='PASS'
    finally:(run/'results.json').write_text(json.dumps(result,indent=2)+'\n');print(result,run)
if __name__=='__main__':main()
