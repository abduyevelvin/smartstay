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

### Issue 7 — `getAvailableRooms` is a stub (wrong results) `[ ]`

**File:** `hotel-service/src/main/java/com/smartstay/hotel/service/HotelServiceImpl.java:getAvailableRooms`

**What's wrong:**
```java
// TODO: wire Booking Service in Phase 4 to check real-time availability
return roomRepository.findByHotelIdAndAvailable(hotelId, true)...
```
`available` is a static flag (is the room "enabled by manager"). It is never set to
`false` when a booking is made. The `/api/v1/hotels/{hotelId}/availability` endpoint
therefore always shows all enabled rooms as "available" regardless of existing bookings.
The frontend's "available rooms" section on the hotel detail page is misleading.

**Why it matters:**
Guests see rooms as available, pick them, and then get a 409 Conflict when they try to
book — confusing UX. The check-then-book UI is broken.

**Fix:**
The availability endpoint needs date params (`checkIn`, `checkOut`) and must call
booking-service for each room:
```
GET /api/v1/hotels/{hotelId}/availability?checkIn=2027-01-01&checkOut=2027-01-05
```
For each room in the hotel where `available=true`, call:
`booking-service GET /api/v1/bookings/availability?roomId=&checkIn=&checkOut=`
Filter to only rooms that return `{ available: true }`.

Because this is N+1 calls, a better approach: add a bulk availability check endpoint
to booking-service that accepts a list of roomIds + date range and returns a map of
`roomId → available`. One Feign call instead of N.

---

## 🟡 Medium — Functionality Gaps

### Issue 8 — `PENDING` and `COMPLETED` statuses are unused dead code `[ ]`

**File:** `booking-service/src/main/java/com/smartstay/booking/enums/BookingStatus.java`

**What's wrong:**
The enum has four values: `PENDING | CONFIRMED | CANCELLED | COMPLETED`.
Bookings are always created as `CONFIRMED`. `PENDING` and `COMPLETED` are never set
anywhere in the codebase.

**Options:**
A) **Remove both** — simplest. Change to `CONFIRMED | CANCELLED`. Update tests.

B) **Implement COMPLETED** — add a scheduled job (`@Scheduled`) in booking-service
that runs nightly and sets all bookings with `checkOut < today` and `status = CONFIRMED`
to `COMPLETED`. This makes the `guestIsStayed` check cleaner (status = COMPLETED
instead of the current `status != CANCELLED AND checkOut <= today` derived logic).

C) **Implement PENDING** — only if a payment step is planned. Bookings start as PENDING,
a payment webhook/callback confirms them → CONFIRMED.

Recommendation: do **A** now (remove both), keep code clean. Add COMPLETED back in
if/when a payment phase is added.

---

### Issue 9 — `ReviewDocument.verified` is always `false` `[ ]`

**File:** `review-service/src/main/java/com/smartstay/review/service/ReviewServiceImpl.java:createReview`

**What's wrong:**
```java
final var review = ReviewDocument.builder()
    .hotelId(request.hotelId())
    .guestId(guestId.toString())
    ...
    // ← .verified() never set — defaults to false
    .build();
```
The code already calls `bookingServiceClient.verifyStay(...)` and gates the review on
`isStayed == true`. But it then forgets to stamp the document with `verified = true`.
Every review in the system shows as unverified even when the stay was confirmed.

**Fix (one line):**
```java
.verified(verifyResponse.isStayed())   // add this line to the builder chain
```

---

### Issue 10 — Zustand auth state lost on page refresh → broken auth loop `[ ]`

**File:** `frontend/src/lib/auth.ts` + `frontend/src/middleware.ts`

**What's wrong:**
Tokens (`accessToken`, `refreshToken`) live only in Zustand memory. On page reload:
1. The `smartstay_session` **cookie** survives (7-day expiry) → middleware allows
   the user into protected routes (`/bookings`, `/manage`, `/admin`)
2. Zustand resets → `accessToken = null` → every API call gets a 401
3. Refresh interceptor has no `refreshToken` → calls `clearAuth()` (clears Zustand)
   but does **NOT** call `clearSessionCookie()`
4. Cookie still set → middleware keeps user on protected pages → broken loop with
   failing API calls, no redirect to login

**Fix:**
Add Zustand `persist` middleware to save `refreshToken` to `localStorage`:
```ts
import { persist } from "zustand/middleware";

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({ ...stateAndActions }),
    {
      name: "smartstay-auth",
      partialize: (s) => ({ refreshToken: s.refreshToken }), // only token, not accessToken
    }
  )
);
```
On app mount (e.g., `layout.tsx`), check if a persisted `refreshToken` exists with no
`accessToken` → silently call `/api/v1/auth/refresh` to get a new access token.
Also: make `clearAuth()` call `clearSessionCookie()` internally so logout is complete.

---

### Issue 11 — Chat session not bound to user identity `[ ]`

**File:** `frontend/src/components/chat/ChatWidget.tsx:12-16`
**File:** `ai-assistant-service/src/main/java/com/smartstay/ai/service/ChatAssistantServiceImpl.java`

**What's wrong:**
The `sessionId` is a random UUID generated client-side in `sessionStorage`. The
AI service stores and retrieves chat history purely by `sessionId` with no check
that the requesting user owns that session.

**Why it matters:**
- A user on a shared/borrowed device inherits another user's full chat history
- A curious user who finds another's session ID (e.g., from browser history or
  a logged network request) can read or continue their private conversation

**Fix:**
Server-side: prefix the session ID with the authenticated user's ID before using it
as the memory key:
```java
// ChatAssistantServiceImpl.java
String safeSessionId = userId + ":" + sessionId;
// use safeSessionId for chatMemory.get() / advisor
```
The `userId` comes from the `X-User-Id` header (forwarded by the gateway). This
guarantees session IDs are namespaced per user with zero client changes.

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
| 7 | getAvailableRooms is a stub | `[ ]` | — |
| 8 | PENDING/COMPLETED dead code | `[ ]` | — |
| 9 | verified field never set | `[ ]` | — |
| 10 | Zustand not persisted on refresh | `[ ]` | — |
| 11 | Chat session not user-bound | `[ ]` | — |

---

*Created: 2026-11-12 | Start from Issue 1, work down. Ask before moving to next.*
