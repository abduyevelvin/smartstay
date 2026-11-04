# ─────────────────────────────────────────────────────────
#  SmartStay — Makefile shortcuts
#  Requires: Docker, Docker Compose, Maven (or use ./mvnw)
# ─────────────────────────────────────────────────────────

.PHONY: up down build logs fresh certs infra-up infra-down

## Start everything (infra + all services)
up:
	docker compose up -d

## Start only infrastructure (DBs, Kafka, Redis) — useful during development
infra-up:
	docker compose up -d postgres-auth postgres-booking postgres-vector mongodb redis zookeeper kafka kafka-ui

## Stop everything
down:
	docker compose down

## Stop infrastructure only
infra-down:
	docker compose stop postgres-auth postgres-booking postgres-vector mongodb redis zookeeper kafka kafka-ui

## Build all services (skip tests)
build:
	./mvnw clean package -DskipTests

## Build a single service: make build-service SERVICE=auth-service
build-service:
	./mvnw clean package -pl $(SERVICE) -DskipTests

## Follow logs for all services
logs:
	docker compose logs -f

## Follow logs for one service: make logs-service SERVICE=auth-service
logs-service:
	docker compose logs -f $(SERVICE)

## Nuke everything: containers + volumes (fresh start)
fresh:
	docker compose down -v
	docker compose up -d

## Generate RSA keypair for JWT
certs:
	bash scripts/generate-certs.sh

## Run all tests
test:
	./mvnw test

## Run tests for one service: make test-service SERVICE=auth-service
test-service:
	./mvnw test -pl $(SERVICE)
