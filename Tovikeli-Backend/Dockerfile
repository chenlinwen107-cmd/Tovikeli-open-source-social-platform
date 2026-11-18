# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# ContentPlatformBackend — Cloud Run image
#
# Multi-stage build:
#   * build   : Java 21 JDK, runs the project's own Gradle Wrapper to produce
#               the executable Spring Boot jar (bootJar). Tests are skipped
#               here because they are run separately in CI/regression.
#   * runtime : Java 21 JRE only. It receives a single executable Boot jar and
#               no source, Gradle wrapper or build tooling.
#
# The application itself binds the Cloud Run port through
# `server.port=${PORT:8080}` (application.properties), so no port is
# hardcoded here and no runtime environment variable is injected by this file.
# ---------------------------------------------------------------------------

# ---- Build stage ----------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# Copy only what the Gradle Wrapper build needs. The `.dockerignore` file keeps
# unrelated project artifacts out of this context.
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts settings.gradle.kts
COPY build.gradle.kts build.gradle.kts
COPY src src

# The wrapper must be executable regardless of the host filesystem mode.
RUN chmod +x gradlew \
    && ./gradlew bootJar -x test --no-daemon --console=plain

# ---- Runtime stage --------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# Run as an unprivileged user.
RUN groupadd --system --gid 1001 app \
    && useradd --system --uid 1001 --gid app --home-dir /app app

# Only the executable Boot jar is copied. The `*-plain.jar` produced alongside
# it is intentionally NOT copied, and no source or build tooling is present.
COPY --from=build /workspace/build/libs/content-platform-backend-0.0.1-SNAPSHOT.jar /app/app.jar
RUN chown -R app:app /app

USER app

# The listening port is supplied by Cloud Run via $PORT and read by the
# application (`server.port=${PORT:8080}`); it is deliberately not fixed here.
EXPOSE 3000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
