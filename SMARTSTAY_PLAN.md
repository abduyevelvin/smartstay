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
  - jjwt 0.12.6, MapStruct 1.6.3, Testcontainers 1.21.4 versions pinned
  - Lombok 1.18.42 (upgraded from 1.18.36 for Java 25 support), spring-boot-starter-test inherited by all modules
  - Surefire: `includes` extended to `**/*IT.java`; `argLine` adds `--add-opens java.base/java.lang=ALL-UNNAMED` + `-Dnet.bytebuddy.experimental=true` (Java 25 Mockito/ByteBuddy fix)
  - `.mvn/jvm.config` — exports jdk.compiler internals for MapStruct annotation processor under Java 25
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
  - JWT claims: `userId`, `role`, `name` (`firstName + " " + lastName`), subject = email
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

- [x] 2.1 Create `api-gateway/` Maven module
- [x] 2.2 Configure `application.yml` routes:
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
- [x] 2.3 Implement `JwtAuthenticationFilter` (GlobalFilter)
  - Extract `Authorization: Bearer <token>` header
  - Validate JWT signature using **public key** (RS256)
  - Reject with `401` if missing/invalid/expired
  - Forward claims as headers to downstream: `X-User-Id`, `X-User-Email`, `X-User-Role`, `X-User-Name`
- [x] 2.4 Add Redis-based `RequestRateLimiterGatewayFilterFactory`
  - 100 req/min per IP for public routes
  - 300 req/min per user for authenticated routes
- [x] 2.5 Add CORS configuration (for future frontend)
- [x] 2.6 Add `CircuitBreaker` fallback (optional, Resilience4j)

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

- [x] 3.1 Create `hotel-service/` Maven module
  - Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-mongodb`,
    `lombok`, `spring-boot-starter-validation`
- [x] 3.2 Create `Hotel` document (`document/HotelDocument.java`)
  ```
  id, name, description, city, country, address, starRating,
  amenities (List<String>), images (List<String>),
  managerId (from JWT header), averageRating, reviewCount,
  checkInTime, checkOutTime, policies (Map<String,String>)
  ```
  - `@CompoundIndex(city + starRating)` for search queries
- [x] 3.3 Create `Room` document (`document/RoomDocument.java`)
  ```
  id, hotelId, roomNumber, type (SINGLE|DOUBLE|SUITE|PENTHOUSE),
  pricePerNight, maxOccupancy, amenities, images,
  available (boolean), floor
  ```
  - `Type` enum in `enums/Type.java`
  - `@Indexed` on `hotelId` for fast room lookups by hotel
- [x] 3.4 Implement `HotelRepository`, `RoomRepository` (Spring Data MongoDB)
  - `HotelRepositoryCustom` + `HotelRepositoryImpl` with `MongoTemplate` for flexible search
- [x] 3.5 Implement `HotelService`
  - Interface + `HotelServiceImpl`
  - `createHotel`, `getHotelById`, `searchHotels`, `updateHotel`, `deleteHotel`
  - `addRoom`, `getRoomsByHotel`, `getAvailableRooms` (stub — Booking Service wired in Phase 4)
  - `HotelMapper` + `RoomMapper` (MapStruct, `NullValuePropertyMappingStrategy.IGNORE`)
  - DTOs: `CreateHotelRequest`, `UpdateHotelRequest`, `CreateRoomRequest`, `HotelResponse`, `RoomResponse`
  - Exceptions: `ResourceNotFoundException`, `ResourceExistsException`, `ForbiddenException`, `GlobalExceptionHandler`
- [x] 3.6 Create `HotelController` (`/api/v1/hotels`)
  - `POST /` — create hotel → 201
  - `GET /{id}` — hotel detail → 200
  - `GET /` — search (city, country, minStars + pageable) → 200
  - `PUT /{id}` — update hotel → 200
  - `DELETE /{id}` — delete hotel → 204
  - `POST /{id}/rooms` — add room → 201
  - `GET /{id}/rooms` — list all rooms → 200
  - `GET /{id}/availability` — available rooms → 200
  - `managerId` extracted from `X-User-Id` header (forwarded by gateway)
- [x] 3.7 Add `@PreAuthorize` role checks using forwarded header
  - `HeaderAuthenticationFilter` — reads `X-User-Id` + `X-User-Role` headers, populates `SecurityContext`
  - `SecurityConfig` — stateless, CSRF disabled, `permitAll` (gateway guards perimeter), `@EnableMethodSecurity`
  - `POST /`, `PUT /{id}`, `POST /{id}/rooms` → `hasRole('HOTEL_MANAGER')`
  - `DELETE /{id}` → `hasRole('HOTEL_MANAGER') or hasRole('ADMIN')`
  - GET endpoints remain public
- [x] 3.8 Write tests (Testcontainers + MongoDB)
  - `HotelServiceTest` — 14 unit tests, all 8 service methods covered with Mockito
  - `HotelControllerIT` — 14 integration tests, full HTTP stack with Testcontainers MongoDB
  - `application-test.yml` — MongoDB URI overridden via `@DynamicPropertySource`

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
- Calls: Hotel Service (verify room exists and get price per night)
- Auth: Trusts headers forwarded by gateway (`X-User-Id`, `X-User-Email`, `X-User-Role`)

### Steps

- [x] 4.1 Create `booking-service/` Maven module
  - Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
    `postgresql`, `flyway-core`, `flyway-database-postgresql`,
    `spring-kafka`, `spring-cloud-starter-openfeign`, `mapstruct`,
    `lombok`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`
  - Test deps: `testcontainers` (junit-jupiter + postgresql), `spring-kafka-test`
  - ⚠️ `spring-boot-starter-security` must also be added (needed for
    `HeaderAuthenticationFilter` + `@PreAuthorize` in step 4.6)
