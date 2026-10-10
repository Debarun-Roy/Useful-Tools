"""Execute HTTP-exported SQL and shipped operator procedures against owned databases."""
import json,os,sqlite3,subprocess,sys,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'b2'))
from postgres import Cluster
run=Path(sys.argv[1]).resolve();results={};operations=0
def extract(target,module,fixture=''):
    name=f'{target}-{module}'+('-'+fixture if fixture else '');dest=run/(name+'-bundle');dest.mkdir()
    with zipfile.ZipFile(run/(name+'.zip')) as z:
        for info in z.infolist():
            assert '/' not in info.filename and '\\' not in info.filename and '..' not in info.filename
            (dest/info.filename).write_bytes(z.read(info))
    return dest
def procedure(dest,args,env=None,expected=0):
    global operations
    operations+=1
    result=subprocess.run([sys.executable,'-X','utf8',str(dest/'migrate.py'),*map(str,args)],env=env,capture_output=True,text=True,encoding='utf-8',timeout=45)
    assert (result.returncode==0)==(expected==0),result.stderr
def integrity(dest,args=[],env=None):
    subprocess.run([sys.executable,'-X','utf8',str(dest/'verify.py'),*map(str,args)],env=env,check=True,capture_output=True,timeout=45)
def seed(sql):
    sql("INSERT INTO customers(id,name) VALUES(1,'Ada'),(2,'Unmatched'),(3,'O''Reilly 世界')")
    sql('INSERT INTO orders(id,customer_id,amount) VALUES(1,1,10),(2,1,20),(3,3,5)')
def assertions(sql,target,fixture):
    if fixture=='additions':
        assert sql('SELECT count(*) FROM customers')=='3';assert sql('SELECT note FROM customers WHERE id=1')=="O'Reilly 世界"
        assert sql('SELECT count(*) FROM customers WHERE email IS NULL')=='3'
        assert sql('SELECT flag FROM customers WHERE id=1') in ['1','t']
        assert 'email_index' in sql("SELECT name FROM sqlite_schema WHERE type='index'" if target=='sqlite' else "SELECT indexname FROM pg_indexes WHERE schemaname='public'")
        sql('UPDATE customers SET email=\'written later\' WHERE id=1')
    elif fixture=='rename':assert sql('SELECT display_name FROM clients WHERE id=1')=='Ada'
    else:assert sql('SELECT count(*) FROM customers')=='3'
    sql('INSERT INTO orders(id,customer_id) VALUES(99,999)',error='23503')
    name='clients' if fixture=='rename' else 'customers'
    sql(f'UPDATE {name} SET id=10 WHERE id=1');assert sql('SELECT count(*) FROM orders WHERE customer_id=10')=='2'
def views_assert(sql):
    assert sql('SELECT customer,orders,joined_rows FROM customer_order_totals ORDER BY customer')=="Ada|2|2\nO'Reilly 世界|1|1\nUnmatched|0|1"
    assert sql("SELECT total FROM customer_order_totals WHERE customer='Ada'") in ['30','30.00']
    assert sql("SELECT minimum FROM customer_order_totals WHERE customer='Ada'") in ['10','10.00']
    assert sql("SELECT maximum FROM customer_order_totals WHERE customer='Ada'") in ['20','20.00']
    assert float(sql("SELECT average FROM customer_order_totals WHERE customer='Ada'"))==15
    assert sql('SELECT count(*) FROM inner_totals')=='2'
    assert sql('SELECT customer,orders FROM unmatched')=='Unmatched|0'
    assert sql('SELECT customer FROM literal_filter')=="O'Reilly 世界"
    assert sql('SELECT customer FROM nested_filter')=="O'Reilly 世界"
    assert sql('SELECT count(*) FROM self_join')=='3'
class SQLite:
    def __init__(self,path,ddl):
        self.path=path;self.db=sqlite3.connect(path,isolation_level=None);self.db.execute('PRAGMA foreign_keys=ON');assert self.db.execute('PRAGMA foreign_keys').fetchone()==(1,);self.db.executescript(ddl)
    def sql(self,statement,error=None):
        global operations
        operations+=1
        try:
            rows=self.db.execute(statement).fetchall();assert error is None,'Expected FK rejection'
            return '\n'.join('|'.join('' if v is None else str(v) for v in row) for row in rows)
        except sqlite3.IntegrityError:
            assert error is not None;return ''
