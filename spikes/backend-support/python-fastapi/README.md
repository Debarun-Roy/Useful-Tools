# Python FastAPI B0 reference

Health-only application, not production auth scaffolding. CPython 3.14.3 is the tested runtime. All resolved dependencies including tests are pinned in requirements.txt; requirements.in records selected direct inputs. To update, create a fresh venv, install requirements.in, freeze, review the diff and rerun B0.

From this directory on Windows:

```
python -m venv .venv
.venv\Scripts\python -m pip install -r requirements.txt
.venv\Scripts\python -m pip check
.venv\Scripts\python -m pytest -q
.venv\Scripts\python -m uvicorn app:app --host 127.0.0.1 --port 8086
```

On POSIX use .venv/bin/python. GET /health returns status, version and target. Stop with Ctrl+C. No environment variables or credentials are required; .env.example contains future placeholders only.

Tests include actual Uvicorn process startup, 404/405 behavior, Argon2id verification and test-only session routes. RedisStore uses fakeredis for bounded TTL/invalidation/replayed-cookie checks. This does not prove live Redis, concurrent revocation or production auth. Starsessions InMemoryStore was rejected after it failed expiry. Do not copy test routes into a production starter. Store connections and shared rate limits require deployment-specific design later.