- [x] 4.2 Create `Booking` entity
  ```
  id (UUID), guestId (UUID), guestEmail (String),
  hotelId (String), roomId (String),      ← MongoDB ObjectId strings from hotel-service
  checkIn (LocalDate), checkOut (LocalDate),
  totalPrice (double), status (BookingStatus enum),
  specialRequests (String), createdAt (OffsetDateTime), updatedAt (OffsetDateTime)
  ```
  - Extends `BaseEntity` (`@MappedSuperclass`) for `id`, `createdAt`, `updatedAt`
  - `OffsetDateTime` timestamps via Spring Data JPA auditing (`JpaConfig` needed in step 4.5)
  - `hotelId` / `roomId` stored as `VARCHAR` — hotel-service uses MongoDB ObjectIds (not UUIDs)
  - `status` uses `@Enumerated(EnumType.STRING)` with `BookingStatus` enum:
    `PENDING | CONFIRMED | CANCELLED | COMPLETED`
  - All fields annotated with `@Column(nullable = false)` constraints
  - Supporting types created: `entity/BaseEntity.java`, `enums/BookingStatus.java`
- [x] 4.3 Create `BookingRepository` (JPA)
  - `findByGuestId(UUID guestId, Pageable)` → `Page<BookingEntity>` — paginated "my bookings"
  - `findByHotelId(String hotelId)` → `List<BookingEntity>` — manager overview
  - `findByHotelIdAndStatus(String hotelId, BookingStatus)` → `List<BookingEntity>` — filtered manager view
  - `existsOverlappingBooking(roomId, checkIn, checkOut, cancelled)` → `boolean`
    - Custom `@Query` with JPQL overlap logic: `checkIn < :checkOut AND checkOut > :checkIn AND status <> :cancelled`
    - Replaces the incorrect `findByRoomIdAndCheckInBetween` (which only checked one boundary)
- [x] 4.4 Implement `HotelServiceClient` (Feign)
  - `getHotel(hotelId)` → `HotelSummary { id, name, city, country }` — for event payload (hotel name)
  - `getRoom(hotelId, roomId)` → `RoomSummary { id, hotelId, roomNumber, pricePerNight, maxOccupancy, available }`
  - URL configured via `feign.hotel-service.url` property (default: `http://hotel-service:8082`)
  - `GET /api/v1/hotels/{hotelId}/rooms/{roomId}` added to hotel-service (`getRoomById`)
- [x] 4.5 Implement `BookingService`
  - Config: `JpaConfig`, `KafkaConfig`, `SecurityConfig`, `HeaderAuthenticationFilter` done
  - `UserPrincipal` record + `SecurityUtils` helper (userId + email from security context)
  - `createBooking` — Feign validation, overlap check, price calc, save, publish event
  - `cancelBooking` — ownership check, save, publish event (Feign for hotel name)
  - `getMyBookings`, `getHotelBookings`, `checkRoomAvailability` implemented
  - Exceptions: `ResourceNotFoundException` (404), `ForbiddenException` (403), `BookingConflictException` (409)
