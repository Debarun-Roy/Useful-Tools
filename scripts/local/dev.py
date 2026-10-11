"""Owned localhost processes. Python stdlib; no global installs or production credentials."""
import argparse
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time
import urllib.request
import urllib.error
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
LOCAL = ROOT / '.local'
FRONTEND = ROOT / 'usefultools-frontend'
RUNTIME = ROOT / 'scripts/local'
ACTIVE = LOCAL / 'active.json'


def environment():
    env = os.environ.copy()
    if 'RAILWAY_ENVIRONMENT_ID' in env or 'VERCEL' in env:
        raise RuntimeError('Local launcher must not run on a hosting platform')
    # Never inherit a parent process database, captcha, Java agent or frontend endpoint.
    for name in ('SQLITE_DB_URL', 'SQLITE_DB_PATH', 'RECAPTCHA_SECRET_KEY', 'VITE_API_BASE',
                 'VITE_RECAPTCHA_SITE_KEY', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS',
                 'CORS_ALLOWED_ORIGINS', 'JAVA_OPTS', 'CATALINA_OPTS', 'NODE_OPTIONS'):
        env.pop(name, None)
    env.update(UT_PROFILE='local', UT_LOCAL_DIR=str(LOCAL),
               SQLITE_DB_PATH=str(LOCAL / 'data/UsefulTools.db'), SQLITE_DB_URL='',
               VITE_API_BASE='/api', NODE_ENV='development')
    private = LOCAL / 'backend.env'
    if private.exists():
        for line in private.read_text().splitlines():
            if not line.strip() or line.lstrip().startswith('#'):
                continue
            key, separator, value = line.partition('=')
            if key != 'RECAPTCHA_SECRET_KEY' or not separator:
                raise ValueError('Only RECAPTCHA_SECRET_KEY is supported in .local/backend.env')
            env[key] = value.strip()
    return env


def prepare():
    LOCAL.mkdir(exist_ok=True)
    if any(p.is_symlink() or (hasattr(p, 'is_junction') and p.is_junction())
           for p in (LOCAL, LOCAL / 'data', LOCAL / 'runs', LOCAL / 'classes')):
        raise RuntimeError('Local runtime/data must not be links to another directory')
    (LOCAL / 'data').mkdir(exist_ok=True)
    (LOCAL / 'runs').mkdir(exist_ok=True)
    config = LOCAL / 'local.properties'
    if not config.exists():
        shutil.copy2(ROOT / 'scripts/local/local.properties.example', config)
    override = FRONTEND / '.env.development.local'
    if not override.exists():
        shutil.copy2(FRONTEND / '.env.development.local.example', override)


def execute(command, cwd, name, env=None):
    log_path = LOCAL / 'verification' / (name + '-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ') + '.log')
    log_path.parent.mkdir(exist_ok=True)
    with log_path.open('w', encoding='utf-8') as log:
        result = subprocess.run(list(map(str, command)), cwd=cwd, env=env,
                                stdout=log, stderr=subprocess.STDOUT)
    with (LOCAL / 'verification/commands.jsonl').open('a', encoding='utf-8') as record:
        record.write(json.dumps({'command': list(map(str, command)), 'cwd': str(cwd),
                                 'exitCode': result.returncode, 'log': str(log_path)}) + '\n')
    if result.returncode:
        raise RuntimeError(f'{name} failed ({result.returncode}); inspect {log_path}')
    print(f'PASS {name}', flush=True)


