# ─── Stage 1: Build ───────────────────────────────────────────────────────────
# NOTE: ai-assistant-service is implemented in Phase 5.
# This Dockerfile is ready to use once Phase 5 is complete.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY .mvn/ .mvn/

COPY pom.xml ./
COPY api-gateway/pom.xml          api-gateway/pom.xml
COPY auth-service/pom.xml         auth-service/pom.xml
COPY hotel-service/pom.xml        hotel-service/pom.xml
COPY booking-service/pom.xml      booking-service/pom.xml
COPY review-service/pom.xml       review-service/pom.xml
COPY ai-assistant-service/pom.xml ai-assistant-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml

RUN for attempt in 1 2 3; do \
      mvn dependency:go-offline -pl ai-assistant-service --no-transfer-progress && break; \
      if [ "$attempt" -eq 3 ]; then exit 1; fi; \
      echo "go-offline attempt $attempt/3 failed, retrying in 15s..."; sleep 15; \
    done

COPY ai-assistant-service/src ai-assistant-service/src

RUN mvn package -pl ai-assistant-service -DskipTests --no-transfer-progress

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

EXPOSE 8085

COPY --from=build /app/ai-assistant-service/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
