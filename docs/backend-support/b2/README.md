# Backend Support B2

Schema generation extends the existing Java WAR and React workspace. Other modules retain B1 sample/custom validation and have no generation capability. B2 does not run generated SQL in production, store schemas, connect to customer databases, or implement B3.

From repository root on Windows:

```powershell
python -X utf8 scripts/b2/verify.py --browser-channel chrome
```

Omit the channel for pinned Playwright Chromium (CI default). An explicitly chosen installed browser is recorded by version; never silently skipped. Requires Node 24.16.0, npm 11.x, Maven 3.9.x, JDK 25 and Python 3.14.3. Maven still compiles release 17; Java 17 execution is not certified. Historical B1 actually executed npm 11.16.0, despite its README's 11.13.0 reference. B2 CI pins 11.16.0.

The verifier runs B1 once, which runs B0 once, clean-builds the production artifacts, executes all Java tests, then B2 frontend, actual WAR HTTP, exported database, browser, independent JSON Schema and packaging checks. It preserves B0/B1 reports in finally blocks. Synthetic sessions exist only in the external Tomcat fixture. Fresh logs, screenshots and exported synthetic bundles live under ignored `.b2/<UTC timestamp>/`. No credentials or database files are uploaded by CI.

PostgreSQL is an owned local cluster on an ephemeral loopback port, never a Windows service. The test provisioner downloads the official EDB 17.11 Windows archive (fileid 1260569), verifies SHA-256 `b9424ee7bc60b52450ff910a3630225df32e633f3cb29c1d126d9299d59aea28`, extracts only bin/lib/share, initializes a new data directory and stops that exact cluster in finally. The driver is its pinned psql 17.11 CLI, with no Python driver dependency. SQLite uses Python's stdlib sqlite3; actual runtime version is in evidence. Windows is the supported certification host; other hosts report a blocked PostgreSQL prerequisite.

The new contract is `contracts/backend-support/v0.2.0/models.schema.json`, template `schema-0.2.0-b2`. See decisions.md for supported semantics, API/digest rules and limits. The report is authoritative only after a complete run against unchanged inputs. Diagnostic `--http-only` runs are never certification.

Expected files: schema.sql, schema.json (canonical normalized request), README.md, verify.py, manifest.json. Run the extracted bundle's `python verify.py`; PostgreSQL additionally needs `--database YOUR_DISPOSABLE_DATABASE --psql PATH_TO_PSQL`. Real constraint tests use SQL extracted from HTTP ZIP exports, not independent hand-written DDL. Only synthetic INSERT/UPDATE/DELETE assertions are handwritten.
