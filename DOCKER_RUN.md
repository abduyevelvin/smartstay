# SmartStay — Full Docker Run Guide

## Prerequisites

- Docker + Docker Compose installed
- Java 21 + Maven (or use `./mvnw`)
- Certs already generated in `./certs/` (run `make certs` if missing)

---

## Before first run

### 1. Fill in `.env`

Set your LiteLLM values:

```
LITELLM_API_KEY=sk-your-real-key
LITELLM_BASE_URL=https://your-litellm-proxy-url   # e.g. http://host.docker.internal:4000
```

> **Running LiteLLM locally?** Use `http://host.docker.internal:4000` so the container can reach your host machine.

### 2. Build all service JARs

`docker-compose.override.yml` volume-mounts local `.jar` files into containers — build them first:

```bash
./mvnw clean package -DskipTests
# or
make build
```

---

## Start the full stack

```bash
docker compose up -d
```

This starts everything:
- Infrastructure: Postgres × 3, MongoDB, Redis, Kafka, Zookeeper, Kafka UI
- Services: api-gateway, auth, hotel, booking, review, ai-assistant, notification
- Frontend: Next.js on port 3000

---

## Watch logs

```bash
docker compose logs -f

# Single service
docker compose logs -f ai-assistant-service
```

---

## Check health

```bash
docker compose ps
```

| Service         | URL                                    |
|-----------------|----------------------------------------|
| Frontend        | http://localhost:3000                  |
| API Gateway     | http://localhost:8080                  |
| Auth Service    | http://localhost:8081/actuator/health  |
| Hotel Service   | http://localhost:8082/actuator/health  |
| Booking Service | http://localhost:8083/actuator/health  |
| Review Service  | http://localhost:8084/actuator/health  |
| AI Assistant    | http://localhost:8085/actuator/health  |
| Notification    | http://localhost:8086/actuator/health  |
| Kafka UI        | http://localhost:9093                  |

---

## Run tests

Tests use Testcontainers — they run on the host, independent of the running Docker stack:

```bash
# All services
./mvnw test

# Single service
./mvnw test -pl auth-service
./mvnw test -pl ai-assistant-service

# Or with Make
make test
make test-service SERVICE=auth-service
```

---

## Common commands

```bash
# Stop everything
docker compose down

# Stop and wipe all volumes (fresh start)
docker compose down -v && docker compose up -d

# Restart a single service
docker compose restart auth-service

# Rebuild a single service image
docker compose build auth-service

# Skip the override file (no hot-swap, no debug ports)
docker compose -f docker-compose.yml up -d
```

---

## Troubleshooting

| Problem                       | Fix                                                      |
|-------------------------------|----------------------------------------------------------|
| Container exits immediately   | `docker compose logs <service>` to see the error         |
| AI service fails to start     | Check `LITELLM_BASE_URL` is reachable from inside Docker |
| JAR mount errors              | Re-run `./mvnw clean package -DskipTests`                |
| Port already in use           | Stop the conflicting process or change the port in `.env`|
| IT tests fail                 | Testcontainers needs Docker running — check `docker ps`  |
