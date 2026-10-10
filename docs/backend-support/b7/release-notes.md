# B7 candidate release notes

Six-family scope is now stated correctly. B7 adds integrated export/ETL/view/restore verification, fixed local workload measurement, overload/disconnect recovery and all-theme axe checks, dependency/credential scans and release procedures. Workspace fixes address module deep links/Back, obsolete REST-unavailable text and text contrast. Security-driven npm updates stay in existing major ranges. Docker build includes contracts and no longer packages/imports a database dump; operators must provision a reviewed persistent DB before startup.

No generated template version or historical artifact bytes changed in these fixes. Java0.5.0/Python0.6.0 auth retain shared API0.5.0. Existing unsupported cases remain in capability-matrix. No new framework/dialect/customer connection, saved spec persistence or distributed auth.

Candidate is NOT APPROVED. Refer to current sprint-pass/evidence for failures and blockers. Independent security, real UAT, staging/deployment qualification, hosted CI and product approval have not been manufactured. No deployment occurred.

Existing login no longer logs session IDs/cookies or raw CAPTCHA rejection bodies. Local credential-login and generated-auth resource tests supplement the inherited matrix. B7 CI is configured for engineering qualification; no hosted result, release approval or deployment is implied.

Removed an external font CSS import that conflicted with the existing self-only style policy and blanked lazy-loaded tools including REST Tester. Existing local font fallbacks are retained; CSP is unchanged.

Recovered idempotent unit-seed SQL is preserved. Application startup now recognizes that form; fresh initialization and preservation of existing values have dedicated regression coverage. This compatibility correction invalidates earlier whole-application certificates and requires the rebuilt full chain.
