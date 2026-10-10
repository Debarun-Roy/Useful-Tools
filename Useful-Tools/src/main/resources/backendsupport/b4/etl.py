"""UsefulTools etl-0.4.0-b4. Fixed operator-run code; etl.spec.json is inert data."""
import argparse
import csv
from datetime import date, datetime, timezone
from decimal import Decimal, InvalidOperation, localcontext
import hashlib
import io
import json
import os
from pathlib import Path
import re
import sqlite3
import sys
from contextlib import ExitStack

ROOT = Path(__file__).resolve().parent
MISSING = object()
OMIT = object()
MAX_CSV = 16 * 1024 * 1024
MAX_JSON = 2 * 1024 * 1024
MAX_RECORDS = 100000
MAX_FIELD = 4096
MAX_RECORD = 65536


class Failure(Exception):
    def __init__(self, code, exit_code=3, field='/'):
        self.code, self.exit_code, self.field = code, exit_code, field


def require(condition, code, exit_code=3):
    if not condition:
        raise Failure(code, exit_code)


def canonical(value):
    if isinstance(value, dict):
        return '{' + ','.join(json.dumps(k, ensure_ascii=False) + ':' + canonical(value[k]) for k in sorted(value)) + '}'
    if isinstance(value, list):
        return '[' + ','.join(map(canonical, value)) + ']'
    if isinstance(value, Decimal):
        require(value.is_finite() and abs(value.as_tuple().exponent) <= 100 and len(value.as_tuple().digits) <= 128, 'NUMBER_LIMIT')
        return format(value, 'f')
    return json.dumps(value, ensure_ascii=False, allow_nan=False, separators=(',', ':'))


def digest(data):
    return hashlib.sha256(data).hexdigest()


def pairs(items):
    result = {}
    for k, v in items:
        require(k not in result, 'DUPLICATE_JSON_KEY')
        result[k] = v
    return result


def parse_json(text):
    depth = 0
    quoted = escaped = False
    for ch in text:
        if quoted:
            if escaped:
                escaped = False
            elif ch == '\\':
                escaped = True
            elif ch == '"':
                quoted = False
        elif ch == '"':
            quoted = True
        elif ch in '[{':
            depth += 1
            require(depth <= 32, 'JSON_DEPTH_LIMIT')
        elif ch in ']}':
            depth -= 1
    def constant(_):
        raise Failure('NONSTANDARD_JSON_NUMBER')
    try:
        result = json.loads(text, parse_float=Decimal, object_pairs_hook=pairs, parse_constant=constant)
    except (ValueError, RecursionError, InvalidOperation):
        raise Failure('MALFORMED_JSON') from None
    stack = [result]
    nodes = 0
    while stack:
        v = stack.pop(); nodes += 1
        require(nodes <= 100000, 'JSON_NODE_LIMIT')
        if isinstance(v, dict):
            require(len(v) <= 100, 'FIELD_COUNT_LIMIT'); stack.extend(v.values())
            require(all(len(k) <= MAX_FIELD for k in v), 'FIELD_LIMIT')
        elif isinstance(v, list):
            stack.extend(v)
        elif isinstance(v, str):
            require(len(v) <= MAX_FIELD, 'FIELD_LIMIT')
        elif isinstance(v, Decimal):
            require(v.is_finite() and len(v.as_tuple().digits) <= 128 and abs(v.as_tuple().exponent) <= 100, 'NUMBER_LIMIT')
        elif isinstance(v, int):
            require(v.bit_length() <= 426, 'NUMBER_LIMIT')
    return result


def read_bounded(path, limit):
    with path.open('rb') as stream:
        raw = stream.read(limit + 1)
    require(len(raw) <= limit, 'SOURCE_SIZE_LIMIT')
    return raw


