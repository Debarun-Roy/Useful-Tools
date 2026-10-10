# Backend Support B1

B1 adds an authenticated specification-preview workspace at `/backend-support`, a versioned capability catalog and bounded validation in the existing Java WAR. It does not generate SQL, ETL scripts, REST handlers or archives and never connects to a submitted database.

## Build and verify

From the Git root, use Node 24.16.0 / npm 11.13.0, Python 3.14.3 and JDK 25 with Maven 3.9.14. Java production compilation remains release 17. Run:

```text
python -X utf8 scripts/b1/verify.py
```

The default installs Playwright Chromium. An explicitly selected installed-browser fallback runs identical tests:

```text
python -X utf8 scripts/b1/verify.py --browser-channel chrome
```

The verifier runs B0 regression certification (saving and restoring its historical reports), locked frontend/backend builds, B1 units, actual-WAR HTTP checks and built-frontend browser workflows. It writes a fresh `.b1/<timestamp>` directory, report and machine-readable evidence; failures or unavailable required checks return nonzero. Its child container uses a disposable SQLite database and loopback-only connector. Synthetic sessions and a SPA fixture servlet live exclusively in the external test harness; WAR inspection checks their exclusion. The verifier stops its own container and removes synthetic session credentials. Logs are sanitized and CI uploads only reports, logs, result JSON and screenshots, never databases or session files.

For focused development, run `mvn -f Useful-Tools/pom.xml test` and `npm --prefix usefultools-frontend run test:b1`. A diagnostic runtime command (`scripts/b1/runtime_checks.py <fresh-.b1-directory> --http-only`) requires already-built artifacts and is not certification.

## Application behavior

Startup inserts `/backend-support` disabled without overwriting an existing choice. Enable it through Admin → Tool Toggles. Admins can validate previews while explicitly disabled; missing configuration or failed lookup never grants preview access. Guests can load immutable built-in samples only. Registered users can copy a loaded sample into the bounded custom JSON editor. Findings disappear on edits and stale responses are ignored. Specifications are held in page memory only; the server stores only a fixed activity event.

Catalog: `GET /api/backend-support/catalog`. Validation: `POST /api/backend-support/validate`, with exactly `sampleId` or `request`. See decisions.md and `contracts/backend-support/b1/validation-transport.schema.json`. Existing `ApiResponse` null omission is preserved. A 422 failure includes `data.result`, `data.truncated`; valid previews include the same wrapper and optionally a server-owned `sampleRequest`.

## Verification limits

Browser runs use built Vite output plus the actual WAR. Account login itself is not tested: container-created synthetic sessions exercise the real filters. External fonts and CAPTCHA scripts return inert responses; no CAPTCHA provider is contacted. Browser-only network/503/delay scenarios deliberately inject faults; server authorization is separately checked with real HTTP. Ten themes and desktop/mobile layouts are exercised. B0 reference projects remain feasibility fixtures. No hosted CI, production deployment, live PostgreSQL/Redis, Java 17 runtime, generated code or evaluator-engine correctness is claimed.
