# SmartStay 🏨

> AI-powered hotel booking platform built with a Java microservices architecture.

SmartStay lets guests search hotels, make bookings, and chat with an AI assistant that recommends properties and answers questions in natural language. Hotel managers can list properties, manage rooms, and track reservations — all through a single API Gateway.

---

## Architecture

```
                        ┌─────────────────────────────────┐
                        │         API Gateway :8080        │
                        │  JWT validation · Rate limiting  │
                        └──────────────┬──────────────────┘
                                       │
          ┌──────────────┬─────────────┼──────────────┬──────────────┐
          ▼              ▼             ▼               ▼              ▼
    ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌──────────────┐
    │   Auth    │ │   Hotel   │ │  Booking  │ │  Review   │ │ AI Assistant │
    │  :8081    │ │  :8082    │ │  :8083    │ │  :8084    │ │    :8085     │
    └─────┬─────┘ └─────┬─────┘ └─────┬─────┘ └─────┬─────┘ └──────┬───────┘
          │             │             │               │               │
      postgres       mongodb      postgres         mongodb        pgvector
       (auth)        (hotel)     (booking)         (review)      (vectors)
                                     │               │
                        ┌────────────┴───────────────┘
                        │         Apache Kafka
                        │  booking.confirmed / cancelled
                        │  review.created / analyzed
                        └────────────┬───────────────
                                     ▼
                           ┌──────────────────┐
                           │  Notification    │
                           │    :8086         │
                           │  (email sender)  │
                           └──────────────────┘
```

---

## Tech Stack

| Concern        | Technology                                    |
|----------------|-----------------------------------------------|
| Language       | Java 21                                       |
| Framework      | Spring Boot 3.4.x                             |
| AI             | Spring AI 1.0.0 + OpenAI (via LiteLLM proxy) |
| API Gateway    | Spring Cloud Gateway                          |
| Security       | Spring Security + JWT RS256 (jjwt 0.12.6)     |
| SQL DBs        | PostgreSQL 16                                 |
| NoSQL DB       | MongoDB 7                                     |
| Cache          | Redis 7                                       |
| Messaging      | Apache Kafka (Confluent 7.7.1)                |
| Vector Store   | pgvector (PostgreSQL extension)               |
| HTTP Client    | Spring Cloud OpenFeign                        |
| Build          | Maven (multi-module)                          |
| Containers     | Docker + Docker Compose                       |
| Testing        | JUnit 5 + Testcontainers + MockMvc            |
| Frontend       | Next.js 15 + TypeScript + Tailwind CSS *(planned)* |

---

## Services

| Service                | Port   | Database                       | Description                                          |
|------------------------|--------|--------------------------------|------------------------------------------------------|
| `api-gateway`          | 8080   | Redis (rate limiting)          | Single entry point — JWT validation & routing        |
| `auth-service`         | 8081   | PostgreSQL `auth_db`           | Registration, login, JWT issue & refresh             |
| `hotel-service`        | 8082   | MongoDB `hotel_db`             | Hotel & room catalog, search, availability           |
| `booking-service`      | 8083   | PostgreSQL `booking_db`        | Reservations lifecycle, publishes Kafka events       |
| `review-service`       | 8084   | MongoDB `review_db`            | Guest reviews, AI-enriched sentiment tags            |
| `ai-assistant-service` | 8085   | PostgreSQL `vector_db` (pgvector) | RAG chatbot, hotel recommendations, review analysis |
| `notification-service` | 8086   | —                              | Email notifications (Kafka consumer, stateless)      |

---

## Infrastructure Ports

| Container               | Host Port | Purpose                        |
|-------------------------|-----------|--------------------------------|
| `smartstay-postgres-auth`    | 5432 | Auth service database          |
| `smartstay-postgres-booking` | 5433 | Booking service database       |
| `smartstay-postgres-vector`  | 5434 | AI embeddings (pgvector)       |
| `smartstay-mongodb`          | 27017 | Hotel & review data           |
| `smartstay-redis`            | 6379 | Cache & rate limiting          |
| `smartstay-kafka`            | 29092 | Kafka broker (local dev)      |
| `smartstay-kafka-ui`         | 9093 | Kafka web UI                   |

---

## Prerequisites

- **Docker** + **Docker Compose** (Rancher Desktop or Docker Desktop)
- **Java 21** (Zulu, Temurin, or similar)
- **Maven** (or use the included `./mvnw` wrapper)
- **make** — install via `choco install make` on Windows
- **OpenSSL** — for generating RSA keypair (`bash scripts/generate-certs.sh`)

---

## Getting Started

### 1. Clone & enter the project

```bash
git clone <repo-url>
cd smartstay
```

### 2. Set up environment variables

```bash
cp .env.example .env
# Open .env and fill in your secrets (see Environment Variables section below)
```

### 3. Generate RSA keypair for JWT

```bash
bash scripts/generate-certs.sh
# Creates certs/private.pem and certs/public.pem — never committed to git
```

### 4. Start infrastructure

```bash
make infra-up
```

Starts: PostgreSQL × 3, MongoDB, Redis, Zookeeper, Kafka, Kafka UI.

### 5. Verify everything is healthy

```bash
docker ps
# All containers should show (healthy) or Up
```

Open **http://localhost:9093** — Kafka UI should load.

### 6. Build & start all services *(once service code is implemented)*

```bash
make build    # compile all modules, skip tests
make up       # bring up the full stack
```

---

## Environment Variables

Copy `.env.example` to `.env` and fill in the values. **Never commit `.env`.**

