"""Execute only SQL extracted from the actual HTTP-exported ZIPs."""
import hashlib
import json
from pathlib import Path
import sqlite3
import subprocess
import sys
import zipfile
from postgres import Cluster

run=Path(sys.argv[1]).resolve(); results={};count=0
def extract(target, fixture):
    dest=run/f'{target}-{fixture}-bundle';dest.mkdir()
    with zipfile.ZipFile(run/f'{target}-{fixture}.zip') as z:
        assert len(z.infolist())==5
        for info in z.infolist():
            assert '/' not in info.filename and '\\' not in info.filename and '..' not in info.filename
            assert not (info.external_attr>>16)&0o120000 == 0o120000
            (dest/info.filename).write_bytes(z.read(info))
    return dest

class SQLite:
    def __init__(self):
        self.db=sqlite3.connect(':memory:',isolation_level=None)
        self.db.execute('PRAGMA foreign_keys=ON');assert self.db.execute('PRAGMA foreign_keys').fetchone()==(1,)
    def sql(self, sql, error=None):
        global count
        count+=1
        try:
            rows=self.db.execute(sql).fetchall()
            assert error is None, 'Expected constraint rejection'
            return '\n'.join('|'.join('' if x is None else str(x) for x in row) for row in rows)
        except sqlite3.IntegrityError:
            assert error is not None, 'Unexpected constraint rejection'
            return ''

def exercise(db, target, fixture):
    global count
    sql=db.sql
    if fixture=='sample':
        sql("INSERT INTO customers(id,name) VALUES(1,'Ada')")
        sql('INSERT INTO orders(id,customer_id) VALUES(1,1)')
        assert sql('SELECT amount FROM orders WHERE id=1') in ['0','0.00']
        assert sql('SELECT active FROM customers WHERE id=1') in ['1','t']
        assert sql('SELECT created_at FROM orders WHERE id=1')
        sql('INSERT INTO orders(id,customer_id) VALUES(2,999)',error='23503')
        sql("INSERT INTO customers(id,name) VALUES(1,'Different')",error='23505')
        sql("INSERT INTO customers(id,name) VALUES(2,'Ada')",error='23505')
        sql('INSERT INTO customers(id,name) VALUES(3,NULL)',error='23502')
        sql('INSERT INTO orders(id,customer_id,amount) VALUES(3,1,-1)',error='23514')
        sql("INSERT INTO orders(id,customer_id,metadata) VALUES(4,1,'not json')",error='22P02')
        sql('UPDATE customers SET id=5 WHERE id=1');assert sql('SELECT customer_id FROM orders')=='5'
        sql('DELETE FROM customers WHERE id=5');assert sql('SELECT count(*) FROM orders')=='0'
        if target=='sqlite':assert 'orders_by_customer' in sql("SELECT name FROM sqlite_master WHERE type='index'")
        else:assert 'orders_by_customer' in sql("SELECT indexname FROM pg_indexes WHERE tablename='orders'")
    else:
        t='"select""客户"'
        sql(f'INSERT INTO {t}(id,part) VALUES(1,10)')
        assert sql(f'SELECT label FROM {t}')=="O'Brien; -- \\ path"
        assert sql(f'SELECT price FROM {t}') in ['12.5','12.50']
        assert sql(f'SELECT day FROM {t}')=='2026-09-20'
        assert sql(f'SELECT instant FROM {t}').startswith('2026-09-20')
        sql('INSERT INTO child(id,ref_part,ref_id) VALUES(1,10,1)')
        sql('INSERT INTO child(id,ref_part,ref_id) VALUES(2,1,10)',error='23503')
        sql(f'INSERT INTO {t}(id,part) VALUES(1,10)',error='23505')
        sql(f"INSERT INTO {t}(id,part,label) VALUES(NULL,20,'other')",error='23502')
        sql(f"INSERT INTO {t}(id,part,label,price) VALUES(2,20,'other',-1)",error='23514')
        sql(f'UPDATE {t} SET part=11 WHERE part=10');assert sql('SELECT ref_part FROM child')=='11'
        sql(f'DELETE FROM {t} WHERE part=11');assert sql('SELECT count(*) FROM child WHERE ref_part IS NULL AND ref_id IS NULL')=='1'
        sql('INSERT INTO tree(id) VALUES(1)');sql('INSERT INTO tree(id,parent_id) VALUES(2,1)')
        sql('DELETE FROM tree WHERE id=1',error='23503')
        sql('INSERT INTO tree(id,parent_id) VALUES(3,99)',error='23503')
        sql('INSERT INTO tree(id,parent_id) VALUES(4,4)')
        sql(f"INSERT INTO {t}(id,part,label,meta) VALUES(2,20,'nullable',NULL)",error='23502')
        sql(f"INSERT INTO {t}(id,part,label) VALUES(3,30,'{'x'*41}')",error='22001')
        sql(f"INSERT INTO {t}(id,part,label,price) VALUES(5,50,'fraction',1.234)")
        if target=='sqlite':
            assert sql(f'SELECT price FROM {t} WHERE id=5')=='1.234'
            sql(f"INSERT INTO {t}(id,part,label,flag) VALUES(6,60,'badbool',2)",error='23514')
            sql(f"INSERT INTO {t}(id,part,label) VALUES('affinity',70,'weak type')")
            assert 'index"金额' in sql("SELECT name FROM sqlite_master WHERE type='index'")
            assert sql('PRAGMA foreign_key_check')==''
        else:
            assert sql(f'SELECT price FROM {t} WHERE id=5')=='1.23'
            sql(f"INSERT INTO {t}(id,part,label,flag) VALUES(6,60,'badbool',2)",error='42804')
            sql(f"INSERT INTO {t}(id,part,label) VALUES('affinity',70,'weak type')",error='22P02')
            assert 'index"金额' in sql("SELECT indexname FROM pg_indexes")
            assert 'jsonb' in sql("SELECT data_type FROM information_schema.columns WHERE column_name='meta'")
            assert 'timestamp with time zone' in sql("SELECT data_type FROM information_schema.columns WHERE column_name='instant'")

try:
    for fixture in ['sample','complex']:
        dest=extract('sqlite',fixture)
        subprocess.run([sys.executable,str(dest/'verify.py')],check=True)
        db=SQLite()
        try:db.db.executescript((dest/'schema.sql').read_text(encoding='utf-8'));exercise(db,'sqlite',fixture)
        finally:db.db.close()
    results['sqlite']=dict(status='PASS',version=sqlite3.sqlite_version,assertionOperations=count)
    (run/'database-results.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
    before=count
    with Cluster(run/'postgres') as pg:
        version=pg.sql('SHOW server_version');assert version.startswith('17.11')
        for fixture in ['sample','complex']:
            dest=extract('postgresql',fixture);database='b2_'+fixture;pg.sql('CREATE DATABASE '+database)
            subprocess.run([sys.executable,str(dest/'verify.py'),'--database',database,'--psql',str(pg.binary/'psql.exe')],env=pg.env,check=True)
            pg.sql((dest/'schema.sql').read_text(encoding='utf-8'),database)
            class DB:
                def sql(self,statement,error=None):
                    global count
                    count+=1
                    return pg.sql(statement,database,error)
            exercise(DB(),'postgresql',fixture)
    results['postgresql']=dict(status='PASS',version=version,assertionOperations=count-before,driver='psql 17.11; no third-party Python driver')
except Exception as error:
    results['failure']=str(error)
    raise
finally:
    (run/'database-results.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf-8')
print(json.dumps(results))
