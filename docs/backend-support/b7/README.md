# B7 release dossier

Scope: cross-module QA, security, performance limits, pilot and release procedures. The six authoritative families are in capability-matrix.md; the B6 inventory erratum is explicit there. This is not a new feature/framework sprint or the separate UI polish workstream.

From the inner repository on the Windows qualification host run `python -X utf8 scripts/b7/verify.py --browser-channel chrome`. It invokes the ordinary B6 verifier once, transitively B5–B0 once, saves/restores all14 historical reports, then runs B7 owned HTTP/export/workload/browser/accessibility/security checks. No ignored recovery script is needed. Dependencies require network during installation/scanning; runtime tests contact owned loopback services. Reports identify exact inputs and environments. Required FAIL/BLOCKED returns nonzero. `--engineering-only` labels a diagnostic; it never grants release approval.

Read evidence.json and sprint-pass.md for executed status, continuation.md for the next operation. Release checklist, external-gates.json and release-handoff.md keep independent security, real pilot, target deployment, hosted CI, product approval and deployment separate. All require genuine evidence; unassigned roles and absent signatures remain blocked. No invitation, push, deployment or production toggle change is authorized by this dossier.

CI: .github/workflows/b7.yml executes the same graph with --engineering-only and uploads sanitized results. It does not ingest or invent human signatures. Full release-gate closure requires reviewer/product/environment evidence assessed against the fingerprint and an explicit update of external-gates.json/gate handling; this initial entry point intentionally cannot auto-grant absent approvals.

Recovery: `python -X utf8 scripts/b7/verify.py --browser-channel chrome --resume <completed-B7-run>` validates the successful ordinary prior chain before reuse and reruns all B7 checks. Source run's B7 overallFAIL is retained: host metrics timed out and Chrome reported insufficient resources. These failures are not reused asPASS. Any application/prior-sprint/dependency/config change rejects reuse and requires an ordinary full run. See resume-validation.json for exact evidence and environment/artifact comparisons.

The07:08 source chain cannot certify the later recovered units.sql change. Strict reuse rejected it as designed. D09 adds compatible initializer handling while preserving the SQL edit; a fresh full ordinary chain is required for that tree.
