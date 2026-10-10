# Python auth starter

Python 3.14, FastAPI with Uvicorn, SQLite or PostgreSQL, and real Redis are required. This is a single-process, single-worker same-origin API. UsefulTools emits source only. Review the project and SECURITY.md before deployment. There are no seeded accounts.

Create a new virtual environment using Python 3.14. Install with `python -m pip install -r requirements.txt`, run `python -m pip check`, then `python -m pytest`. All runtime and test dependencies are pinned. Start using `python -m @MODULE@ --cert /operator/tls/cert.pem --key /operator/tls/key.pem --port 8443`. Production requires direct TLS; the supported launcher disables forwarded-header trust and access logs. Multiple workers, root-path mounts, reverse-proxy TLS termination and reload are unsupported. The Python module name is the validated selected identifier.

Set the environment variable names shown in environment.example.json to operator-owned values. SQLite requires an absolute filesystem path. PostgreSQL requires a URI shaped `postgresql://HOST:PORT/DATABASE?sslmode=verify-full`, with user/password in separate named variables; sslmode=disable is permitted only on loopback. Redis requires rediss outside loopback, bounded connections, and an operator-owned database/ACL. No credentials belong in generator input or browser code. Placeholder examples are not runnable credentials.

Set the initialization variable to `true` only for a new empty database. Startup transactionally creates schema version 1; arbitrary existing schemas or unknown versions are rejected. Subsequent startup uses `false`. No automatic migrations or resets occur. Database users persist across application restart; sessions do not.

RedisStore from Starsessions stores opaque server-side sessions. A cryptographically random namespace is created for each application process start. Old namespace records expire by TTL, and are never accepted by a new process. No KEYS or FLUSHDB is issued. Explicit serialized lifecycle operations prevent late writes from restoring revoked sessions. Session capacity is 4096, Redis connections 16, limiter keys 4096, simultaneous hashes 2, DB workers 8. Local limits are not distributed. Redis outage fails affected operations; there is no in-memory fallback. Redis restart without persistence loses sessions, and persistence must not be treated as application restart continuity.

Client flow (retain the opaque cookie in a browser-managed cookie jar):

1. GET /api/auth/csrf-token; retain csrfToken.
2. POST /api/auth/register with username/password, trusted Origin and X-CSRF-Token. When enabled add a real reCAPTCHA v3 registration token. Response 201 does not log in.
3. POST /api/auth/login with the same security state and login action token if enabled. Response 200 rotates the cookie and token.
4. GET /api/auth/csrf-token again, then GET /api/auth/session. Optional profile: GET /api/user/profile and PATCH it with displayName/preferences and current Origin/CSRF. Profile owner is always the logged-in user.
5. POST /api/auth/logout with `{}`, trusted Origin and current CSRF. Response 204 clears the cookie and server session. GET session now returns authenticated:false.

openapi.json is the framework-neutral HTTP contract (version 0.5.0). The generator/template version is independently 0.6.0. Interactive docs and HTTP OpenAPI discovery are disabled; read the exported file. Unknown fields and invalid JSON are rejected without Pydantic value reflection. No cross-origin support. Production cookie JSESSIONID is Secure/HttpOnly/Lax at path / with no Domain. Explicit development permits loopback HTTP only. Switching mode never rewrites trusted origins.

CAPTCHA uses fixed Google HTTPS verification with strict response validation, no redirects, bounded body/time and fail-closed errors. Disabled mode is explicit. Enabled secret must exist at startup. No configurable provider URL or test-token bypass exists. External verification may inject a clock/transport in Python construction; none is exposed through environment or HTTP.

Unit tests supplement runtime verification. Before release run actual server HTTP/browser checks with real Redis and the selected database, including restart/outage/concurrent revocation. Independent security review, realistic load testing, TLS/ACL/backup/restore and dependency maintenance are operator release requirements. No production-readiness claim follows from generation.
