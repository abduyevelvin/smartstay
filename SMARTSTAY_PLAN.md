# SmartStay — AI-Powered Hotel Booking Platform
## Full Implementation Plan

> **Stack**: Java 21 · Spring Boot 3.4.x · Spring AI 1.0.0 · Spring Cloud Gateway · Spring Security (JWT/RS256)
> **DBs**: PostgreSQL (auth, booking) · MongoDB (hotel, review) · Redis (cache/rate-limit) · pgvector (RAG)
> **Infra**: Docker · Docker Compose · Kafka (async events)
> **AI**: OpenAI via LiteLLM proxy (already configured in spring-ai-demo)

---

## Project Layout (Maven Multi-Module)

```
smartstay/
├── pom.xml                        ← parent POM (dependency management)
├── api-gateway/
├── auth-service/
├── hotel-service/
├── booking-service/
├── review-service/
├── ai-assistant-service/
├── notification-service/
├── docker-compose.yml
└── docker-compose.override.yml    ← local dev overrides
```

---

## Phase 0 — Project Scaffold ✅ DONE

**Goal**: Get the Maven multi-module parent POM and shared infrastructure running.

> **Completed**: 2026-11-04

### Steps

- [x] 0.1 Create `smartstay/` root directory
- [x] 0.2 Create parent `pom.xml`
  - `<packaging>pom</packaging>`
  - Declares all 7 child modules
  - `dependencyManagement`: Spring Boot 3.4.5 BOM, Spring AI 1.0.0 BOM, Spring Cloud 2024.0.1 BOM
  - jjwt 0.12.6, MapStruct 1.6.3, Testcontainers 1.20.4 versions pinned
  - Lombok + spring-boot-starter-test inherited by all modules
- [x] 0.3 Create `docker-compose.yml` with all infrastructure + application services:
  - `postgres-auth` (host port 5432) — auth_db
  - `postgres-booking` (host port 5433) — booking_db
  - `postgres-vector` (host port 5434) — vector_db with pgvector extension
  - `mongodb` (port 27017) — hotel_db + review_db
  - `redis` (port 6379) — with password
  - `zookeeper` + `kafka` (host port 29092 for local dev)
  - `kafka-ui` (port 9093) — dev convenience UI
  - All 7 application service stubs wired in with health checks and `depends_on`
- [x] 0.4 Add `.env` template — all secrets externalized, fill in before first run
- [x] 0.5 RSA keypair script created at `scripts/generate-certs.sh`
  - Run `bash scripts/generate-certs.sh` once to generate `certs/private.pem` + `certs/public.pem`
  - `certs/` is in `.gitignore` — private key never committed
- [x] 0.6 Stub `pom.xml` created for all 7 modules with correct dependencies declared
- [x] 0.7 `infra/init-mongo.js` — creates `hotel_db` and `review_db` with dedicated users
- [x] 0.8 `infra/init-vector.sql` — enables `pgvector` extension on startup
- [x] 0.9 `Makefile` with shortcuts: `make up/down/build/logs/fresh/certs/infra-up/infra-down`
- [x] 0.10 `.gitignore` — covers build output, IDE files, secrets, certs

### What was created
```
smartstay/
├── pom.xml                         ← parent (all 7 modules declared)
├── docker-compose.yml              ← full infra + all 7 app service stubs
├── .env                            ← secrets template (fill this in!)
├── .gitignore
├── Makefile
├── infra/
│   ├── init-mongo.js               ← creates hotel_db + review_db users
│   └── init-vector.sql             ← CREATE EXTENSION vector
├── scripts/
│   └── generate-certs.sh           ← generates RSA keypair for JWT RS256
├── api-gateway/pom.xml
├── auth-service/pom.xml
├── hotel-service/pom.xml
├── booking-service/pom.xml
├── review-service/pom.xml
├── ai-assistant-service/pom.xml
└── notification-service/pom.xml
```

