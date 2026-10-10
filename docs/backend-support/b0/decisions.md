# B0 technical decisions

Accepted for the next implementation slice; they are not production security approval.

1. **Keep current layout and frontend topology.** npm lockfile is authoritative; Maven standard directories already exist. release 17 prevents accidental newer JDK API use. Exclude checked-in WEB-INF/lib from source assembly so resolved Maven versions are unique. No broad dependency upgrade or production auth change.
2. **One Java generator service later.** Java Servlet and Python FastAPI are output targets; Python is only a development reference now. SQLite/PostgreSQL, same-dialect migrations and guest sample-only previews remain planning defaults. No scripts or customer database connections execute in UsefulTools.
3. **Argon2id in future starters.** Password4j 1.8.4 and argon2-cffi 25.1.0, memory 19456 KiB, iterations 2, parallelism 1, 16-byte random salt, 32-byte output. Explicit parameters avoid library-default drift. Correct/wrong password checks establish feasibility only. Benchmark on deployment hardware, cap input/work, store PHC hashes, and rehash on successful login when policy changes. Do not migrate existing jBCrypt users in B0.
4. **Server-side opaque sessions.** Java uses container HttpSession with ID rotation on login, server invalidation on logout and idle expiry. Current B0 test exercises a running Tomcat manager. Single-instance memory storage loses sessions on restart and cannot support multiple replicas without a reviewed shared/replicated store. Python selects Starsessions RedisStore with redis-py. Test Redis operations with fakeredis; real Redis, transport security, outage policy and concurrent invalidation are deferred requirements. InMemoryStore is explicitly rejected because a replayed expired token remained authorized in the initial test. Starsessions regeneration alone does not prove old-token deletion; B5/B6 must verify rotation revokes the prior record. No JWT system.
5. **Cookie and CSRF policy.** Future starters use same-origin Secure/HttpOnly/SameSite=Lax session cookies by default. Unsafe cookie-auth requests require a session-bound token and trusted origin. Missing token in an authenticated session must fail closed. Python test-only routes intentionally do not implement auth/CSRF and are absent from the application. Never use them as generated handlers.
6. **CAPTCHA boundary.** Server verifies provider result, action, age and score at the protected action; enabled-but-unconfigured fails closed. A CAPTCHA pass is not authentication. Separate tickets, if introduced, must be one-use and bound to session/action/expiry. No real provider requests in B0.
7. **Versioned draft contracts.** schemaVersion 0.1.0 and templateVersion 0.1.0-b0 are a reviewed starting point; strict unknown-field handling at object boundaries. Narrow fixture probes are not the production semantic validator. No generated-artifact correctness claims from JSON Schema validation.
8. **Evidence before PASS.** All ten gates must pass. Certification builds and starts both references, the existing WAR, and the built frontend with an isolated database. Reports identify dirty input fingerprint as well as HEAD. Missing tools/network/browser are blocked, not skipped success.

## Existing concerns kept separate from B0

- Existing CsrfFilter grants a pass to sessions without a token. Do not inherit this in B1 validation/generation endpoints or later starters.
- ToolToggleDAO defaults tools on and has fail-open behavior; B1 needs an explicit disabled pilot and server-side fail-closed control.
- Existing session-status includes a session ID; generated profiles/session responses must expose minimal identity only.
- Existing Gson omits null envelope fields despite ApiResponse comments showing them. Draft contracts accept omitted null fields; baseline test records actual behavior.
- Existing cookie comments about setSameSite and context configuration are not a reusable security design. Revisit with official container API and real browser auth tests in B5.
- Existing debug/database logs may reveal paths and auth metadata; B1 metrics must be metadata-only and never copy this pattern for specifications.
- Existing Docker tags float and dump handling deserves separate deployment review. No production deployment certified.

## Open questions and change process

B1 must decide admin preview bypass, catalog availability while disabled, exact semantic finding codes, request/response limits, and framework/database runtime catalog pins. Initial proposed caps are 1 MiB request, 100 tables, 1000 columns, 100 files, 5 MiB output; they are not enforced by a B0 production API. B2 must define decimals, timestamps, cycles and quoting per dialect. Python Redis adds an operational dependency; if a single-file SQLite session option is required, review a maintained alternative rather than falling back to InMemoryStore.

To change contracts: add a version directory for breaking changes, include a migration note, update positive/negative fixtures, rerun the full suite and update consumers deliberately. Template output changes require a templateVersion bump. Never silently reinterpret an exported old spec.
