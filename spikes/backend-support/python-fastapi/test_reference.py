import socket
import subprocess
import sys
import time
from pathlib import Path

import httpx
import pytest
from argon2 import PasswordHasher, Type
from argon2.exceptions import VerifyMismatchError
from fastapi import FastAPI, Request
from fastapi.testclient import TestClient
from starsessions import SessionMiddleware, load_session
from starsessions.stores.redis import RedisStore
from fakeredis.aioredis import FakeRedis
from app import app


def test_health_contract():
    with TestClient(app) as client:
        assert client.get('/health').json() == {'status': 'ok', 'version': '0.1.0', 'target': 'python-fastapi'}
        assert client.get('/missing').status_code == 404
        assert client.post('/health').status_code == 405


def test_argon2id():
    hasher = PasswordHasher(time_cost=2, memory_cost=19456, parallelism=1, hash_len=32, salt_len=16, type=Type.ID)
    encoded = hasher.hash('synthetic-only-password')
    assert encoded.startswith('$argon2id$')
    assert hasher.verify(encoded, 'synthetic-only-password')
    with pytest.raises(VerifyMismatchError):
        hasher.verify(encoded, 'wrong-password')


def test_opaque_session_lifecycle():
    # Test-only adapter routes, never included in the reference app.
    sample = FastAPI()
    store = RedisStore(connection=FakeRedis())
    sample.add_middleware(SessionMiddleware, store=store, lifetime=2, cookie_https_only=True, cookie_same_site='lax')

    @sample.post('/create')
    async def create(request: Request):
        await load_session(request)
        request.session['principal'] = 'synthetic-user'
        return {'created': True}

    @sample.get('/read')
    async def read(request: Request):
        await load_session(request)
        return {'principal': request.session.get('principal')}

    @sample.post('/invalidate')
    async def invalidate(request: Request):
        await load_session(request)
        request.session.clear()
        return {'invalidated': True}

    with TestClient(sample, base_url='https://testserver') as client:
        response = client.post('/create')
        cookie = response.headers['set-cookie']
        assert 'httponly' in cookie.lower() and 'secure' in cookie.lower() and 'samesite=lax' in cookie.lower()
        assert 'synthetic-user' not in cookie
        token = client.cookies.get('session')
        assert token and len(token) >= 32
        assert client.get('/read').json()['principal'] == 'synthetic-user'
        client.post('/invalidate')
        client.cookies.set('session', token)
        assert client.get('/read').json()['principal'] is None
        client.cookies.clear()
        client.post('/create')
        token = client.cookies.get('session')
        # Advance server clock, explicitly replay cookie: browser expiry alone is insufficient.
        time.sleep(2.1)
        client.cookies.clear()
        client.cookies.set('session', token)
        assert client.get('/read').json()['principal'] is None


def test_real_uvicorn_process():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    process = subprocess.Popen([sys.executable, '-m', 'uvicorn', 'app:app', '--host', '127.0.0.1', '--port', str(port)], cwd=Path(__file__).parent, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        deadline = time.monotonic() + 20
        while True:
            assert process.poll() is None, 'Uvicorn exited before health check'
            try:
                response = httpx.get(f'http://127.0.0.1:{port}/health', timeout=1)
                break
            except (httpx.ConnectError, httpx.ConnectTimeout):
                if time.monotonic() > deadline:
                    raise AssertionError('Uvicorn startup timed out')
                time.sleep(0.1)
        assert response.status_code == 200
        assert response.json()['target'] == 'python-fastapi'
    finally:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
