# SmartStay — Known Issues & Fixes Backlog

> Work through these **one by one**, top to bottom.
> Before moving to the next, ask: "ready for the next one?"
> Each issue includes: what's wrong, why it matters, and exactly what to fix.

---

## Legend
- `[ ]` — not started
- `[~]` — in progress
- `[x]` — done

---

## 🔴 Critical — Security

### Issue 1 — `PATCH /api/v1/hotels/{id}/rating` is unprotected `[x]`

**Completed: 2026-11-12**

**Approach used:** Internal secret header (`X-Internal-Secret`)
- Gateway strips the header from every external request (spoofing prevention)
- `review-service` `FeignConfig` injects it on every outgoing Feign call
- `hotel-service` `updateRating` validates it; returns 403 if missing or wrong
- Secret value in `.env` as `INTERNAL_SECRET`, propagated via `docker-compose.yml`

**Files changed:**
- `.env` / `.env.example` — added `INTERNAL_SECRET`
- `api-gateway/.../JwtAuthenticationFilter.java` — strip header before any routing
- `review-service/.../config/FeignConfig.java` — new `RequestInterceptor` bean
- `review-service/.../resources/application.yml` — `app.internal-secret` property
- `hotel-service/.../HotelController.java` — secret validation in `updateRating`
- `hotel-service/.../resources/application.yml` — `app.internal-secret` property
- `docker-compose.yml` — `INTERNAL_SECRET` env var for hotel-service + review-service

**Tests:**
- `hotel-service` `HotelControllerIT` — 2 existing tests fixed, 2 new security tests added
  - `updateRating_shouldReturn403WhenSecretMissing`
  - `updateRating_shouldReturn403WhenSecretWrong` (also asserts DB not modified)
- Result: 38 tests, 0 failures ✅

---

### Issue 2 — `GET /api/v1/bookings/verify-stay` is unprotected `[x]`

**Completed: 2026-11-12**

**Approach used:** Same `X-Internal-Secret` pattern as Issue 1.
- Gateway already strips the header from external requests (from Issue 1)
- `review-service`'s `FeignConfig` is `@Configuration` (global) — already injects the header into `BookingServiceClient` calls too
- `booking-service` `verifyStay` validates the header; returns 403 if missing or wrong

**Files changed:**
- `booking-service/.../application.yml` — added `app.internal-secret`
- `docker-compose.yml` — added `INTERNAL_SECRET` to booking-service env block
- `booking-service/.../BookingController.java` — secret validation in `verifyStay`

**Tests:**
- `BookingControllerIT` — 4 existing `verifyStay` tests fixed to include secret header
- 2 new security tests added:
  - `verifyStay_missingSecret_returns403`
  - `verifyStay_wrongSecret_returns403` (also confirms data not leaked when secret wrong)
- Result: 60 tests, 0 failures ✅

---

### Issue 3 — Hotel Manager can cancel bookings at ANY hotel `[x]`

**Completed: 2026-11-12**

**Approach used:** Snapshot `managerId` on the booking at creation time.
- `BookingEntity` gained a `manager_id VARCHAR` column (Flyway `V2__add_manager_id_to_bookings.sql`)
- `HotelSummary` DTO already carries `managerId`; `createBooking` snapshots it: `booking.setManagerId(hotel.managerId())`
- `cancelBooking` now does a three-way check: guest owner **OR** ADMIN **OR** (HOTEL_MANAGER whose `userId` equals `booking.managerId`)
- A manager from a different hotel fails the `managerId` comparison and gets 403

**Files changed:**
- `booking-service/.../entity/BookingEntity.java` — added `managerId` field
- `booking-service/.../client/dto/HotelSummary.java` — confirmed `managerId` in record
- `booking-service/.../resources/db/migration/V2__add_manager_id_to_bookings.sql` — new migration
- `booking-service/.../service/BookingServiceImpl.java` — snapshot in `createBooking`; ownership check in `cancelBooking`

**Tests:**
- `BookingServiceImplTest` — `cancelBooking_asHotelManager_succeeds`, `cancelBooking_asHotelManagerOfDifferentHotel_throwsForbidden`
- `BookingControllerIT` — `cancelBooking_asHotelManager_returns200`, `cancelBooking_asManagerOfDifferentHotel_returns403`

---

