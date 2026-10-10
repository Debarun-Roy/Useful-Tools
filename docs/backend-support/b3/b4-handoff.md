# B4 handoff

B4 has not started. Read B3 sprint-pass.md and evidence.json first; all G1–G10 must PASS against the delivered fingerprint before treating B3 as certified. B0/B1/B2/B3 changes remain uncommitted on main. Do not reset, clean, stash, switch, commit, push, merge, deploy or access production databases without separate authorization.

The original backlog defines B4 as BS06 Python ETL mappings, batching, dry run, rejects and restart tests, dependent on B2. Its exit condition is that an interrupted job resumes with the documented duplicate policy. Recover the original B4 requirements before implementing; do not infer ETL behavior from B3 SQL artifacts or begin auth generators.

Reuse the bounded decoder, strict feature/guest/CSRF/Origin controls, shared rate/assembly budgets, artifact manifest/canonical digest and independent export validation. Add a separately versioned ETL template and capability; retain schema-0.2.0-b2 and all 0.3.0 template/ruleset behavior. Preserve finite diagnostic report semantics and do not treat a report download as executable-schema eligibility.

B3 integration points: GenerationController dispatches migration/view/evaluator to B3Generation; SchemaGeneration and SchemaDialect supply validated executable snapshots and quoting. ArtifactBundle.finish adds B3 metadata with unchanged hashing domains and ZIP policy. Catalog generationModules is additive. B3Editor holds separate in-memory drafts; SchemaEditor stays mounted to preserve its draft. Module/access/edits invalidate pending generations and downloads through abort plus revision identity.

Migration limitations remain mandatory: same dialect, conservative safe subset, manual review/backup/maintenance, no automatic drop/rebuild/conversion, finite metadata prechecks, no column-addition down script. Generated migrate.py is operator-run only. Views require the referenced schema to exist. SQLite affinity/date conventions and exact PostgreSQL 17.11 verification boundary remain unchanged.

Reproduce B3 with python -X utf8 scripts/b3/verify.py --browser-channel chrome on Windows, or omit the channel for pinned Chromium. It runs B2/B1/B0 once and preserves historical report bytes. Actual HTTP ZIP SQL and bundled procedures execute in owned SQLite/PostgreSQL fixtures. No hosted CI or deployment has been performed. Any future changes affecting these contracts, renderers, endpoint controls, UI request identity or harness require affected regression checks and a fresh evidence fingerprint.
