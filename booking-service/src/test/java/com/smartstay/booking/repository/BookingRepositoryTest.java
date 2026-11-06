package com.smartstay.booking.repository;

import static com.smartstay.booking.enums.BookingStatus.CANCELLED;
import static com.smartstay.booking.enums.BookingStatus.CONFIRMED;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.smartstay.booking.config.JpaConfig;
import com.smartstay.booking.entity.BookingEntity;
import com.smartstay.booking.enums.BookingStatus;

/**
 * Repository-slice test for {@link BookingRepository}.
 *
 * <p>Uses a real PostgreSQL container (via Testcontainers) so that the custom
 * JPQL overlap query is validated against the actual PostgreSQL dialect.
 * Flyway is disabled — Hibernate creates the schema via {@code create-drop}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class BookingRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    BookingRepository bookingRepository;

    static final String ROOM_ID = "room-001";
    static final String HOTEL_ID = "hotel-001";

    // 2026-12-10 – 2026-12-15  (existing booking window)
    static final LocalDate EXISTING_CHECK_IN = LocalDate.of(2026, 12, 10);
    static final LocalDate EXISTING_CHECK_OUT = LocalDate.of(2026, 12, 15);

    @BeforeEach
    void cleanUp() {
        bookingRepository.deleteAll();
    }

    // ── existsOverlappingBooking ───────────────────────────────────────────────

    @Test
    void existsOverlappingBooking_noExistingBookings_returnsFalse() {
        var result = bookingRepository.existsOverlappingBooking(ROOM_ID, LocalDate.of(2026, 12, 10),
                LocalDate.of(2026, 12, 15), CANCELLED);

        assertThat(result).isFalse();
    }

    @Test
    void existsOverlappingBooking_fullyOverlapping_returnsTrue() {
        saveBooking(ROOM_ID, HOTEL_ID, EXISTING_CHECK_IN, EXISTING_CHECK_OUT, CONFIRMED);

        // Query window: 12-11 to 12-14 — fully inside existing booking
        var result = bookingRepository.existsOverlappingBooking(ROOM_ID, LocalDate.of(2026, 12, 11),
                LocalDate.of(2026, 12, 14), CANCELLED);

        assertThat(result).isTrue();
    }

    @Test
    void existsOverlappingBooking_partialOverlapAtStart_returnsTrue() {
        saveBooking(ROOM_ID, HOTEL_ID, EXISTING_CHECK_IN, EXISTING_CHECK_OUT, CONFIRMED);

        // Query window: 12-08 to 12-12 — starts before, ends inside
        var result = bookingRepository.existsOverlappingBooking(ROOM_ID, LocalDate.of(2026, 12, 8),
                LocalDate.of(2026, 12, 12), CANCELLED);

        assertThat(result).isTrue();
    }

    @Test
    void existsOverlappingBooking_partialOverlapAtEnd_returnsTrue() {
        saveBooking(ROOM_ID, HOTEL_ID, EXISTING_CHECK_IN, EXISTING_CHECK_OUT, CONFIRMED);

        // Query window: 12-13 to 12-17 — starts inside, ends after
        var result = bookingRepository.existsOverlappingBooking(ROOM_ID, LocalDate.of(2026, 12, 13),
                LocalDate.of(2026, 12, 17), CANCELLED);

        assertThat(result).isTrue();
    }

    @Test
    void existsOverlappingBooking_adjacentDates_returnsFalse() {
        saveBooking(ROOM_ID, HOTEL_ID, EXISTING_CHECK_IN, EXISTING_CHECK_OUT, CONFIRMED);

        // Query window: 12-15 to 12-18 — starts exactly on checkout of existing booking (no overlap)
        var result = bookingRepository.existsOverlappingBooking(ROOM_ID, LocalDate.of(2026, 12, 15),
                LocalDate.of(2026, 12, 18), CANCELLED);

        assertThat(result).isFalse();
    }

    @Test
    void existsOverlappingBooking_cancelledBookingIgnored_returnsFalse() {
        // Save a CANCELLED booking — it must be excluded from the overlap check
        saveBooking(ROOM_ID, HOTEL_ID, EXISTING_CHECK_IN, EXISTING_CHECK_OUT, CANCELLED);

        var result = bookingRepository.existsOverlappingBooking(ROOM_ID, LocalDate.of(2026, 12, 10),
                LocalDate.of(2026, 12, 15), CANCELLED);

        assertThat(result).isFalse();
    }

    @Test
    void existsOverlappingBooking_differentRoom_returnsFalse() {
        saveBooking(ROOM_ID, HOTEL_ID, EXISTING_CHECK_IN, EXISTING_CHECK_OUT, CONFIRMED);

        // Same dates but different room
        var result = bookingRepository.existsOverlappingBooking("room-002", EXISTING_CHECK_IN, EXISTING_CHECK_OUT,
                CANCELLED);

        assertThat(result).isFalse();
    }

    // ── findByGuestId ─────────────────────────────────────────────────────────

    @Test
    void findByGuestId_returnsPaginatedResults() {
        var guestA = UUID.randomUUID();
        var guestB = UUID.randomUUID();

        saveBookingForGuest(guestA, ROOM_ID, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3));
        saveBookingForGuest(guestA, ROOM_ID, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 5));
        saveBookingForGuest(guestB, ROOM_ID, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 4));

        var page = bookingRepository.findByGuestId(guestA, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(b -> guestA.equals(b.getGuestId()));
    }

    @Test
    void findByGuestId_secondPage_returnsCorrectSlice() {
        var guestId = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            saveBookingForGuest(guestId, "room-" + i, LocalDate.of(2027, 1, 1 + i * 3),
                    LocalDate.of(2027, 1, 3 + i * 3));
        }

        var page = bookingRepository.findByGuestId(guestId, PageRequest.of(1, 3));

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getContent()).hasSize(2); // 5 total, page 1 (0-indexed) with size 3 → 2 remaining
    }

    // ── findByHotelId ─────────────────────────────────────────────────────────

    @Test
    void findByHotelId_returnsAllBookingsForHotel() {
        saveBooking("room-1", HOTEL_ID, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 4), CONFIRMED);
        saveBooking("room-2", HOTEL_ID, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 4), CONFIRMED);
        saveBooking("room-1", "other-hotel", LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 4), CONFIRMED);

        var results = bookingRepository.findByHotelId(HOTEL_ID);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(b -> HOTEL_ID.equals(b.getHotelId()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BookingEntity saveBooking(String roomId,
                                      String hotelId,
                                      LocalDate checkIn,
                                      LocalDate checkOut,
                                      BookingStatus status) {
        var entity = new BookingEntity();
        entity.setGuestId(UUID.randomUUID());
        entity.setGuestEmail("guest@test.com");
        entity.setHotelId(hotelId);
        entity.setRoomId(roomId);
        entity.setCheckIn(checkIn);
        entity.setCheckOut(checkOut);
        entity.setTotalPrice(300.0);
        entity.setStatus(status);
        return bookingRepository.save(entity);
    }

    private BookingEntity saveBookingForGuest(UUID guestId,
                                              String roomId,
                                              LocalDate checkIn,
                                              LocalDate checkOut) {
        var entity = new BookingEntity();
        entity.setGuestId(guestId);
        entity.setGuestEmail("guest@test.com");
        entity.setHotelId(HOTEL_ID);
        entity.setRoomId(roomId);
        entity.setCheckIn(checkIn);
        entity.setCheckOut(checkOut);
        entity.setTotalPrice(200.0);
        entity.setStatus(CONFIRMED);
        return bookingRepository.save(entity);
    }
}
