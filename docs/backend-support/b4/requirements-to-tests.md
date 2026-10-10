# B4 acceptance checklist

| Gate | Required evidence |
|---|---|
| G1 | B3 once through B2/B1/B0, frozen contracts and byte-pinned released bundles |
| G2 | New 0.4.0 contract, ETL capability/dispatch, semantic mapping/key/policy validation |
| G3 | Actual HTTP ZIP Python installs and runs in fresh environments for all four source/target combinations |
| G4 | CSV/JSON edge cases, typed transformations, null/default distinctions, precision, overflow and upsert preservation |
| G5 | Batch/final partial batch, strict rollback, savepoint continuation, rejects and reconciled counters |
| G6 | Dry run without credentials; database/checkpoint/source unchanged |
| G7 | Subprocess interruption before/during/after commit and checkpoint/reject writes; identity mismatch, malformed checkpoint, concurrent runner and replay policy |
| G8 | Actual HTTP role/state/CSRF/Origin/budget matrix, deterministic ZIP/hash/limits, privacy and WAR exclusion |
| G9 | Guided UI, errors/races/drafts/downloads, keyboard/mobile/ten themes, reproducible verifier and matching CI |
| G10 | Reviewed final diff, unchanged-input certification, complete docs/evidence/B5 handoff |

Gate statuses are recorded by the complete verifier in evidence.json and sprint-pass.md. No historical database check proves the generated Python adapter. Missing PostgreSQL/restart execution is BLOCKED. Scope excludes REST/auth/B5 and all production execution/data uploads.

Executable mapping: G1 is scripts/b4/verify.py's single nested B3 chain, frozen-contracts.json and b3-bundle-sha256.json. G2 uses B4Test plus scripts/b4/contracts.py and actual HTTP capability/negative checks. G3–G7 use scripts/b4/database_checks.py: four independently installed environments and nine required groups per source/target, including exact decimal/bigint, composite keys, null/default/empty policies, NOT NULL/FK/unique/CHECK failures, rollback/savepoints, non-mutating dry run, all five interruption boundaries, changed identities, lock release, fatal connection and ambiguous commit. Each subprocess command/cwd/exit and dependency inventory is recorded.

G8 uses scripts/b4/http_checks.py for role/feature/CSRF/Origin/malformed/version/body/depth/rate/concurrency checks and 36 deterministic ETL exports; inherited B0–B3 gates retain shared decoder/output limits. verify.py compares actual browser ZIP/preview/hash bytes and checks application logs and WAR exclusions. G9 uses tests/b4-browser.mjs for guided options, invalid keys, draft retention, delayed generation/export, network/clipboard errors, keyboard/focus, twenty screenshots across ten themes and two widths, and guest/disabled/admin access. .github/workflows/b4.yml invokes the same full verifier. G10 checks documentation, historical bytes, git diff whitespace and unchanged implementation fingerprint. Human visual/diff review is documented in review.md; no file-presence check substitutes for runtime behavior.