- [x] 4.6 Create `BookingController` (`/api/v1/bookings`)
  - `POST /` — create booking → 201 (`GUEST` only)
  - `GET /my` — my bookings (guest, paginated)
  - `GET /hotel/{hotelId}` — hotel bookings (`HOTEL_MANAGER` only)
  - `PUT /{id}/cancel` — cancel booking → 200
  - `GET /availability?roomId=&checkIn=&checkOut=` — check availability → 200 `{ available: bool }`
  - Principal extracted from `Authentication` via `UserPrincipal`, role from authorities
- [x] 4.7 Implement `BookingEventProducer` (Kafka)
  - Publishes `BookingConfirmedEvent { bookingId, guestEmail, hotelName, checkIn, checkOut }`
  - Publishes `BookingCancelledEvent { bookingId, guestEmail, hotelName, checkIn, checkOut }`
  - Key: `bookingId.toString()` for partition locality
  - Topics declared as `@Bean NewTopic` in `KafkaConfig`
- [x] 4.8 Flyway migration
  - `V1__init.sql` — creates `bookings` table; indexes on `guest_id`, `room_id`, `hotel_id`
- [x] 4.9 `application.yml` + `application-docker.yml`
  - Datasource: `postgres-booking:5433 / booking_db`
  - Kafka bootstrap: `kafka:9092` (docker) / `localhost:29092` (local)
  - Feign URL: `feign.hotel-service.url`
  - Producer value serializer: `JsonSerializer`
- [x] 4.10 Write tests (Testcontainers PostgreSQL + `@EmbeddedKafka`)
  - `BookingServiceImplTest` — 16 unit tests, all 5 service methods covered with Mockito
    - `createBooking`: success, room disabled, overlap, hotel 404, room 404, total-price calc
    - `cancelBooking`: as owner, as manager, as admin, not-found, already-cancelled, forbidden
    - `getMyBookings`, `getHotelBookings`, `checkRoomAvailability` (available + not available)
  - `BookingRepositoryTest` — 10 `@DataJpaTest` + Testcontainers PostgreSQL tests
    - `existsOverlappingBooking`: no booking, full overlap, start overlap, end overlap, adjacent (no overlap), CANCELLED excluded, different room
    - `findByGuestId`: pagination, second-page slice
    - `findByHotelId`: returns only matching hotel
  - `BookingControllerIT` — 19 full-stack tests (`@SpringBootTest` + Flyway + `@EmbeddedKafka` + Feign `@MockBean`)
    - `POST /api/v1/bookings`: 201 + DB persist, 403 wrong role, 409 disabled, 409 overlap, 404 hotel, 400 blank field, 400 bad dates
    - `PUT /{id}/cancel`: 200 as owner (verifies DB), 403 other guest, 200 as manager, 404 not found, 409 already cancelled
    - `GET /my`: 200 with 2 results (filters other guests), 403 wrong role
    - `GET /hotel/{hotelId}`: 200 with 2 bookings (filters other hotel), 403 wrong role
    - `GET /availability`: true (no booking), false (overlap), true (cancelled ignored)
  - **Build fixes required (Java 25 + Lombok 1.18.36 incompatibility)**:
    - Upgraded Lombok to `1.18.42` in parent pom (`TypeTag.UNKNOWN` removed in Java 25)
    - Added `.mvn/jvm.config` with javac internal API exports for MapStruct
    - Added Surefire `argLine`: `--add-opens java.base/java.lang=ALL-UNNAMED` + `-Dnet.bytebuddy.experimental=true`
    - Added Surefire `includes` for `**/*IT.java` (default pattern excluded integration test class)
    - Added `spring-boot-testcontainers` to booking-service test deps (`@ServiceConnection`)

> **Result**: 45 tests, 0 failures — `mvn -pl booking-service test` → BUILD SUCCESS

