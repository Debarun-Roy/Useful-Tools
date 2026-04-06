FROM tomcat:11.0-jdk17

# Remove default webapps
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy your built WAR
COPY target/UsefulTools.war /usr/local/tomcat/webapps/ROOT.war

# Create database directory inside container
RUN mkdir -p /databases

EXPOSE 8080

CMD ["catalina.sh", "run"]