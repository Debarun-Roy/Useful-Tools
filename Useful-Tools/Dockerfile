# ── Stage 1: Build the WAR with Maven ────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml first so Maven can download dependencies as a cached layer.
# If only source files change on the next build, this layer is reused.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy all source files and build the WAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Deploy the WAR to Tomcat ────────────────────────────────────────
FROM tomcat:11.0-jdk17
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /app/target/UsefulTools.war /usr/local/tomcat/webapps/ROOT.war
RUN mkdir -p /databases
EXPOSE 8080
CMD ["catalina.sh", "run"]