### Key Files
```
booking-service/src/main/java/com/smartstay/booking/
  entity/BaseEntity.java          ✅ done
  entity/BookingEntity.java       ✅ done
  enums/BookingStatus.java        ✅ done
  repository/BookingRepository.java ✅ done
  config/JpaConfig.java                   ✅ done
  config/KafkaConfig.java                 ✅ done
  config/SecurityConfig.java              ✅ done
  security/HeaderAuthenticationFilter.java ✅ done
  security/UserPrincipal.java             ✅ done
  utils/SecurityUtils.java                ✅ done
  client/HotelServiceClient.java          ✅ done
  client/dto/HotelSummary.java            ✅ done
  client/dto/RoomSummary.java             ✅ done
  dto/CreateBookingRequest.java           ✅ done
  dto/BookingResponse.java                ✅ done
  dto/ErrorResponse.java                  ✅ done
  service/BookingService.java             ✅ done
  service/BookingServiceImpl.java         ✅ done
  controller/BookingController.java       ✅ done
  event/BookingConfirmedEvent.java        ✅ done
  event/BookingCancelledEvent.java        ✅ done
  event/BookingEventProducer.java         ✅ done
  exceptions/ResourceNotFoundException.java ✅ done
  exceptions/ForbiddenException.java      ✅ done
  exceptions/BookingConflictException.java ✅ done
  exceptions/GlobalExceptionHandler.java  ✅ done
  mapper/BookingMapper.java               ✅ done
src/main/resources/
  application.yml                         ✅ done
  application-docker.yml                  ✅ done
  db/migration/V1__init.sql               ✅ done
src/test/java/com/smartstay/booking/
  service/BookingServiceImplTest.java     ✅ done
  repository/BookingRepositoryTest.java   ✅ done
  controller/BookingControllerIT.java     ✅ done
src/test/resources/
  application-test.yml                    ✅ done
```

---

## Phase 5 — AI Assistant Service ✅ DONE

**Goal**: Smart hotel recommendations, RAG-based Q&A chatbot, conversational booking help.

> **Completed**: 2026-11-10

### Service Details
- Port: `8085`
- DB: pgvector (PostgreSQL with vector extension) for embeddings
- AI: Spring AI 1.0.0 with OpenAI (via LiteLLM — already configured)
- Calls: Hotel Service (fetch hotel data for indexing)

### Spring AI 1.0.0 API notes (differs from pre-release docs)
- `InMemoryChatMemory` → replaced by `MessageWindowChatMemory` + `InMemoryChatMemoryRepository`
- `SearchRequest.query(...).withTopK(n)` → `SearchRequest.builder().query(...).topK(n).build()`
- `Document::getContent` → `Document::getText`; constructor replaced by `Document.builder().id().text().metadata().build()`
- `new MessageChatMemoryAdvisor(mem, sessionId, n)` → `MessageChatMemoryAdvisor.builder(mem).conversationId(sessionId).build()`
- `ChatMemory.get(id, n)` → `ChatMemory.get(id)` (window size set on `MessageWindowChatMemory`)
- `InMemoryChatMemory` package: `org.springframework.ai.chat.memory` (in `spring-ai-model`, not `spring-ai-core`)

### Steps