def build():
    prepare()
    env = environment()
    env.pop('UT_PROFILE', None)
    env.pop('UT_LOCAL_DIR', None)
    env.pop('RECAPTCHA_SECRET_KEY', None)
    env['SQLITE_DB_PATH'] = str(LOCAL / 'build-tests.db')
    if not subprocess.check_output([shutil.which('node'), '--version'], text=True).startswith('v24.'):
        raise RuntimeError('The checked-in frontend requires Node 24.x')
    # Preserve the existing package lock. Install only when dependencies are absent.
    if not (FRONTEND / 'node_modules/vite/bin/vite.js').exists():
        execute([shutil.which('npm'), 'ci'], FRONTEND, 'frontend-install', env)
    # Quarantine only generated launcher outputs; preserve databases, config and run logs.
    archive = LOCAL / 'verification' / ('generated-before-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'))
    archive.mkdir(parents=True)
    for name in ('classes', 'classpath.txt'):
        source = LOCAL / name
        if source.exists():
            if not source.resolve().is_relative_to(LOCAL.resolve()):
                raise RuntimeError('Generated output escaped the local directory')
            shutil.move(str(source), str(archive / name))
    execute([shutil.which('mvn'), '-B', 'package'], ROOT / 'Useful-Tools', 'backend-build', env)
    execute([shutil.which('mvn'), '-B', 'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath',
             '-Dmdep.outputFile=' + str(LOCAL / 'classpath.txt')], RUNTIME, 'runtime-build', env)
    classes = LOCAL / 'classes'
    classes.mkdir(exist_ok=True)
    execute([shutil.which('javac'), '--release', '17', '-encoding', 'UTF-8', '-cp',
             (LOCAL / 'classpath.txt').read_text().strip(), '-d', classes,
             ROOT / 'scripts/local/Server.java'], ROOT, 'launcher-build', env)
    execute([shutil.which('npm'), 'run', 'build', '--', '--mode', 'development'],
            FRONTEND, 'frontend-build', env | {'NODE_ENV': 'production'})


def active():
    if not ACTIVE.exists():
        return None
    value = json.loads(ACTIVE.read_text())
    run = Path(value['run']).resolve()
    if not run.is_relative_to(LOCAL / 'runs'):
        raise RuntimeError('Invalid local process state')
    return run


def process_state(pid):
    """Read-only liveness/creation time. Never signal a recorded Windows PID."""
    if type(pid) is not int or pid <= 0:
        return {'state': 'unknown'}
    if os.name != 'nt':
        try:
            os.kill(pid, 0)
            return {'state': 'running'}
        except ProcessLookupError:
            return {'state': 'exited'}
        except OSError:
            return {'state': 'unknown'}
    import ctypes
    from ctypes import wintypes
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    kernel.OpenProcess.restype = wintypes.HANDLE
    kernel.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
    kernel.WaitForSingleObject.restype = wintypes.DWORD
    kernel.GetProcessTimes.argtypes = [wintypes.HANDLE] + [ctypes.POINTER(wintypes.FILETIME)] * 4
    kernel.GetProcessTimes.restype = wintypes.BOOL
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    kernel.CloseHandle.restype = wintypes.BOOL
    handle = kernel.OpenProcess(0x1000 | 0x100000, False, pid)  # QUERY_LIMITED_INFORMATION | SYNCHRONIZE
    if not handle:
        return {'state': 'exited' if ctypes.get_last_error() == 87 else 'unknown'}
    try:
        wait = kernel.WaitForSingleObject(handle, 0)
        if wait == 0:
            return {'state': 'exited'}
        if wait != 258:  # WAIT_TIMEOUT means still running; other failures are uncertain.
            return {'state': 'unknown'}
        created, exited, system, user = (wintypes.FILETIME() for _ in range(4))
        result = {'state': 'running'}
        if kernel.GetProcessTimes(handle, ctypes.byref(created), ctypes.byref(exited),
                                  ctypes.byref(system), ctypes.byref(user)):
            result['createdAt'] = ((created.dwHighDateTime << 32) | created.dwLowDateTime) / 10000000 - 11644473600
        return result
    finally:
        kernel.CloseHandle(handle)


def occupied_local_ports():
    occupied = []
    for port in (5173, 8080):
        with socket.socket() as probe:
            try:
                probe.bind(('127.0.0.1', port))
            except OSError:
                occupied.append(port)
    return occupied


