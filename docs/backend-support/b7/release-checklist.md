# Required B7 gates

| Gate | Source / owner | Required evidence / remaining action |
|---|---|---|
| G1 | BS01–09/backlog; engineering | Correct inventory, requirement map, current B0–B6 chain |
| G2 | Verification BS02–08; QA/engineering | All six exported families, cross-module data checks and full auth parity |
| G3 | Security plan; independent reviewer | Scan dispositions/remediation plus independent fingerprinted sign-off |
| G4 | Proposed performance target; QA/product/platform | Accepted thresholds and documented staging workload/resource/fault results |
| G5 | BS01/09 usability; QA | Browser/theme/a11y, existing tools and actual-login acceptance; human assistive-tech review |
| G6 | Pilot proposal; coordinator/product | Five real developers, approved completion target, actual outcomes |
| G7 | Release plan; deployment owner | Intended platform compatibility, backups/restore and rollout rollback rehearsal |
| G8 | BS09/operations; support owner | Metadata-only monitoring, actionable alerts, ownership and maintenance procedure |
| G9 | B7 prompt; engineering/CI owner | Reproducible entry point, exact fingerprints, historical provenance and required hosted CI |
| G10 | B7 exit; product owner | Final dossier and explicit staged-rollout approval |

Executed status is maintained in evidence.json/sprint-pass.md, not inferred from checked boxes. External-gates.json contains concrete missing actions. Release approval is distinct from deployment execution; neither is authorized by a local PASS. No missing external gate becomes optional.
