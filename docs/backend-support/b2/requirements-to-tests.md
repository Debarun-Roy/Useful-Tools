# B2 requirement-to-test mapping

Execution counts and current gate status are recorded in evidence.json. The first full run .b2/20260920T040838Z passed; archive review then added a timezone correction and another admin-export assertion. The final delivered verdict requires the subsequent complete run.

| Gate | Requirement | Executable evidence |
|---|---|---|
| G1 | B0/B1 foundations and integrations | scripts/b1/verify.py once; nested B0 clean builds/runtime smoke, 111 B1 HTTP requests, 9 browser groups, 10 themes |
| G2 | BSP03 versioned request/transport/response/manifest; compatibility | GenerationTest.contractsAndTypes; scripts/b2/contracts.py validates 7 fixtures, strict transport negatives, actual preview/manifest responses; pinned frozen-contract hashes |
| G3 | BSP04 types/defaults/checks/names/FKs/dependencies | GenerationTest namesAndNamespaces, relationshipsAndCycles, checksAndTypedDefaults, escapedResponseBudgetAndJsonLiteralBounds; real HTTP unsupported cases |
| G4 | SQLite executable exported bundle | database_checks.py: 43 mutation/metadata operations across customers/orders and composite/Unicode/self-reference fixtures; both extracted verify.py procedures |
| G5 | PostgreSQL executable exported bundle | Same HTTP ZIP inputs targeting PostgreSQL 17.11: 44 mutation/metadata operations; both extracted verify.py procedures; owned pinned cluster and psql |
| G6 | BSP12 deterministic safe bounded artifacts | GenerationTest deterministicNormalizationAndArchives, zipIsIndependentOfHostTimezone, pathsAndBounds, escapedResponseBudgetAndJsonLiteralBounds, concurrencyBound; actual ZIP/preview hashes and fixed-path inspection |
| G7 | BSP14 auth/access/CSRF/Origin/shared rate/privacy | http_checks.py: no identity, all roles/states, disabled admin generate/export, malformed/media/duplicate, size/depth, revalidation/digest mismatch, two occupied slots, shared 40 POST budget; application-log sentinels and WAR inspection |
| G8 | BSP13 complete UI and stale-state protection | b2-browser.mjs: 7 workflow groups, 20 theme/width screenshots, downloaded file/ZIP hashes, rename references, invalid JSON, escaped source, late generation/export, export/clipboard failures, native keyboard focus, guest/disabled/admin; b2-tests.mjs binary/JSON/non-JSON helper cases |
| G9 | Reproducibility/CI and historical evidence | scripts/b2/verify.py, runtime-results.json commands/cwd/exit codes, pinned EDB archive checksum, Windows CI same command, historical reports restored in finally; hosted CI execution not claimed |
| G10 | Review, traceability and handoff | review.md, decisions.md, frozen hashes, diff whitespace check, unchanged input fingerprint, continuation.md and b3-handoff.md |

The 20-table measurement is five sequential loopback generation responses after warmup, with every elapsed time and median recorded. It does not establish staging p95 or B7 load qualification. Boundaries and deliberate unsupported cases are in decisions.md. No later generator has been certified.