- [x] 5.1 Create `ai-assistant-service/` Maven module
  - Dependencies: `spring-ai-starter-model-openai`, `spring-ai-starter-vector-store-pgvector`,
    `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `postgresql`,
    `spring-kafka`, `spring-cloud-starter-openfeign`, `spring-boot-starter-validation`,
    `spring-boot-starter-actuator`, `lombok`
  - Test deps: `spring-boot-starter-test`, `spring-kafka-test`
- [x] 5.2 Configure `application.yml` + `application-docker.yml`
  - pgvector: `postgres-vector:5432` (docker) / `localhost:5434` (local)
  - Spring AI OpenAI config via LiteLLM (`LITELLM_BASE_URL`, `LITELLM_API_KEY`)
  - `spring.jpa.hibernate.ddl-auto: none` — Spring AI manages `vector_store` table
  - Kafka consumer + producer config
- [x] 5.3 `AiConfig.java` — core beans
  - `ChatClient` with `defaultSystem` prompt
  - `PgVectorStore` with `dimensions(1536)`, `COSINE_DISTANCE`, `initializeSchema(true)`
  - `MessageWindowChatMemory` with `InMemoryChatMemoryRepository`, `maxMessages(10)`
  - `@EnableFeignClients`, `@EnableScheduling`
- [x] 5.4 `HotelIndexingService` + `HotelIndexingScheduler`
  - `HotelServiceClient` (Feign): `getAllHotels(@SpringQueryMap HotelSearchParams)`, `getHotelById`, `getRooms`
  - `HotelSearchParams` record: `city, country, minStars, page, size`
  - `HotelSummary` DTO includes `starRating, description, amenities, averageRating`
  - `HotelPage` wrapper for Spring Data Page JSON (`content, totalPages, totalElements`)
  - `HotelIndexingServiceImpl`: `indexHotel()` uses `Document.builder()`, `reindexAll()` fetches all
  - `HotelIndexingScheduler`: separate `@Component` with `@Scheduled` — calls `reindexAll()`
- [x] 5.5 `RecommendationService`
  - `SearchRequest.builder().query(...).topK(5).build()` → similarity search
  - Empty-doc guard → fallback message (no LLM call)
  - `summarizeHotel(hotelId)` → fetches hotel+rooms via Feign, asks LLM for 2-3 sentence summary
- [x] 5.6 `ChatAssistantService` (RAG chatbot)
  - `MessageChatMemoryAdvisor.builder(chatMemory).conversationId(sessionId).build()`
  - `getHistory(sessionId)` → `chatMemory.get(sessionId)`, filters SYSTEM messages, maps to `MessageDto`
- [x] 5.7 `ReviewAnalysisConsumer` (Kafka)
  - `Sentiment` enum (`POSITIVE|NEUTRAL|NEGATIVE`) with `@JsonCreator` for case-insensitive LLM output
  - `@RetryableTopic(attempts=3, backoff=2s×2)` — retries on LLM failure without blocking partition
  - `@DltHandler` — logs permanently failed events to `review.created.DLT`
  - `.entity(ReviewAnalysis.class)` — Spring AI structured output, no manual JSON parsing
- [x] 5.8 `AiController` (`/api/v1/ai`)
  - `POST /recommend` — `RecommendRequest { preferences }` → `{ recommendation }`
  - `POST /chat` — `ChatRequest { sessionId, message }` → `{ reply, sessionId }`
  - `GET /chat/{sessionId}/history` → `List<MessageDto>`
  - `POST /hotels/{hotelId}/summary` → `{ hotelId, summary }`
  - `GlobalExceptionHandler` — 400 for `@Valid`, 500 catch-all
- [x] 5.9 Write tests (26 tests, 0 failures)
  - `RecommendationServiceImplTest` — 5 unit tests (Mockito + deep stubs)
  - `ChatAssistantServiceImplTest` — 5 unit tests (explicit mock chain — deep stubs fail on `advisors()` vararg)
  - `HotelIndexingServiceImplTest` — 3 unit tests
  - `ReviewAnalysisConsumerTest` — 4 unit tests (verifies exception propagates for retry)
  - `AiControllerIT` — 9 `@WebMvcTest` tests
  - `application-test.yml` — disables Kafka, DataSource, JPA, pgvector auto-config

### Key Files
```
ai-assistant-service/src/main/java/com/smartstay/ai/
  config/AiConfig.java                         ✅ done
  config/KafkaConfig.java                      ✅ done
  client/HotelServiceClient.java               ✅ done
  client/dto/HotelSearchParams.java            ✅ done
  client/dto/HotelSummary.java                 ✅ done
  client/dto/HotelPage.java                    ✅ done
  client/dto/RoomSummary.java                  ✅ done
  client/service/HotelIndexingService.java     ✅ done
  client/service/HotelIndexingServiceImpl.java ✅ done
  client/scheduler/HotelIndexingScheduler.java ✅ done
  service/RecommendationService.java           ✅ done
  service/RecommendationServiceImpl.java       ✅ done
  service/ChatAssistantService.java            ✅ done
  service/ChatAssistantServiceImpl.java        ✅ done
  consumer/ReviewAnalysisConsumer.java         ✅ done
  event/ReviewCreatedEvent.java                ✅ done
  event/ReviewAnalyzedEvent.java               ✅ done
  event/ReviewAnalysis.java                    ✅ done
  event/Sentiment.java                         ✅ done
  controller/AiController.java                 ✅ done
  dto/RecommendRequest.java                    ✅ done
  dto/ChatRequest.java                         ✅ done
  dto/MessageDto.java                          ✅ done
  exception/GlobalExceptionHandler.java        ✅ done
src/main/resources/
  application.yml                              ✅ done
  application-docker.yml                       ✅ done