### Before moving to Phase 1 — do these manually
1. Open `smartstay/` as a Maven project in IntelliJ
2. Fill in `.env` (copy LiteLLM API key from old `spring-ai-demo`)
3. Run `bash scripts/generate-certs.sh` to generate RSA keys
4. Run `make infra-up` and verify `localhost:9093` (Kafka UI) loads

---

## Phase 1 — Auth Service

**Goal**: User registration, login, JWT issue (access + refresh tokens).

### Service Details
- Port: `8081`
- DB: PostgreSQL (`postgres-auth`, database: `auth_db`)
- JWT: RS256, asymmetric keys (private key signs, public key verifies)

### Steps

- [x] 1.1 Create `auth-service/` Maven module
  - Dependencies: `spring-boot-starter-web`, `spring-boot-starter-security`,
    `spring-boot-starter-data-jpa`, `postgresql`, `jjwt-api`, `jjwt-impl`,
    `jjwt-jackson`, `lombok`, `spring-boot-starter-validation`
- [x] 1.2 Create `User` entity
  ```
  id (UUID), email, password (bcrypt), firstName, lastName,
  role (GUEST | HOTEL_MANAGER | ADMIN), createdAt, active
  ```
  - Uses `BaseEntity` (`@MappedSuperclass`) for `id`, `createdAt`, `updatedAt`
  - `OffsetDateTime` for timestamps, Spring Data auditing via `JpaConfig`
- [x] 1.3 Create `RefreshToken` entity
  ```
  id (UUID), token, userId (FK), expiresAt, revoked
  ```
  - Extends `BaseEntity`, `@ManyToOne` FK to `UserEntity`
- [x] 1.4 Implement `UserRepository` (JPA)
  - `findByEmail`, `existsByEmail`
  - `RefreshTokenRepository`: `findByToken`, `deleteAllByUser`