try:
    for target in ['sqlite','postgresql']:
        dest=extract(target,'schema');ddl=(dest/'schema.sql').read_text(encoding='utf-8')
        bundles={f:extract(target,'migration',f) for f in ['additions','rename','noop','index']}
        vb={f:extract(target,'view',f) for f in ['left','inner','null','literal','self','nested']}
        evaluation=extract(target,'evaluator','errors');integrity(evaluation)
        start=operations
        if target=='sqlite':
            for fixture,bundle in bundles.items():
                db=SQLite(run/(fixture+'.db'),ddl)
                try:
                    seed(db.sql);integrity(bundle);procedure(bundle,['--sqlite',db.path]);assertions(db.sql,target,fixture)
                    if fixture in ['rename','index']:procedure(bundle,['--sqlite',db.path,'--reverse']);assert db.sql('SELECT name FROM customers WHERE id=10')=='Ada'
                    if fixture=='additions':assert not (bundle/'down.sql').exists();procedure(bundle,['--sqlite',db.path,'--reverse'],expected=1)
                finally:db.db.close()
            db=SQLite(run/'mismatch.db',ddl)
            try:
                seed(db.sql);db.sql('ALTER TABLE customers ADD COLUMN unexpected TEXT');procedure(bundles['additions'],['--sqlite',db.path],expected=1)
                assert 'email' not in db.sql('PRAGMA table_info(customers)')
            finally:db.db.close()
            db=SQLite(run/'rollback.db',ddl)
            try:
                seed(db.sql);b=bundles['additions'];body='\n'.join((b/name).read_text(encoding='utf-8') for name in ['prechecks.sql','up.sql','postchecks.sql'])
                # Fault injection after exported DDL: denied operation proves all earlier changes roll back.
                try:db.db.executescript('BEGIN IMMEDIATE;\n'+body+'\nINSERT INTO orders(id,customer_id) VALUES(90,999);\nCOMMIT;');raise AssertionError('Fault must fail')
                except sqlite3.IntegrityError:db.db.rollback()
                assert 'email' not in db.sql('PRAGMA table_info(customers)');assert db.sql('SELECT count(*) FROM customers')=='3'
            finally:db.db.close()
            db=SQLite(run/'views.db',ddl)
            try:
                seed(db.sql)
                for b in vb.values():integrity(b,['--sqlite',db.path]);db.db.executescript((b/'view.sql').read_text(encoding='utf-8'))
                views_assert(db.sql)
            finally:db.db.close()
            version=sqlite3.sqlite_version
        else:
            with Cluster(run/'postgres') as cluster:
                def database(name):
                    cluster.sql('CREATE DATABASE '+name);cluster.sql(ddl,database=name)
                    def sql(statement,error=None):
                        global operations
                        operations+=1;return cluster.sql(statement,database=name,error=error)
                    seed(sql);return sql
                for fixture,b in bundles.items():
                    name='b3_'+fixture;sql=database(name);integrity(b);procedure(b,['--database',name,'--psql',cluster.binary/'psql.exe'],cluster.env);assertions(sql,target,fixture)
                    if fixture in ['rename','index']:procedure(b,['--database',name,'--psql',cluster.binary/'psql.exe','--reverse'],cluster.env);assert sql('SELECT name FROM customers WHERE id=10')=='Ada'
                    if fixture=='additions':assert not (b/'down.sql').exists()
                sql=database('b3_mismatch');sql('ALTER TABLE customers ADD COLUMN unexpected TEXT');procedure(bundles['additions'],['--database','b3_mismatch','--psql',cluster.binary/'psql.exe'],cluster.env,expected=1)
                assert sql("SELECT count(*) FROM information_schema.columns WHERE table_name='customers' AND column_name='email'")=='0'
                sql=database('b3_rollback');b=bundles['additions'];body='\n'.join((b/name).read_text(encoding='utf-8') for name in ['prechecks.sql','up.sql','postchecks.sql'])
                sql('BEGIN; LOCK TABLE customers, orders IN ACCESS EXCLUSIVE MODE;\n'+body+'\nINSERT INTO orders(id,customer_id) VALUES(90,999);\nCOMMIT;',error='23503')
                assert sql("SELECT count(*) FROM information_schema.columns WHERE table_name='customers' AND column_name='email'")=='0';assert sql('SELECT count(*) FROM customers')=='3'
                sql=database('b3_views')
                for b in vb.values():integrity(b,['--database','b3_views','--psql',cluster.binary/'psql.exe'],cluster.env);sql((b/'view.sql').read_text(encoding='utf-8'))
                views_assert(sql);version=cluster.sql('SHOW server_version')
        results[target]=dict(status='PASS',version=version,operations=operations-start,migrations=4,views=6,preconditionFailure=True,atomicRollback=True,proceduresExecuted=True)
        (run/'database-results.json').write_text(json.dumps(results,indent=2),encoding='utf-8');print('PASS',target,version,flush=True)
except Exception as e:results['failure']=str(e);raise
finally:(run/'database-results.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