### Issue 4 — Any Hotel Manager can read bookings for any hotel `[x]`

**Completed: 2026-11-12**

**Approach used:** Service-layer ownership check via Feign call to hotel-service.
- `BookingController.getHotelBookings` passes `principal.userId()` and `userRole` to the service
- `BookingServiceImpl.getHotelBookings`: when role is `HOTEL_MANAGER`, calls `hotel-service GET /hotels/{hotelId}` and verifies `hotel.managerId() == principalId`; throws `ForbiddenException` (→ 403) if they don't match
- ADMIN callers bypass the ownership check (already excluded from `HOTEL_MANAGER` branch)

**Files changed:**
- `booking-service/.../controller/BookingController.java` — passes `principal.userId()` + `userRole` to service
- `booking-service/.../service/BookingServiceImpl.java` — ownership check in `getHotelBookings`

**Tests:**
- `BookingServiceImplTest` — `getHotelBookings_asOwningManager_*`, `getHotelBookings_asManagerOfDifferentHotel_throwsForbidden`
- `BookingControllerIT` — `getHotelBookings_asHotelManager_returnsAllBookings`, `getHotelBookings_asManagerOfDifferentHotel_returns403`, `getHotelBookings_asGuest_returns403`

---

## 🟠 High — Data Integrity

### Issue 5 — Double-booking race condition (no DB lock) `[x]`

**Completed: 2026-11-12**

**Approach used:** Three-layer defence.
1. **DB EXCLUDE constraint** (`V3__booking_room_dates_exclude_constraint.sql`) — hard
   guarantee at the DB level; any overlapping INSERT for a non-cancelled booking fails
   with a constraint violation. Requires `btree_gist` extension (added in the same migration).
2. **`SELECT … FOR UPDATE` locking query** (`findOverlappingForUpdate`) — concurrent
   Thread B blocks at this point until Thread A's transaction commits; it then sees
   Thread A's booking and returns a clean 409 instead of hitting the constraint.
3. **`DataIntegrityViolationException` catch** in `createBooking` — safety net for the
   vanishingly rare window where both threads passed the lock check; translates the raw
   DB error into a user-friendly `BookingConflictException` (409).

**Files changed:**
- `booking-service/.../db/migration/V3__booking_room_dates_exclude_constraint.sql` — new migration
- `booking-service/.../repository/BookingRepository.java` — added `findOverlappingForUpdate` with `@Lock(PESSIMISTIC_WRITE)`
- `booking-service/.../service/BookingServiceImpl.java` — `@Transactional` on `createBooking`; uses `findOverlappingForUpdate`; catches `DataIntegrityViolationException`

**Tests:**
- `BookingServiceImplTest` — updated existing overlap test to mock `findOverlappingForUpdate`; added `createBooking_dbConstraintFires_throwsConflict`
- `BookingControllerIT` — existing `createBooking_overlappingDates_returns409` still passes (full-stack with real Postgres + Flyway)

---

### Issue 6 — Hotel deletion leaves orphaned confirmed bookings `[x]`

**Completed: 2026-11-12**

**Approach used:** Full Kafka event-driven cancellation.

**hotel-service (producer):**
- Added `spring-kafka` dependency to `pom.xml`
- New `KafkaConfig` bean: declares `hotel.deleted` topic
- New `HotelDeletedEvent(hotelId, hotelName, managerId)` record
- New `HotelEventProducer` — publishes to `hotel.deleted`
- `HotelServiceImpl.deleteHotel` — publishes event after hotel + rooms are deleted
- `application.yml` — added Kafka producer config with `KAFKA_BOOTSTRAP_SERVERS` env var
- `HotelControllerIT` — added `@EmbeddedKafka` so tests don't need a real broker

**booking-service (consumer):**
- New `HotelDeletedEvent` record (local copy for deserialization)
- New `HotelDeletedConsumer` — `@KafkaListener` on `hotel.deleted` → delegates to service
- `BookingService` / `BookingServiceImpl` — new `cancelAllActiveBookingsForHotel(hotelId, hotelName)`:
  1. Finds all non-CANCELLED bookings for the hotel
  2. Sets each to CANCELLED
  3. Publishes `booking.cancelled` for each (triggers guest notification email)
- `BookingRepository` — added `findByHotelIdAndStatusNot`
- `KafkaConfig` — declares `hotel.deleted` topic (consumer side)
- `application.yml` — added consumer config with type mapping for cross-service deserialization

