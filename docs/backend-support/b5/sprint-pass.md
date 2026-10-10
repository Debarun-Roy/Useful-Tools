# B5 sprint report

Verdict: **PASS**
Tested: 2026-09-27T09:16:01.207923+00:00
HEAD: `b36265e70701221a748b3f4881524c289bf32dd6` (dirty; no commit)
Input fingerprint: `72b04eb1fee33fe367945bcbc0093ae1f57d9cfb79125e08884eec31a32b83ab`

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

Command: `python -X utf8 scripts/b5/verify.py --browser-channel chrome`
Evidence: `.b5\20260927T083741Z`. Exact commands, cwd, exit codes, counts, versions and matrix results are in evidence.json. Historical B0–B4 report bytes restored.

- Windows certification host; actual JDK/container/JDBC versions recorded by exported applications
- Release-17 compilation does not prove Java 17 execution
- Single-instance volatile sessions/throttling; no distributed revocation
- No real CAPTCHA traffic, hosted CI, deployment or production database access
- Self-review only; independent security review required before external auth-template release
- B6 Python implementation not started

B6 technical prerequisites are satisfied: every G1–G10 passed and the post-run fingerprint matches. Follow b6-handoff.md after separate user authorization; B6 is not started.

Continuation outcome: recovered the completed failed run .b5/20260926T202609Z, isolated its only failing gate to B4 PostgreSQL cleanup, and corrected the disposable-cluster shutdown deadline from 30 seconds to pg_ctl 180 / outer 195 seconds. The server had logged a slow durable checkpoint followed by clean shutdown; no product assertion, fsync setting or generated template was weakened. Full certification was rerun successfully. The failed run remains available.

Final verification: B0–B4 full regression chain PASS, including 180 B4 ETL cases; 42 historical ZIP pins identical; ten historical report files restored exactly. Eight actual HTTP-exported WARs clean-built with 40 generated unit tests (zero failures/errors/skips), 2,588 counted HTTPS requests across 100 runtime groups, plus concurrent registrations and four browser flows. Generator: 116 HTTP requests, seven browser groups, ten themes/two widths, zero unexpected browser errors. Six B5 generator unit tests passed. Negative startup, DB uniqueness/rollback/errors/restart, CSRF/Origin/session/throttle, CAPTCHA transport/provider, OpenAPI and framework-neutral parity cases passed.

Actual application runtimes: JDK 25, Tomcat 11.0.26, SQLite 3.53.4 with JDBC 3.53.4.0, PostgreSQL 17.11 with JDBC 42.7.13. Chrome 153.0.8010.54. Visual review of current Aurora mobile and Crimson Dusk desktop screenshots found readable controls and bounded layouts. Final read-only audit found no Java/Python/PostgreSQL process, owned postmaster.pid or generator sessions.json remaining. Source review covered template injection, nested archives, session/CSRF, profile identity, password handling, provider boundaries and secret-safe errors. No new product defect was found during this continuation.

Final handoff: the released B5 integration points and accepted decisions are in b6-handoff.md, decisions.md, api-contract.md and parity-fixtures.json. All required B5 work is complete. Next action is user authorization and scope for B6; do not begin it automatically. Independent security review remains required before external auth-template release. No commit, push or deployment was performed.

This report and evidence.json were enriched after execution with audit/count summaries only; continuation.md is also report-only. All are excluded by the B5 input fingerprint. No tested implementation, dependency, configuration, test or static handoff source changed after certification.
