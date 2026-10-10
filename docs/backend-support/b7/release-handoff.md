# B7 release handoff

Release NOT APPROVED; deployment NOT EXECUTED. Do not start another workstream. Exact current technical status and next command belong to continuation.md and evidence.json. Preserve dirty B0–B7 work and all historical reports; no commit/push/deploy authorized.

Review capability-matrix erratum, defect-register and release-notes before judging scope. Run normal `python -X utf8 scripts/b7/verify.py --browser-channel chrome` after final corrections. The ordinary prior chain replaces dependence on B6's historical ignored recovery driver; B7 does not silently reuse stale passes.

External actions once engineering is reviewable: assign independent reviewer and return fingerprinted findings/sign-off; product/QA accept workload and pilot criteria; provide isolated intended staging/Redis/TLS/ACL environment and run qualification/rollback; authorize participant invitations separately and collect real unaided outcomes; authorize hosted CI separately; product owner records staged-rollout approval, owner and rollback trigger. No invitations or dispatches sent. Release owner then separately authorizes deployment.

Known boundaries: local Windows/JDK25/Python3.14 results do not prove JDK17/container/production Redis or proxy/multi-worker behavior. Auth templates require separate operator deployment, secrets and DBs. Snapshot rollback cannot recover later writes. Already downloaded bundles require version-specific notices and regeneration, not remote recall.
