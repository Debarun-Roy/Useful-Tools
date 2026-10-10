"""Checksum-pinned real Redis portable build; no Windows service or shared server."""
import hashlib,json,os,socket,subprocess,time,urllib.request,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
VERSION='8.2.10'
SHA256='92d9bdde87ed52bf859a677e5197d8ac855ba78d9940d457881723ae960df429'
URL='https://github.com/redis-windows/redis-windows/releases/download/8.2.10/Redis-8.2.10-Windows-x64-cygwin.zip'
def provision():
    if os.name!='nt':raise RuntimeError('BLOCKED: this certification adapter supports Windows x64')
    base=ROOT/'.b6';base.mkdir(exist_ok=True);archive=base/'redis-8.2.10.zip'
    if not archive.exists():urllib.request.urlretrieve(URL,archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest()!=SHA256:raise RuntimeError('Redis checksum mismatch')
    dest=base/'redis'
    binary=dest/'Redis-8.2.10-Windows-x64-cygwin/redis-server.exe'
    if not binary.exists():
        with zipfile.ZipFile(archive) as z:
            for n in z.namelist():
                path=(dest/n).resolve()
                if not path.is_relative_to(dest.resolve()):raise RuntimeError('Unsafe archive')
                if not n.endswith('/'):
                    path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(z.read(n))
    with zipfile.ZipFile(archive) as z:
        for entry in z.infolist():
            if not entry.is_dir():
                candidate=(dest/entry.filename).resolve()
                if not candidate.is_relative_to(dest.resolve()) or not candidate.is_file() or hashlib.sha256(candidate.read_bytes()).digest()!=hashlib.sha256(z.read(entry)).digest():raise RuntimeError('Redis extracted runtime checksum mismatch')
    if VERSION not in subprocess.check_output([binary,'--version'],text=True):raise RuntimeError('Redis version mismatch')
    return binary
class Server:
    def __init__(self,directory):
        self.directory=Path(directory).resolve()
        if not self.directory.is_relative_to(ROOT/'.b6'):raise ValueError('Owned .b6 directory required')
        self.binary=provision()
        with socket.socket() as s:s.bind(('127.0.0.1',0));self.port=s.getsockname()[1]
    def __enter__(self):
        self.directory.mkdir(parents=True,exist_ok=True)
        self.log=(self.directory/'redis.log').open('a')
        self.command=[str(self.binary),'--bind','127.0.0.1','--port',str(self.port),'--protected-mode','yes','--save','','--appendonly','no','--maxmemory','64mb','--maxmemory-policy','noeviction']
        self.process=subprocess.Popen(self.command,cwd=self.directory,stdout=self.log,stderr=subprocess.STDOUT)
        deadline=time.monotonic()+20
        while time.monotonic()<deadline:
            if self.process.poll() is not None:raise RuntimeError('Redis startup failed; see owned redis.log')
            try:
                with socket.create_connection(('127.0.0.1',self.port),timeout=1) as s:
                    s.sendall(b'*1\r\n$4\r\nPING\r\n')
                    if s.recv(64)==b'+PONG\r\n':return self
            except OSError:pass
            time.sleep(.1)
        self.__exit__();raise RuntimeError('Redis readiness timeout')
    def __exit__(self,*exc):
        if self.process.poll() is None:
            try:
                with socket.create_connection(('127.0.0.1',self.port),timeout=2) as s:s.sendall(b'*2\r\n$8\r\nSHUTDOWN\r\n$6\r\nNOSAVE\r\n')
            finally:self.process.wait(timeout=15)
        self.log.close()
        if self.process.returncode!=0:raise RuntimeError('Owned Redis exit nonzero')
if __name__=='__main__':
    with Server(ROOT/'.b6/redis-probe') as server:print('PASS real Redis',VERSION,'loopback PING',server.port)