**docker-compose:**
- `hotel-service` — added `kafka: condition: service_healthy` dependency + `KAFKA_BOOTSTRAP_SERVERS: kafka:9092`
- `booking-service` — added `KAFKA_BOOTSTRAP_SERVERS: kafka:9092`

**Tests:**
- `BookingServiceImplTest` — `cancelAllActiveBookingsForHotel_cancelsAllAndPublishesEvents`, `cancelAllActiveBookingsForHotel_noActiveBookings_doesNothing`

---

### Issue 7 — `getAvailableRooms` is a stub (wrong results) `[x]`

**Completed: 2026-11-13**

**Approach used:** Bulk Feign call from hotel-service to booking-service.

**booking-service (new endpoint):**
- New DTO `BulkAvailabilityRequest(roomIds, checkIn, checkOut)` 
- New `BookingService.checkBulkAvailability` interface method + impl (delegates to per-room `existsOverlappingBooking`)
- New endpoint `POST /api/v1/bookings/bulk-availability` — returns `Map<roomId, Boolean>`

**hotel-service (wired up):**
- Added `spring-cloud-starter-openfeign` dependency
- Added `@EnableFeignClients` to `HotelServiceApplication`
- New `client/BookingServiceClient` Feign interface + `client/dto/BulkAvailabilityRequest`
- `HotelService.getAvailableRooms` now requires `checkIn` / `checkOut` params
- `HotelServiceImpl`: fetches enabled rooms → one Feign bulk-availability call → filters to available-only
- Controller endpoint now: `GET /{hotelId}/availability?checkIn=YYYY-MM-DD&checkOut=YYYY-MM-DD`
- `application.yml`: `feign.booking-service.url` with `BOOKING_SERVICE_URL` env fallback
- `docker-compose.yml`: added `BOOKING_SERVICE_URL: http://booking-service:8083` to hotel-service

**Tests:**
- `BookingServiceImplTest` — `checkBulkAvailability_mixedAvailability_returnsCorrectMap`, `checkBulkAvailability_emptyList_returnsEmptyMap` (26 tests, 0 failures ✅)
- `BookingControllerIT` — `checkBulkAvailability_noBookings_returnsAllAvailable`, `checkBulkAvailability_oneRoomBooked_returnsCorrectMap`
- `HotelServiceTest` — replaced old stub test with 3 real tests: available rooms returned, booked rooms excluded, no Feign call when no enabled rooms. Added missing `@Mock HotelEventProducer` (pre-existing gap). (21 tests, 0 failures ✅)
- `HotelControllerIT` — added `@MockBean BookingServiceClient`, updated existing test + added 2 new tests (400 when no date params, rooms filtered by Feign response)

---

## 🟡 Medium — Functionality Gaps

### Issue 8 — `PENDING` and `COMPLETED` statuses are unused dead code `[x]`

**Completed: 2026-11-13**

**Approach used:** Option A — removed both.

**Files changed:**
- `booking-service/.../enums/BookingStatus.java` — now `CONFIRMED | CANCELLED` only
- `frontend/src/types/index.ts` — `BookingStatus` type narrowed to `"CONFIRMED" | "CANCELLED"`
- `frontend/src/components/booking/BookingCard.tsx`:
  - Removed `PENDING`/`COMPLETED` entries from `STATUS_CLASS`
  - `canCancel`: was `PENDING || CONFIRMED` → now just `CONFIRMED`
  - `canReview`: was `status === "COMPLETED"` → now `status === "CONFIRMED" && checkOut <= today` (matches the backend `guestIsStayed` logic)
- `frontend/src/components/manage/HotelBookingsModal.tsx` — removed dead `STATUS_CLASS` entries

**No DB migration needed** — `status` column is plain `VARCHAR(20)`, no CHECK constraint.

**Tests:** 26 tests, 0 failures ✅

---

### Issue 9 — `ReviewDocument.verified` is always `false` `[x]`

**Completed: 2026-11-13**

**Fix:** Added `.verified(true)` to the `ReviewDocument` builder in `ReviewServiceImpl.createReview`.
The stay is already confirmed before this point (the method throws `UnverifiedStayException` if not), so `true` is unconditional here rather than `verifyResponse.isStayed()` — cleaner and equally correct.

