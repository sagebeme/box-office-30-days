# The sync server as a Docker image. Build the jar first, then the image:
#
#   ../../mvnw package -DskipTests
#   docker build -t boxoffice-server .
#
# Only the jar goes in (see .dockerignore): no source, no .env, no database.

# A Java 21 runtime (a JRE, not the whole JDK: nothing compiles in here) on Ubuntu 24.04.
FROM eclipse-temurin:21-jre-noble

# A user of its own. If someone breaks into the server, they're "boxoffice", not root.
RUN useradd --system --uid 10001 --home-dir /data --shell /usr/sbin/nologin boxoffice \
    && mkdir -p /data /app \
    && chown boxoffice /data

WORKDIR /app
COPY server/target/boxoffice-server.jar app.jar

USER boxoffice

# The prod profile (application-prod.properties), with the data in /data.
# Secrets are NOT set here: an image is easy to copy, so they come from the
# environment when the container starts (docker run --env-file .env ...).
ENV SPRING_PROFILES_ACTIVE=prod \
    BOXOFFICE_DATA_DIR=/data

# /data outlives the container: a new version of the server keeps every sale.
VOLUME /data
EXPOSE 8080

# Use up to 75% of the container's memory for Java, not a guess based on the whole machine.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
