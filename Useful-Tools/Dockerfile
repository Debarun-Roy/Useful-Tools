# ── Stage 1: Build the WAR with Maven ────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependencies
COPY Useful-Tools/pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build WAR
COPY Useful-Tools/src ./src
COPY contracts/backend-support /contracts/backend-support
RUN mvn clean package -DskipTests -B


# ── Stage 2: Deploy the WAR to Tomcat ────────────────────────────────────────
FROM tomcat:11.0-jdk17

# Remove default apps
RUN rm -rf /usr/local/tomcat/webapps/*

# Install sqlite3 client with retry logic for network resilience
# This is needed for database initialization but won't block if it fails
RUN for i in 1 2 3 4; do \
  if apt-get update && apt-get install -y --fix-missing sqlite3; then \
    rm -rf /var/lib/apt/lists/*; \
    exit 0; \
  fi; \
  if [ "$i" -lt 4 ]; then \
    echo "Attempt $i failed, waiting 10 seconds before retry..."; \
    sleep 10; \
  fi; \
done || echo "⚠ Warning: sqlite3 install failed after retries, continuing with Tomcat startup"

# Copy built WAR
COPY --from=build /app/target/UsefulTools.war /usr/local/tomcat/webapps/ROOT.war

# Operator provisions a reviewed database on the persistent volume before launch.
# No account dump or production data is embedded in this image.
RUN mkdir -p /databases
ENV SQLITE_DB_PATH=/databases/UsefulTools.db
EXPOSE 8080
CMD ["sh", "-c", "test -s \"$SQLITE_DB_PATH\" || { echo 'Provision the application database on the persistent volume before startup' >&2; exit 1; }; exec catalina.sh run"]
