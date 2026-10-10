"""Run actual HTTP-exported ETL code in four fresh Python environments and owned databases."""
import argparse,copy,csv,hashlib,io,json,os,shutil,sqlite3,subprocess,sys,time,zipfile
from contextlib import ExitStack
from pathlib import Path
from fixtures import row
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/b2'))
from postgres import Cluster
parser=argparse.ArgumentParser();parser.add_argument('run');parser.add_argument('--target',choices=['sqlite','postgresql']);parser.add_argument('--source',choices=['csv','json']);args=parser.parse_args()
run=Path(args.run).resolve();assert run.is_relative_to(ROOT/'.b4')
work=run/'etl-runtime';work.mkdir(exist_ok=True)
checks=[];commands=[];versions={};counter=0

def execute(command,cwd,env=None,expected=0):
    command=list(map(str,command));p=subprocess.run(command,cwd=cwd,env=env,capture_output=True,text=True,encoding='utf-8',timeout=180)
    commands.append(dict(command=command,cwd=str(cwd),exitCode=p.returncode))
    assert p.returncode==expected,(command,p.returncode,expected,p.stdout[:1000],p.stderr[:500])
    assert 'private-runtime-sentinel' not in p.stdout+p.stderr
    return p.stdout

def unpack(name):
    dest=work/name;dest.mkdir()
    with zipfile.ZipFile(run/(name+'.zip')) as z:
        assert all('/' not in n and '\\' not in n and '..' not in n for n in z.namelist());z.extractall(dest)
    return dest

def source_bytes(rows,format):
    if format=='json':return (json.dumps(rows,ensure_ascii=False)+'\n').encode('utf-8')
    fields=list(rows[0]) if rows else list(row())
    out=io.StringIO(newline='');writer=csv.DictWriter(out,fields);writer.writeheader()
    for r in rows:
        values={k:('' if v is None else 'true' if v is True else 'false' if v is False else json.dumps(v,ensure_ascii=False) if isinstance(v,(dict,list)) else str(v)) for k,v in r.items()}
        writer.writerow(values)
    return out.getvalue().encode('utf-8-sig')

class Case:
    def __init__(self,adapter,label,rows=None,variant='strict',raw=None):
        global counter
        counter+=1;self.a=adapter;self.name=f'case{counter}';self.path=work/self.name;self.path.mkdir();self.label=label
        self.bundle=adapter.bundles[variant];self.source=self.path/('source.'+adapter.format);self.source.write_bytes(raw if raw is not None else source_bytes(rows or [],adapter.format))
        self.cp=self.path/'checkpoint.json';self.rejects=self.path/'rejects.jsonl';self.db=self.path/'destination.db'
        self.env=os.environ.copy();self.env.update(PYTHONUTF8='1',PYTHONDONTWRITEBYTECODE='1',UT_ETL_SOURCE=str(self.source),UT_ETL_CHECKPOINT=str(self.cp),UT_ETL_REJECTS=str(self.rejects))
        if adapter.target=='sqlite':
            with sqlite3.connect(self.db) as conn:conn.executescript(adapter.ddls.get(variant,adapter.ddls['strict']))
            self.env['UT_ETL_CONNECTION']=str(self.db)
        else:
            adapter.cluster.sql('CREATE DATABASE '+self.name);adapter.cluster.sql(adapter.ddls.get(variant,adapter.ddls['strict']),self.name)
            self.env['UT_ETL_CONNECTION']=json.dumps(dict(host='127.0.0.1',port=adapter.cluster.port,dbname=self.name,user='b2',password='',sslmode='disable'))
    def sql(self,statement):
        if self.a.target=='sqlite':
            with sqlite3.connect(self.db) as conn:return conn.execute(statement).fetchall()
        return self.a.cluster.sql(statement,self.name)
    def count(self):
        value=self.sql('SELECT COUNT(*) FROM items');return value[0][0] if self.a.target=='sqlite' else int(value)
    def invoke(self,*args,expected=0,wrapper=None,env=None):
        output=execute([self.a.python,wrapper or self.bundle/'etl.py',*args],self.path,env or self.env,expected)
        result=json.loads(output);c=result['counters'];assert c['read']==c['committed']+c['rejected']+c['uncommitted'],result
        assert result['exitCode']==expected;return result
    def checkpoint(self):return json.loads(self.cp.read_text())

