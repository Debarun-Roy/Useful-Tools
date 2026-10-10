"""Run only in an extracted bundle and against an owned disposable PostgreSQL database."""
import argparse
import hashlib
import json
from pathlib import Path
import sqlite3
import subprocess

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
canonical = json.dumps(manifest['core'], sort_keys=True, ensure_ascii=False, separators=(',', ':')).encode()
assert hashlib.sha256(canonical).hexdigest() == manifest['digest'], 'manifest digest'
for entry in manifest['core']['files']:
    assert '/' not in entry['path'] and '\\' not in entry['path'] and '..' not in entry['path']
    data = (root / entry['path']).read_bytes()
    assert len(data) == entry['bytes'] and hashlib.sha256(data).hexdigest() == entry['sha256'], 'payload integrity'
schema = json.loads((root / 'schema.json').read_text(encoding='utf-8'))
sql = (root / 'schema.sql').read_text(encoding='utf-8')
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--database')
parser.add_argument('--psql', default='psql')
args = parser.parse_args()
quote = lambda s: '"' + s.replace('"', '""') + '"'
if schema['target'] == 'sqlite':
    with sqlite3.connect(':memory:') as db:
        db.execute('PRAGMA foreign_keys=ON')
        assert db.execute('PRAGMA foreign_keys').fetchone() == (1,)
        db.executescript(sql)
        for table in schema['tables']:
            actual = [c[1] for c in db.execute('PRAGMA table_info(' + quote(table['name']) + ')')]
            assert actual == [c['name'] for c in table['columns']]
        assert db.execute('PRAGMA foreign_key_check').fetchall() == []
    print('PASS SQLite', sqlite3.sqlite_version)
else:
    if not args.database: parser.error('--database must identify your disposable database')
    probes = '\n'.join('SELECT * FROM ' + quote(t['name']) + ' LIMIT 0;' for t in schema['tables'])
    subprocess.run([args.psql, '-X', '-v', 'ON_ERROR_STOP=1', '-d', args.database],
                   input='BEGIN;\n' + sql + probes + '\nROLLBACK;\n', text=True, encoding='utf-8', check=True)
    print('PASS PostgreSQL schema smoke and payload integrity')
