"""Pinned EDB PostgreSQL runtime; Windows CI/local, no service installation or shared DB."""
from pathlib import Path
import hashlib
import os
import socket
import subprocess
import urllib.request
import urllib.error
import zipfile

ROOT = Path(__file__).resolve().parents[2]
URL = 'https://sbp.enterprisedb.com/getfile.jsp?fileid=1260569'
SHA256 = 'b9424ee7bc60b52450ff910a3630225df32e633f3cb29c1d126d9299d59aea28'
VERSION = '17.11'

def provision():
    if os.name != 'nt': raise RuntimeError('BLOCKED: certification currently supports Windows PostgreSQL binaries only')
    base = ROOT / '.b2'
    archive = base / 'postgresql.zip'
    base.mkdir(exist_ok=True)
    if not archive.exists():
        try:
            with urllib.request.urlopen(URL, timeout=90) as source, archive.open('wb') as dest:
                while block := source.read(1024 * 1024): dest.write(block)
        except (urllib.error.URLError, OSError) as error:
            raise RuntimeError('BLOCKED: PostgreSQL verification runtime download unavailable') from error
    assert hashlib.file_digest(archive.open('rb'), 'sha256').hexdigest() == SHA256, 'PostgreSQL archive checksum'
    dest = base / 'postgresql'
    binary = dest / 'pgsql/bin'
    if not (binary / 'postgres.exe').exists():
        with zipfile.ZipFile(archive) as z:
            for info in z.infolist():
                if info.filename.startswith(('pgsql/bin/', 'pgsql/lib/', 'pgsql/share/')) and not info.is_dir():
                    target = (dest / info.filename).resolve()
                    assert target.is_relative_to(dest.resolve())
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(z.read(info))
    assert VERSION in subprocess.check_output([binary/'postgres.exe', '--version'], text=True)
    return binary

class Cluster:
    def __init__(self, run):
        self.run = run.resolve()
        assert any(self.run.is_relative_to(ROOT/name) for name in ['.b2', '.b3', '.b4', '.b5', '.b6'])
        self.binary = provision()
        self.data = self.run / 'pgdata'
        self.env = os.environ.copy()
        # Do not inherit customer connection, credentials or SQL startup configuration.
        for key in list(self.env):
            if key.startswith('PG'): self.env.pop(key)
        self.env.update(PGCLIENTENCODING='UTF8', PGUSER='b2', PGHOST='127.0.0.1')
        with socket.socket() as s:
            s.bind(('127.0.0.1', 0)); self.port = s.getsockname()[1]
        self.env['PGPORT'] = str(self.port)
        self.started = False

    def __enter__(self):
        self.run.mkdir(parents=True, exist_ok=True)
        subprocess.run([self.binary/'initdb.exe', '-D', self.data, '-U', 'b2', '--encoding=UTF8', '--locale=C', '--auth=trust'],
                       env=self.env, check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        # Only loopback; no system service; stop only this owned cluster in finally.
        with (self.data/'postgresql.conf').open('a') as f:
            f.write(f"\nlisten_addresses='127.0.0.1'\nport={self.port}\nmax_connections=10\nshared_buffers='16MB'\n")
        try:
            subprocess.run([self.binary/'pg_ctl.exe', '-D', self.data, '-l', self.run/'postgres.log', '-w', 'start'],
                           env=self.env, check=True, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=45)
            self.started = True
        except Exception:
            subprocess.run([self.binary/'pg_ctl.exe', '-D', self.data, '-m', 'immediate', 'stop'], env=self.env, capture_output=True)
            raise
        return self

    def sql(self, statement, database='postgres', error=None):
        result = subprocess.run([self.binary/'psql.exe', '-X', '-At', '-v', 'ON_ERROR_STOP=1', '-v', 'VERBOSITY=verbose', '-d', database],
                                input=statement, text=True, encoding='utf-8', env=self.env, capture_output=True, timeout=30)
        if error:
            assert result.returncode != 0 and error in result.stderr, f'Expected SQLSTATE {error}: {result.stderr}'
        else: assert result.returncode == 0, result.stderr
        return result.stdout.strip()

    def __exit__(self, *exc):
        if self.started:
            # Windows fsync of the B4 crash matrix exceeded 30s; await clean shutdown,
            # still fail on pg_ctl error or the explicit bounded deadline.
            subprocess.run([self.binary/'pg_ctl.exe', '-D', self.data, '-m', 'fast', '-t', '180', '-w', 'stop'], env=self.env, check=True,
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=195)
