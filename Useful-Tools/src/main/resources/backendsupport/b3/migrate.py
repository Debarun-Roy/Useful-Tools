"""Operator-run migration in a maintenance window, after backup and external review."""
import argparse
import json
from pathlib import Path
import sqlite3
import subprocess
from verify import verify

ROOT = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument('--sqlite')
    target.add_argument('--database')
    parser.add_argument('--psql', default='psql')
    parser.add_argument('--reverse', action='store_true')
    args = parser.parse_args()
    verify()
    spec = json.loads((ROOT / 'migration.spec.json').read_text(encoding='utf-8'))
    if (spec['target'] == 'sqlite') != bool(args.sqlite):
        raise ValueError('Database target does not match the artifact')
    if args.reverse and not (ROOT / 'down.sql').is_file():
        raise ValueError('This migration has no automatic reversal')
    start, end = ('postchecks.sql', 'prechecks.sql') if args.reverse else ('prechecks.sql', 'postchecks.sql')
    names = [start, 'down.sql' if args.reverse else 'up.sql', end]
    body = '\n'.join((ROOT / name).read_text(encoding='utf-8') for name in names)
    if args.sqlite:
        path = Path(args.sqlite).resolve()
        if not path.is_file():
            raise ValueError('An existing owned database is required')
        connection = sqlite3.connect(path.as_uri() + '?mode=rw', uri=True, isolation_level=None)
        try:
            connection.execute('PRAGMA foreign_keys=ON')
            if connection.execute('PRAGMA foreign_keys').fetchone()[0] != 1:
                raise RuntimeError('Foreign keys are not enabled')
            # executescript executes BEGIN too; no implicit commit can split our transaction.
            connection.executescript('BEGIN IMMEDIATE;\n' + body + '\nCOMMIT;')
        finally:
            if connection.in_transaction:
                connection.rollback()
            connection.close()
    else:
        snapshot = spec['after' if args.reverse else 'before']
        quote = lambda name: '"' + name.replace('"', '""') + '"'
        locks = '\n'.join('LOCK TABLE public.' + quote(table['name']) + ' IN ACCESS EXCLUSIVE MODE;' for table in snapshot['tables'])
        sql = 'BEGIN;\nSET LOCAL search_path=public,pg_catalog;\nSET LOCAL standard_conforming_strings=on;\nSET LOCAL lock_timeout=\'10s\';\n' + locks + '\n' + body + '\nCOMMIT;'
        # ON_ERROR_STOP exits nonzero; connection closure rolls back an uncommitted transaction.
        subprocess.run([args.psql, '-X', '-v', 'ON_ERROR_STOP=1', '-d', args.database], input=sql, text=True, encoding='utf-8', check=True)


if __name__ == '__main__':
    main()
