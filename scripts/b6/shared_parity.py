"""Additional identical socket scenarios for both adapters; only volatile IDs normalized."""
import concurrent.futures,http.client,json,ssl,uuid

def extra_matrix(app, Client):
    app.reset_window()
    client=Client(app).bootstrap()
    # Stable error precedence: parsing precedes Origin, Origin precedes CSRF and fields.
    for raw,expected in [(b'{bad',400),(b'{"username":false,"password":42}',400),
                         (b'{"x":NaN}',400),(b'{"x":1e101}',400),
                         (b'{"x":"\\ud800"}',400),(b'{"x":"\xff"}',400),
                         (b'{"x":'+b'['*10+b'0'+b']'*10+b'}',413),
                         (b'{"x":['+b','.join([b'0']*257)+b']}',413),
                         (b'{"x":"'+b'a'*4097+b'"}',413)]:
        client.call('/api/auth/login','POST',raw,expected)
    client.call('/api/auth/login','POST',b'{bad',400,origin=False,token=False)
    client.call('/api/auth/login','POST',{},403,origin=False,token=False)
    client.call('/api/auth/login','POST',{},403,token=False)
    app.reset_window()
    for index,password in enumerate(['😀'*15,'😀'*128,' leading and trailing password ']):
        body=client.credentials('register','unicode_'+str(index));body['password']=password
        client.call('/api/auth/register','POST',body,201)
        body=client.credentials('login','unicode_'+str(index));body['password']=password
        client.call('/api/auth/login','POST',body);client.bootstrap()
        client.call('/api/auth/logout','POST',{},204);client.bootstrap()
    for password in ['😀'*129,'x'*14]:
        value=client.credentials('register','unicode_rejected');value['password']=password
        client.call('/api/auth/register','POST',value,400)
    # Password normalization/trimming must never grant login.
    value=client.credentials('login','unicode_2');value['password']='leading and trailing password'
    client.call('/api/auth/login','POST',value,401)
    app.groups.append('shared Unicode/password boundaries, coercion and strict parser/error precedence')
    app.reset_window()
    # Send a chunked body with no Content-Length, proving streaming bound enforcement.
    for chunks,status in [([b'{' ,b'bad'],400),([b' '*8192,b' '*8193],413)]:
        conn=http.client.HTTPSConnection('127.0.0.1',app.item['port'],context=ssl._create_unverified_context(),timeout=15)
        try:
            conn.request('POST','/api/auth/login',iter(chunks),{'Content-Type':'application/json','Origin':app.origin,'Cookie':'JSESSIONID='+client.cookie,'X-CSRF-Token':client.token},encode_chunked=True)
            response=conn.getresponse();value=json.loads(response.read());assert response.status==status
            assert response.getheader('Cache-Control')=='no-store' and set(value)=={'error','requestId'}
            assert str(uuid.UUID(value['requestId']))==value['requestId']
            app.trace.append({'path':'/api/auth/login','method':'CHUNKED POST','status':status,'body':{'error':value['error'],'requestId':'<uuid>'}})
        finally:conn.close()
    app.groups.append('shared chunked body/no Content-Length parser and size enforcement')
    app.reset_window()
    client=Client(app).bootstrap();client.login();client.bootstrap();cookie=client.cookie;token=client.token
    def request(method,path,body=None):
        conn=http.client.HTTPSConnection('127.0.0.1',app.item['port'],context=ssl._create_unverified_context(),timeout=15)
        try:
            conn.request(method,path,json.dumps(body) if body is not None else None,{'Content-Type':'application/json','Origin':app.origin,'Cookie':'JSESSIONID='+cookie,'X-CSRF-Token':token})
            r=conn.getresponse();raw=r.read();assert r.status in (200,204,401,403);return r.status,json.loads(raw) if raw else None,r.getheader('Set-Cookie')
        finally:conn.close()
    # Ordering may vary, but after both requests complete the old cookie must remain revoked.
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        a=pool.submit(request,'GET','/api/auth/session');b=pool.submit(request,'POST','/api/auth/logout',{})
        a.result();assert b.result()[0]==204
    replay=Client(app);replay.cookie=cookie
    for _ in range(3):assert replay.call('/api/auth/session')=={'authenticated':False}
    app.groups.append('shared concurrent read/logout never resurrects revoked authentication')

    app.reset_window()
    fresh=Client(app).bootstrap();cookie=fresh.cookie;token=fresh.token
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        before=pool.submit(request,'GET','/api/auth/session')
        login=pool.submit(request,'POST','/api/auth/login',fresh.credentials('login'))
        assert before.result()[1]=={'authenticated':False}
        signed=login.result();assert signed[0]==200 and signed[2]
    old=Client(app);old.cookie=cookie;assert old.call('/api/auth/session')=={'authenticated':False}
    current=Client(app);current.cookie=signed[2].split(';')[0].split('=',1)[1];current.bootstrap()
    assert current.cookie!=cookie and current.call('/api/auth/session')['authenticated']
    cookie=current.cookie;token=current.token
    app.control(advance=app.spec['session']['idleSeconds']*1000+1)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        assert all(result[1]=={'authenticated':False} for result in pool.map(lambda _:request('GET','/api/auth/session'),range(2)))
    assert current.call('/api/auth/session')=={'authenticated':False}
    app.groups.append('shared concurrent rotation/expiry preserves old-ID invalidation and cannot restore identity')

    if app.item['profile']:
        app.reset_window();profile=Client(app).bootstrap();profile.login();profile.bootstrap()
        for separator in ['\u2028','\u2029']:
            oversized={'preferences':{f'p{i}':separator*200 for i in range(20)}}
            raw=json.dumps(oversized,ensure_ascii=False,separators=(',',':')).encode('utf-8')
            assert len(raw)<16384
            profile.call('/api/user/profile','PATCH',raw,400)
        value=profile.call('/api/user/profile','PATCH',{'preferences':{'separator':'\u2028\u2029'}})
        assert value['preferences']=={'separator':'\u2028\u2029'}
        app.groups.append('shared serialized profile boundary accounts for Unicode line separators')
