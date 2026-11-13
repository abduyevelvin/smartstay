# ─────────────────────────────────────────────────────────
#  SmartStay — Makefile shortcuts
#  Requires: Docker, Docker Compose
#  Note: Maven runs inside Docker — no local Maven needed.
# ─────────────────────────────────────────────────────────

.PHONY: up down build rebuild logs fresh fresh-infra certs infra-up infra-down test test-service build-service logs-service

## Build all service images (Maven runs inside Docker)
build:
	docker compose -f docker-compose.yml build

## Build a single service image (no cache): make build-service SERVICE=auth-service
build-service:
	docker compose -f docker-compose.yml build --no-cache $(SERVICE)
	docker compose -f docker-compose.yml up -d $(SERVICE)

## Start everything (infra + all services), no override/hot-swap
up:
	docker compose -f docker-compose.yml up -d

## Start with hot-swap override (requires pre-built local JARs)
up-dev:
	docker compose up -d

## Start only infrastructure (DBs, Kafka, Redis) — useful during development
infra-up:
	docker compose -f docker-compose.yml up -d postgres-auth postgres-booking postgres-vector mongodb redis zookeeper kafka kafka-ui

## Stop everything (keeps volumes/data intact — use 'rebuild' or 'fresh' for a clean slate)
down:
	docker compose down

## Clean rebuild: wipe volumes, rebuild all images, start fresh (fixes Kafka cluster-ID mismatch)
rebuild:
	docker compose down -v
	docker compose -f docker-compose.yml build
	docker compose -f docker-compose.yml up -d

## Stop infrastructure only
infra-down:
	docker compose stop postgres-auth postgres-booking postgres-vector mongodb redis zookeeper kafka kafka-ui

## Follow logs for all services
logs:
	docker compose logs -f

## Follow logs for one service: make logs-service SERVICE=auth-service
logs-service:
	docker compose logs -f $(SERVICE)

## Nuke everything: containers + volumes, then rebuild and start fresh
fresh:
	docker compose down -v
	docker compose -f docker-compose.yml up -d --build

## Nuke infra only: containers + volumes, then restart infrastructure
fresh-infra:
	docker compose down -v
	$(MAKE) infra-up

## Generate RSA keypair for JWT
certs:
	bash scripts/generate-certs.sh

## Run all tests (Testcontainers — needs Docker running)
test:
	mvn test

## Run tests for one service: make test-service SERVICE=auth-service
test-service:
	mvn test -pl $(SERVICE)
