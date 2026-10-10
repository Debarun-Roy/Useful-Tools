# B7 traceability

| Requirement | Current executable evidence | Supplemental / acceptance |
|---|---|---|
| BS01 workspace | B1–B6 browser suites: invalid/unapplied drafts, stale responses, clipboard, themes, retained targets | B7 browser.mjs axe70 module/target/theme scans, keyboard, screenshots and deep-link/Back; human assistive technology still required |
| BS02 schema | B2 actual HTTP ZIP DDL/constraints on SQLite/PG | B7 schema→ETL→view journey and backup restoration |
| BS03 migration | B3 actual generated runner, data-preserving additions/rename/index and reverse; mixed/destructive rejection and injected rollback | Operator snapshot/dependency precondition review; no invented down script |
| BS04 views | B3 real joins/NULL/group/aggregate outputs on both DBs | B7 exported view over exported ETL destination |
| BS05 evaluator | B3 rule positive/negative fixtures and actual exported report | Findings are diagnostic; require independent generator validation |
| BS06 ETL | B4 four fresh environments; dry/strict/continue/upsert, malformed types, real batches and crash/checkpoint replay | B7 1000 rows, no-write dry run, postload constraints/data/restore |
| BS07 auth | B5/B6 real HTTPS apps across16 variants, shared parity/OpenAPI, DB/Redis/provider faults and browser flows | Independent reviewer; deployment Redis/TLS/ACL and load qualification |
| BS08 integrity | Every prior suite manifest/ZIP/digest/privacy; generated installs/tests | B7 deterministic exports and hashes, scanner inventory and deployment no-dump change |
| BS09 integration | B1 role/feature/guest/CSRF/search/favorites/activity; shared40/min and two-slot bound | B7 concurrency/disconnect/recovery and scanner checks; actual UsefulTools credential login and local-provider negative cases in login_checks.py; real provider/target acceptance remains external |

G1–G10 status lives in evidence.json and release-checklist.md. No optional BS10 gate substitutes for required P0. Planning proposals that differ from accepted versioned semantics use B5 frozen API (anonymous session200) and B2/B3 supported subsets, not an invented wider contract.

B7 external-harness additions: Java hash2/limiter4096/container-session4096 saturation; Python database-worker8/real Redis pause plus inherited lifecycle faults. B5/B6 record representative bounded authenticated-session workloads on both databases, separate from generator/ETL timings. package_checks verifies assembled WAR resource bytes and excludes harness classes; login_checks verifies real UsefulTools credential lifecycle and raw-log privacy. CI runs the same engineering graph; independent signatures and intended-environment results remain separate.
