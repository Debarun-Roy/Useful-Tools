# Sprint B6: Python auth and shared parity

Scope is BS07–BS08 Python FastAPI auth starter, SQLite/PostgreSQL, CAPTCHA off/recaptcha-v3, core plus optional profile. Generator schema 0.6.0/template python-auth-0.6.0-b6 is independent from unchanged Java 0.5.0. Both use the frozen HTTP API 0.5.0. No B7 work, account recovery, email, MFA, OAuth, JWT, account deletion or arbitrary endpoints.

From the Useful-Tools repository root run:

    python -X utf8 scripts/b6/verify.py --browser-channel chrome

Omit the channel to use the B0 pinned browser. The supported verification host is Windows x64 with Python 3.14, Java/Maven, Node/npm and access to pinned dependency archives. The same command is configured in .github/workflows/b6.yml; this is not evidence of hosted CI execution.

The verifier runs B5 once through B0, saves/restores twelve historical report files, checks frozen inputs, builds the generator through the regression chain, independently validates contracts, exports eight Python projects over authenticated HTTP and checks eight historical Java ZIP digests. Each Python export gets a fresh venv, pinned install, pip check, import, tests and actual HTTPS ASGI process. Disposable PostgreSQL and checksum-pinned real Redis run on loopback. Java WARs from the same regression run are built and exercised with the identical shared sequence and response comparator. Browser authentication uses the existing Java assertions against Python as well.

Generated project README/SECURITY documents environment placeholders, empty-database initialization, requirements install and direct TLS launch. Python is single worker, root path only, with no forwarded-header trust. RedisStore holds opaque server-side sessions; per-boot namespace revokes on application restart. No production DB or real CAPTCHA provider is contacted by certification.

The authoritative result is sprint-pass.md and evidence.json, including input/final fingerprints, exact commands, exit codes, versions, ZIP hashes, trace counts, gate states and limitations. Diagnostic runs below .b6 do not certify B6. See requirements-to-tests.md, parity-matrix.md, decisions.md, review.md and b7-handoff.md. Independent security review remains required before external auth-template release.