def verify_bundle():
    manifest = json.loads((ROOT / 'manifest.json').read_text(encoding='utf-8'))
    require(digest(canonical(manifest['core']).encode()) == manifest['digest'], 'MANIFEST_INTEGRITY')
    seen = set(); total = (ROOT / 'manifest.json').stat().st_size
    require(len(manifest['core']['files']) < 100, 'ARTIFACT_LIMIT')
    for f in manifest['core']['files']:
        name = f['path']
        require(re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_-]*(?:\.[A-Za-z0-9][A-Za-z0-9_-]*)+', name) and name.lower() not in seen and name != 'manifest.json', 'ARTIFACT_PATH')
        seen.add(name.lower()); path = ROOT / name
        require(not path.is_symlink(), 'ARTIFACT_PATH'); size = path.stat().st_size; total += size
        require(size == f['bytes'] and total <= 5 * 1024 * 1024, 'ARTIFACT_LIMIT')
        require(digest(path.read_bytes()) == f['sha256'], 'ARTIFACT_INTEGRITY')


def records(raw, spec):
    try:
        text = raw.decode(spec['source']['encoding'], errors='strict')
    except UnicodeError:
        raise Failure('INVALID_ENCODING') from None
    if spec['source']['format'] == 'json':
        rows = parse_json(text)
        require(isinstance(rows, list), 'JSON_ARRAY_REQUIRED')
        require(len(rows) <= MAX_RECORDS, 'RECORD_COUNT_LIMIT')
        for n, row in enumerate(rows, 1):
            yield n, row, None if isinstance(row, dict) else 'OBJECT_REQUIRED'
    else:
        csv.field_size_limit(MAX_FIELD)
        reader = csv.reader(io.StringIO(text, newline=''), delimiter=spec['source']['delimiter'], quotechar='"', doublequote=True, escapechar=None, strict=True)
        try:
            headers = next(reader, None)
            if headers is None:
                return
            require(0 < len(headers) <= 100 and len(headers) == len(set(headers)) and all(headers), 'INVALID_CSV_HEADER')
            require(all(len(h) <= MAX_FIELD for h in headers), 'FIELD_LIMIT')
            n = 0
            for fields in reader:
                if not fields:
                    continue
                n += 1; require(n <= MAX_RECORDS, 'RECORD_COUNT_LIMIT')
                if len(fields) != len(headers):
                    yield n, {}, 'CSV_WIDTH'
                else:
                    yield n, dict(zip(headers, fields)), None
        except csv.Error:
            raise Failure('CSV_STRUCTURE') from None


