# ─── Stage 1: Build ───────────────────────────────────────────────────────────
# Uses the official Maven image that includes Maven + Eclipse Temurin JDK 21.
# Build context is the repo root (set in docker-compose.yml: context: .)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy Maven config (jvm.config carries JVM flags for annotation processors)
COPY .mvn/ .mvn/

# ── Dependency-cache layer ─────────────────────────────────────────────────────
# Copy ALL pom.xml files before any source code.
# Docker only rebuilds this layer when a pom.xml changes, not on every code edit.
COPY pom.xml ./
COPY api-gateway/pom.xml          api-gateway/pom.xml
COPY auth-service/pom.xml         auth-service/pom.xml
COPY hotel-service/pom.xml        hotel-service/pom.xml
COPY booking-service/pom.xml      booking-service/pom.xml
COPY review-service/pom.xml       review-service/pom.xml
COPY ai-assistant-service/pom.xml ai-assistant-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml

# Pre-download this module's dependencies into the local Maven cache.
# If pom.xml hasn't changed, this layer is served from cache — no network hit.
RUN mvn dependency:go-offline -pl auth-service --no-transfer-progress -q

# ── Source & compile layer ─────────────────────────────────────────────────────
# Only copy this service's source. Other modules' source never lands in this image.
COPY auth-service/src auth-service/src

# Package: skip tests (tests run in CI, not here), suppress noisy progress logs.
RUN mvn package -pl auth-service -DskipTests --no-transfer-progress

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
# Lean JRE image — no compiler, no Maven, ~200 MB smaller than the JDK image.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# curl is used by Docker Compose health checks (healthcheck.test).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

EXPOSE 8081

# Copy only the executable fat-JAR produced by the Spring Boot Maven plugin.
COPY --from=build /app/auth-service/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
