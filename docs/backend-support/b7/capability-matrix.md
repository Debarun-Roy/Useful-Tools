# Authoritative capability inventory and B6 erratum

Original requirements01 and backlog04 establish this inventory. The B6 b7-handoff.md sentence listing mock data and separating auth/profile is incorrect. This B7 erratum supersedes that sentence for current release planning; historical B6 files and certificates remain byte-preserved. Mock data is synthetic verification material, not a tool family. BS01 is the common workspace, BS09 integration/security, and BS08 also artifact completeness.

| Family / requirement | Verified subset and versions | Unsupported / release dependencies |
|---|---|---|
| Schema BS02 | SQLite/PostgreSQL; model0.2.0/schema-0.2.0-b2; keys, defaults, FK ordering, checks, indexes | No arbitrary SQL/functions/collations; SQLite affinity and approximate decimal limits; target runtime qualification |
| Migrations BS03 | Model0.3.0/migration-0.3.0-b3; nullable/constant-default additions, ordinary indexes, explicit stable-ID renames/no-op | No destructive/mixed plans, type/key/default edits, table addition or rebuild; manual dependency review/backup |
| Views BS04 | Model0.3.0/view-0.3.0-b3; INNER/LEFT/self equijoins, typed predicates, grouping/count/sum/avg/min/max | No raw SQL, subqueries, parameters, materialization or universal performance claim |
| Evaluation BS05 | Model0.3.0/evaluator-0.3.0-b3, ruleset schema-rules-0.3.0; eight deterministic diagnostic rules | Report export does not certify executable-schema eligibility or workload performance |
| ETL BS06 | Model0.4.0/etl-0.4.0-b4; Python3.14 CSV/JSON arrays, SQLite/PostgreSQL, typed mappings, strict/continue/upsert, dry-run/rejects/checkpoints | No uploads/remote sources/live customer connections/exactly-once; external checkpoint replay requires documented reconciliation |
| REST/auth BS07–BS08 | Java model0.5.0/java-auth-0.5.0-b5 and Python model0.6.0/python-auth-0.6.0-b6; shared API0.5.0; both DBs, CAPTCHA off/on, core/optional profile | No recovery/MFA/OAuth/JWT/email; independent security review. Python one worker/root/direct TLS/managed Redis; no shared Java/Python sessions |

Executed baseline: B2 database-results, B3 exported procedure/views/evaluator checks, B4 four ETL runtime combinations and B5/B6 eight variants each. These reside in their historical evidence and the final B7 prior-chain certificate when it completes. B6 runtime was Python3.14.3, SQLite3.50.4, PG17.11, Redis8.2.10 community Windows test build; JDK25/Tomcat11.0.26, Java compilation release17. Do not claim JDK17 execution or production Redis approval. Current B7 evidence supersedes historical behavior only after execution against matching inputs.

BS10 local import/export is optional and not newly advertised. No saved-server configurations or localStorage schema persistence. Module query parameter contains only an allowlisted family name, never a specification.
