"""Execute current Maven-exported PostgreSQL schema in an owned disposable cluster."""
import sys, json, zipfile, hashlib
from pathlib import Path
from datetime import datetime, timezone
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/b2'))
from postgres import Cluster
run=ROOT/'.b2'/('schema-postgres-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
run.mkdir()
source=ROOT/'Useful-Tools/target/b2-fixtures/postgresql.zip'
with zipfile.ZipFile(source) as bundle:
    sql=bundle.read('schema.sql').decode('utf8')
with Cluster(run) as pg:
    pg.sql(sql)
    pg.sql("INSERT INTO customers(id,name) VALUES(1,'O''Reilly')")
    assert pg.sql('SELECT active FROM customers WHERE id=1')=='t'
    pg.sql('INSERT INTO orders(id,customer_id) VALUES(1,1)')
    assert float(pg.sql('SELECT amount FROM orders WHERE id=1'))==0
    pg.sql("INSERT INTO customers(id,name) VALUES(2,'O''Reilly')",error='23505')
    pg.sql('INSERT INTO orders(id,customer_id) VALUES(2,999)',error='23503')
    pg.sql("INSERT INTO customers(id,name) VALUES(NULL,'new')",error='23502')
result={'status':'PASS','dialect':'PostgreSQL 17.11','sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),
        'checks':['execute generated SQL','boolean default','numeric zero default','UNIQUE rejects duplicate','child FK rejects orphan','primary key rejects NULL'],
        'clusterStopped':True}
(run/'results.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result));print('Evidence:',run)