def run_state(run):
    """A readiness file is historical evidence, not proof of a live process."""
    if not run or (run / 'stopped').exists():
        return {'state': 'stopped', 'processes': [], 'occupiedPorts': []}
    try:
        recorded = json.loads((run / 'processes.json').read_text(encoding='utf-8'))
        pids = [recorded['supervisorPid'], *recorded['pids']]
        if len(pids) != 3 or any(type(pid) is not int or pid <= 0 for pid in pids):
            raise ValueError('Incomplete process record')
        # Legacy records were written after all children started. A process created
        # later cannot be that recorded process, even if Windows reused its PID.
        recorded_at = datetime.fromisoformat(recorded['startedAt']).timestamp()
        identities = recorded.get('createdAt', {})
        if not isinstance(identities, dict) or any(type(v) not in (int, float) for v in identities.values()):
            raise ValueError('Invalid process identities')
        processes = []
        for pid in pids:
            observed = process_state(pid)
            created = observed.get('createdAt')
            expected = identities.get(str(pid))
            if observed['state'] == 'running' and created is not None:
                if (expected is not None and created != expected) or (expected is None and created > recorded_at):
                    observed = {**observed, 'state': 'pid-reused'}
            processes.append({'pid': pid, **observed})
    except (OSError, ValueError, KeyError, TypeError):
        return {'state': 'unknown', 'processes': [], 'occupiedPorts': occupied_local_ports(),
                'reason': 'Missing or invalid processes.json; automatic recovery cannot prove ownership.'}
    ports = occupied_local_ports()
    gone = all(p['state'] in ('exited', 'pid-reused') for p in processes)
    state = 'stale' if gone and not ports else 'unknown' if any(p['state'] == 'unknown' for p in processes) else 'registered'
    return {'state': state, 'processes': processes, 'occupiedPorts': ports}


def recover_stale_run(run):
    """Retire only proven-dead runs; retain active.json, readiness, logs and data."""
    observed = run_state(run)
    if observed['state'] != 'stale':
        return False
    (run / 'recovered-stale.json').write_text(json.dumps({
        'recoveredAt': datetime.now(timezone.utc).isoformat(), **observed}, indent=2), encoding='utf-8')
    (run / 'stop').touch()
    (run / 'stopped').touch()
    return True


def supervisor(run):
    env = environment()
    cp = str(LOCAL / 'classes') + os.pathsep + (LOCAL / 'classpath.txt').read_text().strip()
    children = []
    handles = []
    try:
        commands = [([shutil.which('java'), '-Xms32m', '-Xmx512m',
                      '-Djava.io.tmpdir=' + str(run), '-cp', cp, 'local.Server', str(ROOT), str(run)], run, 'backend'),
                    ([shutil.which('node'), str(FRONTEND / 'node_modules/vite/bin/vite.js'),
                      '--mode', 'development'], FRONTEND, 'frontend')]
        for command, cwd, name in commands:
            handle = (run / (name + '.log')).open('w', encoding='utf-8')
            handles.append(handle)
            children.append(subprocess.Popen(command, cwd=cwd, env=env, stdout=handle,
                                             stderr=subprocess.STDOUT,
                                             creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0))
        pids = [os.getpid(), *(p.pid for p in children)]
        identities = {str(pid): process_state(pid).get('createdAt') for pid in pids}
        (run / 'processes.json').write_text(json.dumps({'supervisorPid': os.getpid(),
            'startedAt': datetime.now(timezone.utc).isoformat(), 'pids': [p.pid for p in children],
            'createdAt': {pid: created for pid, created in identities.items() if created is not None},
            'commands': [command for command, _, _ in commands]}))
        while not (run / 'stop').exists():
            if any(p.poll() is not None for p in children):
                raise RuntimeError('A localhost service exited; inspect this run logs')
            time.sleep(.25)
    finally:
        (run / 'stop').touch()
        cleanup_errors = []
        for index, child in enumerate(children):
            try:
                if index != 0 and child.poll() is None:
                    child.terminate()
                try:
                    child.wait(timeout=20 if index == 0 else 10)
                except subprocess.TimeoutExpired:
                    child.terminate()
                    child.wait(timeout=10)
            except OSError as error:
                cleanup_errors.append(str(error))
            except subprocess.TimeoutExpired as error:
                cleanup_errors.append(str(error))
        for handle in handles:
            handle.close()
        if cleanup_errors:
            (run / 'cleanup-errors.json').write_text(json.dumps(cleanup_errors), encoding='utf-8')
        if all(child.poll() is not None for child in children):
            (run / 'stopped').touch()


