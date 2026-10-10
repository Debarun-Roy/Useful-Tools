# Java Servlet B0 reference

Health-only WAR, not production auth scaffolding. Requires Maven 3.9.x and JDK 17+ (B0 exercised JDK 25). Pinned Tomcat 11.0.26 implements Servlet 6.1. Password4j is test-only.

From this directory:

```
mvn -B clean verify
mvn test-compile exec:java
```

GET http://127.0.0.1:8085/health returns status, version and target. Stop with Ctrl+C. Port override: `mvn test-compile exec:java -Db0.port=8086`. Maven startup runner is a test-classpath convenience; the packaged target/b0-servlet.war can also be deployed to Tomcat 11.0.26. Servlet/container/test dependencies are not bundled in the WAR.

Tests verify the actual WAR health/404, Argon2id correct/incorrect passwords and running Tomcat session invalidation/expiry. Optional application smoke is enabled by -Db0.applicationWar=<absolute-existing-application-war>, only with SQLITE_DB_PATH set inside a disposable .b0 directory. Root certification always enables it and checks the seeded database and startup logs. No reference auth endpoints or credentials exist.
