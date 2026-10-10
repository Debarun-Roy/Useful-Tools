# B1 sprint report

Verdict: **PASS**
Tested: 2026-09-19T08:38:09.018806+00:00
HEAD: `b36265e70701221a748b3f4881524c289bf32dd6` (uncommitted working tree)
Input fingerprint: `32750477c5a016426752df6036b75042107017f822ed91a8d53c92f2b1e0448e`

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

Exact command: `python -X utf8 scripts/b1/verify.py --browser-channel chrome`
Logs and screenshots: `.b1\20260919T083526Z`. Commands, counts, environment/version logs and exit codes are linked in evidence.json and the preserved B0 regression report.

## Limitations

- Hosted CI not executed
- No production deployment, generated code, live PostgreSQL/Redis or Java 17 runtime execution
- Synthetic sessions bypass account login only in external test container; real application filters execute
- External fonts/CAPTCHA inert; error scenarios use explicit browser fault injection

See decisions.md for policy and validation coverage, continuation.md for recovery, and b2-handoff.md for next-sprint boundaries. B2 prerequisites are satisfied only when every gate above is PASS.

## Final review and readiness

Final diff/privacy/registration review PASS. 15 backend tests, 3 B1 frontend tests, full B0 regression, 111 actual-WAR HTTP requests, nine browser scenarios and all ten themes passed. Chrome 153.0.8010.48; zero unexpected browser errors. Final desktop/mobile screenshots reviewed. Synthetic session fixture removed; no owned server remains. No submitted sentinel values appear in server logs.

B2 prerequisites are satisfied; follow b2-handoff.md only when B2 is authorized. Nothing was committed or deployed. Only report/evidence/continuation outputs changed after certification.

## Stable code inventory

HTTP/transport error codes (status policy in decisions.md): `BODY_TOO_LARGE`, `COLLECTION_LIMIT`, `COMPLEXITY_LIMIT`, `CSRF_INVALID`, `DUPLICATE_KEY`, `GUEST_SAMPLE_ONLY`, `INVALID_SPECIFICATION`, `INVALID_TRANSPORT`, `MALFORMED_JSON`, `METHOD_NOT_ALLOWED`, `ORIGIN_REJECTED`, `RATE_LIMITED`, `RESPONSE_LIMIT`, `STRING_LIMIT`, `TOOL_DISABLED`, `TOOL_UNAVAILABLE`, `TOOL_UNCONFIGURED`, `UNAUTHENTICATED`, `UNKNOWN_SAMPLE`, `UNSUPPORTED_MEDIA_TYPE`.

Finding rule IDs (all messages exclude submitted values): `CAPTCHA_OPTIONS_MISMATCH`, `COLLECTION_OR_LENGTH_LIMIT`, `CSRF_REQUIRED`, `DESTRUCTIVE_MIGRATION`, `DIALECT_MISMATCH`, `DUPLICATE_IDENTIFIER`, `FOREIGN_KEY_NOT_UNIQUE`, `INVALID_COMBINATION`, `INVALID_DECIMAL_OPTIONS`, `INVALID_DELIMITER_OPTION`, `INVALID_FORMAT`, `INVALID_ORIGIN`, `INVALID_RENAME_REFERENCE`, `INVALID_TYPE`, `KEY_ARITY_MISMATCH`, `KEY_TYPE_MISMATCH`, `NULLABLE_PRIMARY_KEY`, `PROFILE_MODULE_REQUIRED`, `REQUIRED_FIELD`, `SESSION_REQUIRED`, `TOTAL_COLUMN_LIMIT`, `UNKNOWN_FIELD`, `UNKNOWN_REFERENCE`, `UNSUPPORTED_VALUE`, `UPSERT_KEY_REQUIRED`, `VALUE_LIMIT`.
