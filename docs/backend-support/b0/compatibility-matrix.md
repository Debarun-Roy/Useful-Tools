# Compatibility matrix

B0 pins reference dependencies, not the whole production deployment. Actual installed versions and runtime commands are recorded by each certification run in evidence.json and its logs. Checked official sources on 2026-09-18.

| Component | B0 selection | Verification boundary |
|---|---|---|
| Node / npm | 24.16.0 / 11.13.0; engines 24.x / 11.x | Clean npm ci, five tests, Vite production build and Chromium smoke |
| React / Vite | Existing 19.2.4 / 8.0.0 locked | Existing frontend; routing and API topology preserved |
| JDK | Oracle 25 locally; release 17 API and bytecode | Both WARs compile; Tomcat starts and serves HTTP on JDK 25. Java 17 execution is not claimed |
| Maven | 3.9.14 local, 3.9.x supported | Pinned clean/resources/compiler/surefire/war/failsafe/exec plugins |
| Servlet | Existing application compiles 6.0.0 provided; spike 6.1.0 provided | Tomcat 11.0.26 implements 6.1 and runs both WARs; no servlet API in either WAR |
| Tomcat | 11.0.26 embedded core/Jasper/annotations | Minimum Java 17 per Apache; actual JDK above; packaged WAR health and session lifecycle |
| Python | CPython 3.14.3 | Fresh venv install, pip check, import/TestClient and live Uvicorn |
| FastAPI / Uvicorn | 0.135.1 / 0.41.0 | Health/version, 404/405 and actual loopback process |
| Python transitive packages | Exact requirements.txt freeze | Includes Starlette 1.6.0 and Pydantic 2.13.5; pip check. Starlette's HTTPX TestClient deprecation is recorded, not a failing runtime test |
| Hashing | Password4j 1.8.4 / argon2-cffi 25.1.0 | Explicit Argon2id params; correct/wrong password checks |
| Sessions | Tomcat StandardManager; Starsessions 2.2.1 RedisStore + redis-py 6.4.0 | Java real container lifecycle; Python HTTP cookie lifecycle with fakeredis 2.32.1 emulator, not live Redis |
| SQLite | Existing Xerial JDBC 3.45.1.0; Python stdlib sqlite3 | Disposable application initialization checked. Future schema/ETL SQL execution remains B2/B4 |
| PostgreSQL | Proposed PostgreSQL 16+, pgJDBC / psycopg 3 | No PostgreSQL connections or installed driver claim in B0; choose exact pins and verify both dialects at B2/B5/B6 |
| Browser | @playwright/test 1.58.2 and its Chromium revision | Local built frontend and real backend response; fonts/CAPTCHA isolated |

## Official compatibility sources

- [Apache Tomcat version matrix](https://tomcat.apache.org/whichversion.html) and [Tomcat 11 migration guide](https://tomcat.apache.org/migration-11.0.html): Java 17+ and Servlet 6.1. Existing comments implying all Tomcat 11 cookie APIs behave differently are not accepted without tests.
- [Vite requirements](https://vite.dev/guide/): selected Node 24 satisfies the documented minimum.
- [FastAPI server startup](https://fastapi.tiangolo.com/deployment/manually/): ASGI Uvicorn deployment; the spike tests an actual server process.
- [Password4j](https://github.com/Password4j/password4j) and [Argon2 configuration](https://github.com/Password4j/password4j/wiki/Argon2): JVM Argon2 implementation used only in the reference test dependency graph.
- [argon2-cffi](https://pypi.org/project/argon2-cffi/): Python adapter and dependency metadata; installed pins are recorded in requirements.txt.
- [Starsessions](https://github.com/alex-oleshkevich/starsessions) and [store documentation](https://pypi.org/project/starsessions/): server-side Redis TTL semantics. InMemoryStore does not implement TTL in the pinned source; executable replay test found this and the adapter was rejected.
- [Starlette middleware](https://www.starlette.io/middleware/): built-in SessionMiddleware stores signed client-side data; it is not the selected opaque-session strategy.

No claim that every documented minimum runtime or proposed database version was executed. Reproduction uses the exact resolved dependency files; changes require another complete B0 run. Docker's existing Java 17 image is compatible by documented minimum/API compilation, but that image was not launched here.

Local browser fallback: explicitly selected installed Chrome 151.0.7922.176 after Chromium extraction stalled; actual launch/version and identical smoke assertions are recorded by the verifier. CI keeps default pinned Chromium.
