# B1 decisions

The frozen 0.1.0 specification and 0.1.0-b0 draft template remain unchanged. A separate B1 transport accepts exactly {"sampleId":"known-id"} or {"request":GenerationRequest}. Guests can use only the former. Sample content is returned only after authorized validation; catalog contains metadata only.

Catalog requires a session identity even when disabled. Validation requires an enabled entry, except admins may preview an explicitly disabled entry. Absent configuration denies everyone (403); failed lookup returns generic 503 for everyone. Initialization inserts disabled once and preserves admin choices.

POST requires a nonempty stored and submitted session CSRF token and Origin matching the server origin or configured CORS allowlist. Missing Origin is denied. No forwarded-header trust is added. New security filter runs after AuthFilter and before legacy CSRF. Rate limit: 40 POST attempts per session per fixed 60-second window starting with the first attempt, including rejected authenticated attempts; admins have the same limit.

HTTP: 401 no identity; 403 CSRF/origin/guest/disabled/unconfigured; 503 unavailable; 400 malformed/ambiguous transport/unknown sample; 413 raw or resource bounds; 415 media type; 422 invalid specification; 429 rate. Invalid specifications return a failure envelope with bounded findings in data; successful valid previews use ApiResponse.ok. Gson null omission remains unchanged.

Limits: raw bytes 1 MiB while reading, depth 32, string/key length 4096, array/object entries 1000, total JSON nodes 20000, findings 100, serialized response 256 KiB. Each embedded SchemaSpec has at most 100 tables and 1000 total columns (migration before and after counted separately). ETL mappings at most 1000. Paths contain only contract property names and numeric indices; unknown property names and values are never echoed. No remote schema fetch, SQL execution, customer connection or generator is used.

Structural checking interprets only the vocabulary present in the frozen bundled schema; schema changes require corresponding tests. Semantic checks cover duplicate identifiers, references, compatible keys, dialect consistency, decimal scale, migration rename/destructive changes, view source fields, ETL key/mapping options and REST dependencies. Evaluator quality rules, generated-code/SQL correctness and execution remain later-sprint work.

Browser integration exposed a pre-existing global Google Fonts import blocked by the WAR CSP. Removed only that shared remote import and retained existing font-family fallback stacks. The CSP and console-error assertion remain unchanged. This avoids external font dependency for the B1 shell without a theme redesign. Other legacy tool-specific font imports are outside B1 scope.