src/test/
  service/RecommendationServiceImplTest.java   ✅ done
  service/ChatAssistantServiceImplTest.java    ✅ done
  client/service/HotelIndexingServiceImplTest.java ✅ done
  consumer/ReviewAnalysisConsumerTest.java     ✅ done
  controller/AiControllerIT.java               ✅ done
  resources/application-test.yml              ✅ done
```

---

## Phase 6 — Review Service

**Goal**: Guest reviews, ratings, AI-enriched sentiment tags.

### Service Details
- Port: `8084`
- DB: MongoDB (`review_db`)
- Events: Publishes `review.created`, consumes `review.analyzed`

### Steps

- [x] 6.1 Create `review-service/` Maven module
  - Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-mongodb`,
    `spring-boot-starter-security`, `spring-kafka`, `spring-cloud-starter-openfeign`,
    `mapstruct`, `lombok` (inherited from parent)
  - Test deps: `testcontainers` (junit-jupiter + mongodb), `spring-boot-testcontainers`, `spring-kafka-test`
  - `application.yml` (port 8084, MongoDB URI, Kafka, Feign booking-service URL)
  - `application-docker.yml` (docker hostnames)
- [x] 6.2 Create `Review` document
  ```
  id, hotelId, guestId, guestName, rating (1-5), title, body,
  sentiment (AI-filled), tags (AI-filled: ["clean","friendly",...]),
  stayDate (LocalDate), createdAt, verified (boolean)
  ```
  - `BaseDocument` (`@MappedSuperclass`-style): `id`, `createdAt`, `updatedAt` with `@EnableMongoAuditing`
  - `@Indexed` on `hotelId`; `@Min(1) @Max(5)` on rating; `@NotBlank` on required fields
  - `@Builder @NoArgsConstructor @AllArgsConstructor` on `ReviewDocument`
  - `ReviewRepository`: `findByHotelIdOrderByCreatedAtDesc`, `findByGuestId`, `existsByHotelIdAndGuestId`, `findByHotelId`
- [x] 6.3 Implement `ReviewService`
  - `security/HeaderAuthenticationFilter` + `UserPrincipal(userId, email, name)` + `SecurityConfig` ✅
  - `client/BookingServiceClient` — `GET /api/v1/bookings/verify-stay` ✅
  - `client/dto/VerifyStayResponse(boolean isStayed)` ✅
  - `dto/CreateReviewRequest`, `dto/ReviewResponse`, `dto/ErrorResponse` ✅
  - `mapper/ReviewMapper` (MapStruct) ✅
  - `exceptions/` — `ResourceNotFoundException`, `ForbiddenException`, `UnverifiedStayException`, `AlreadyReviewedException`, `GlobalExceptionHandler` ✅
  - `service/ReviewService` (interface) + `service/ReviewServiceImpl` ✅
    - `createReview`: verify stay via Feign → check duplicate → save → publish `review.created`
    - `getHotelReviews`: paginated by hotel
    - `getMyReviews`: by guestId
    - `deleteReview`: owner or ADMIN only
- [x] 6.4 Create `ReviewController` (`/api/v1/reviews`)
  - `POST /` — submit review (GUEST only)
  - `GET /hotel/{hotelId}` — hotel reviews (paginated)
  - `GET /my` — my reviews
  - `DELETE /{id}` — owner or ADMIN
- [x] 6.5 Implement `ReviewEventProducer` and `ReviewAnalyzedConsumer` (Kafka)
  - `ReviewCreatedEvent` — `{ reviewId, hotelId, guestId, rating, body }`
  - `ReviewAnalyzedEvent` — `{ reviewId, hotelId, sentiment, tags }`
  - `ReviewEventProducer` — publishes `review.created` keyed by `reviewId`
  - `ReviewAnalyzedConsumer` — updates sentiment/tags → recalculates avg rating → calls hotel-service
  - `KafkaConfig` — declares `review.created` and `review.analyzed` topics
