# Sprint B0 foundations

B0 contains build/test foundations, draft contracts and two isolated reference projects. No production BackendSupportPage, generator endpoints or auth starters are implemented. Current status and exact commands are in [sprint-pass.md](sprint-pass.md) and [evidence.json](evidence.json).

## Reproduce

From the Git root (inner Useful-Tools folder), with Node 24.16.0, npm 11.13.0, Maven 3.9.x, JDK 25 and Python 3.14.3 on PATH:

```
python scripts/b0/verify.py
```

The command installs locked npm dependencies, clean-builds the backend and Java reference, checks WAR contents and test execution, creates a fresh Python venv, downloads pinned wheels, installs them, checks contracts, starts isolated servers and verifies built frontend behavior in Chromium. First run requires Maven Central, npm, PyPI and Playwright browser-download access. Only loopback application traffic is used. No production databases, dumps, CAPTCHA or production services are contacted.

If a browser is already installed, explicitly select it:

```
python scripts/b0/verify.py --browser-channel chrome
```

`msedge` is also supported. This still executes every required browser check; the browser version is recorded in browser-smoke.log. The default downloads Playwright's pinned Chromium. Hosted CI uses the default and uploads reports/logs. Browser installation can be slow or blocked by environment restrictions; a missing/unlaunchable selected browser fails verification.

Each run writes sanitized command logs under .b0/<UTC timestamp>/, a screenshot, evidence.json and sprint-pass.md. The runner returns nonzero for failed or blocked checks. A fresh venv prevents accidental reliance on installed Python packages. Dependency wheels and browsers are local disposable caches. Git HEAD plus the input fingerprint identifies uncommitted changes; generated report/evidence are excluded to avoid self-reference.

## Checklist

- [x] Read original/resume instructions and five DOCX planning texts; inspect clean baseline.
- [x] Reconcile npm lockfile and Maven release/plugin/WAR dependency handling.
- [x] Add meaningful backend tests; preserve existing frontend tests.
- [x] Add Java/Python health reference projects and bounded hash/session probes.
- [x] Version six module models and 16 positive/negative fixtures.
- [x] Document runtime/security decisions and B1 integration map.
- [x] Add shared local/CI verification command and sanitized evidence report.
- [x] Complete full verification and visual/diff review; the latest generated report remains authoritative after any change.

## Documents

- [Repository baseline](repository-baseline.md): observed failures and layout.
- [Compatibility matrix](compatibility-matrix.md): selected versus proposed/tested runtimes.
- [Decisions](decisions.md): adapter policy and existing security concerns.
- [Continuation](continuation.md): recovery and remaining work.
- [B1 handoff](b1-handoff.md): exact bounded next steps.

Safety: current branch preserved; no push, merge, deployment or production data access. Example credentials are synthetic. B0 PASS only means foundation readiness for B1.
