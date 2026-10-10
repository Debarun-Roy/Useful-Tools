# B6 acceptance checklist
All required gates are implemented and mapped below; the per-gate execution checklist and authoritative final status are maintained in evidence.json and sprint-pass.md. Diagnostic evidence is recorded in continuation.md and is not final certification.

| Gate | Required evidence |
|---|---|
| G1 | B5 once through B0, preserve historical reports and old template/contract/ZIP bytes |
| G2 | Python 0.6.0 contract, identifiers/dependencies, strict configuration, catalog |
| G3 | Eight HTTP ZIP exports, fresh venv/pip check/import/live ASGI, both DBs/startup/persistence |
| G4 | Shared real register/login/profile, Unicode/password/hash, uniqueness/transaction failures |
| G5 | Real Redis TTL/deletion/rotation/restart/outage/concurrent revocation, CSRF/Origin/cookies/root path, throttle/resource bounds |
| G6 | CAPTCHA on/off, real HTTP transport with isolated provider, strict semantics/errors, zero off calls |
| G7 | Same sequences/assertions Java/Python × two DBs × on/off × core/profile, OpenAPI |
| G8 | HTTP generator security/bounds/digest/ZIP/privacy and independent target drafts/browser/themes |
| G9 | Reproducible full verifier and CI; commands/versions/exits/hashes; cleanup/restoration |
| G10 | Review/remediation, threat model, exact final fingerprint, B7 handoff |

No TestClient-only, fakeredis-only, compilation-only or skipped required gate can certify PASS. Independent release security review remains outstanding.