- [x] 6.6 Write tests (Testcontainers + MongoDB)
    - `ReviewServiceImplTest` — 10 unit tests: createReview (success, correct fields, unverified, feign down, duplicate), getHotelReviews, getMyReviews, deleteReview (owner, admin, not found, forbidden)
    - `ReviewControllerIT` — 13 full-stack tests: Testcontainers MongoDB + `@EmbeddedKafka` + `@MockBean` Feign clients
      - `POST /`: 201+persist, 403 wrong role, 403 unverified, 403 feign down, 409 duplicate, 400 blank title, 400 rating > 5
      - `GET /hotel/{id}`: 200 paginated (filters other hotel), 200 empty
      - `GET /my`: 200 filters own reviews, 403 wrong role
      - `DELETE /{id}`: 204 owner (DB verified), 204 admin, 403 other guest (DB verified not deleted), 404 not found

### Hotel-service additions (done alongside Phase 6) ✅
  - `dto/UpdateRatingRequest` — `{ averageRating, reviewCount }`
  - `HotelService.updateRating` + `HotelServiceImpl.updateRating`
  - `PATCH /api/v1/hotels/{id}/rating` in `HotelController` (`@RequestBody`)
  - Unit + IT tests added for `updateRating`

### Key Files
```
review-service/src/main/java/com/smartstay/review/
  document/ReviewDocument.java            ✅ done
  repository/ReviewRepository.java        ✅ done
  event/ReviewCreatedEvent.java           ✅ done
  event/ReviewAnalyzedEvent.java          ✅ done
  event/ReviewEventProducer.java          ✅ done
  event/ReviewAnalyzedConsumer.java       ✅ done
  client/HotelServiceClient.java          ✅ done
  client/dto/UpdateRatingRequest.java     ✅ done
  config/KafkaConfig.java                 ✅ done
  security/HeaderAuthenticationFilter.java  ← next
  security/UserPrincipal.java               ← next
  config/SecurityConfig.java                ← next
  client/BookingServiceClient.java          ← next
  client/dto/VerifyStayResponse.java        ← next
  dto/CreateReviewRequest.java              ← next
  dto/ReviewResponse.java                   ← next
  mapper/ReviewMapper.java                  ← next
  exception/GlobalExceptionHandler.java     ← next
  service/ReviewService.java                ← next
  service/ReviewServiceImpl.java            ← next
  controller/ReviewController.java          ← next
```

---

## Phase 7 — Notification Service ✅ DONE

**Goal**: Send email notifications on booking events. Async, fire-and-forget.

> **Completed**: 2026-11-09

### Service Details
- Port: `8086`
- No DB (stateless consumer)
- Events: Consumes `booking.confirmed`, `booking.cancelled`

### Steps

- [x] 7.1 Create `notification-service/` Maven module
  - Dependencies: `spring-kafka`, `spring-boot-starter-mail`, `spring-boot-starter-thymeleaf`, `lombok`
- [x] 7.2 Implement `BookingEventConsumer`
  - Listen `booking.confirmed` → send confirmation email
  - Listen `booking.cancelled` → send cancellation email
- [x] 7.3 Create HTML email templates (Thymeleaf)
  - `booking-confirmed.html`
  - `booking-cancelled.html`
- [x] 7.4 Implement `EmailService` (JavaMailSender)
- [x] 7.5 Write tests
  - `EmailServiceImplTest` — unit tests with `JavaMailSender` mock
  - `BookingEventConsumerIT` — `@EmbeddedKafka` integration test

### Key Files
```
notification-service/src/main/java/com/smartstay/notification/
  consumer/BookingEventConsumer.java      ✅ done
  service/EmailService.java              ✅ done
  service/EmailServiceImpl.java          ✅ done
  event/BookingConfirmedEvent.java       ✅ done
  event/BookingCancelledEvent.java       ✅ done
  config/KafkaConfig.java                ✅ done
src/main/resources/
  templates/booking-confirmed.html       ✅ done
  templates/booking-cancelled.html       ✅ done
  application.yml                        ✅ done
  application-docker.yml                 ✅ done
src/test/
  consumer/BookingEventConsumerIT.java   ✅ done
  service/EmailServiceImplTest.java      ✅ done
  resources/application-test.yml        ✅ done
```

---

## Phase 8 — Docker Compose Full Stack ✅ DONE

**Goal**: One `docker-compose up` brings everything up locally.

> **Completed**: 2026-11-08

### Steps

