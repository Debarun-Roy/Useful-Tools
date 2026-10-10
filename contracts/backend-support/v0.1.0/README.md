# Contract baseline 0.1.0

Draft B0 machine-readable definitions are in models.schema.json ($defs). No production endpoints consume them yet. GenerationRequest identifies module, target dialect/language/framework, schemaVersion and templateVersion. Every object rejects unknown fields. SQL modules require sql/none; ETL python/none; REST java/jakarta-servlet or python/fastapi with matching ApiSpec. Future catalog runtime pinning is separate from these target identities.

The six initial specification shapes cover stable table/column IDs, PK/FK/unique/index metadata, before/after migration and explicit renames, typed view references/predicates, evaluator rule version, ETL mappings/policies, and REST module/session/CAPTCHA settings. Arbitrary SQL expressions, user templates, live DB URLs and embedded secrets are deliberately absent. Check-constraint/aggregate expression grammar and advanced transformations need a versioned extension before implementation.

Finding carries ruleId, severity, JSON Pointer path, message, suggestion and blocking. ValidationResult has valid and findings. ArtifactManifest includes target/runtime, versions, spec digest, file paths/SHA256/bytes, dependency inventory, warnings and destructive acknowledgement. Example hashes are synthetic placeholders, not generated file evidence. Future packaging must reject duplicate normalized paths, enforce total bytes, and calculate real hashes.

## Proposed generator service contracts

These belong to UsefulTools, NOT to generated applications. JSON uses existing ApiResponse fields success/data/error/errorCode; null fields may be omitted by Gson.

| Method and path | Request | Response |
|---|---|---|
| GET /api/backend-support/catalog | Authenticated session | 200 envelope with catalogVersion, modules, compatible targets and template versions |
| POST /api/backend-support/validate | GenerationRequest | 200 ValidationResult for ordinary invalid drafts; 400 malformed JSON; 422 unsupported target |
| POST /api/backend-support/generate | GenerationRequest | 200 manifest, findings, normalized spec digest and UTF-8 files |
| POST /api/backend-support/export | GenerationRequest plus expectedDigest | 200 ZIP generated from same frozen spec/template; 409 digest mismatch |

Error codes: UNAUTHENTICATED 401; GUEST_RESTRICTED, CSRF_INVALID and TOOL_DISABLED 403; INPUT_TOO_LARGE 413; UNSUPPORTED_TARGET and GENERATION_LIMIT 422; RATE_LIMITED 429; generic INTERNAL_ERROR 500 with correlation ID. Every unsafe cookie-auth request including export requires CSRF and server feature checks. Apply no-store to user data. No bundle persistence. Deterministic ordering/digest normalization and export wrapper schema are B1/B2 work; not proven by B0.

Generated project contracts are separate future APIs: POST /api/auth/register (201 safe profile), login (200 rotated opaque session), logout (204 invalidation), GET session and csrf-token, GET/PATCH /api/user/profile, optional POST captcha/verify. No complete auth implementation exists in either reference project. Future PATCH dispatch and negative auth/CSRF cases require execution tests.

## What fixtures prove

Run the root certification command or the isolated Python environment's `python scripts/b0/contracts.py` from Git root. Sixteen cases exercise each module, customers/orders FK, additive/drop migrations, valid/unknown view fields, CSV/JSON decimal rows, Java/Python auth configs, unsupported target, malformed spec, unknown security field and manifest traversal. Structural negatives match a schema keyword and pointer prefix, not merely any exception.

Structural validation cannot check FK existence/types, reference resolution, destructive effects, SQL execution, auth dependencies or data transformation correctness. scripts/b0/contracts.py has deliberately narrow fixture probes for dropped columns, view field references and decimal row conversion. They verify expected fixture meaning only; they are NOT a reusable semantic validator or generated ETL implementation. Safe additive fixture passing does not prove safe SQL. Destructive acknowledgement never means permission to execute a migration.

Change policy: breaking models get a new version directory and migration notes. Update fixtures and rerun certification; never overwrite old exported semantics. Runtime catalog, cap enforcement, canonical digest rules and complete semantic findings remain B1/B2 decisions.
