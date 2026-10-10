# B1 handoff

B0 creates foundations only. Consult sprint-pass.md and evidence.json for current certification status before beginning. No production BackendSupportPage or /api/backend-support endpoints have been added.

## Verified integration map

| Concern | Existing files relative to repository root | B1 action |
|---|---|---|
| Route | usefultools-frontend/src/App.jsx | Lazy protected /backend-support route; revision-aware page shell |
| Dashboard | usefultools-frontend/src/pages/DashboardPage/DashboardPage.jsx | Register card after server control exists |
| API | usefultools-frontend/src/api/apiBase.js, apiClient.js | Preserve /api normalization; pass /backend-support paths without duplicate prefix; special-case ZIP response |
| Auth | usefultools-frontend/src/auth/AuthContext.jsx; Useful-Tools/src/main/java/common/filter/AuthFilter.java, CsrfFilter.java, GuestRestrictionFilter.java | Direct-call tests for guest/user/admin, deny absent CSRF token |
| Admin/toggles | usefultools-frontend/src/pages/AdminPage/AdminPage.jsx; Useful-Tools/src/main/java/common/dao/ToolToggleDAO.java | Explicit disabled seed; fail closed for new service; deliberate admin preview policy |
| Favorites | usefultools-frontend/src/hooks/useFavorites.js; Useful-Tools/src/main/java/common/dao/FavoritesDAO.java | Keep frontend/backend allowlists in sync |
| Search | usefultools-frontend/src/hooks/useSearch.js; Useful-Tools/src/main/java/formatter/controller/SearchController.java | Test discoverability, do not assume card implies search |
| Activity | usefultools-frontend/src/components/RecentActivity/RecentActivity.jsx; common/dao/ActivityDAO.java under backend src/main/java | Add label; metadata only, no spec/source/rows |
| Registration | Useful-Tools/src/main/webapp/WEB-INF/web.xml and annotated servlets | Verify filter coverage/order and route authorization |
| Shared UI | usefultools-frontend/src/components/PageTabs; src/theme and src/index.css | Reuse existing accessible controls and theme tokens |

## Ordered next steps

1. Review contracts/backend-support/v0.1.0 and decide unresolved catalog and admin-preview rules. Keep Java Servlets/Python FastAPI plus SQLite/PostgreSQL; guest sample-only is the planning default.
2. Add a small versioned catalog resource and server-side disabled-by-default policy. Test direct requests with tool disabled, absent identity, guest, user and admin. Fail closed on toggle read error.
3. Implement bounded request parsing and structural/semantic validation service in backendsupport packages. Reject unknown security fields, unsupported combinations, malformed JSON, excessive sizes and missing CSRF. Use actual envelope serialization behavior.
4. Add the lazy page shell and catalog-driven module/target controls. Track draft revision/request ID, stale previews and accessible findings. A sample can validate without implying generation exists.
5. Integrate favorites, search, activity labels and admin toggles once with parity tests. Never log schema names, fields, code or source rows.
6. Define B1 acceptance tests: disabled direct endpoint, guest custom-spec denial, guest sample policy, invalid target, request cap, out-of-order response and missing-token rejection. Keep B2 generation and B5/B6 auth work out of B1.

## Accepted decisions and limitations

See decisions.md and compatibility-matrix.md. Baseline compilation and smoke tests do not certify existing login/logout or production security. Reference health endpoints and test-only adapter routes are not generator templates. Python uses RedisStore; fakeredis is bounded feasibility evidence, not live Redis deployment evidence. PostgreSQL execution, SQL/ETL generators, deterministic archive export, performance targets and real CAPTCHA are unimplemented later-sprint gates. Java 17 bytecode/API compatibility is verified with JDK 25 execution; current production Docker image was not run.

Original planner suggested pilot task-time baselines; no pilot participants were available in B0. No task-time or performance improvement claim is made. Collect pilot baseline before setting usability targets.

Run from Git root: `python scripts/b0/verify.py`. It installs locked dependencies in a fresh Python venv, clean-builds both WARs, starts isolated services, validates fixtures and checks the built frontend. It returns nonzero on failure/blockage. No hosted CI result is claimed until the workflow runs in GitHub.
