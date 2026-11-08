# ─── Stage 1: Build ───────────────────────────────────────────────────────────
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

RUN mvn dependency:go-offline -pl hotel-service --no-transfer-progress -q

COPY hotel-service/src hotel-service/src

RUN mvn package -pl hotel-service -DskipTests --no-transfer-progress

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

EXPOSE 8082

COPY --from=build /app/hotel-service/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