def cast(value, column, conversion, naive):
    t = column['type']
    if t in ('text', 'varchar'):
        require(isinstance(value, str), 'TEXT_REQUIRED')
        require('\x00' not in value and not any(0xD800 <= ord(c) <= 0xDFFF for c in value), 'INVALID_TEXT')
        require(len(value) <= column.get('length', MAX_FIELD), 'TEXT_LENGTH')
        return value
    if t in ('integer', 'bigint'):
        require(not isinstance(value, bool), 'INTEGER_REQUIRED')
        require(isinstance(value, int) or isinstance(value, str) and re.fullmatch(r'[+-]?[0-9]+', value), 'INTEGER_REQUIRED')
        number = int(value); bits = 32 if t == 'integer' else 64
        require(-(2 ** (bits - 1)) <= number < 2 ** (bits - 1), 'INTEGER_RANGE')
        return number
    if t == 'boolean':
        if isinstance(value, bool): return value
        if type(value) is int and value in (0, 1): return bool(value)
        require(isinstance(value, str) and value in ('true', 'false', '0', '1'), 'BOOLEAN_REQUIRED')
        return value in ('true', '1')
    if t == 'decimal':
        require(not isinstance(value, (bool, float)) and isinstance(value, (str, int, Decimal)), 'DECIMAL_REQUIRED')
        try:
            v = Decimal(value)
            require(v.is_finite() and abs(v.as_tuple().exponent) <= 100 and len(v.as_tuple().digits) <= 128, 'DECIMAL_RANGE')
            p, s = column['precision'], column['scale']
            with localcontext() as ctx:
                ctx.prec = 160
                q = v.quantize(Decimal(1).scaleb(-s))
                require(q == v and abs(q) < Decimal(10) ** (p - s), 'DECIMAL_PRECISION_SCALE')
            return q
        except InvalidOperation:
            raise Failure('DECIMAL_REQUIRED') from None
    if t == 'date':
        require(isinstance(value, str), 'DATE_REQUIRED')
        try:
            if conversion == 'date_dmy':
                require(re.fullmatch(r'\d{2}/\d{2}/\d{4}', value), 'DATE_FORMAT')
                return datetime.strptime(value, '%d/%m/%Y').date().isoformat()
            require(re.fullmatch(r'\d{4}-\d{2}-\d{2}', value), 'DATE_FORMAT')
            return date.fromisoformat(value).isoformat()
        except ValueError:
            raise Failure('DATE_REQUIRED') from None
    if t == 'timestamp':
        require(isinstance(value, str) and re.fullmatch(r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?(?:Z|[+-]\d{2}:\d{2})?', value), 'TIMESTAMP_FORMAT')
        try:
            v = datetime.fromisoformat(value.replace('Z', '+00:00'))
            if v.tzinfo is None:
                require(naive == 'utc', 'TIMESTAMP_OFFSET_REQUIRED'); v = v.replace(tzinfo=timezone.utc)
            return v.astimezone(timezone.utc).isoformat(timespec='microseconds').replace('+00:00', 'Z')
        except (ValueError, OverflowError):
            raise Failure('TIMESTAMP_REQUIRED') from None
    if t == 'json':
        v = parse_json(value) if isinstance(value, str) else value
        result = canonical(v); require(len(result) <= MAX_FIELD, 'FIELD_LIMIT')
        return result
    raise Failure('UNSUPPORTED_TYPE')


def mapped(row, spec, columns):
    require(isinstance(row, dict) and len(canonical(row).encode('utf-8')) <= MAX_RECORD, 'RECORD_LIMIT')
    if spec['source']['unexpectedFields'] == 'reject':
        require(set(row) <= {m['source'] for m in spec['mappings']}, 'UNEXPECTED_FIELD')
    result = {}
    for index, mapping in enumerate(spec['mappings']):
        column = columns[mapping['columnId']]; value = row.get(mapping['source'], MISSING)
        try:
            policy = mapping['missing'] if value is MISSING else mapping['null'] if value is None else None
            if policy is None:
                for transform in mapping['transforms'][:-1]:
                    if transform == 'trim' and isinstance(value, str): value = value.strip()
                    elif transform in ('lower', 'upper'):
                        require(isinstance(value, str), 'TEXT_TRANSFORM_REQUIRED'); value = value.lower() if transform == 'lower' else value.upper()
                if value == '': policy = mapping['empty']
            if policy == 'reject': raise Failure('VALUE_REQUIRED')
            if policy == 'default': continue
            if policy == 'null':
                require(column['nullable'], 'NULL_NOT_ALLOWED'); result[mapping['columnId']] = None; continue
            result[mapping['columnId']] = cast(value, column, mapping['transforms'][-1], spec['naiveTimestamp'])
        except Failure as e:
            e.field = '/mappings/' + str(index); raise
        except (ValueError, TypeError, OverflowError, UnicodeError):
            raise Failure('INVALID_VALUE', field='/mappings/' + str(index)) from None
    return result


class FileLock:
    def __init__(self, path): self.path, self.stream = path, None
    def __enter__(self):
        self.stream = self.path.open('a+b')
        if self.stream.seek(0, 2) == 0: self.stream.write(b'0'); self.stream.flush()
        self.stream.seek(0)
        try:
            if os.name == 'nt':
                import msvcrt
                msvcrt.locking(self.stream.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(self.stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            self.stream.close(); raise Failure('RUNNER_LOCKED') from None
        return self
    def __exit__(self, *exc): self.stream.close()


def quote(name): return '"' + name.replace('"', '""') + '"'


class Database:
    def __init__(self, spec, table, setting, stack):
        self.spec, self.table, self.columns = spec, table, {c['id']: c for c in table['columns']}
        self.sqlite = spec['target'] == 'sqlite'
        if self.sqlite:
            path = Path(setting).resolve(); require(path.is_file(), 'DATABASE_MUST_EXIST')
            stack.enter_context(FileLock(Path(str(path) + '.utetl.lock')))
            self.connection = sqlite3.connect(path.as_uri() + '?mode=rw', uri=True, isolation_level=None)
            stack.callback(self.connection.close)
            self.connection.execute('PRAGMA foreign_keys=ON'); require(self.connection.execute('PRAGMA foreign_keys').fetchone() == (1,), 'FOREIGN_KEYS_DISABLED')
            self.integrity_errors = (sqlite3.IntegrityError,)
            stat = path.stat(); identity = [str(path), stat.st_dev, stat.st_ino]
        else:
            import psycopg
            try: config = json.loads(setting)
            except (ValueError, TypeError): raise Failure('EXPLICIT_CONNECTION_REQUIRED') from None
            require(isinstance(config, dict) and set(config) == {'host','port','dbname','user','password','sslmode'}, 'EXPLICIT_CONNECTION_REQUIRED')
            require(all(isinstance(config[k], str) for k in ['host','dbname','user','password','sslmode']) and all(config[k] for k in ['host','dbname','user']) and type(config['port']) is int and 1 <= config['port'] <= 65535 and config['sslmode'] in ('disable','require','verify-ca','verify-full'), 'EXPLICIT_CONNECTION_REQUIRED')
            for key in list(os.environ):
                if key.startswith('PG'): del os.environ[key]
            self.connection = psycopg.connect(**config, connect_timeout=10, options='', autocommit=True)
            stack.callback(self.connection.close)
            self.integrity_errors = (psycopg.IntegrityError,)
            identity = [config[k] for k in ['host','port','dbname','user']]
            identity.append(self.connection.execute('SELECT oid FROM pg_catalog.pg_database WHERE datname=current_database()').fetchone()[0])
            key = int.from_bytes(hashlib.sha256(('public.' + table['name']).encode()).digest()[:8], 'big', signed=True)
            require(self.connection.execute('SELECT pg_try_advisory_lock(%s)', (key,)).fetchone()[0], 'RUNNER_LOCKED')
            self.connection.execute("SET search_path=public,pg_catalog")
        self.identity = digest(canonical(identity).encode())
        self.compatibility()
    def compatibility(self):
        name = self.table['name']
        if self.sqlite:
            rows = self.connection.execute('SELECT name,type,"notnull" FROM pragma_table_info(?) ORDER BY cid', (name,)).fetchall()
            expected = [(c['name'], {'integer':'INT','bigint':'BIGINT','decimal':'NUMERIC','boolean':'INTEGER'}.get(c['type'],'TEXT'), int(not c['nullable'])) for c in self.table['columns']]
            require(rows == expected, 'DESTINATION_SHAPE_MISMATCH')
            keys = []
            for index in self.connection.execute('SELECT name FROM pragma_index_list(?) WHERE "unique"=1 AND partial=0', (name,)):
                keys.append([r[0] for r in self.connection.execute('SELECT name FROM pragma_index_info(?) ORDER BY seqno', index)])
        else:
            rows = self.connection.execute("SELECT a.attname,pg_catalog.format_type(a.atttypid,a.atttypmod),a.attnotnull FROM pg_catalog.pg_attribute a JOIN pg_catalog.pg_class c ON c.oid=a.attrelid JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname=%s AND c.relkind='r' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum", (name,)).fetchall()
            def typename(c):
                t = c['type']
                if t == 'decimal': return f"numeric({c['precision']},{c['scale']})"
                if t == 'varchar': return f"character varying({c['length']})"
                return {'timestamp':'timestamp with time zone','json':'jsonb'}.get(t,t)
            require(rows == [(c['name'], typename(c), not c['nullable']) for c in self.table['columns']], 'DESTINATION_SHAPE_MISMATCH')
            keys = [row[0] for row in self.connection.execute("SELECT ARRAY(SELECT a.attname::text FROM unnest(k.conkey) WITH ORDINALITY x(num,ord) JOIN pg_catalog.pg_attribute a ON a.attrelid=k.conrelid AND a.attnum=x.num ORDER BY x.ord) FROM pg_catalog.pg_constraint k JOIN pg_catalog.pg_class c ON c.oid=k.conrelid JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname=%s AND k.contype IN ('p','u') AND NOT k.condeferrable", (name,))]
        if self.spec['mode'] == 'upsert': require([self.columns[k]['name'] for k in self.spec['conflictKey']] in keys, 'DESTINATION_KEY_MISMATCH')
    def begin(self): self.connection.execute('BEGIN IMMEDIATE' if self.sqlite else 'BEGIN')
    def commit(self): self.connection.commit()
    def rollback(self): self.connection.rollback()
    def write(self, values):
        ids = list(values); names = [quote(self.columns[k]['name']) for k in ids]; params = []
        for k in ids:
            value = values[k]; t = self.columns[k]['type']
            if self.sqlite and isinstance(value, Decimal): value = str(value)
            elif not self.sqlite and value is not None:
                if t == 'date': value = date.fromisoformat(value)
                if t == 'timestamp': value = datetime.fromisoformat(value.replace('Z','+00:00'))
                if t == 'json':
                    from psycopg.types.json import Jsonb
                    value = Jsonb(parse_json(value), dumps=canonical)
            params.append(value)
        table = quote(self.table['name']) if self.sqlite else 'public.' + quote(self.table['name'])
        sql = 'INSERT INTO ' + table + ' (' + ','.join(names) + ') VALUES (' + ','.join(['?' if self.sqlite else '%s'] * len(ids)) + ')'
        if not ids: sql = 'INSERT INTO ' + table + ' DEFAULT VALUES'
        if self.spec['mode'] == 'upsert':
            key = ','.join(quote(self.columns[k]['name']) for k in self.spec['conflictKey'])
            updates = [quote(self.columns[k]['name']) + '=excluded.' + quote(self.columns[k]['name']) for k in self.spec['updateColumns'] if k in values]
            sql += ' ON CONFLICT (' + key + ') DO ' + ('UPDATE SET ' + ','.join(updates) if updates else 'NOTHING')
        self.connection.execute(sql, params)


def checkpoint(path, identity, last, counters, complete=False):
    value = dict(version='1.0.0', identity=identity, lastRecord=last, counters=counters, complete=complete)
    temporary = path.with_name(path.name + '.tmp')
    with temporary.open('w', encoding='utf-8', newline='\n') as stream:
        stream.write(canonical(value) + '\n'); stream.flush(); os.fsync(stream.fileno())
    os.replace(temporary, path)


def load_checkpoint(path, identity):
    try:
        value = parse_json(read_bounded(path, 16384).decode('utf-8'))
        require(set(value) == {'version','identity','lastRecord','counters','complete'} and value['version'] == '1.0.0' and value['identity'] == identity and type(value['complete']) is bool, 'CHECKPOINT_IDENTITY')
        require(type(value['lastRecord']) is int and 0 <= value['lastRecord'] <= MAX_RECORDS, 'CHECKPOINT_POSITION')
        c = value['counters']; require(set(c) == {'read','validated','committed','rejected','uncommitted'} and all(type(n) is int and 0 <= n <= MAX_RECORDS for n in c.values()), 'CHECKPOINT_COUNTERS')
        require(c['read'] == value['lastRecord'] and c['read'] == c['committed'] + c['rejected'] and c['uncommitted'] == 0 and c['committed'] <= c['validated'] <= c['read'], 'CHECKPOINT_COUNTERS')
        return value
    except (OSError, ValueError, TypeError, KeyError, UnicodeError):
        raise Failure('CHECKPOINT_INVALID') from None


def reject_bytes(rejects): return ''.join(canonical(r) + '\n' for r in rejects).encode('utf-8')


def write_rejects(path, rejects, maximum):
    if not rejects or path is None: return
    raw = reject_bytes(rejects); size = path.stat().st_size if path.exists() else 0
    require(size + len(raw) <= maximum, 'REJECT_REPORT_LIMIT', 4)
    with path.open('ab') as stream:
        stream.write(raw); stream.flush(); os.fsync(stream.fileno())


def distinct_paths(paths):
    # Include auxiliary outputs and existing hard links, not just spelling aliases.
    resolved = [p.resolve() for p in paths]
    require(len(set(resolved)) == len(paths), 'PATH_COLLISION')
    for index, path in enumerate(paths):
        require(not path.is_symlink(), 'PATH_SYMLINK')
        if path.exists():
            require(path.is_file(), 'PATH_NOT_FILE')
            for other in paths[:index]:
                require(not other.exists() or not path.samefile(other), 'PATH_COLLISION')


def run(args, spec, counters):
    source = Path(args.source or os.environ.get(spec['runtime']['sourceEnv'], '')).resolve()
    require(source.is_file(), 'SOURCE_REQUIRED'); limit = MAX_CSV if spec['source']['format'] == 'csv' else MAX_JSON
    raw = read_bounded(source, limit); source_hash = digest(raw)
    rows = iter(records(raw, spec))
    table = next(t for t in spec['schema']['tables'] if t['id'] == spec['tableId']); columns = {c['id']: c for c in table['columns']}
    require(not (args.dry_run and args.resume), 'DRY_RUN_RESUME_UNSUPPORTED')
    with ExitStack() as stack:
        db = None; last = 0; cp = None; identity = None
        reject_path = Path(args.reject_report).resolve() if args.reject_report else None
        if not args.dry_run:
            setting = os.environ.get(spec['runtime']['connectionEnv']); require(setting is not None and setting != '', 'CONNECTION_REQUIRED')
            cp_value = args.checkpoint or os.environ.get(spec['runtime']['checkpointEnv']); require(cp_value, 'CHECKPOINT_REQUIRED'); cp = Path(cp_value).resolve()
            reject_value = args.reject_report or os.environ.get(spec['runtime']['rejectsEnv']); require(reject_value, 'REJECT_REPORT_REQUIRED'); reject_path = Path(reject_value).resolve()
            paths = [source, cp, reject_path, Path(str(cp) + '.tmp'), Path(str(cp) + '.lock')]
            if spec['target'] == 'sqlite':
                destination = Path(setting).resolve()
                paths.extend([destination, Path(str(destination) + '.utetl.lock')])
            distinct_paths(paths + [p for p in ROOT.iterdir() if p.is_file() and p.resolve() != source])
            stack.enter_context(FileLock(Path(str(cp) + '.lock')))
            db = Database(spec, table, setting, stack)
            identity = dict(source=source_hash, specification=digest((ROOT/'etl.spec.json').read_bytes()), template=digest((ROOT/'etl.py').read_bytes()), destination=db.identity)
            if args.resume:
                require(spec['mode'] != 'insert' or args.acknowledge_insert_replay, 'INSERT_REPLAY_ACK_REQUIRED')
                previous = load_checkpoint(cp, identity); last = previous['lastRecord']; counters.update(previous['counters'])
            else:
                require(not cp.exists(), 'CHECKPOINT_EXISTS_USE_RESUME')
                checkpoint(cp, identity, 0, counters)
        elif reject_path is not None:
            distinct_paths([source, reject_path] + [p for p in ROOT.iterdir() if p.is_file() and p.resolve() != source])
        skipped = 0
        for _ in range(last):
            require(next(rows, None) is not None, 'CHECKPOINT_POSITION'); skipped += 1
        while True:
            batch = []
            for _ in range(spec['batchSize']):
                row = next(rows, None)
                if row is None: break
                batch.append(row); counters['read'] += 1
            if not batch: break
            valid = []; rejects = []
            for number, row, error in batch:
                try:
                    if error: raise Failure(error)
                    value = mapped(row, spec, columns); counters['validated'] += 1; valid.append((number, value))
                except Failure as e:
                    rejects.append(dict(record=number, field=e.field, reason=e.code))
                    if spec['errorMode'] == 'strict':
                        counters['rejected'] += len(rejects); counters['uncommitted'] = counters['read'] - counters['committed'] - counters['rejected']
                        write_rejects(reject_path, rejects, spec['rejects']['maxBytes']); raise Failure('STRICT_VALIDATION', 4)
            if args.dry_run:
                counters['rejected'] += len(rejects); counters['uncommitted'] = counters['read'] - counters['rejected']
                write_rejects(reject_path, rejects, spec['rejects']['maxBytes']); continue
            require(digest(read_bounded(source, limit)) == source_hash, 'SOURCE_CHANGED', 4)
            accepted = 0
            try:
                db.begin()
                for number, values in valid:
                    if spec['errorMode'] == 'continue': db.connection.execute('SAVEPOINT ut_etl_row')
                    try:
                        db.write(values); accepted += 1
                    except db.integrity_errors:
                        rejects.append(dict(record=number, field='/', reason='DATABASE_CONSTRAINT'))
                        if spec['errorMode'] == 'strict': raise Failure('STRICT_DATABASE_CONSTRAINT', 4)
                        db.connection.execute('ROLLBACK TO SAVEPOINT ut_etl_row')
                    if spec['errorMode'] == 'continue': db.connection.execute('RELEASE SAVEPOINT ut_etl_row')
                current_size = reject_path.stat().st_size if reject_path.exists() else 0
                require(current_size + len(reject_bytes(rejects)) <= spec['rejects']['maxBytes'], 'REJECT_REPORT_LIMIT', 4)
                require(digest(read_bounded(source, limit)) == source_hash, 'SOURCE_CHANGED', 4)
                # Any exception from commit is ambiguous, not proof of rollback.
                try: db.commit()
                except Exception: raise Failure('COMMIT_OUTCOME_UNKNOWN', 4) from None
            except BaseException:
                db.rollback(); counters['rejected'] += len(rejects); counters['uncommitted'] = counters['read'] - counters['committed'] - counters['rejected']
                write_rejects(reject_path, rejects, spec['rejects']['maxBytes']); raise
            counters['committed'] += accepted; counters['rejected'] += len(rejects); counters['uncommitted'] = 0
            write_rejects(reject_path, rejects, spec['rejects']['maxBytes'])
            checkpoint(cp, identity, batch[-1][0], counters)
            last = batch[-1][0]
        if not args.dry_run: checkpoint(cp, identity, last, counters, complete=True)
    return 2 if counters['rejected'] else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source'); parser.add_argument('--checkpoint'); parser.add_argument('--reject-report')
    parser.add_argument('--dry-run', action='store_true'); parser.add_argument('--resume', action='store_true'); parser.add_argument('--acknowledge-insert-replay', action='store_true')
    args = parser.parse_args(); counters = dict(read=0, validated=0, committed=0, rejected=0, uncommitted=0); reason = None
    try:
        verify_bundle(); spec = json.loads((ROOT/'etl.spec.json').read_text(encoding='utf-8')); code = run(args, spec, counters)
    except Failure as e: code, reason = e.exit_code, e.code
    except KeyboardInterrupt: code, reason = 4, 'INTERRUPTED'
    except Exception: code, reason = 4, 'RUNTIME_FAILURE'
    counters['uncommitted'] = counters['read'] - counters['committed'] - counters['rejected']
    print(json.dumps(dict(status='completed' if code in (0,2) else 'failed', exitCode=code, reason=reason, dryRun=args.dry_run, counters=counters), sort_keys=True))
    return code


if __name__ == '__main__': sys.exit(main())
