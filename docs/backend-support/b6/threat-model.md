# B6 threat model

Assets: credentials/hashes, opaque session IDs and CSRF tokens, profile ownership, generator availability, ZIP integrity, and database/store resources. Trust boundaries: browser to UsefulTools; generated browser to HTTPS ASGI; ASGI to database/Redis/provider; operator-only configuration; isolated external verification harness.

| Threat | Control and verification |
|---|---|
| CSRF/session fixation/replay | Exact Origin plus stored CSRF, random opaque IDs, rotation/deletion, idle/absolute expiry; shared socket and browser lifecycle tests |
| Late session resurrection | Per-ID striped request lock, reread live record, explicit save only, no automatic middleware persistence; concurrent logout/rotation/expiry and old-cookie replay |
| Store outage/restart | No memory authentication fallback; safe503; per-boot namespace and TTL; live Redis outage/restart separate from application restart |
| Credential enumeration/CPU exhaustion | Generic401, bounded dummy verification, Argon2id19MiB/t2/p1, two hash workers and bounded account/address windows; malformed PHC, saturation and recovery |
| Mass assignment/cross-user profile | UUID/user role assigned by server; identity exclusively from session; fields allowlist and bound SQL; two users, unknown fields, rollback and DB failure |
| Parser/resource abuse | Streaming16KiB limit plus strict UTF8/duplicates/number/depth/count/string bounds, Pydantic strict models, no raw422; common negatives and crafted ASGI lengths |
| CAPTCHA bypass/SSRF | Fixed HTTPS endpoint, no redirects/proxy env, timeouts/8KiB cap, strict action/host/score/time/type; actual HTTP transport with isolated provider |
| Spoofed transport/topology | Direct TLS production, loopback-only cleartext development, proxy headers disabled, root_path rejected, one worker; ASGI boundary and startup cases |
| Generator abuse/ZIP injection | Existing authorization/availability/Origin/CSRF, shared budgets, fixed templates, strict versioned contract, deterministic manifest/digest and safe filenames; real HTTP export/browser checks |
| Secret leakage | Placeholder configuration only, sanitized errors/no request logging, no source/digest logging; runtime/generator logs and deployed WAR inspection |

Redis ACLs/TLS and protected network are operator responsibilities. Windows verification uses an owned loopback Redis process, no persistence and no system service. It never scans or flushes shared keys. A persistent Redis restart may retain records within current process namespace and TTL; restart without persistence revokes, and an application restart always revokes through a new namespace. Redis eviction/loss can end sessions. No distributed session revocation or multi-worker rate consistency is advertised.

External test clock/provider/scopes/control files exist only in scripts/b6 and do not appear in exported projects or production WAR. Controlled component saturation is diagnostic injection into an owned process, followed by actual HTTP failure/recovery assertions. This threat model and self-review are not independent review or production approval.