- [x] 1.5 Implement `JwtService`
  - `generateAccessToken(user)` → expires 15 min
  - `generateRefreshToken(user)` → expires 7 days, stored in DB
  - `validateToken(token)` → returns claims
  - Load RS256 private/public keys from classpath
  - Keys loaded via `ResourceLoader` at `@PostConstruct`
  - `extractEmail`, `extractUserId`, `extractRole` helpers included
  - `SecurityProperties` holds key paths + expiry values
  - ⚠️ `scripts/generate-certs.sh` fixed: switched from `openssl genrsa` (PKCS#1)
    to `openssl genpkey` (PKCS#8) — required by Java `PKCS8EncodedKeySpec`
- [x] 1.6 Implement `AuthService`
  - `register(RegisterRequest)` → hash password, save user, return tokens
  - `login(LoginRequest)` → verify password, return tokens
  - `refresh(refreshToken)` → validate, revoke old, issue new pair
  - `logout(refreshToken)` → revoke token
  - `PasswordEncoder` bean in `AppConfig` (not in `SecurityConfig` — avoids circular dep)
  - Login uses same `BadCredentialsException` for all failures (prevents user enumeration)
  - `active` check on login — deactivated users rejected with same generic error
  - All methods have structured logs (INFO on success, WARN on failure)
- [x] 1.7 Create `AuthController` (`/api/v1/auth`)
  - `POST /register` → 201, `POST /login` → 200, `POST /refresh` → 200, `POST /logout` → 204
- [x] 1.8 Configure Spring Security — permit `/api/v1/auth/**` + `/actuator/health`, lock everything else
  - CSRF disabled (stateless JWT API), `SessionCreationPolicy.STATELESS`
- [x] 1.9 Add `GlobalExceptionHandler` (`@RestControllerAdvice`)
  - `ResourceExistsException` → 409, `ResourceNotFoundException` → 404
  - `BadCredentialsException` → 401, `MethodArgumentNotValidException` → 400
  - `HttpMessageNotReadableException` → 400 (invalid enum values, malformed JSON)
  - `Exception` → 500 (catch-all with full stack trace log)
  - Consistent `ErrorResponse` record: `timestamp, status, error, message, path`
- [x] 1.11 Flyway migration scripts
  - `V1__init.sql` — creates `users` and `refresh_tokens` tables
  - Indexes: `idx_users_email`, `idx_refresh_tokens_token`, `idx_refresh_tokens_user_id`
  - `ON DELETE CASCADE` on `refresh_tokens.user_id` — deletes tokens when user deleted
  - ⚠️ Each future service that adds DB changes needs a new versioned migration (V2, V3, ...)
- [x] 1.10 Write integration tests (Testcontainers + PostgreSQL) + unit tests
  - `JwtServiceTest` — token generation, validation, expiry, tamper detection, claim extraction
  - `AuthServiceTest` — all 4 methods with mocked deps, 12 test cases
  - `AuthControllerIT` — full stack with Testcontainers PostgreSQL, RSA keys generated at runtime
  - `application-test.yml` — Flyway disabled, `ddl-auto: create-drop` for test profile

### DTOs
```java
RegisterRequest  { email, password, firstName, lastName, role }
LoginRequest     { email, password }
AuthResponse     { accessToken, refreshToken, tokenType="Bearer", expiresIn }
RefreshRequest   { refreshToken }
```

### Key Files
```
auth-service/src/main/java/com/smartstay/auth/
  entity/User.java
  entity/RefreshToken.java
  repository/UserRepository.java
  repository/RefreshTokenRepository.java
  service/JwtService.java
  service/AuthService.java
  controller/AuthController.java
  security/SecurityConfig.java
  exception/GlobalExceptionHandler.java
  entity/...
```

---

## Phase 2 — API Gateway

**Goal**: Single entry point — JWT validation, routing to all downstream services, rate limiting.

### Service Details
- Port: `8080`
- No DB (stateless)
- Dependencies: `spring-cloud-starter-gateway`, `spring-boot-starter-data-redis-reactive`,
  `jjwt-api`, `jjwt-impl`

### Steps

- [ ] 2.1 Create `api-gateway/` Maven module
- [ ] 2.2 Configure `application.yml` routes:
  ```yaml
  routes:
    - id: auth-service
      uri: http://auth-service:8081
      predicates: Path=/api/v1/auth/**
      # No JWT filter — public endpoint

    - id: hotel-service
      uri: http://hotel-service:8082
      predicates: Path=/api/v1/hotels/**
      filters: [JwtAuthenticationFilter]

    - id: booking-service
      uri: http://booking-service:8083
      predicates: Path=/api/v1/bookings/**
      filters: [JwtAuthenticationFilter]

    - id: ai-assistant-service
      uri: http://ai-assistant-service:8085
      predicates: Path=/api/v1/ai/**
      filters: [JwtAuthenticationFilter]
  ```
- [ ] 2.3 Implement `JwtAuthenticationFilter` (GlobalFilter)
  - Extract `Authorization: Bearer <token>` header
  - Validate JWT signature using **public key** (RS256)
  - Reject with `401` if missing/invalid/expired
  - Forward claims as headers to downstream: `X-User-Id`, `X-User-Email`, `X-User-Role`
- [ ] 2.4 Add Redis-based `RequestRateLimiterGatewayFilterFactory`
  - 100 req/min per IP for public routes
  - 300 req/min per user for authenticated routes
- [ ] 2.5 Add CORS configuration (for future frontend)
- [ ] 2.6 Add `CircuitBreaker` fallback (optional, Resilience4j)

### Key Files
```
api-gateway/src/main/java/com/smartstay/gateway/
  filter/JwtAuthenticationFilter.java
  config/GatewayConfig.java
  config/RateLimiterConfig.java
  config/CorsConfig.java
```

---

## Phase 3 — Hotel Service

**Goal**: Hotel catalog — CRUD for hotels and rooms, search/filter, availability check.

### Service Details
- Port: `8082`
- DB: MongoDB (`hotel_db`)
- Auth: Trusts headers forwarded by gateway (`X-User-Id`, `X-User-Role`)

### Steps

- [ ] 3.1 Create `hotel-service/` Maven module
  - Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-mongodb`,
    `lombok`, `spring-boot-starter-validation`
- [ ] 3.2 Create `Hotel` document
  ```
  id, name, description, city, country, address, starRating,
  amenities (List<String>), images (List<String>),
  managerId (from JWT header), averageRating, reviewCount,
  checkInTime, checkOutTime, policies (Map<String,String>)
  ```
- [ ] 3.3 Create `Room` document (embedded in Hotel or separate collection)
  ```
  id, hotelId, roomNumber, type (SINGLE|DOUBLE|SUITE|PENTHOUSE),
  pricePerNight, maxOccupancy, amenities, images,
  available (boolean), floor
  ```
- [ ] 3.4 Implement `HotelRepository`, `RoomRepository` (Spring Data MongoDB)
- [ ] 3.5 Implement `HotelService`
  - `createHotel(request, managerId)`
  - `updateHotel(id, request, managerId)` — only owner can update
  - `searchHotels(city, checkIn, checkOut, guests, minStars)` — with pagination
  - `getHotelById(id)`
  - `addRoom(hotelId, roomRequest, managerId)`
  - `checkAvailability(hotelId, checkIn, checkOut)` → calls Booking Service
- [ ] 3.6 Create `HotelController` (`/api/v1/hotels`)
  - `GET /` — search with filters
  - `GET /{id}` — detail
  - `POST /` — create (HOTEL_MANAGER only)
  - `PUT /{id}` — update (owner only)
  - `GET /{id}/rooms` — list rooms
  - `POST /{id}/rooms` — add room (owner only)
  - `GET /{id}/availability?checkIn=&checkOut=` — available rooms
- [ ] 3.7 Add `@PreAuthorize` role checks using forwarded header
- [ ] 3.8 Write tests (Testcontainers + MongoDB)

### Key Files
```
hotel-service/src/main/java/com/smartstay/hotel/
  document/Hotel.java
  document/Room.java
  repository/HotelRepository.java
  repository/RoomRepository.java
  service/HotelService.java
  controller/HotelController.java
  entity/...
```

---

## Phase 4 — Booking Service

**Goal**: Create/manage reservations, enforce availability, handle booking lifecycle.

### Service Details
- Port: `8083`
- DB: PostgreSQL (`postgres-booking`, database: `booking_db`)
- Events: Publishes `booking.confirmed`, `booking.cancelled` to Kafka
- Calls: Hotel Service (verify room exists)

### Steps

- [ ] 4.1 Create `booking-service/` Maven module
  - Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
    `postgresql`, `spring-kafka`, `openfeign`, `lombok`, `spring-boot-starter-validation`
- [ ] 4.2 Create `Booking` entity
  ```
  id (UUID), guestId, guestEmail, hotelId, roomId,
  checkIn (LocalDate), checkOut (LocalDate),
  totalPrice, status (PENDING|CONFIRMED|CANCELLED|COMPLETED),
  specialRequests, createdAt, updatedAt
  ```
- [ ] 4.3 Create `BookingRepository` (JPA)
  - `findByGuestId(guestId)` — my bookings
  - `findByHotelIdAndStatus(hotelId, status)` — manager view
  - `findByRoomIdAndCheckInBetween(...)` — availability check
- [ ] 4.4 Implement `HotelServiceClient` (Feign)
  - Calls `hotel-service` to validate room exists and get price
- [ ] 4.5 Implement `BookingService`
  - `createBooking(request, guestId)`:
    1. Validate room via Feign
    2. Check no overlapping bookings
    3. Calculate total price
    4. Save as PENDING → CONFIRMED
    5. Publish `booking.confirmed` to Kafka
  - `cancelBooking(bookingId, userId)` — guest or manager
  - `getMyBookings(guestId)` — paginated
  - `getHotelBookings(hotelId, managerId)` — manager view
  - `checkRoomAvailability(roomId, checkIn, checkOut)` — used by hotel-service
- [ ] 4.6 Create `BookingController` (`/api/v1/bookings`)
  - `POST /` — create booking
  - `GET /my` — my bookings (guest)
  - `GET /hotel/{hotelId}` — hotel bookings (manager)
  - `PUT /{id}/cancel` — cancel
  - `GET /availability?roomId=&checkIn=&checkOut=` — check availability
- [ ] 4.7 Implement `BookingEventProducer` (Kafka)
  - Publishes `BookingConfirmedEvent { bookingId, guestEmail, hotelName, checkIn, checkOut }`
  - Publishes `BookingCancelledEvent { ... }`
- [ ] 4.8 Write integration tests (Testcontainers PostgreSQL + EmbeddedKafka)

### Key Files
```
booking-service/src/main/java/com/smartstay/booking/
  entity/Booking.java
  repository/BookingRepository.java
  service/BookingService.java
  controller/BookingController.java
  client/HotelServiceClient.java
  event/BookingEventProducer.java
  entity/...
```

---

## Phase 5 — AI Assistant Service

**Goal**: Smart hotel recommendations, RAG-based Q&A chatbot, conversational booking help.

### Service Details
- Port: `8085`
- DB: pgvector (PostgreSQL with vector extension) for embeddings
- AI: Spring AI 1.0.0 with OpenAI (via LiteLLM — already configured)
- Calls: Hotel Service (fetch hotel data for indexing)

### Steps

- [ ] 5.1 Create `ai-assistant-service/` Maven module
  - Dependencies: `spring-ai-starter-model-openai`, `spring-ai-vector-store-pgvector`,
    `spring-boot-starter-web`, `spring-kafka` (consume review events),
    `openfeign`, `lombok`
- [ ] 5.2 Configure `application.yml`
  - Inherit OpenAI LiteLLM config from existing `spring-ai-demo`
  - pgvector datasource config
  - Embedding model config
- [ ] 5.3 Set up pgvector schema
  - Enable `vector` extension in PostgreSQL
  - Spring AI auto-creates `vector_store` table
- [ ] 5.4 Implement `HotelIndexingService`
  - Fetches all hotels from hotel-service via Feign
  - Converts hotel + room data to `Document` objects
  - Ingests into pgvector via `VectorStore`
  - Re-index triggered on schedule (`@Scheduled`) or Kafka event
- [ ] 5.5 Implement `RecommendationService`
  - Takes user preference text: *"I want a beachfront hotel in Baku with a pool, 2 adults"*
  - Embeds query → similarity search in VectorStore → retrieve top-K hotels
  - Pass retrieved context + query to ChatModel
  - Returns structured recommendation list
- [ ] 5.6 Implement `ChatAssistantService` (RAG chatbot)
  - Stateful conversation using `ChatMemory` (in-memory or Redis per sessionId)
  - System prompt: *"You are a hotel booking assistant for SmartStay..."*
  - User message → embed → retrieve relevant hotel docs → augmented prompt → response
  - Handle booking intent: *"I want to book this room"* → return structured booking suggestion
- [ ] 5.7 Implement `ReviewAnalysisService` (Kafka consumer)
  - Listens to `review.created` topic
  - Sends review text to AI: extract sentiment (POSITIVE/NEUTRAL/NEGATIVE) + tags
  - Publishes `review.analyzed` event with extracted metadata
- [ ] 5.8 Create `AiController` (`/api/v1/ai`)
  - `POST /recommend` — `{ preferences: "..." }` → hotel recommendations
  - `POST /chat` — `{ message: "...", sessionId: "..." }` → chat response
  - `GET /chat/{sessionId}/history` — conversation history
  - `POST /hotels/{hotelId}/summary` — AI-generated hotel summary
- [ ] 5.9 Write tests with MockChatModel

### Key Files
```
ai-assistant-service/src/main/java/com/smartstay/ai/
  service/HotelIndexingService.java
  service/RecommendationService.java
  service/ChatAssistantService.java
  service/ReviewAnalysisService.java
  controller/AiController.java
  config/AiConfig.java           ← ChatClient bean, VectorStore bean
  config/ChatMemoryConfig.java
  entity/...
```

---

## Phase 6 — Review Service

**Goal**: Guest reviews, ratings, AI-enriched sentiment tags.

### Service Details
- Port: `8084`
- DB: MongoDB (`review_db`)
- Events: Publishes `review.created`, consumes `review.analyzed`

### Steps

- [ ] 6.1 Create `review-service/` Maven module
  - Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-mongodb`,
    `spring-kafka`, `lombok`
- [ ] 6.2 Create `Review` document
  ```
  id, hotelId, guestId, guestName, rating (1-5), title, body,
  sentiment (AI-filled), tags (AI-filled: ["clean","friendly",...]),
  stayDate, createdAt, verified (boolean)
  ```
- [ ] 6.3 Implement `ReviewService`
  - `createReview(request, guestId)`:
    1. Verify guest actually stayed (call Booking Service)
    2. Save review
    3. Publish `review.created` to Kafka
  - `getReviewsByHotel(hotelId)` — paginated, sorted by date
  - `getMyReviews(guestId)`
  - On `review.analyzed` event: update `sentiment` and `tags` fields
  - On `review.analyzed`: recalculate hotel average rating → call Hotel Service
- [ ] 6.4 Create `ReviewController` (`/api/v1/reviews`)
  - `POST /` — submit review (GUEST only)
  - `GET /hotel/{hotelId}` — hotel reviews
  - `GET /my` — my reviews
  - `DELETE /{id}` — owner or ADMIN
- [ ] 6.5 Implement `ReviewEventProducer` and `ReviewAnalyzedConsumer` (Kafka)

### Key Files
```
review-service/src/main/java/com/smartstay/review/
  document/Review.java
  repository/ReviewRepository.java
  service/ReviewService.java
  controller/ReviewController.java
  event/ReviewEventProducer.java
  event/ReviewAnalyzedConsumer.java
```

---

## Phase 7 — Notification Service

**Goal**: Send email notifications on booking events. Async, fire-and-forget.

### Service Details
- Port: `8086`
- No DB (stateless consumer)
- Events: Consumes `booking.confirmed`, `booking.cancelled`

### Steps

- [ ] 7.1 Create `notification-service/` Maven module
  - Dependencies: `spring-kafka`, `spring-boot-starter-mail` (or JavaMailSender), `lombok`
- [ ] 7.2 Implement `BookingEventConsumer`
  - Listen `booking.confirmed` → send confirmation email
  - Listen `booking.cancelled` → send cancellation email
- [ ] 7.3 Create HTML email templates (Thymeleaf)
  - `booking-confirmed.html`
  - `booking-cancelled.html`
- [ ] 7.4 Implement `EmailService` (JavaMailSender)
- [ ] 7.5 Configure retry + dead-letter topic for failed sends

### Key Files
```
notification-service/src/main/java/com/smartstay/notification/
  consumer/BookingEventConsumer.java
  service/EmailService.java
  template/booking-confirmed.html
  template/booking-cancelled.html
```

---

## Phase 8 — Docker Compose Full Stack

**Goal**: One `docker-compose up` brings everything up locally.

### Steps

- [ ] 8.1 Write `Dockerfile` for each service (multi-stage build):
  ```dockerfile
  FROM eclipse-temurin:21-jdk AS build
  WORKDIR /app
  COPY . .
  RUN ./mvnw package -pl <service-name> -DskipTests

  FROM eclipse-temurin:21-jre
  COPY --from=build /app/<service>/target/*.jar app.jar
  ENTRYPOINT ["java","-jar","app.jar"]
  ```
- [ ] 8.2 Finalize `docker-compose.yml` with all 7 services + infra
  - Health checks on all DB/Kafka containers
  - `depends_on` with `condition: service_healthy`
  - Named volumes for DB persistence
  - `.env` file for all secrets
- [ ] 8.3 Add `docker-compose.override.yml` for dev mode
  - Mount local JARs for faster iteration
  - Expose internal ports for debugging
- [ ] 8.4 Add `Makefile` with shortcuts:
  ```makefile
  make up       → docker-compose up -d
  make down     → docker-compose down
  make build    → mvn clean package -DskipTests
  make logs     → docker-compose logs -f
  make fresh    → down + remove volumes + up
  ```

---

## Phase 9 — Frontend (React / Next.js)

**Goal**: Web UI consuming all APIs via the Gateway.

### Pages & Components

- [ ] 9.1 Setup Next.js 15 app with TypeScript + Tailwind CSS
- [ ] 9.2 **Auth pages**: Register, Login (stores JWT in httpOnly cookie or memory)
- [ ] 9.3 **Home / Search**: Search bar (city, dates, guests) → hotel listing grid
- [ ] 9.4 **Hotel Detail**: Photos, amenities, room list, reviews, AI summary
- [ ] 9.5 **Booking Flow**: Select room → confirm dates → booking summary → confirm
- [ ] 9.6 **Guest Dashboard**: My bookings, cancel, leave review
- [ ] 9.7 **Hotel Manager Dashboard**: My hotels, add hotel/room, view bookings
- [ ] 9.8 **AI Chat Widget**: Floating chat button → opens sidebar → talks to `/api/v1/ai/chat`
- [ ] 9.9 Add to `docker-compose.yml` as `frontend` service

---

## Cross-Cutting Concerns (apply throughout)

### Logging
- Structured JSON logs (Logback + logstash-logback-encoder)
- MDC: always log `traceId`, `userId`, `service`
- Propagate `X-Trace-Id` header across services

### Validation
- `@Valid` + `@NotBlank`, `@Email`, `@Min`, `@Max` on all DTOs
- Custom validators where needed (e.g. `checkOut` must be after `checkIn`)

### Error Handling
- Each service has `GlobalExceptionHandler` (`@RestControllerAdvice`)
- Consistent error response: `{ timestamp, status, error, message, path }`

### Configuration
- All secrets via environment variables / `.env` — **never hardcode**
- `application.yml` per service + `application-docker.yml` for container profile

---

## Tech Stack Reference Card

| Concern | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.x |
| AI | Spring AI 1.0.0 + OpenAI (LiteLLM) |
| API Gateway | Spring Cloud Gateway |
| Security | Spring Security + JWT (RS256, jjwt) |
| SQL DB | PostgreSQL 16 |
| NoSQL DB | MongoDB 7 |
| Cache | Redis 7 |
| Messaging | Apache Kafka |
| ORM | Spring Data JPA (SQL) + Spring Data MongoDB |
| Vector Store | pgvector |
| HTTP Client | Spring Cloud OpenFeign |
| Build | Maven (multi-module) |
| Containers | Docker + Docker Compose |
| Testing | JUnit 5 + Testcontainers + MockMvc |
| Frontend | Next.js 15 + TypeScript + Tailwind CSS |

---

## Order of Implementation Summary

```
Phase 0  →  Parent POM + Docker Compose infra
Phase 1  →  Auth Service (JWT foundation)
Phase 2  →  API Gateway (JWT validation + routing)
Phase 3  →  Hotel Service (core domain, MongoDB)
Phase 4  →  Booking Service (transactions, Kafka events)
Phase 5  →  AI Assistant Service (RAG, recommendations, chatbot)
Phase 6  →  Review Service (MongoDB + Kafka)
Phase 7  →  Notification Service (email, Kafka consumer)
Phase 8  →  Docker full stack assembly
Phase 9  →  Frontend (Next.js)
```

---

*Last updated: 2026-11-04 — come back to this file whenever you get stuck, or ask Claude to continue from any specific phase.*
