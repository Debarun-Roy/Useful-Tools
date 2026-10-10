# B3 sprint report

Verdict: **PASS**
Tested: 2026-09-24T19:25:29.196932+00:00
HEAD: `b36265e70701221a748b3f4881524c289bf32dd6` (dirty; no commit)
Input fingerprint: `34fda6ba85ef28214d5849940b637f80ef98e505da7d643a9780dd3038aa4bcd`

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

Command: `python -X utf8 scripts/b3/verify.py --browser-channel chrome`
Evidence directory: `.b3\20260924T192024Z`. Exact commands, exits, versions and counts are in evidence.json. Historical B0/B1/B2 reports restored byte-for-byte.

- Windows certification host; pinned PostgreSQL/psql 17.11 only
- JDK 25 release-17 compilation; Java 17 runtime not executed
- No hosted CI, deployment, production database or B7 load qualification
- Schema shape prechecks do not prove default/FK/CHECK/collation equivalence; maintenance and operator review required
- Column additions have no lossless automatic down script; unsupported operations block complete plans
- Synthetic session fixture is test-only; actual production security filters execute

B4 prerequisites are satisfied only when all B3 gates PASS. Follow b4-handoff.md; B4 has not started.

## Executed results

- 37 backend tests, including 12 B3 tests; zero failures/errors/skips.
- B2/B1/B0 complete regression chain PASS; all four B2 HTTP ZIPs remain byte-identical.
- B3: 286 HTTP requests; 77 independent contract assertions across five schemas and 25 previews.
- SQLite 3.50.4: four migration/six view fixtures, 59 mutation/procedure/assertion operations. PostgreSQL 17.11: the same fixture families, 65 operations using pinned psql. Actual HTTP-exported SQL and bundled procedures executed, including precondition rejection and atomic rollback fault injection.
- Chrome 153.0.8010.53: eight browser groups, ten themes, sixty screenshots, zero unexpected errors. All three browser ZIPs match their preview and manifest hashes.
- Historical reports preserved; owned test processes stopped and ephemeral session file removed. Final source fingerprint unchanged after report-only finalization.

B4 prerequisites are satisfied for the certified B3 boundary. B4 has not started. See b4-handoff.md for integration points and retained limitations.
