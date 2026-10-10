# Sprint B4 — BS06 Python ETL

B4 adds local CSV/JSON-array ETL to SQLite/PostgreSQL through the existing protected preview/export service. Contract 0.4.0 embeds the unchanged B2 0.2.0 destination snapshot and pins etl-0.4.0-b4. Java renders fixed Python files and inert JSON; operators run the bundle separately. No dataset upload, production database connection, REST/auth starter, remote source, arbitrary transformation or B5 work is included.

Authoritative sources: user implementation attachment 811fe3a6-ad79-49aa-98a8-c7389ea1c9c2; continuation attachment d4baf521-5229-43b4-9e3a-f15016bc7ab7; six original BackendSupportPage planning documents; B3 final report and b4-handoff.md. The planning documents define BS06 as mappings, batching, dry run, rejects and restart tests, dependent on B2, with interrupted-job resume under a documented duplicate policy.

Run from the inner Useful-Tools repository on Windows:

`python -X utf8 scripts/b4/verify.py --browser-channel chrome`

Omit the channel for pinned Chromium. The verifier runs B3 once through B2/B1/B0, restores historical certification reports even on failure, checks frozen contracts and actual earlier ZIP bytes, then executes current HTTP exports in four fresh Python environments with generated pinned requirements. It runs parser/type/transaction/dry-run/crash tests, browser workflows, independent contracts, packaging/privacy and unchanged-input checks. Required failures or missing infrastructure return nonzero. Diagnostic runtime flags cannot satisfy certification.

Runtime boundary: Python 3.14.3, stdlib SQLite 3.50.4, PostgreSQL 17.11 and psycopg/psycopg-binary 3.3.6 with tzdata 2026.4. The final evidence records executed versions and installation inventories. Dependencies are installed only in disposable verifier environments; production remains Java, with Python source as an exported resource. JDK 25 compiles release 17; Java 17 execution and hosted CI are not claimed.

Read decisions.md for parser limits, typed policies, finite destination checks and external checkpoint semantics. Generated README.md is the operator procedure. Initial empty examples avoid pretending arbitrary schema constraints admit a universal row; tests use explicit synthetic fixtures. External checkpoints advance after commit, so replay is possible. Insert resume requires acknowledgement/manual reconciliation; upsert preserves explicitly mapped values but can repeat side effects. No exactly-once guarantee.

Current status and executed inputs belong to sprint-pass.md and evidence.json, not this static guide. continuation.md records recovery actions; requirements-to-tests.md maps G1–G10; review.md documents review and test corrections; b5-handoff.md defines the next-sprint boundary. B5 prerequisites require a final PASS against unchanged delivered inputs.
