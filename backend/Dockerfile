# SENTINEL v2 backend — Spring Boot 3.4, Java 21.
# Multi-stage: build (JDK + Maven, tests run here) -> runtime (JRE only, non-root).

# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Maven wrapper + pom first for layer caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw -B -q dependency:go-offline

# Source + full build. Tests MUST pass or the image fails to build.
# Testcontainers integration tests need the Docker socket (BuildKit mount).
COPY src src
RUN --mount=type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock     ./mvnw -B package -DskipTests=false

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre

# wget for the container healthcheck
RUN apt-get update -qq && apt-get install -y -qq wget > /dev/null \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Non-root user (never run Java as root in production)
RUN groupadd -r sentinel && useradd -r -g sentinel sentinel
USER sentinel

COPY --from=build --chown=sentinel:sentinel /app/target/sentinel-backend-*.jar app.jar

EXPOSE 8080

# Actuator health is exposed at /actuator/health (see application.yaml)
ENTRYPOINT ["java", "-jar", "app.jar"]
