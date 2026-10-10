"""Bounded artifact integrity check; optional view smoke against an operator-owned test DB."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import subprocess

root = Path(__file__).resolve().parent
def verify():
    manifest = json.loads((root/'manifest.json').read_text(encoding='utf-8'))
    core = json.dumps(manifest['core'], sort_keys=True, ensure_ascii=False, separators=(',', ':')).encode()
    assert hashlib.sha256(core).hexdigest() == manifest['digest'], 'manifest integrity'
    seen=set();total=(root/'manifest.json').stat().st_size
    assert len(manifest['core']['files']) < 100
    for f in manifest['core']['files']:
        name=f['path'];assert re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_-]*(?:\.[A-Za-z0-9][A-Za-z0-9_-]*)+',name)
        assert name.lower() not in seen and name!='manifest.json';seen.add(name.lower())
        file=root/name;assert not file.is_symlink();size=file.stat().st_size;total+=size
        assert size==f['bytes'] and total<=5*1024*1024
        assert hashlib.file_digest(file.open('rb'),'sha256').hexdigest()==f['sha256'], 'payload integrity'
    return manifest

def main():
    manifest=verify();parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sqlite');parser.add_argument('--database');parser.add_argument('--psql',default='psql');args=parser.parse_args()
    if args.sqlite or args.database:
        assert manifest['core']['module']=='view','Optional SQL smoke is for views only; use migrate.py for migrations.'
        spec=json.loads((root/'view.spec.json').read_text(encoding='utf-8'))
        quoted='"'+spec['name'].replace('"','""')+'"'
        sql=(root/'view.sql').read_text(encoding='utf-8')+'\nSELECT * FROM '+quoted+' LIMIT 0;\n'
        if args.sqlite:
            assert spec['target']=='sqlite' and not args.database
            db=sqlite3.connect('file:'+str(Path(args.sqlite).resolve())+'?mode=rw',uri=True,isolation_level=None)
            try:
                db.execute('PRAGMA foreign_keys=ON');assert db.execute('PRAGMA foreign_keys').fetchone()==(1,)
                db.executescript('BEGIN IMMEDIATE;\n'+sql+'\nROLLBACK;')
            finally:
                if db.in_transaction:db.rollback()
                db.close()
        else:
            assert spec['target']=='postgresql'
            subprocess.run([args.psql,'-X','-v','ON_ERROR_STOP=1','-d',args.database],input='BEGIN;\n'+sql+'\nROLLBACK;\n',text=True,encoding='utf-8',check=True)
    print('PASS artifact integrity and requested smoke procedure')
if __name__=='__main__':main()