class Adapter:
    def __init__(self,target,format,cluster):
        self.target,self.format,self.cluster=target,format,cluster
        self.bundles={v:unpack(f'{target}-{format}-{v}') for v in ['strict','continue','upsert','cap','utc','ignore','types','case','composite']}
        self.ddls={}
        for variant in ['strict','types','composite']:
            with zipfile.ZipFile(run/(target+'-schema'+('' if variant=='strict' else '-'+variant)+'.zip')) as z:self.ddls[variant]=z.read('schema.sql').decode()
        envdir=work/f'venv-{target}-{format}'
        execute([sys.executable,'-m','venv',envdir],ROOT)
        self.python=envdir/('Scripts/python.exe' if os.name=='nt' else 'bin/python')
        install=execute([self.python,'-m','pip','install','--disable-pip-version-check','-r',self.bundles['strict']/'requirements.txt'],ROOT)
        (work/f'install-{target}-{format}.log').write_text(install,encoding='utf-8')
        freeze=execute([self.python,'-m','pip','freeze','--all'],ROOT)
        version=execute([self.python,'-c',"import sys,sqlite3,json;print(json.dumps({'python':sys.version.split()[0],'sqlite':sqlite3.sqlite_version}))"],ROOT)
        versions[target+'-'+format]=dict(runtime=json.loads(version),installed=freeze.splitlines())
        if target=='postgresql':
            for pin in ['psycopg==3.3.6','psycopg-binary==3.3.6','tzdata==2026.4']:assert pin in freeze.splitlines(),freeze
        for bundle in self.bundles.values():execute([self.python,bundle/'verify.py'],bundle)
    def check(self,name,fn):
        fn();checks.append(dict(target=self.target,source=self.format,name=name));print('PASS',self.target,self.format,name,flush=True)
    def basics(self):
        empty=Case(self,'empty source',[]);r=empty.invoke();assert empty.count()==0 and empty.checkpoint()['complete'] and all(n==0 for n in r['counters'].values())
        c=Case(self,'normal',[row(1),row(2,parent_id=1),row(3)]);r=c.invoke();assert r['counters']==dict(read=3,validated=3,committed=3,rejected=0,uncommitted=0);assert c.count()==3 and c.checkpoint()['complete']
        assert 'preserved' in str(c.sql('SELECT note FROM items WHERE id=1'))
        assert '06:30:00' in str(c.sql('SELECT instant FROM items WHERE id=1' if self.target=='sqlite' else "SELECT instant AT TIME ZONE 'UTC' FROM items WHERE id=1"))
        before=c.count();r=c.invoke('--resume','--acknowledge-insert-replay');assert c.count()==before and r['counters']['committed']==3
        c.invoke(expected=3);c.invoke('--resume',expected=3)
    def batches(self):
        c=Case(self,'strict',[row(1),row(2),row(3),row(4,amount='-1')]);r=c.invoke(expected=4)
        assert c.count()==2 and c.checkpoint()['lastRecord']==2 and r['counters']==dict(read=4,validated=4,committed=2,rejected=1,uncommitted=1),r
        c=Case(self,'continue',[row(1),row(1,name='duplicate'),row(2,amount='-1'),row(3,parent_id=999),row(4)],'continue');r=c.invoke(expected=2)
        assert c.count()==2 and r['counters']==dict(read=5,validated=5,committed=2,rejected=3,uncommitted=0),r
        rejects=[json.loads(x) for x in c.rejects.read_text().splitlines()];assert len(rejects)==3 and all(set(x)=={'record','field','reason'} for x in rejects)
        c=Case(self,'strictvalidation',[row(1),row(2),row(3),row(4,id='2147483648')]);r=c.invoke(expected=4);assert c.count()==2 and r['counters']['rejected']==1 and r['counters']['uncommitted']==1
        c=Case(self,'boundedrejects',[row(i,id='bad') for i in range(1,15)],'cap');r=c.invoke(expected=4);assert c.rejects.stat().st_size<=256 and c.count()==0 and r['reason']=='REJECT_REPORT_LIMIT'
    def upsert(self):
        c=Case(self,'upsert',[row(1,name='old'),row(1,name='new',amount='45.67'),row(2,parent_id=1),row(1,name='latest')],'upsert')
        c.sql("INSERT INTO items(id,name,note) VALUES(1,'baseline','kept')");c.sql("INSERT INTO items(id,name,parent_id) VALUES(9,'child',1)")
        r=c.invoke();assert c.count()==3 and r['counters']['committed']==4
        assert 'latest' in str(c.sql('SELECT name FROM items WHERE id=1')) and 'kept' in str(c.sql('SELECT note FROM items WHERE id=1'))
        c.invoke('--resume');assert c.count()==3
        c=Case(self,'composite key',[row(1,name='a'),row(1,name='a',amount='44.44')],'composite');c.invoke();assert c.count()==1 and '44.44' in str(c.sql('SELECT amount FROM items'))
    def dry(self):
        c=Case(self,'dry',[row(1),row(2,id='bad')],'continue');before={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in c.path.iterdir() if p.is_file()}
        env=c.env.copy();env.pop('UT_ETL_CONNECTION');r=c.invoke('--dry-run',expected=2,env=env)
        after={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in c.path.iterdir() if p.is_file()};assert before==after and c.count()==0 and r['counters']==dict(read=2,validated=1,committed=0,rejected=1,uncommitted=1)
        c.invoke('--dry-run','--reject-report',str(c.rejects),expected=2,env=env);assert c.rejects.exists() and not c.cp.exists()
        c=Case(self,'existing checkpoint dry',[row(1)]);c.invoke();checkpoint=c.cp.read_bytes();before=c.count();env=c.env.copy();env['UT_ETL_CONNECTION']='invalid-private-runtime-sentinel';c.invoke('--dry-run',env=env);assert c.cp.read_bytes()==checkpoint and c.count()==before
    def parsing(self):
        bad=[dict(id='2147483648'),dict(id='1.2'),dict(active='yes'),dict(amount='1.001'),dict(amount='10000000000'),dict(day='2026-02-30'),dict(instant='2026-09-25T12:00:00'),dict(instant='2026-09-25T12:00:00.1234567Z'),dict(name='x'*81),dict(name='private-runtime-sentinel\x00'),dict(name={'nested':'not text'}),dict(metadata='{bad')]
        if self.format=='csv':bad=[v for v in bad if not isinstance(v.get('name'),dict)] # CSV cells are text, including JSON-looking text.
        c=Case(self,'type rejects',[row(i+1,**v) for i,v in enumerate(bad)],'continue');r=c.invoke(expected=2);assert c.count()==0 and r['counters']['rejected']==len(bad),r
        c=Case(self,'trim multiline quoted unicode',[row(1,name='  O\'Reilly "世界"\nline  ')]);c.invoke();value=c.sql('SELECT name FROM items');assert (value[0][0] if self.target=='sqlite' else value)=='O\'Reilly "世界"\nline'
        c=Case(self,'UTC and DMY',[row(1,day='25/09/2026',instant='2026-09-25T12:00:00')],'utc');c.invoke();assert '12:00:00' in str(c.sql('SELECT instant FROM items' if self.target=='sqlite' else "SELECT instant AT TIME ZONE 'UTC' FROM items"))
        c=Case(self,'unexpected reject',[row(1,unexpected='private-runtime-sentinel')],'continue');c.invoke(expected=2);assert c.count()==0
        c=Case(self,'unexpected ignore',[row(1,unexpected='private-runtime-sentinel')],'ignore');c.invoke();assert c.count()==1
        c=Case(self,'ordered case normalization',[row(1,name='  MiXeD  ')],'case');c.invoke();value=c.sql('SELECT name FROM items');assert (value[0][0] if self.target=='sqlite' else value)=='MIXED'
        precise='9007199254740993.123456789012345678'
        c=Case(self,'bigint exact decimal',[row(9223372036854775807,amount=precise)],'types');c.invoke();assert c.count()==1
        if self.target=='postgresql':assert c.sql('SELECT amount::text FROM items')==precise
        c=Case(self,'bigint overflow',[row(9223372036854775808)],'types');c.invoke(expected=4);assert c.count()==0
        raw_cases=[b'\xff',b'x'*((2 if self.format=='json' else 16)*1024*1024+1)]
        if self.format=='json':raw_cases += [b'{}',b'[{"id":1,"id":2}]',b'[NaN]',b'[Infinity]',b'['*33+b'0'+b']'*33,b'[1e1000]',b'[',b'["'+b'x'*4097+b'"]']
        else:raw_cases += [b'id,id\n1,2\n',b'id,name\n1,"unterminated',b'id,name\n1,'+b'x'*4097+b'\n']
        for raw in raw_cases:
            c=Case(self,'fatal parser',raw=raw);r=c.invoke('--dry-run',expected=3);assert not c.cp.exists() and c.count()==0
        if self.format=='csv':
            c=Case(self,'header width',variant='continue',raw=b'id,name\n1,a,extra\n2\n');r=c.invoke(expected=2);assert c.count()==0 and r['counters']['rejected']==2
        else:
            data=row(1);del data['amount'];del data['parent_id'];c=Case(self,'missing default and nullable',[data]);c.invoke();assert c.count()==1 and '0' in str(c.sql('SELECT amount FROM items'))
            c=Case(self,'precise JSON number',raw=source_bytes([row(1)],'json').replace(b'"12.30"',b'12.30'));c.invoke();assert c.count()==1
    def faults(self):
        wrapper=work/'fault_runner.py'
        if not wrapper.exists():wrapper.write_text(FAULT_WRAPPER,encoding='utf-8')
        for fault,expected_count,position in [('before_commit',2,2),('during_batch',2,2),('after_commit',4,2),('after_checkpoint',2,2),('during_reject',1,0)]:
            values=[row(1),row(2),row(3),row(4)] if fault!='during_reject' else [row(1),row(2,id='bad'),row(3)]
            c=Case(self,fault,values,'upsert');execute([self.python,wrapper,c.bundle,fault],c.path,c.env,91)
            assert c.count()==expected_count and c.checkpoint()['lastRecord']==position,(fault,c.count(),c.checkpoint())
            r=c.invoke('--resume',expected=2 if fault=='during_reject' else 0);assert c.count()==(2 if fault=='during_reject' else 4) and c.checkpoint()['complete']
        c=Case(self,'insert replay',[row(1),row(2),row(3),row(4)])
        execute([self.python,wrapper,c.bundle,'after_commit'],c.path,c.env,91);assert c.count()==4 and c.checkpoint()['lastRecord']==2
        assert c.invoke('--resume',expected=3)['reason']=='INSERT_REPLAY_ACK_REQUIRED'
        c.invoke('--resume','--acknowledge-insert-replay',expected=4);assert c.count()==4 and c.checkpoint()['lastRecord']==2
        c=Case(self,'first batch rollback',[row(1),row(2)],'upsert')
        execute([self.python,wrapper,c.bundle,'first_commit'],c.path,c.env,91);assert c.count()==0 and c.checkpoint()['lastRecord']==0;c.invoke('--resume');assert c.count()==2
    def identities(self):
        c=Case(self,'identity',[row(1)],'upsert');c.invoke();original=c.cp.read_bytes();source=c.source.read_bytes()
        c.source.write_bytes(source+b' ');assert c.invoke('--resume',expected=3)['reason']=='CHECKPOINT_IDENTITY';c.source.write_bytes(source)
        old=c.bundle;c.bundle=self.bundles['continue'];assert c.invoke('--resume','--acknowledge-insert-replay',expected=3)['reason']=='CHECKPOINT_IDENTITY';c.bundle=old
        other=Case(self,'other destination',[],'upsert');env=c.env.copy();env['UT_ETL_CONNECTION']=other.env['UT_ETL_CONNECTION'];assert c.invoke('--resume',expected=3,env=env)['reason']=='CHECKPOINT_IDENTITY';assert other.count()==0
        for malformed in [b'{',b'{}',original.replace(b'"lastRecord":1',b'"lastRecord":100')]:
            c.cp.write_bytes(malformed);c.invoke('--resume',expected=3)
        c.cp.write_bytes(original);c.invoke('--resume');assert c.count()==1
        c=Case(self,'path aliases',[row(1)]);source=c.source.read_bytes();env=c.env.copy();env['UT_ETL_REJECTS']=str(c.source);assert c.invoke(expected=3,env=env)['reason']=='PATH_COLLISION';assert c.source.read_bytes()==source
        env=c.env.copy();env['UT_ETL_SOURCE']=str(c.cp)+'.tmp';Path(env['UT_ETL_SOURCE']).write_bytes(source);assert c.invoke(expected=3,env=env)['reason']=='PATH_COLLISION'
    def concurrent(self):
        c=Case(self,'concurrent',[row(1),row(2)],'upsert');wrapper=work/'fault_runner.py'
        process=subprocess.Popen([str(self.python),str(wrapper),str(c.bundle),'hold'],cwd=c.path,env=c.env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        try:
            deadline=time.monotonic()+15
            while not (c.path/'ready').exists():
                assert process.poll() is None and time.monotonic()<deadline,'Owned runner did not reach transaction';time.sleep(.05)
            assert c.invoke('--resume',expected=3)['reason']=='RUNNER_LOCKED'
        finally:
            process.terminate();process.wait(timeout=10)
        c.invoke('--resume');assert c.count()==2
    def preflight(self):
        data=row(1);del data['amount'];c=Case(self,'NOT NULL destination failure',[data])
        if self.target=='sqlite':
            with sqlite3.connect(c.db) as connection:connection.executescript('DROP TABLE items;\n'+self.ddls['strict'].replace('"amount" NUMERIC NOT NULL DEFAULT 0','"amount" NUMERIC NOT NULL DEFAULT NULL'))
        else:c.sql('ALTER TABLE items ALTER COLUMN amount SET DEFAULT NULL')
        assert c.invoke(expected=4)['reason']=='STRICT_DATABASE_CONSTRAINT' and c.count()==0
        c=Case(self,'wrong destination shape',[row(1)]);c.sql('ALTER TABLE items ADD COLUMN extra TEXT');assert c.invoke(expected=3)['reason']=='DESTINATION_SHAPE_MISMATCH';assert c.count()==0
        c=Case(self,'invalid connection',[row(1)]);env=c.env.copy()
        if self.target=='sqlite':
            absent=c.path/'absent.db';env['UT_ETL_CONNECTION']=str(absent);assert c.invoke(expected=3,env=env)['reason']=='DATABASE_MUST_EXIST';assert not absent.exists()
        else:
            env['UT_ETL_CONNECTION']='{private-runtime-sentinel';assert c.invoke(expected=3,env=env)['reason']=='EXPLICIT_CONNECTION_REQUIRED'
        c=Case(self,'null key',[row(1,id=None)],'upsert');c.invoke(expected=2);assert c.count()==0
        c=Case(self,'empty after trim',[row(1,name='   ')]);c.invoke();value=c.sql('SELECT length(name) FROM items');assert (value[0][0] if self.target=='sqlite' else int(value))==0
        c=Case(self,'fatal connection',[row(1),row(2),row(3),row(4)],'upsert');wrapper=work/'fault_runner.py'
        r=json.loads(execute([self.python,wrapper,c.bundle,'connection_failure'],c.path,c.env,4))
        assert c.count()==2 and c.checkpoint()['lastRecord']==2 and r['counters']['committed']==2
        c.invoke('--resume');assert c.count()==4
        c=Case(self,'ambiguous commit',[row(1),row(2),row(3),row(4)],'upsert')
        r=json.loads(execute([self.python,wrapper,c.bundle,'ambiguous_commit'],c.path,c.env,4));assert r['reason']=='COMMIT_OUTCOME_UNKNOWN' and r['counters']['committed']==2 and r['counters']['uncommitted']==2 and c.count()==4 and c.checkpoint()['lastRecord']==2
        c.invoke('--resume');assert c.count()==4
    def run(self):
        for name,fn in [('clean insert and completion resume',self.basics),('transaction rollback savepoints rejects counters',self.batches),('explicit key upsert preserves unrelated fields',self.upsert),('dry run no destination credentials or mutations',self.dry),('parser types precision bounds and policies',self.parsing),('subprocess crash commit checkpoint and reject boundaries',self.faults),('source spec target checkpoint and path identities',self.identities),('concurrent runner lock and crash release',self.concurrent),('destination preflight fatal connection and ambiguous commit',self.preflight)]:self.check(name,fn)

FAULT_WRAPPER='''import os,sys,time
from pathlib import Path
sys.path.insert(0,sys.argv.pop(1));fault=sys.argv.pop(1)
import etl
commits=writes=0
original_commit=etl.Database.commit
original_write=etl.Database.write
original_checkpoint=etl.checkpoint
original_rejects=etl.write_rejects
def commit(self):
    global commits
    commits+=1
    if (fault=='before_commit' and commits==2) or fault=='first_commit':os._exit(91)
    original_commit(self)
    if fault=='ambiguous_commit' and commits==2:raise OSError('private-runtime-sentinel')
    if fault=='after_commit' and commits==2:os._exit(91)
def write(self,values):
    global writes
    writes+=1
    if fault=='connection_failure' and writes==3:self.connection.close()
    original_write(self,values)
    if fault=='during_batch' and writes==3:os._exit(91)
    if fault=='hold':Path('ready').touch();time.sleep(60)
def checkpoint(path,identity,last,counters,complete=False):
    original_checkpoint(path,identity,last,counters,complete)
    if fault=='after_checkpoint' and last==2:os._exit(91)
def rejects(path,values,maximum):
    if fault=='during_reject' and values:
        with path.open('ab') as stream:stream.write(b'{');stream.flush();os.fsync(stream.fileno())
        os._exit(91)
    original_rejects(path,values,maximum)
etl.Database.commit=commit;etl.Database.write=write;etl.checkpoint=checkpoint;etl.write_rejects=rejects
sys.exit(etl.main())
'''

result=dict(status='FAIL')
try:
    with Cluster(work/'postgres') as cluster:
        versions['postgresql']=cluster.sql('SHOW server_version')
        for target in ([args.target] if args.target else ['sqlite','postgresql']):
            for format in ([args.source] if args.source else ['csv','json']):Adapter(target,format,cluster).run()
    result=dict(status='PASS',diagnostic=bool(args.target or args.source),checks=checks,versions=versions,cases=counter,commands=commands)
except Exception as error:
    result=dict(status='FAIL',checks=checks,versions=versions,cases=counter,commands=commands,failure=str(error));raise
finally:
    (run/'database-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
