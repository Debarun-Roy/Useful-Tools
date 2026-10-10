# B6 sprint report

Verdict: **PASS**
Tested: 2026-09-28T14:50:15.127410+00:00
HEAD: `b36265e70701221a748b3f4881524c289bf32dd6` (dirty; no commit)
Input fingerprint: `ddb22c4a8733c15ae86bf7637b150e398b9dc0d85e8affee043cf4dca682248c`

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

Normal verification entry point: `python -X utf8 scripts/b6/verify.py --browser-channel chrome`

Executed recovery command: `python -X utf8 .b6/20260928T042805Z/resume_verification.py --browser-channel chrome`
Evidence: `.b6\20260928T141110Z`. Exact commands, cwd, exit codes, counts, versions and matrix results are in evidence.json. Historical B0–B5 report bytes restored.

- Windows certification host; actual JDK/container/JDBC versions recorded by exported applications
- Release-17 compilation does not prove Java 17 execution
- Single-instance volatile sessions/throttling; no distributed revocation
- No real CAPTCHA traffic, hosted CI, deployment or production database access
- Self-review only; independent security review required before external auth-template release
- Redis 8.2.10 Windows Cygwin community build for local verification; managed production Redis deployment is outside this certification
- Redis restart tested with persistence disabled; application restart always revokes old namespace sessions
- Python 3.14 single worker, root deployment, direct TLS; no proxy trust or multi-instance support

B7 technical prerequisites are satisfied only when every B6 gate PASS against unchanged inputs. Follow b7-handoff.md; B7 is not started. Independent security review and release qualification remain outstanding.

Interruption recovery: unchanged verifier logic resumed via .b6/20260928T042805Z/resume_verification.py. Completed B0–B4 and then B5 certificates were reused only after exact current fingerprint matching; runtime version output also matched. All unfinished B5/B6 checks ran fresh. Runner hash and certificate references are recorded in evidence.json.

Final recovery audit (2026-09-30T06:24:30.137895+00:00): PASS. Current inputs exactly match the executed fingerprint above. All 22 checks and G1–G10 passed; eight exported Python application variants completed 3,156 requests and 2,964 matching Java/Python responses. Frozen53 files and all12 B0–B5 reports remain byte-identical; git diff --check returned0. No owned recovery/test processes or PostgreSQL PID files remain; ephemeral TLS material was removed. Only report/continuation/evidence files were updated after execution. B7 technical entry prerequisites are satisfied; independent security review and release qualification remain outstanding. No B6 work remains.