**Files changed:**
- `review-service/.../service/ReviewServiceImpl.java` — one line added to builder chain

**Tests:**
- `ReviewServiceImplTest` — `createReview_savesCorrectGuestIdAndName` updated to assert `doc.isVerified()`; new `createReview_verifiedFlagIsSetToTrue` test added
- 12 tests, 0 failures ✅

---

### Issue 10 — Zustand auth state lost on page refresh → broken auth loop `[x]`

**Completed: 2026-11-13**

**Root cause confirmed:** `clearAuth()` cleared Zustand but not the session cookie, so on page reload the broken-API-call loop had no escape.

**Files changed:**

`frontend/src/lib/auth.ts`:
- Added `persist` middleware (Zustand v5) — persists only `refreshToken` to `localStorage` key `smartstay-auth`
- Moved `setSessionCookie` / `clearSessionCookie` helpers here (exported `setSessionCookie`)
- `clearAuth()` now calls `clearSessionCookie()` internally — fixes the root cause

`frontend/src/lib/hooks/useAuth.ts`:
- Removed local cookie helpers (now imported from `auth.ts`)
- `useLogout.onSettled` simplified to just `clearAuth()` (which handles the cookie)

`frontend/src/components/AuthHydrator.tsx` (new):
- Invisible `"use client"` component; runs once on mount via `useEffect`
- Detects persisted `refreshToken` + no `accessToken` (= page reload scenario)
- Silently calls `/api/v1/auth/refresh`, decodes the new JWT, re-populates store + cookie
- On failure (expired refresh token): calls `clearAuth()` → clears cookie → middleware redirects to /login

`frontend/src/components/providers.tsx`:
- Mounts `<AuthHydrator/>` once at the top of the client tree

**TypeScript:** `tsc --noEmit` — 0 errors ✅

---

### Issue 11 — Chat session not bound to user identity `[x]`

**Completed: 2026-11-13**

**Approach used:** Server-side namespacing — `safeSessionId = userId + ":" + sessionId`. Zero client changes.

**Files changed:**

`ai-assistant-service/.../service/ChatAssistantService.java`:
- Added `userId` param to `chat()` and `getHistory()`

`ai-assistant-service/.../service/ChatAssistantServiceImpl.java`:
- Both methods build `safeSessionId = userId + ":" + sessionId`
- `conversationId` on `MessageChatMemoryAdvisor` now uses `safeSessionId`
- `chatMemory.get(safeSessionId)` in `getHistory`

`ai-assistant-service/.../controller/AiController.java`:
- Both endpoints read `@RequestHeader("X-User-Id")` and pass it to the service

`ai-assistant-service/.../exception/GlobalExceptionHandler.java`:
- Added `MissingRequestHeaderException` handler → returns 400 (was 500)

**Tests:**
- `ChatAssistantServiceImplTest` — all stubs updated to use namespaced key; added `getHistory_differentUserSameSessionId_usesDistinctKey` test that directly proves isolation (6 tests ✅)
- `AiControllerIT` — stubs updated; added `chat_missingUserIdHeader_returns400` and `history_missingUserIdHeader_returns400` tests (12 tests ✅)
- **Total: 18 tests, 0 failures ✅**

---

## Progress Tracker

| # | Issue | Status | Done date |
|---|-------|--------|-----------|
| 1 | Rating endpoint unprotected | `[x]` | 2026-11-12 |
| 2 | verify-stay endpoint unprotected | `[x]` | 2026-11-12 |
| 3 | Manager cancels any hotel's booking | `[x]` | 2026-11-12 |
| 4 | Manager reads any hotel's bookings | `[x]` | 2026-11-12 |
| 5 | Double-booking race condition | `[x]` | 2026-11-12 |
| 6 | Hotel delete orphans bookings | `[x]` | 2026-11-12 |
| 7 | getAvailableRooms is a stub | `[x]` | 2026-11-13 |
| 8 | PENDING/COMPLETED dead code | `[x]` | 2026-11-13 |
| 9 | verified field never set | `[x]` | 2026-11-13 |
| 10 | Zustand not persisted on refresh | `[x]` | 2026-11-13 |
| 11 | Chat session not user-bound | `[x]` | 2026-11-13 |

---

*Created: 2026-11-12 | Start from Issue 1, work down. Ask before moving to next.*