def api_ready():
    # HTTP 401 is the expected unauthenticated API response, not a readiness failure.
    try:
        urllib.request.urlopen('http://localhost:5173/api/backend-support/catalog', timeout=2)
    except urllib.error.HTTPError as error:
        return error.code == 401 and 'json' in error.headers.get('Content-Type', '')
    except (OSError, TimeoutError):
        return False
    return False


def _start():
    prepare()
    environment()
    previous = active()
    if previous:
        recover_stale_run(previous)
    if previous and not (previous / 'stopped').exists():
        raise RuntimeError('A local run is registered; use status/stop before starting another')
    for port in (5173, 8080):
        with socket.socket() as probe:
            probe.bind(('127.0.0.1', port))  # Fail; never kill another port owner.
    if not (LOCAL / 'classes/local/Server.class').exists():
        raise RuntimeError('Run python scripts/local/dev.py build first')
    run = LOCAL / 'runs' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    run.mkdir()
    ACTIVE.write_text(json.dumps({'run': str(run)}))
    with (run / 'supervisor.log').open('w') as log:
        process = subprocess.Popen([sys.executable, str(Path(__file__).resolve()), '_serve', str(run)],
                                   cwd=ROOT, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f'Local startup failed; logs: {run}')
        try:
            with urllib.request.urlopen('http://localhost:5173/', timeout=1) as response:
                if response.status == 200 and (run / 'backend-ready.json').exists() and api_ready():
                    print('Ready: http://localhost:5173 (API proxy to http://localhost:8080)')
                    return
        except (OSError, TimeoutError):
            pass
        time.sleep(.3)
    (run / 'stop').touch()
    try:
        process.wait(timeout=35)
    except subprocess.TimeoutExpired:
        raise RuntimeError(f'Startup and cleanup not confirmed; inspect {run}; no unrelated PID was killed')
    raise RuntimeError(f'Local startup timed out; logs: {run}')


def start():
    prepare()
    lock = LOCAL / 'start.lock'
    try:
        lock.mkdir()  # Serialize simultaneous starts; never overwrite another run's state.
    except FileExistsError:
        raise RuntimeError('Another start owns .local/start.lock; inspect it before retrying')
    try:
        _start()
    finally:
        lock.rmdir()


def stop():
    run = active()
    if not run or (run / 'stopped').exists():
        print('Stopped')
        return
    if recover_stale_run(run):
        print('Recovered stale localhost registration; no processes killed; data and logs retained')
        return
    (run / 'stop').touch()
    deadline = time.monotonic() + 40
    while time.monotonic() < deadline:
        if (run / 'stopped').exists() or recover_stale_run(run):
            print('Stopped owned localhost services; data and logs retained')
            return
        time.sleep(.25)
    raise RuntimeError('Stop not confirmed. Current process/port state: ' + json.dumps(run_state(run)) +
                       f'. Inspect {run}. No unrelated process was killed; registration retained.')


def status():
    run = active()
    observed = run_state(run)
    healthy = bool(observed['state'] == 'registered' and api_ready())
    print(json.dumps({'run': str(run) if run else None,
                      **observed,
                      'apiHealthy': healthy,
                      'backendReadyMarker': bool(run and (run / 'backend-ready.json').exists())}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['build', 'start', 'stop', 'status', '_serve'])
    parser.add_argument('run', nargs='?')
    args = parser.parse_args()
    if args.command == '_serve':
        run = Path(args.run).resolve()
        if not run.is_relative_to(LOCAL / 'runs'):
            raise RuntimeError('Invalid supervisor directory')
        supervisor(run)
    else:
        globals()[args.command]()
