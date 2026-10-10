# B4 sprint report

Verdict: **PASS**
Tested: 2026-09-26T05:45:22.617925+00:00
HEAD: `b36265e70701221a748b3f4881524c289bf32dd6` (dirty; no commit)
Input fingerprint: `4949b625ad6da743760cd88719cee845ded2859a9e030a6fbc1c6aea1638b419`

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

Command: `python -X utf8 scripts/b4/verify.py --browser-channel chrome`
Evidence directory: `.b4\20260926T053511Z`. Exact commands, versions, exits and counts are in evidence.json. Historical B0/B1/B2/B3 report bytes restored.

- Windows / Python 3.14.3 / PostgreSQL 17.11 verification boundary
- JDK 25 release-17 compilation; Java 17 execution not measured
- No hosted CI, deployment, production DB or B7 load qualification
- External checkpoint commit gap permits replay; insert requires acknowledgement; upsert side effects can repeat
- Finite destination shape checks do not certify defaults/checks/FKs/triggers/collation
- Synthetic session fixture is test-only; production security filters run

B5 prerequisites are satisfied only if all B4 gates PASS. Follow b5-handoff.md; B5 has not started.

## Verified delivery

Completed during continuation: recovered the interrupted B4 state; rebuilt current code; finished guided ETL integration, accessible controls and clipboard feedback; expanded empty-input, typed/bigint/decimal/composite-key, NOT NULL, preflight, fatal-connection and ambiguous-commit verification; completed the full verifier, CI configuration, traceability, review and B5 handoff. All implementation, configuration and test inputs remained unchanged during final certification.

- Backend: 45 tests passed, including eight B4 tests, no skips/failures/errors.
- Generated programs: 180 cases across 36 groups; CSV/JSON × SQLite/PostgreSQL each installed generated requirements in a fresh environment and executed actual HTTP-exported code.
- Runtime versions: Python 3.14.3; SQLite 3.50.4; PostgreSQL 17.11; psycopg and psycopg-binary 3.3.6; tzdata 2026.4.
- HTTP: 218 requests, 36 deterministic ETL exports, role/feature/CSRF/Origin/rate/concurrency and privacy checks.
- Browser: Chrome 153.0.8010.54; seven workflow groups, ten themes, twenty desktop/mobile screenshots, zero unexpected errors.
- Independent contracts: 83 assertions, five schemas, 37 actual previews including the browser export.
- B3/B2/B1/B0 chain passed exactly once in this certification. Earlier HTTP bundles matched all recorded byte pins. Historical report bytes were restored and checked again after finalization.

Restart guarantee: confirmed batches never advance checkpoint state before commit; interrupted current transactions resume from durable logical records. External commit/checkpoint gaps can replay. Insert requires explicit replay acknowledgement/manual reconciliation. Upsert deterministically reapplies selected mapped values but can repeat trigger and other side effects. No exactly-once guarantee or whole-file atomicity is claimed. Dry run requires no destination credentials and preserves destination/checkpoint/source state.

B5 prerequisites are satisfied by this PASS. B5 has not begun; recover its own user-authorized requirements before implementation. No implementation work remains for B4. No commit, push, deployment, production database operation or hosted CI run was performed. Owned verification processes exited. Report-only finalization does not change the certified input fingerprint.
