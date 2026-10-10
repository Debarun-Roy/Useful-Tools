# B5 acceptance checklist

All gates are required. Execution status is recorded in sprint-pass.md and evidence.json. The first full run passed G2–G10; G1 failed on prior-sprint database cleanup timeout. Final certification must pass every gate.

| Gate | Required checks |
|---|---|
| G1 | One B4→B3→B2→B1→B0 chain; restore historical reports; frozen template/contract and actual ZIP byte pins |
| G2 | Version 0.5.0, Java-only runtime, identifiers, coherent core/profile dependencies, catalog and strict configuration |
| G3 | Actual HTTP export, clean Maven resolution/test/package, real WAR startup and persistence on SQLite/PostgreSQL; invalid configuration and transaction rollback |
| G4 | Register/login/logout/session/profile; duplicate concurrency, mass assignment, cross-user identity and safe response fields |
| G5 | Rotation, idle/absolute expiry, stale/cross/missing-stored CSRF, Origin, cookie-only tracking, HTTPS cookies, bounded account/address limits and clocks, malformed/duplicate/oversized JSON and methods |
| G6 | CAPTCHA on/off on both DBs; action/hostname/score/freshness/types/replay/provider rejection/timeout/unavailability; no real provider calls |
| G7 | Pinned OpenAPI validation, actual response schema checks, complete client sequence and shared framework-neutral B6 fixtures |
| G8 | Nested archive adversarial cases, bounds/determinism/export revalidation/privacy, real generator HTTP role/feature/security matrix, browser drafts/races/errors/accessibility/mobile/ten themes |
| G9 | Full verify.py, matching CI command, exact commands/cwd/versions/exits/counts/fingerprint and owned-process cleanup |
| G10 | Focused self-review/remediation, threat model, traceability, final unchanged-input evidence and B6 handoff |

The original sprint prompt remains authoritative for individual negative cases. Implementation tests cannot substitute for exported-WAR HTTP checks. Missing required infrastructure is BLOCKED and nonzero; executed failures are FAIL. No skipped security/database/CAPTCHA gate may be labelled PASS.

Executable mapping: G1 is scripts/b5/verify.py's single nested B4 chain, b4-bundle-sha256.json (42 non-browser historical HTTP ZIPs) and frozen-contracts.json. G2 uses backend B5Test, independently pinned scripts/b5/contracts.py and export_checks.py catalog/dependency/target/version cases. G3–G7 use scripts/b5/application_checks.py across SQLite/PostgreSQL × CAPTCHA off/on × core/profile: clean Maven tests/package; actual WAR HTTPS requests; real register/login, DB errors/rollback/restart, synthetic provider failures and OpenAPI response validation. Shared parity-fixtures.json drives language-neutral negative cases; its positive sequence is exercised by the actual client matrix and HTTPS browser client.

G8 uses export_checks.py for role/feature/CSRF/Origin/strict body/budget/determinism/extraction and tests/b5-browser.mjs for guided choices, invalid dependencies, draft retention, races, failed exports/clipboard, keyboard, ten themes/two widths and access. tests/b5-application-browser.mjs uses the external same-origin HTTPS page and real generated endpoints. G9 uses the full verifier and .github/workflows/b5.yml; the external StarterServer owns its local provider/clock and never changes production auth code. G10 includes documented focused self-review/threat model, visual review, historical report preservation, diff whitespace and an unchanged final fingerprint. Reports record actual results; a check's existence is not its PASS.
