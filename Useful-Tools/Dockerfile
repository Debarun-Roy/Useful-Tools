# ── Stage 1: Build the WAR with Maven ────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build WAR
COPY src ./src
RUN mvn clean package -DskipTests -B


# ── Stage 2: Deploy the WAR to Tomcat ────────────────────────────────────────
FROM tomcat:11.0-jdk17

# Remove default apps
RUN rm -rf /usr/local/tomcat/webapps/*

# Install sqlite3 client
RUN apt-get update && apt-get install -y sqlite3 && rm -rf /var/lib/apt/lists/*

# Copy built WAR
COPY --from=build /app/target/UsefulTools.war /usr/local/tomcat/webapps/ROOT.war

# Copy SQL dump into container
COPY usefultools_dump.sql /tmp/usefultools_dump.sql

# Ensure database directory exists (Railway volume mount target)
RUN mkdir -p /databases

EXPOSE 8080

# Import DB dump only if database doesn't already exist
CMD ["sh", "-c", "\
if [ ! -f /databases/UsefulTools.db ]; then \
  echo 'Initializing Railway volume SQLite database...'; \
  sqlite3 /databases/UsefulTools.db < /tmp/usefultools_dump.sql; \
else \
  echo 'SQLite database already exists. Skipping import.'; \
fi && catalina.sh run"]