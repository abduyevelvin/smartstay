# SmartStay — Full Docker Run Guide

## Prerequisites

- Docker + Docker Compose installed
- Certs already generated in `./certs/` (run `make certs` if missing)

---

## Before first run

Fill in `.env` with your LiteLLM values:

```
LITELLM_API_KEY=sk-your-real-key
LITELLM_BASE_URL=https://your-litellm-proxy-url   # e.g. http://host.docker.internal:4000
```

> **Running LiteLLM locally?** Use `http://host.docker.internal:4000` so the container can reach your host machine.

---

## Start the full stack

```bash
make up
```

Maven runs inside Docker — no local Maven needed. First run takes a few minutes as dependencies are downloaded.

This starts everything:
- Infrastructure: Postgres × 3, MongoDB, Redis, Kafka, Zookeeper, Kafka UI
- Services: api-gateway, auth, hotel, booking, review, ai-assistant, notification
- Frontend: Next.js on port 3000

---

## Watch logs

```bash
make logs

# Single service
make logs-service SERVICE=auth-service
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
make test

# Single service
make test-service SERVICE=auth-service
```

---

## Common commands

```bash
# Stop everything
make down

# Nuke everything and rebuild from scratch (⚠️ deletes all DB data)
make fresh

# Build all images (without starting)
make build

# Build a single image
make build-service SERVICE=auth-service

# Start infra only (DBs, Kafka, Redis)
make infra-up

# Stop infra only
make infra-down
```

---

## Restart the stack

**Resume after a clean stop — keeps all data:**
```bash
make down
make up
```

> ⚠️ `make down` keeps volumes. If you also ran `make build` between `down` and `up`,
> Kafka will fail on restart with a **cluster ID mismatch** — Zookeeper gets a new ID
> but the Kafka volume still has the old one. Use `make rebuild` instead (see below).

**Rebuild all images + clean restart (the right way):**
```bash
make rebuild
```
This wipes volumes, rebuilds every image, and starts fresh. Use this whenever you
change code and want a clean environment.

**Restart a single service after a code change:**
```bash
make build-service SERVICE=ai-assistant-service
docker compose up -d ai-assistant-service
```

**Full wipe — deletes all DB data and rebuilds from scratch:**
```bash
make fresh
```

---

## Troubleshooting

| Problem                          | Fix                                                              |
|----------------------------------|------------------------------------------------------------------|
| Container shows "exited"         | `docker compose ps` to find it, then `docker compose up -d <name>` |
| Container exited with code 0     | Cleanly stopped (not a crash) — just `docker compose up -d <name>` |
| Container exited with code 1/137 | `make logs-service SERVICE=<name>` to see the error              |
| Kafka fails: cluster ID mismatch | `make rebuild` — `make down` keeps volumes; Kafka/ZK IDs diverge after restart |
| AI service fails to start        | Check `LITELLM_BASE_URL` is reachable from inside Docker         |
| Port already in use              | Stop the conflicting process or change the port in `.env`        |
| IT tests fail                    | Testcontainers needs Docker running — check `docker ps`          |
