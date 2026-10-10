# Sprint B0 PASS

Tested 2026-09-18T12:53:22.229728+00:00 on `main`, HEAD `b36265e70701221a748b3f4881524c289bf32dd6`.
The working tree is dirty. HEAD alone does not identify these changes.
Input fingerprint: `fa974075d9c224c0ba6f746d3342340272814d7269a9ad468f216af32d444502`.

Fingerprint covers tracked and nonignored untracked files, excluding this generated report and evidence.json.
Full commands, working directories, exit codes, dirty state and evidence paths: [evidence.json](evidence.json). Run logs: `.b0\20260918T125143Z`.

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

## Checks

- node-version: PASS (exit 0)
- npm-version: PASS (exit 0)
- maven-version: PASS (exit 0)
- java-version: PASS (exit 0)
- python-version: PASS (exit 0)
- layout-and-docs: PASS (exit 0)
- frontend-install: PASS (exit 0)
- frontend-tests: PASS (exit 0)
- frontend-build: PASS (exit 0)
- backend-build: PASS (exit 0)
- war-integrity: PASS (exit 0)
- java-reference: PASS (exit 0)
- test-execution-counts: PASS (exit 0)
- initialization-logs: PASS (exit 0)
- database-initialization: PASS (exit 0)
- python-venv: PASS (exit 0)
- python-download: PASS (exit 0)
- python-install: PASS (exit 0)
- python-dependencies: PASS (exit 0)
- python-reference: PASS (exit 0)
- contracts: PASS (exit 0)
- java-classpath: PASS (exit 0)
- browser-smoke: PASS (exit 0)
- browser-startup: PASS (exit 0)
- diff-review: PASS (exit 0)
- inputs-unchanged: PASS (exit 0)

## Scope and limitations

Health/startup and bounded adapter tests do not certify production auth or generated SQL. Python RedisStore is exercised against fakeredis; live shared Redis and concurrent auth are later gates. Browser smoke blocks third-party fonts/CAPTCHA scripts and does not verify CAPTCHA or login. Java 17 API/bytecode is enforced; actual executing JDK is recorded in java-version.log. Docker deployment, real PostgreSQL and production services are outside this run.

B1 readiness: Ready for the bounded B1 shell/catalog/validation work; follow b1-handoff.md.
