# B2 sprint report

Verdict: **PASS**
Tested: 2026-09-20T04:19:10.445875+00:00
HEAD: `b36265e70701221a748b3f4881524c289bf32dd6` (uncommitted)
Input fingerprint: `0d8c24dfd3535622ae0f8be134e7c6fcbb5785f08b5dab9f1a0f1d8b86bf99e8`

| Gate | Result |
|---|---|
| G1 | PASS |
| G2 | PASS |
| G3 | PASS |
| G4 | PASS |
| G5 | PASS |
| G6 | PASS |
| G7 | PASS |
| G8 | PASS |
| G9 | PASS |
| G10 | PASS |

Exact command: `python -X utf8 scripts/b2/verify.py --browser-channel chrome`
Logs, exported synthetic fixtures and screenshots: `.b2\20260920T041537Z`.
Commands, working directories, exit codes, counts, database/browser versions and B1/B0 regression evidence are in evidence.json. Historical B0/B1 reports were restored byte-for-byte.

- Hosted CI and deployment not executed
- Java 17 runtime not executed; release 17 compilation on JDK 25
- PostgreSQL 17.11 only; Windows certification host
- No B3 or B7 load qualification; 20-table times are local sequential HTTP observations
- External CAPTCHA inert in verification; account login replaced by external synthetic sessions, actual production filters execute

B3 readiness requires every gate PASS. See requirements-to-tests.md, decisions.md, continuation.md, review.md and b3-handoff.md. No B3 work was started.

## Executed results

- 25 backend tests: 10 generation, 11 B1 validation, 1 availability, 3 baseline; zero failures/errors/skips.
- B0 and B1 regressions PASS; B1 includes 111 HTTP requests, 9 browser groups and 10 themes.
- B2: 2 frontend unit tests, 121 HTTP requests in 6 groups, 21 independent contract assertions across 5 schemas.
- HTTP-exported ZIP SQL executed in SQLite 3.50.4 (43 mutation/metadata operations) and PostgreSQL 17.11 (44); both bundles' verify.py procedures passed for both fixture sets. PostgreSQL CLI driver: psql 17.11.
- Chrome 153.0.8010.48: 7 browser workflow groups, 10 themes, 20 screenshots, zero unexpected browser errors. File/ZIP hashes match previews/manifests; stale export cannot download.
- ZIP bytes are identical across Asia/Kolkata, America/Los_Angeles and UTC; exported entries have fixed DOS dates and no extended timestamp fields.
- Local 20-table sequential loopback generation: 20.537, 30.322, 32.373, 32.098, 33.733 ms; median 32.098 ms. Windows 11 amd64, JDK 25, Node 24.16.0, npm 11.16.0, Python 3.14.3. This is not a staging p95 or load qualification.

B3 prerequisites are satisfied for the certified B2 boundary. No B3 implementation started. Explicitly unsupported: multi-table cycles, raw SQL expressions, deferred constraints/identity sequences, migrations, views, evaluator, ETL and REST/auth generation. SQLite affinity/approximate decimal and date/timestamp text conventions remain documented limitations. No commits, push, merge, deployment or production database access occurred.