| Variable                    | Description                                  |
|-----------------------------|----------------------------------------------|
| `POSTGRES_AUTH_USER`        | Auth DB username                             |
| `POSTGRES_AUTH_PASSWORD`    | Auth DB password                             |
| `POSTGRES_BOOKING_USER`     | Booking DB username                          |
| `POSTGRES_BOOKING_PASSWORD` | Booking DB password                          |
| `POSTGRES_VECTOR_USER`      | pgvector DB username                         |
| `POSTGRES_VECTOR_PASSWORD`  | pgvector DB password                         |
| `MONGO_ROOT_USER`           | MongoDB root username                        |
| `MONGO_ROOT_PASSWORD`       | MongoDB root password                        |
| `REDIS_PASSWORD`            | Redis auth password                          |
| `SPRING_AI_OPENAI_API_KEY`  | LiteLLM proxy API key (used as OpenAI key)   |
| `MAIL_HOST`                 | SMTP host (use Mailtrap for local dev)       |
| `MAIL_PORT`                 | SMTP port                                    |
| `MAIL_USERNAME`             | SMTP username                                |
| `MAIL_PASSWORD`             | SMTP password                                |

---

## Makefile Commands

```bash
make infra-up           # Start only infrastructure (DBs, Kafka, Redis)
make infra-down         # Stop infrastructure containers

make up                 # Start full stack (infra + all services)
make down               # Stop everything

make build              # Build all modules (skip tests)
make build-service SERVICE=auth-service   # Build one module

make logs               # Tail logs for all services
make logs-service SERVICE=auth-service    # Tail logs for one service

make fresh              # Nuke everything (containers + volumes) then restart

make certs              # Generate RSA keypair → certs/private.pem + certs/public.pem
make test               # Run all tests
make test-service SERVICE=auth-service    # Run tests for one module
```

---

## Project Structure

```
smartstay/
├── pom.xml                         ← Parent POM — dependency management for all modules
├── docker-compose.yml              ← Full infrastructure + application services
├── Makefile                        ← Dev shortcuts
├── .env                            ← Secrets (fill in, never commit)
├── .env.example                    ← Template with all required keys
│
├── infra/
│   ├── init-mongo.js               ← Creates hotel_db + review_db with dedicated users
│   └── init-vector.sql             ← Enables pgvector extension on startup
│
├── scripts/
│   └── generate-certs.sh           ← Generates RSA keypair for JWT RS256
│
├── certs/                          ← Generated keys (gitignored)
│   ├── private.pem                 ← Signs JWT tokens (auth-service only)
│   └── public.pem                  ← Verifies JWT tokens (gateway + all services)
│
├── api-gateway/
├── auth-service/
├── hotel-service/
├── booking-service/
├── review-service/
├── ai-assistant-service/
└── notification-service/
```

Each service module follows the same layout:
```
<service>/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/smartstay/<service>/
    │   │   ├── controller/
    │   │   ├── service/
    │   │   ├── repository/
    │   │   ├── entity/ (or document/ for MongoDB)
    │   │   ├── dto/
    │   │   ├── config/
    │   │   └── exception/
    │   └── resources/
    │       ├── application.yml
    │       └── application-docker.yml
    └── test/
```

---

## Kafka Topics

| Topic               | Producer         | Consumer(s)                              |
|---------------------|------------------|------------------------------------------|
| `booking.confirmed` | booking-service  | notification-service                     |
| `booking.cancelled` | booking-service  | notification-service                     |
| `review.created`    | review-service   | ai-assistant-service                     |
| `review.analyzed`   | ai-assistant-service | review-service                       |

---

## Security Model

- **JWT RS256** — auth-service signs tokens with the RSA **private key**; all other services (gateway) verify with the **public key** only. Private key never leaves auth-service.
- **Token types** — access token (15 min), refresh token (7 days, stored in DB, revocable).
- **Gateway enforcement** — `JwtAuthenticationFilter` validates every request before it reaches downstream services. Auth endpoints (`/api/v1/auth/**`) are public.
- **Downstream trust** — services trust `X-User-Id`, `X-User-Email`, `X-User-Role` headers forwarded by the gateway; they do not re-validate JWTs.
- **Rate limiting** — Redis-backed: 100 req/min per IP (public), 300 req/min per user (authenticated).

---

## Development Phases

| Phase | Description                        | Status      |
|-------|------------------------------------|-------------|
| 0     | Project scaffold & infrastructure  | ✅ Done     |
| 1     | Auth Service (JWT)                 | 🔲 Planned  |
| 2     | API Gateway (routing, rate limit)  | 🔲 Planned  |
| 3     | Hotel Service (catalog, search)    | 🔲 Planned  |
| 4     | Booking Service (reservations)     | 🔲 Planned  |
| 5     | AI Assistant Service (RAG, chat)   | 🔲 Planned  |
| 6     | Review Service                     | 🔲 Planned  |
| 7     | Notification Service (email)       | 🔲 Planned  |
| 8     | Docker full-stack assembly         | 🔲 Planned  |
| 9     | Frontend (Next.js)                 | 🔲 Planned  |

See [`SMARTSTAY_PLAN.md`](SMARTSTAY_PLAN.md) for the detailed implementation plan per phase.

---

## Troubleshooting

**Port 5432 already in use**
Another PostgreSQL container is running. Find and stop it:
```bash
docker ps -a --format "table {{.Names}}\t{{.Ports}}" | grep 5432
docker stop <container-name>
```

**`make` not found on Windows**
Install via Chocolatey (run PowerShell as Administrator):
```powershell
choco install make -y
```

**Containers stuck in `Created` (not starting)**
Check logs for the failing container:
```bash
docker logs smartstay-postgres-auth
```

**Kafka UI not loading at localhost:9093**
`kafka-ui` depends on Kafka being healthy. Wait ~30 seconds after `make infra-up`, then retry. Check Kafka health:
```bash
docker ps --filter name=smartstay-kafka --format "{{.Status}}"
```