- [x] 8.1 Write `Dockerfile` for each service (multi-stage build)
  - `maven:3.9-eclipse-temurin-21` build stage (Maven + JDK 21 in one image)
  - `eclipse-temurin:21-jre-jammy` runtime stage (lean JRE, ~200MB smaller)
  - `curl` installed in runtime for `/actuator/health` health checks
  - Optimized layer caching: all `pom.xml` files copied first → `dependency:go-offline` → source copy → `package`
  - Build context is repo root (`context: .`) so each Dockerfile can reference the parent POM
- [x] 8.2 Finalize `docker-compose.yml` with all 7 services + infra
  - Health checks added to all 5 implemented app services (`/actuator/health`, `start_period: 60s`)
  - Inter-service `depends_on` with `condition: service_healthy`:
    - `booking-service` waits for `hotel-service` (Feign calls on booking creation)
    - `review-service` waits for `booking-service` (Feign calls to verify stay)
    - `api-gateway` waits for all 4 downstream services
  - Named volumes for DB persistence (already in place from Phase 0)
  - `.env` file for all secrets (already in place from Phase 0)
  - **Bug fixed**: `booking-service/application-docker.yml` used `postgres-booking:5433` — corrected to `:5432` (5433 is the host-mapped port; inside the Docker network the container listens on 5432)
  - **Bug fixed**: `review-service/application-docker.yml` used `${MONGO_USER}/${MONGO_PASSWORD}` — corrected to `${MONGO_REVIEW_USER}/${MONGO_REVIEW_PASSWORD}` to match docker-compose env vars
- [x] 8.3 Add `docker-compose.override.yml` for dev mode
  - JDWP remote debugging on `*:5005` inside each container
  - Host debug ports: gateway=5080, auth=5081, hotel=5082, booking=5083, review=5084, ai=5085, notification=5086
  - `LOGGING_LEVEL_COM_SMARTSTAY: DEBUG` for verbose app logs
  - Hot JAR swap via volume mounts — `make build-service SERVICE=xxx` + `docker compose restart xxx`
- [x] 8.4 `Makefile` with shortcuts — already complete from Phase 0 (up/down/build/logs/fresh/certs/infra-up/infra-down/test/build-service/logs-service/test-service)
- [x] 8.5 `.dockerignore` added — excludes `**/target/`, `.git/`, `.env`, `certs/`, IDE files

### What was created/changed
```
smartstay/
├── .dockerignore                                    ✅ new
├── docker-compose.yml                               ✅ updated (health checks, depends_on)
├── docker-compose.override.yml                      ✅ new (dev mode)
├── api-gateway/Dockerfile                           ✅ new
├── auth-service/Dockerfile                          ✅ new
├── hotel-service/Dockerfile                         ✅ new
├── booking-service/Dockerfile                       ✅ new
│   └── src/main/resources/application-docker.yml   ✅ bug fix (port 5433→5432)
├── review-service/Dockerfile                        ✅ new
│   └── src/main/resources/application-docker.yml   ✅ bug fix (MONGO_USER→MONGO_REVIEW_USER)
├── ai-assistant-service/Dockerfile                  ✅ new (ready for Phase 5)
└── notification-service/Dockerfile                  ✅ new (ready for Phase 7)
```

### To bring the full stack up (after Phase 5 + 7 are done)
```bash
# One time: generate RSA keys if not done yet
make certs

# Build all service JARs
make build

# Start everything
make up

# Or for a fresh start (wipes all DB data)
make fresh
```

### ✅ All 7 services implemented
All services including `ai-assistant-service` (Phase 5) and `notification-service` (Phase 7) are done.
Run `make build && make up` to bring the full stack up.

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
Phase 0  →  Parent POM + Docker Compose infra          ✅ DONE
Phase 1  →  Auth Service (JWT foundation)               ✅ DONE
Phase 2  →  API Gateway (JWT validation + routing)      ✅ DONE
Phase 3  →  Hotel Service (core domain, MongoDB)        ✅ DONE
Phase 4  →  Booking Service (transactions, Kafka)       ✅ DONE
Phase 5  →  AI Assistant Service (RAG, chatbot)         ✅ DONE
Phase 6  →  Review Service (MongoDB + Kafka)            ✅ DONE
Phase 7  →  Notification Service (email, Kafka)         ✅ DONE
Phase 8  →  Docker full stack assembly                  ✅ DONE
Phase 9  →  Frontend (Next.js)                          ⬜ TODO
```

---

*Last updated: 2026-11-10 — Phases 0–8 complete. Only Phase 9 (Frontend) remaining.*
