# B3 handoff

Read B2 sprint-pass.md and evidence.json first. B3 readiness is conditional on every required B2 gate passing against the delivered input fingerprint. B2 is uncommitted alongside B0/B1; do not reset, clean, stash, switch, commit, push or deploy without authorization.

B3 scope is BS03–BS05 migrations, views and evaluator. It has not been implemented here. Keep the released schema generator path and frozen historical contracts intact. Add independently versioned templates and explicit capability flags for later modules; never reinterpret initial CREATE bundles as migrations or reconciliation.

Reuse SchemaGeneration validation/normalized model, SchemaDialect adapters, ArtifactBundle digest/path/bound policies, GenerationController access pattern and the shared BackendSupportSecurityFilter budget. Preserve strong export revalidation/digest checks and UI request identity. Extending supported checks, defaults or cycles requires matching contract, capability, SQL execution and deterministic fixture evidence in both engines. Do not add raw SQL interpolation to accommodate later features.

Verification is `python -X utf8 scripts/b2/verify.py --browser-channel chrome` locally or default Chromium in CI. PostgreSQL 17.11 provisioning is test-only; psql is the exact pinned CLI driver. SQLite and PostgreSQL outputs are executed from actual HTTP ZIP exports. The test fixture may mint synthetic sessions only outside the WAR. No production SQL executor or customer connection exists.

Limits remain: 1 MiB request, 100 tables/1,000 columns, 100 artifacts/5 MiB including manifest, generation JSON 32 MiB, two concurrent assemblies, 40 shared POST attempts/session/minute. SQLite affinity and approximate decimal, UTC text conventions, conservative name collisions and rejection of multi-table cycles are deliberate boundaries. B3 must not silently weaken them.

Hosted CI, deployments, Java 17 execution, PostgreSQL versions other than 17.11, and B7 load qualification are not claimed. Performance measurements are local sequential 20-table generation observations only.
