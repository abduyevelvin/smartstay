package com.smartstay.booking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstay.booking.client.HotelServiceClient;
import com.smartstay.booking.client.dto.HotelSummary;
import com.smartstay.booking.client.dto.RoomSummary;
import com.smartstay.booking.dto.CreateBookingRequest;
import com.smartstay.booking.entity.BookingEntity;
import com.smartstay.booking.enums.BookingStatus;
import com.smartstay.booking.repository.BookingRepository;

import feign.FeignException;

/**
 * Full-stack integration test for the Booking Service HTTP layer.
 *
 * <p>Starts the full Spring application context with:
 * <ul>
 *   <li>Testcontainers PostgreSQL — real DB with Flyway migrations applied</li>
 *   <li>{@code @EmbeddedKafka} — in-process Kafka broker for event publishing</li>
 *   <li>{@code @MockBean HotelServiceClient} — mocks the Feign call to hotel-service</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"booking.confirmed", "booking.cancelled", "hotel.deleted"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@ActiveProfiles("test")
class BookingControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    BookingRepository bookingRepository;

    @MockBean
    HotelServiceClient hotelServiceClient;

    // ── Shared fixtures ───────────────────────────────────────────────────────

    static final UUID GUEST_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID MANAGER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    static final String HOTEL_ID = "hotel-abc";
    static final String ROOM_ID = "room-xyz";
    static final String EMAIL = "guest@smartstay.test";

    static final LocalDate CHECK_IN = LocalDate.now()
                                               .plusDays(10);
    static final LocalDate CHECK_OUT = LocalDate.now()
                                                .plusDays(13);  // 3 nights

    HotelSummary hotelSummary;
    RoomSummary availableRoom;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        hotelSummary = new HotelSummary(HOTEL_ID, "Grand Hotel", "Baku", "AZ", MANAGER_ID.toString());
        availableRoom = new RoomSummary(ROOM_ID, HOTEL_ID, 101, 150.0, 2, true);
    }

    // ── POST /api/v1/bookings ─────────────────────────────────────────────────

    @Test
    void createBooking_asGuest_returns201AndPersistsBooking() throws Exception {
        given(hotelServiceClient.getHotel(HOTEL_ID)).willReturn(hotelSummary);
        given(hotelServiceClient.getRoom(HOTEL_ID, ROOM_ID)).willReturn(availableRoom);

        var request = new CreateBookingRequest(HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, "Sea-view please");

        mockMvc.perform(post("/api/v1/bookings").header("X-User-Id", GUEST_ID.toString())
                                                .header("X-User-Email", EMAIL)
                                                .header("X-User-Role", "GUEST")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.status").value("CONFIRMED"))
               .andExpect(jsonPath("$.guestId").value(GUEST_ID.toString()))
               .andExpect(jsonPath("$.totalPrice").value(450.0))
               .andExpect(jsonPath("$.specialRequests").value("Sea-view please"))
               .andExpect(jsonPath("$.id").isNotEmpty());

        // Verify the booking was actually saved
        assertThat(bookingRepository.findAll()).hasSize(1);
    }

    @Test
    void createBooking_asHotelManager_returns403() throws Exception {
        var request = new CreateBookingRequest(HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, null);

        mockMvc.perform(post("/api/v1/bookings").header("X-User-Id", UUID.randomUUID()
                                                                         .toString())
                                                .header("X-User-Email", "mgr@test.com")
                                                .header("X-User-Role", "HOTEL_MANAGER")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isForbidden());
    }

    @Test
    void createBooking_roomDisabled_returns409() throws Exception {
        var disabledRoom = new RoomSummary(ROOM_ID, HOTEL_ID, 101, 150.0, 2, false);
        given(hotelServiceClient.getHotel(HOTEL_ID)).willReturn(hotelSummary);
        given(hotelServiceClient.getRoom(HOTEL_ID, ROOM_ID)).willReturn(disabledRoom);

        var request = new CreateBookingRequest(HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, null);

        mockMvc.perform(post("/api/v1/bookings").header("X-User-Id", GUEST_ID.toString())
                                                .header("X-User-Email", EMAIL)
                                                .header("X-User-Role", "GUEST")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.message").value("Room is not available for booking"));
    }

    @Test
    void createBooking_overlappingDates_returns409() throws Exception {
        // Pre-save a confirmed booking that overlaps with the request
        saveBooking(ROOM_ID, HOTEL_ID, GUEST_ID, CHECK_IN, CHECK_OUT, BookingStatus.CONFIRMED);

        given(hotelServiceClient.getHotel(HOTEL_ID)).willReturn(hotelSummary);
        given(hotelServiceClient.getRoom(HOTEL_ID, ROOM_ID)).willReturn(availableRoom);

        var request = new CreateBookingRequest(HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, null);

        mockMvc.perform(post("/api/v1/bookings").header("X-User-Id", GUEST_ID.toString())
                                                .header("X-User-Email", EMAIL)
                                                .header("X-User-Role", "GUEST")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isConflict());
    }

    @Test
    void createBooking_hotelNotFound_returns404() throws Exception {
        given(hotelServiceClient.getHotel(any())).willThrow(mock(FeignException.NotFound.class));

        var request = new CreateBookingRequest(HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, null);

        mockMvc.perform(post("/api/v1/bookings").header("X-User-Id", GUEST_ID.toString())
                                                .header("X-User-Email", EMAIL)
                                                .header("X-User-Role", "GUEST")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isNotFound());
    }

    @Test
    void createBooking_missingHotelId_returns400() throws Exception {
        String invalidBody = """
                {"hotelId":"","roomId":"%s","checkIn":"%s","checkOut":"%s"}
                """.formatted(ROOM_ID, CHECK_IN, CHECK_OUT);

        mockMvc.perform(post("/api/v1/bookings").header("X-User-Id", GUEST_ID.toString())
                                                .header("X-User-Email", EMAIL)
                                                .header("X-User-Role", "GUEST")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(invalidBody))
               .andExpect(status().isBadRequest());
    }

    @Test
    void createBooking_checkOutBeforeCheckIn_returns400() throws Exception {
        // checkIn later than checkOut — @AssertTrue fails
        var invalidRequest = new CreateBookingRequest(HOTEL_ID, ROOM_ID, LocalDate.now()
                                                                                  .plusDays(13), LocalDate.now()
                                                                                                          .plusDays(10),
                null);

        mockMvc.perform(post("/api/v1/bookings").header("X-User-Id", GUEST_ID.toString())
                                                .header("X-User-Email", EMAIL)
                                                .header("X-User-Role", "GUEST")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(invalidRequest)))
               .andExpect(status().isBadRequest());
    }

    // ── PUT /api/v1/bookings/{id}/cancel ──────────────────────────────────────

    @Test
    void cancelBooking_asOwner_returns200AndStatusCancelled() throws Exception {
        var booking = saveBooking(ROOM_ID, HOTEL_ID, GUEST_ID, CHECK_IN, CHECK_OUT, BookingStatus.CONFIRMED);
        given(hotelServiceClient.getHotel(HOTEL_ID)).willReturn(hotelSummary);

        mockMvc.perform(put("/api/v1/bookings/{id}/cancel", booking.getId()).header("X-User-Id", GUEST_ID.toString())
                                                                            .header("X-User-Email", EMAIL)
                                                                            .header("X-User-Role", "GUEST"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Verify persisted status change
        var updated = bookingRepository.findById(booking.getId())
                                       .orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancelBooking_asDifferentGuestWithNoPrivilege_returns403() throws Exception {
        var booking = saveBooking(ROOM_ID, HOTEL_ID, GUEST_ID, CHECK_IN, CHECK_OUT, BookingStatus.CONFIRMED);
        var otherGuestId = UUID.randomUUID();

        mockMvc.perform(
                       put("/api/v1/bookings/{id}/cancel", booking.getId()).header("X-User-Id", otherGuestId.toString())
                                                                           .header("X-User-Email", "other@test.com")
                                                                           .header("X-User-Role", "GUEST"))
               .andExpect(status().isForbidden());
    }

    @Test
    void cancelBooking_asHotelManager_returns200() throws Exception {
        // Booking's managerId must match the cancelling manager's userId
        var booking = saveBooking(ROOM_ID, HOTEL_ID, GUEST_ID, CHECK_IN, CHECK_OUT, BookingStatus.CONFIRMED,
                MANAGER_ID.toString());
        given(hotelServiceClient.getHotel(HOTEL_ID)).willReturn(hotelSummary);

        mockMvc.perform(put("/api/v1/bookings/{id}/cancel", booking.getId()).header("X-User-Id", MANAGER_ID.toString())
                                                                            .header("X-User-Email", "mgr@test.com")
                                                                            .header("X-User-Role", "HOTEL_MANAGER"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelBooking_asManagerOfDifferentHotel_returns403() throws Exception {
        // Booking belongs to MANAGER_ID; a different manager tries to cancel
        var booking = saveBooking(ROOM_ID, HOTEL_ID, GUEST_ID, CHECK_IN, CHECK_OUT, BookingStatus.CONFIRMED,
                MANAGER_ID.toString());
        var differentManager = UUID.randomUUID();

        mockMvc.perform(
                       put("/api/v1/bookings/{id}/cancel", booking.getId()).header("X-User-Id", differentManager.toString())
                                                                           .header("X-User-Email", "other-mgr@test.com")
                                                                           .header("X-User-Role", "HOTEL_MANAGER"))
               .andExpect(status().isForbidden());
    }

    @Test
    void cancelBooking_bookingNotFound_returns404() throws Exception {
        mockMvc.perform(put("/api/v1/bookings/{id}/cancel", UUID.randomUUID()).header("X-User-Id", GUEST_ID.toString())
                                                                              .header("X-User-Email", EMAIL)
                                                                              .header("X-User-Role", "GUEST"))
               .andExpect(status().isNotFound());
    }

    @Test
    void cancelBooking_alreadyCancelled_returns409() throws Exception {
        var booking = saveBooking(ROOM_ID, HOTEL_ID, GUEST_ID, CHECK_IN, CHECK_OUT, BookingStatus.CANCELLED);

        mockMvc.perform(put("/api/v1/bookings/{id}/cancel", booking.getId()).header("X-User-Id", GUEST_ID.toString())
                                                                            .header("X-User-Email", EMAIL)
                                                                            .header("X-User-Role", "GUEST"))
               .andExpect(status().isConflict());
    }

    // ── GET /api/v1/bookings/my ───────────────────────────────────────────────

    @Test
    void getMyBookings_asGuest_returnsPaginatedResults() throws Exception {
        saveBooking(ROOM_ID, HOTEL_ID, GUEST_ID, CHECK_IN, CHECK_OUT, BookingStatus.CONFIRMED);
        saveBooking("room-2", HOTEL_ID, GUEST_ID, LocalDate.now()
                                                           .plusDays(20), LocalDate.now()
                                                                                   .plusDays(23),
                BookingStatus.CONFIRMED);
        // Booking for a different guest — should not appear
        saveBooking(ROOM_ID, HOTEL_ID, UUID.randomUUID(), CHECK_IN, CHECK_OUT, BookingStatus.CONFIRMED);

        mockMvc.perform(get("/api/v1/bookings/my").header("X-User-Id", GUEST_ID.toString())
                                                  .header("X-User-Email", EMAIL)
                                                  .header("X-User-Role", "GUEST"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.content").isArray())
               .andExpect(jsonPath("$.content.length()").value(2))
               .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getMyBookings_asHotelManager_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/my").header("X-User-Id", UUID.randomUUID()
                                                                           .toString())
                                                  .header("X-User-Email", "mgr@test.com")
                                                  .header("X-User-Role", "HOTEL_MANAGER"))
               .andExpect(status().isForbidden());
    }

    // ── GET /api/v1/bookings/hotel/{hotelId} ──────────────────────────────────

    @Test
    void getHotelBookings_asHotelManager_returnsAllBookings() throws Exception {
        saveBooking("room-1", HOTEL_ID, UUID.randomUUID(), CHECK_IN, CHECK_OUT, BookingStatus.CONFIRMED, null);
        saveBooking("room-2", HOTEL_ID, UUID.randomUUID(), LocalDate.now()
                                                                    .plusDays(20), LocalDate.now()
                                                                                            .plusDays(23),
                BookingStatus.CANCELLED, null);
        // Different hotel — must not appear
        saveBooking("room-9", "other-hotel", UUID.randomUUID(), CHECK_IN, CHECK_OUT, BookingStatus.CONFIRMED, null);

        // hotelSummary.managerId = MANAGER_ID → ownership check passes for MANAGER_ID
        given(hotelServiceClient.getHotel(HOTEL_ID)).willReturn(hotelSummary);

        mockMvc.perform(get("/api/v1/bookings/hotel/{hotelId}", HOTEL_ID).header("X-User-Id", MANAGER_ID.toString())
                                                                         .header("X-User-Email", "mgr@test.com")
                                                                         .header("X-User-Role", "HOTEL_MANAGER"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getHotelBookings_asManagerOfDifferentHotel_returns403() throws Exception {
        // Hotel's managerId is MANAGER_ID; a different manager tries to read
        given(hotelServiceClient.getHotel(HOTEL_ID)).willReturn(hotelSummary);
        var differentManager = UUID.randomUUID();

        mockMvc.perform(
                       get("/api/v1/bookings/hotel/{hotelId}", HOTEL_ID).header("X-User-Id", differentManager.toString())
                                                                        .header("X-User-Email", "other-mgr@test.com")
                                                                        .header("X-User-Role", "HOTEL_MANAGER"))
               .andExpect(status().isForbidden());
    }

    @Test
    void getHotelBookings_asGuest_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/hotel/{hotelId}", HOTEL_ID).header("X-User-Id", GUEST_ID.toString())
                                                                         .header("X-User-Email", EMAIL)
                                                                         .header("X-User-Role", "GUEST"))
               .andExpect(status().isForbidden());
    }

    @Test
    void getHotelBookings_withStatusFilter_returnsOnlyMatchingStatus() throws Exception {
        saveBooking("room-1", HOTEL_ID, UUID.randomUUID(), CHECK_IN, CHECK_OUT, BookingStatus.CONFIRMED, null);
        saveBooking("room-2", HOTEL_ID, UUID.randomUUID(), CHECK_IN, CHECK_OUT, BookingStatus.CONFIRMED, null);
        saveBooking("room-3", HOTEL_ID, UUID.randomUUID(), CHECK_IN, CHECK_OUT, BookingStatus.CANCELLED, null);

        given(hotelServiceClient.getHotel(HOTEL_ID)).willReturn(hotelSummary);

        mockMvc.perform(get("/api/v1/bookings/hotel/{hotelId}", HOTEL_ID).param("status", "CONFIRMED")
                                                                         .header("X-User-Id", MANAGER_ID.toString())
                                                                         .header("X-User-Email", "mgr@test.com")
                                                                         .header("X-User-Role", "HOTEL_MANAGER"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/v1/bookings/hotel/{hotelId}", HOTEL_ID).param("status", "CANCELLED")
                                                                         .header("X-User-Id", MANAGER_ID.toString())
                                                                         .header("X-User-Email", "mgr@test.com")
                                                                         .header("X-User-Role", "HOTEL_MANAGER"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(1));
    }

    // ── POST /api/v1/bookings/bulk-availability ───────────────────────────────

    @Test
    void checkBulkAvailability_noBookings_returnsAllAvailable() throws Exception {
        String body = """
                {"roomIds":["%s","%s"],"checkIn":"%s","checkOut":"%s"}
                """.formatted(ROOM_ID, "other-room", CHECK_IN, CHECK_OUT)
                   .strip();

        mockMvc.perform(post("/api/v1/bookings/bulk-availability").contentType("application/json")
                                                                  .content(body))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$." + ROOM_ID).value(true))
               .andExpect(jsonPath("$.other-room").value(true));
    }

    @Test
    void checkBulkAvailability_oneRoomBooked_returnsCorrectMap() throws Exception {
        saveBooking(ROOM_ID, HOTEL_ID, GUEST_ID, CHECK_IN, CHECK_OUT, BookingStatus.CONFIRMED);

        String body = """
                {"roomIds":["%s","%s"],"checkIn":"%s","checkOut":"%s"}
                """.formatted(ROOM_ID, "free-room", CHECK_IN, CHECK_OUT)
                   .strip();

        mockMvc.perform(post("/api/v1/bookings/bulk-availability").contentType("application/json")
                                                                  .content(body))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$." + ROOM_ID).value(false))
               .andExpect(jsonPath("$.free-room").value(true));
    }

    // ── GET /api/v1/bookings/availability ─────────────────────────────────────

    @Test
    void checkAvailability_noExistingBooking_returnsTrue() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/availability").param("roomId", ROOM_ID)
                                                            .param("checkIn", CHECK_IN.toString())
                                                            .param("checkOut", CHECK_OUT.toString()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void checkAvailability_overlappingConfirmedBooking_returnsFalse() throws Exception {
        saveBooking(ROOM_ID, HOTEL_ID, GUEST_ID, CHECK_IN, CHECK_OUT, BookingStatus.CONFIRMED);

        mockMvc.perform(get("/api/v1/bookings/availability").param("roomId", ROOM_ID)
                                                            .param("checkIn", CHECK_IN.toString())
                                                            .param("checkOut", CHECK_OUT.toString()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void checkAvailability_cancelledBookingIgnored_returnsTrue() throws Exception {
        // A CANCELLED booking must not block availability
        saveBooking(ROOM_ID, HOTEL_ID, GUEST_ID, CHECK_IN, CHECK_OUT, BookingStatus.CANCELLED);

        mockMvc.perform(get("/api/v1/bookings/availability").param("roomId", ROOM_ID)
                                                            .param("checkIn", CHECK_IN.toString())
                                                            .param("checkOut", CHECK_OUT.toString()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.available").value(true));
    }

    // ── GET /api/v1/bookings/verify-stay ─────────────────────────────────────

    @Test
    void verifyStay_hasPastConfirmedBooking_returnsTrue() throws Exception {
        // checkOut is 5 days ago — counts as a completed stay
        saveBooking(ROOM_ID, HOTEL_ID, GUEST_ID, LocalDate.now()
                                                          .minusDays(10), LocalDate.now()
                                                                                   .minusDays(5),
                BookingStatus.CONFIRMED);

        mockMvc.perform(get("/api/v1/bookings/verify-stay").header("X-Internal-Secret", TEST_INTERNAL_SECRET)
                                                           .param("guestId", GUEST_ID.toString())
                                                           .param("hotelId", HOTEL_ID))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.isStayed").value(true));
    }

    @Test
    void verifyStay_noPastBooking_returnsFalse() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/verify-stay").header("X-Internal-Secret", TEST_INTERNAL_SECRET)
                                                           .param("guestId", GUEST_ID.toString())
                                                           .param("hotelId", HOTEL_ID))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.isStayed").value(false));
    }

    @Test
    void verifyStay_cancelledBookingIgnored_returnsFalse() throws Exception {
        saveBooking(ROOM_ID, HOTEL_ID, GUEST_ID, LocalDate.now()
                                                          .minusDays(10), LocalDate.now()
                                                                                   .minusDays(5),
                BookingStatus.CANCELLED);

        mockMvc.perform(get("/api/v1/bookings/verify-stay").header("X-Internal-Secret", TEST_INTERNAL_SECRET)
                                                           .param("guestId", GUEST_ID.toString())
                                                           .param("hotelId", HOTEL_ID))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.isStayed").value(false));
    }

    @Test
    void verifyStay_futureBookingIgnored_returnsFalse() throws Exception {
        saveBooking(ROOM_ID, HOTEL_ID, GUEST_ID, CHECK_IN, CHECK_OUT, BookingStatus.CONFIRMED);

        mockMvc.perform(get("/api/v1/bookings/verify-stay").header("X-Internal-Secret", TEST_INTERNAL_SECRET)
                                                           .param("guestId", GUEST_ID.toString())
                                                           .param("hotelId", HOTEL_ID))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.isStayed").value(false));
    }

    @Test
    void verifyStay_missingSecret_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/verify-stay")
                       // No X-Internal-Secret header
                       .param("guestId", GUEST_ID.toString())
                       .param("hotelId", HOTEL_ID))
               .andExpect(status().isForbidden());
    }

    @Test
    void verifyStay_wrongSecret_returns403() throws Exception {
        // A past confirmed booking exists, but the secret is wrong — must not leak data
        saveBooking(ROOM_ID, HOTEL_ID, GUEST_ID, LocalDate.now()
                                                          .minusDays(10), LocalDate.now()
                                                                                   .minusDays(5),
                BookingStatus.CONFIRMED);

        mockMvc.perform(get("/api/v1/bookings/verify-stay").header("X-Internal-Secret", "totally-wrong-secret")
                                                           .param("guestId", GUEST_ID.toString())
                                                           .param("hotelId", HOTEL_ID))
               .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Must match app.internal-secret in application-test.yml
     */
    static final String TEST_INTERNAL_SECRET = "test-internal-secret";

    private BookingEntity saveBooking(String roomId,
                                      String hotelId,
                                      UUID guestId,
                                      LocalDate checkIn,
                                      LocalDate checkOut,
                                      BookingStatus status) {
        return saveBooking(roomId, hotelId, guestId, checkIn, checkOut, status, null);
    }

    private BookingEntity saveBooking(String roomId,
                                      String hotelId,
                                      UUID guestId,
                                      LocalDate checkIn,
                                      LocalDate checkOut,
                                      BookingStatus status,
                                      String managerId) {
        var entity = new BookingEntity();
        entity.setGuestId(guestId);
        entity.setGuestEmail("guest@test.com");
        entity.setHotelId(hotelId);
        entity.setRoomId(roomId);
        entity.setCheckIn(checkIn);
        entity.setCheckOut(checkOut);
        entity.setTotalPrice(300.0);
        entity.setStatus(status);
        entity.setManagerId(managerId);
        return bookingRepository.save(entity);
    }
}
