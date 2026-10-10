# B3 requirement-to-test checklist

| Gate | Requirement | Planned executable coverage |
|---|---|---|
| G1 | B2 and prior foundations unchanged | B2 verifier once through B1/B0; frozen contracts and B2 ZIP hash pins |
| G2 | Contracts/capabilities/dispatch | Independent schemas and actual HTTP versions/modules/transports |
| G3 | Migration classification, mappings and blocking | Unit operation matrix, mixed/destructive/acknowledgement, honest rollback |
| G4 | Exported migrations in both engines | Existing data, additions/renames/indexes, no-op, failed preconditions/mismatch, atomic failure and FK enforcement |
| G5 | Exported views in both engines | INNER/LEFT/self joins, aliases, literals/Unicode/NULL, predicates/grouping/aggregates and negatives |
| G6 | Eight evaluator rules and inert diagnostics | Positive/negative/not-applicable fixtures, prerequisite skips, bounded/truncated stable findings and report bytes |
| G7 | HTTP security/resource/privacy/export | Role/state matrix per module, CSRF/Origin, strict decode, budgets, revalidation/digests, WAR/log inspection |
| G8 | Integrated guided workflows | Three configure/preview/download flows, preserved module drafts, races, errors, keyboard and 10 themes/mobile |
| G9 | Reproducible local/CI | B3 verifier, pinned Windows PG/psql 17.11, preserved reports and process cleanup |
| G10 | Final review/handoff | Unchanged certification inputs, complete evidence/docs/B4 handoff |

Executable mapping: Useful-Tools/src/test/java/backendsupport/B3Test.java covers G3/G6 and module bounds/determinism; scripts/b3/contracts.py independently validates G2; scripts/b3/http_checks.py exercises G2/G3/G6/G7 through the actual WAR; scripts/b3/database_checks.py executes HTTP-exported SQL and generated procedures for G4/G5; usefultools-frontend/tests/b3-browser.mjs covers G8; scripts/b3/verify.py binds all checks to G1–G10 with one B2/B1/B0 chain, historical report restoration, input fingerprints and review. .github/workflows/b3.yml invokes the same certification entry point.

Diagnostic evidence before final certification: 286 B3 HTTP requests, four migration/six view fixtures per engine, eight browser groups/ten themes/60 screenshots, and 77 independent contract assertions. Subsequent changes require fresh certification; only sprint-pass.md/evidence.json establish the final gate verdict. No B2 database result substitutes for B3 artifact execution. No production connection or later generator is authorized.
