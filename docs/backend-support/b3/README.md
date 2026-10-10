# Backend Support B3

B3 implements BS03–BS05: same-dialect migration planning, structured view generation and deterministic schema evaluation. Read sprint-pass.md and evidence.json for the actual certification verdict; diagnostic runs are not a final certificate. B0/B1/B2 changes remain uncommitted. No production execution endpoint, live customer connections, ETL, REST/auth generation or deployment is included.

## Contracts and entry points

POST /api/backend-support/generate accepts exactly {request}; POST /api/backend-support/export accepts exactly {request, expectedDigest}. Both independently authenticate, check feature state, decode, validate and assemble. Generation returns the existing success envelope with manifest, digest, files, validation findings and adminPreview, plus module-specific details. Export regenerates and rejects stale digests with 409 before committing ZIP headers. Errors remain JSON, including errors from binary requests. Catalog generationModules is additive; released generation and historical B1 operations retain their semantics.

Contracts live in contracts/backend-support/v0.3.0: models.schema.json, generate-transport.schema.json, export-transport.schema.json, generation-response.schema.json and manifest.schema.json. Templates are migration-0.3.0-b3, view-0.3.0-b3 and evaluator-0.3.0-b3. Evaluator ruleset is schema-rules-0.3.0. Executable embedded schemas retain the released 0.2.0 model and schema-0.2.0-b2 template. Historical contracts are frozen and four B2 ZIP hashes are independently pinned.

Malformed/unprocessable input gets a blocking response without artifacts. Valid migration/view input may produce executable SQL. A completed diagnostic evaluation may contain errors and still export its report; generationEligibility explicitly requires independent generator validation. No diagnostic report contains SQL or asserts a universal quality/performance score.

## Supported subsets

| Module | SQLite and PostgreSQL subset | Explicit boundary |
|---|---|---|
| Migration | Append nullable columns, append supported constant-default columns, ordinary indexes, explicit stable-ID table/column renames, no-op | No type/default/nullability/key/FK/check changes, removals, table additions, unique-index creation, rebuilds or inferred renames |
| View | Explicit aliases and projections; INNER/LEFT/self equijoins; typed comparisons, NULL predicates, bounded AND/OR/NOT; grouping; COUNT/SUM/AVG/MIN/MAX | No raw SQL, user functions, subqueries, parameters or materialized views; conservative portable grouping/type checks |
| Evaluator | Eight versioned diagnostic rules with field paths, explanations, corrections, severity and generation-block scopes | Finite advisory/diagnostic checks do not prove all generator requirements or workload performance |

Every unsupported migration difference blocks the complete executable bundle, even if safe changes are present or destructive acknowledgement is true. Stable IDs identify objects; rename maps must exactly match before/after names and IDs. Existing occupied rename targets are rejected. The migration runner's fixed temporary name ut_b3_guard is reserved for this module to prevent object shadowing.

## Migration procedure and recovery

Bundles include before.schema.json, after.schema.json, migration.spec.json, up.sql, prechecks.sql, postchecks.sql, migration_report.md, README.md, migrate.py, verify.py and manifest.json. Rename/index-only bundles have down.sql; column additions do not, because dropping a newly populated column can destroy later writes. No-op bundles explicitly say no-op and still enforce preconditions.

Back up, review the exact starting snapshot and external dependencies, and establish a maintenance window. Then run python migrate.py --sqlite OWNED_DATABASE or python migrate.py --database OWNED_DATABASE --psql PATH_TO_PSQL. Use --reverse only when down.sql exists. These are operator actions, never web application actions. SQLite opens an existing database, confirms foreign_keys before BEGIN IMMEDIATE, executes prechecks/up/postchecks, then commits. PostgreSQL uses ON_ERROR_STOP, an explicit transaction, public schema and ACCESS EXCLUSIVE locks; lock timeout is ten seconds. Failure causes rollback. External views/triggers and PostgreSQL inheritance/partition objects are rejected; a maintenance window covers dependencies not modeled or locked by these checks.

Prechecks enforce modeled table count, ordered column names/rendered types/nullability and explicit index count/name/ordered columns/uniqueness. They use a failing CHECK-guard INSERT, not informational SELECTs. They do not prove arbitrary default, FK, CHECK or collation equivalence. Operator review is mandatory. Index/rename reversal preserves existing rows but is not backup recovery or reversal of application writes. SQLite type affinity/approximate numeric behavior and date/timestamp text conventions remain B2 limitations.

## Views and evaluation

Referenced tables must already exist. View bundles do not recreate them. COUNT(*) counts unmatched LEFT rows; COUNT(column) omits NULL. Equality to NULL is rejected in favor of IS NULL/IS NOT NULL. Every projected nonaggregate column must appear in GROUP BY when grouping or aggregation is used. SUM/AVG require numeric types; unsupported JSON/boolean aggregate or comparison combinations block. verify.py can integrity-check any B3 bundle and optionally create/select/roll back a view in an operator-owned test database.

The diagnostic model permits missing primary keys, unknown logical types, invalid references and inert expression-default text. Duplicate stable IDs are unprocessable rather than ambiguous. Invalid references/types skip dependent checks with reasons. Findings and skips are deterministic, sorted and capped at 100 each; truncation is explicit. Naming is snake_case advisory or disabled. Nullable-business-key checks use explicit unique/business-key declarations. Redundant-index checks compare ordered columns and uniqueness, not name similarity or prefixes.

## Limits, UI and privacy

Requests: 1 MiB, depth 32, 20,000 nodes, 4,096-character strings and collections at most 1,000. Each executable schema: 100 tables, 100 columns per table and 1,000 columns total. Migration before+after combined: 1,000 columns. Views: eight joins, 100 projections/group references and predicate depth eight/100 nodes. Output: 100 files/5 MiB including manifest, generation JSON 32 MiB, two assemblies with no queue, shared 40 POST attempts/session/minute. All responses are no-store.

Unauthenticated users are denied. Guests retain historical immutable server sample validation only and cannot submit B3 custom models or generate/export. Registered users need enabled access; admins may bypass only explicit disabled state. Missing configuration denies everyone; failed lookup is generic unavailable. Existing CSRF/Origin checks cover all POST operations.

Guided migration operations and rename maps, view sources/joins/projections/grouping/predicates, and evaluator fields/severity filters share bounded advanced JSON and artifact previews. Unapplied/invalid JSON is preserved and locks guided controls until apply or explicit discard. Separate module drafts remain in memory; module/access/edit changes abort and invalidate request identity. No custom content is persisted to storage/URLs or production databases. No submitted identifiers, values, SQL or digests enter application activity/logs.

## Verification

From repository root: python -X utf8 scripts/b3/verify.py --browser-channel chrome. Omit the channel for the pinned Playwright Chromium in CI. Windows is the supported certification host; PostgreSQL and psql 17.11 are verified test-only EDB binaries, never production dependencies. JDK 25 compiles release 17; this does not claim execution on Java 17.

The verifier runs B2 once through B1/B0, restores their report bytes even after failures, executes B3 HTTP ZIP artifacts and bundled procedures on both engines, tests all browser workflows and ten themes, validates actual responses independently, checks packaging/privacy and final fingerprints, and exits nonzero for FAIL/BLOCKED. Diagnostic --skip-database/--http-only modes are never used for certification. Fresh evidence lives under .b3; CI uploads sanitized logs/results/screenshots, not sessions or database files. Hosted CI and deployment are not claimed.
