# B0 continuation checkpoint

Recovered 2026-09-18 on branch `main`, HEAD `b36265e70701221a748b3f4881524c289bf32dd6`.
All B0 changes are uncommitted; the initial checkout was clean. No branches switched.
Original and resume instructions and all five planning DOCX texts are available/read. No applicable AGENTS.md found in workspace or ancestor directories.

## State on recovery

1. Implemented and verified: repaired npm lockfile (npm ci and build exit 0); three Java baseline tests; 16 contract cases; Java reference WAR health and container session lifecycle; four Python tests including live Uvicorn (outside socket-restricted sandbox).
2. Implemented but needs final verification: Maven release 17 compilation and WAR inspector; full Python dependency freeze; application WAR startup (prior smoke exit 0 but packaging duplication discovered).
3. Partial: contracts need behavioral documentation; reference projects need setup documentation; test runner and adapters need final review.
4. Not started: compatibility matrix, decision log, repository baseline, CI, certification command, browser smoke, sprint report and B1 handoff.
5. Environment blocker: browser install failed EPERM writing default Playwright cache; retry with approved install in workspace. Sandbox network/socket restrictions required approved Maven/npm/pip runs. No remaining install process is assumed successful without its exit result.

## Findings and next steps

- Existing tracked WEB-INF/lib jars duplicate Maven-managed Gson and SQLite versions. Exclude legacy webapp libraries from WAR assembly and verify exactly one resolved copy; preserve original files.
- Starsessions InMemoryStore failed server-side expiration with replayed cookies. Rejected; RedisStore with fakeredis TTL now passes bounded feasibility checks. Live Redis is not verified and remains a later integration requirement.
- Java session test now uses the running container manager; the initial unstarted manager failed. Embedded Jasper added to avoid a missing JSP servlet startup exception.
- Next: finish WAR packaging, install browser, add isolated application/browser smoke, certification/CI, documentation, review, complete final verification.

Last evidence: `.b0/java-integration.log` (Maven verify exit 0, 2 integration tests); backend target/surefire-reports (3 tests); Python pytest (4 passed); frontend production build (exit 0). These are interim, not a B0 PASS.
Processes observed include other Java/Node activity; no unfamiliar processes terminated. New tests will own and close their subprocesses.

## Recovery implementation milestone

Completed documentation, CI, contract behavior notes and root certification command. Duplicate source JARs excluded without deleting legacy files; WAR inspector passes. Fixed Tomcat extraction into an owned temporary docBase and checked StandardContext startup failure policy. Targeted Java verification exit 0 with no startup exceptions and both ITs executing. Python wheel download retry succeeded.

First full attempt found an incorrect Context API call (fixed) and a transient clean-install DNS failure; dependent Python tests failed due to incomplete install. Browser installer stalled partway through extraction for over ten minutes; stopped only the owned installer/verifier sessions. Added explicit --browser-channel chrome/msedge alternative that executes the same checks and records actual browser version. Local Chrome 151.0.7922.176 is available. Default CI still installs pinned Chromium.

Next action: `python scripts/b0/verify.py --browser-channel chrome`; inspect failures, screenshot and final diff. No PASS claimed yet. No other processes stopped.


## Completion checkpoint and final review

The full command `python scripts/b0/verify.py --browser-channel chrome` passed G1-G10 at 2026-09-18T12:50:01Z. Evidence from that run is under `.b0/20260918T124729Z/`. Five existing frontend tests, three backend JUnit tests, one Java hash test plus two Java runtime tests, four Python tests and sixteen fixture cases passed. Both real servers and the built frontend ran successfully. Screenshot reviewed: existing login UI renders without clipping; no browser console/runtime errors.

Final code review covered tracked and untracked deliverables, source/lockfile changes, CI, contracts and verification isolation. No production route/endpoints, unrelated source changes or credentials were added. Working changes remain uncommitted on the original main branch. Existing legacy JAR files are preserved but excluded from WAR source assembly. The final verifier refinements make failed classpath resolution fail startup explicitly and close the static server if browser launch fails; clean/dirty reporting is now conditional.

A complete final verification is required after these refinements; its automatically generated `sprint-pass.md` and `evidence.json` supersede the interim run above and record the final input fingerprint. Do not interpret this checkpoint as overriding a later failed report.

Last completed step: all B0 implementation, visual inspection and diff review. Exact final command: `python scripts/b0/verify.py --browser-channel chrome` from the repository root. If it passes, B0 is complete and B1 can begin only on user instruction. If interrupted, inspect the latest `.b0/<timestamp>/` logs and rerun this command; no independent implementation work remains.

Known non-blocking limits: installed Chrome fallback (pinned Chromium extraction stalled), no hosted CI execution, no real Redis/PostgreSQL/Docker/CAPTCHA verification, no production auth security certification. Live Redis and full auth/SQL behavior belong to later sprints. Python HTTPX/AnyIO deprecations and existing Tomcat shutdown/JDBC warnings are retained in logs. No B0 failures are intentionally suppressed.
