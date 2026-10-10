# B5 threat model

Assets are user credentials/hashes, session identity, CSRF state, profile data, runtime secrets, generated project integrity and the host filesystem. Trust boundaries separate untrusted generator specifications, fixed source templates, exported runtime configuration, browser requests/cookies, the database, and the external CAPTCHA provider. UsefulTools renders source only; exported applications execute solely in operator environments and disposable tests.

| Threat | Boundary/control | Verification |
|---|---|---|
| Source/XML/path injection | Strict bounded schema, conservative package/artifact identifiers, reserved words; other submitted values serialized as JSON | B5Test identifier/contract/path negatives; actual HTTP unknown/version/target checks |
| Archive escape/collision | Opt-in ASCII relative regular-file paths; reject traversal, device names, drives, separators, case duplicates and file-directory conflicts; bounded deterministic ZIP | B5Test adversarial paths/count/bytes; export extraction resolves under owned root and checks hashes |
| Account abuse/resource exhaustion | DB unique normalized username, UUID/role assigned server-side, Argon2id random salt, two concurrent hashes, bounded address/account limiter and 4096 sessions | Actual duplicate concurrency/login/role tests; generated hashing/limiter tests; HTTP body bounds and Retry-After/reset |
| Fixation/CSRF/session theft | Opaque server session, login ID/token rotation, idle/absolute expiry, cookie-only tracking, HTTPS Secure/HttpOnly/Lax, exact Origin plus stored token; missing state fails | Actual HTTPS negative matrix, stale/cross/missing tokens, logout/replay/restart, browser client |
| Cross-user profile access | Session-derived identity, fixed allowlisted fields, no owner or username mutation; bound SQL | Profile/cross-user/mass-assignment tests and injected transaction failure |
| Database misconfiguration/corruption | Explicit configuration and initialization, no fallback or destructive reset, version 1 only, parameterized values and transactions | Both JDBC adapters, missing/invalid startup, persistence/restart, duplicate constraints and rollback |
| Provider forgery/outage | Fixed HTTPS Google endpoint; success/action/score/hostname/types/age; timeouts/response cap; enabled missing secret fails | Isolated provider transport tests for rejection, malformed response, wrong action/host/score, age, replay and outages |
| Secret/data leakage | Environment names in specs; secrets only runtime env; constant errors/correlation IDs, no exception logging in handlers | Response/log privacy scans, WAR inspection, placeholder examples |

Residual boundaries: single-instance sessions and throttling, finite limits, operator-controlled database/schema/configuration and connector settings, provider dependency when enabled, no cross-origin browser API, no account recovery/MFA/email lifecycle. Forwarded headers are ignored; TLS must reach the servlet connector. Container/proxy logs must not record sensitive headers or bodies. The embedded test clock/provider wiring is external Java construction, not a production configuration switch, endpoint or authentication bypass. Synthetic generator sessions never authenticate an exported application.

Independent security review is required by the original release plan before external release of auth templates. This threat model and the implementation self-review do not satisfy that independent gate. Certification only establishes the executed acceptance matrix against its recorded inputs.
