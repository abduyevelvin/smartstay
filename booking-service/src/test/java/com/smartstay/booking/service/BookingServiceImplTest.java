package com.smartstay.booking.service;

import static com.smartstay.booking.enums.BookingStatus.CANCELLED;
import static com.smartstay.booking.enums.BookingStatus.CONFIRMED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.smartstay.booking.client.HotelServiceClient;
import com.smartstay.booking.client.dto.HotelSummary;
import com.smartstay.booking.client.dto.RoomSummary;
import com.smartstay.booking.dto.BookingResponse;
import com.smartstay.booking.dto.CreateBookingRequest;
import com.smartstay.booking.entity.BookingEntity;
import com.smartstay.booking.enums.BookingStatus;
import com.smartstay.booking.event.BookingEventProducer;
import com.smartstay.booking.exceptions.BookingConflictException;
import com.smartstay.booking.exceptions.ForbiddenException;
import com.smartstay.booking.exceptions.ResourceNotFoundException;
import com.smartstay.booking.mapper.BookingMapper;
import com.smartstay.booking.repository.BookingRepository;

import feign.FeignException;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    BookingRepository bookingRepository;
    @Mock
    HotelServiceClient hotelServiceClient;
    @Mock
    BookingEventProducer eventProducer;
    @Mock
    BookingMapper bookingMapper;

    @InjectMocks
    BookingServiceImpl bookingService;

    // ── Fixtures ─────────────────────────────────────────────────────────────

    static final UUID GUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID BOOKING_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID MANAGER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final String HOTEL_ID = "hotel-abc";
    static final String ROOM_ID = "room-xyz";
    static final String EMAIL = "guest@smartstay.test";

    static final LocalDate CHECK_IN = LocalDate.now()
                                               .plusDays(5);
    static final LocalDate CHECK_OUT = LocalDate.now()
                                                .plusDays(8);   // 3 nights

    HotelSummary hotelSummary;
    RoomSummary roomSummary;
    BookingEntity savedEntity;
    BookingResponse bookingResponse;

    @BeforeEach
    void setUp() {
        hotelSummary = new HotelSummary(HOTEL_ID, "Grand Hotel", "Baku", "AZ", MANAGER_ID.toString());
        roomSummary = new RoomSummary(ROOM_ID, HOTEL_ID, 101, 150.0, 2, true);
        savedEntity = buildEntity(BOOKING_ID, GUEST_ID, EMAIL, HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, 450.0, CONFIRMED,
                null, MANAGER_ID.toString());
        bookingResponse = buildResponse(BOOKING_ID, GUEST_ID, EMAIL, HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, 450.0,
                CONFIRMED, null);
    }

    // ── createBooking ─────────────────────────────────────────────────────────

    @Test
    void createBooking_success_returnsResponse() {
        var request = new CreateBookingRequest(HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, null);

        when(hotelServiceClient.getHotel(HOTEL_ID)).thenReturn(hotelSummary);
        when(hotelServiceClient.getRoom(HOTEL_ID, ROOM_ID)).thenReturn(roomSummary);
        when(bookingRepository.findOverlappingForUpdate(ROOM_ID, CHECK_IN, CHECK_OUT, CANCELLED)).thenReturn(List.of());
        when(bookingRepository.save(any())).thenReturn(savedEntity);
        when(bookingMapper.toResponse(savedEntity)).thenReturn(bookingResponse);

        var result = bookingService.createBooking(request, GUEST_ID, EMAIL);

        assertThat(result).isEqualTo(bookingResponse);
        verify(eventProducer).publishBookingConfirmed(any());
    }

    @Test
    void createBooking_calculatesTotalPriceByNights() {
        // 3 nights × $150 = $450
        var request = new CreateBookingRequest(HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, "Late check-in");

        when(hotelServiceClient.getHotel(HOTEL_ID)).thenReturn(hotelSummary);
        when(hotelServiceClient.getRoom(HOTEL_ID, ROOM_ID)).thenReturn(roomSummary);
        when(bookingRepository.findOverlappingForUpdate(any(), any(), any(), any())).thenReturn(List.of());
        when(bookingRepository.save(any())).thenReturn(savedEntity);
        when(bookingMapper.toResponse(savedEntity)).thenReturn(bookingResponse);

        bookingService.createBooking(request, GUEST_ID, EMAIL);

        verify(bookingRepository).save(
                argThat(e -> e.getTotalPrice() == 450.0 && "Late check-in".equals(e.getSpecialRequests())
                        && e.getStatus() == CONFIRMED && GUEST_ID.equals(e.getGuestId())));
    }

    @Test
    void createBooking_roomDisabled_throwsConflict() {
        var unavailableRoom = new RoomSummary(ROOM_ID, HOTEL_ID, 101, 150.0, 2, false);
        var request = new CreateBookingRequest(HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, null);

        when(hotelServiceClient.getHotel(HOTEL_ID)).thenReturn(hotelSummary);
        when(hotelServiceClient.getRoom(HOTEL_ID, ROOM_ID)).thenReturn(unavailableRoom);

        assertThatThrownBy(() -> bookingService.createBooking(request, GUEST_ID, EMAIL)).isInstanceOf(
                                                                                                BookingConflictException.class)
                                                                                        .hasMessageContaining(
                                                                                                "not available");

        verifyNoInteractions(bookingRepository);
        verifyNoInteractions(eventProducer);
    }

    @Test
    void createBooking_overlappingDates_throwsConflict() {
        var request = new CreateBookingRequest(HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, null);

        when(hotelServiceClient.getHotel(HOTEL_ID)).thenReturn(hotelSummary);
        when(hotelServiceClient.getRoom(HOTEL_ID, ROOM_ID)).thenReturn(roomSummary);
        // Locking query returns an existing booking → conflict
        when(bookingRepository.findOverlappingForUpdate(ROOM_ID, CHECK_IN, CHECK_OUT, CANCELLED)).thenReturn(
                List.of(savedEntity));

        assertThatThrownBy(() -> bookingService.createBooking(request, GUEST_ID, EMAIL)).isInstanceOf(
                                                                                                BookingConflictException.class)
                                                                                        .hasMessageContaining(
                                                                                                "already booked");

        verify(bookingRepository).findOverlappingForUpdate(eq(ROOM_ID), any(), any(), eq(CANCELLED));
        verifyNoInteractions(eventProducer);
    }

    @Test
    void createBooking_dbConstraintFires_throwsConflict() {
        // Simulates the safety-net path: locking query returns empty (extremely narrow
        // race window), but the DB EXCLUDE constraint fires on INSERT.
        var request = new CreateBookingRequest(HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, null);

        when(hotelServiceClient.getHotel(HOTEL_ID)).thenReturn(hotelSummary);
        when(hotelServiceClient.getRoom(HOTEL_ID, ROOM_ID)).thenReturn(roomSummary);
        when(bookingRepository.findOverlappingForUpdate(any(), any(), any(), any())).thenReturn(List.of());
        when(bookingRepository.save(any())).thenThrow(
                new DataIntegrityViolationException("excl_room_dates_no_overlap"));

        assertThatThrownBy(() -> bookingService.createBooking(request, GUEST_ID, EMAIL)).isInstanceOf(
                                                                                                BookingConflictException.class)
                                                                                        .hasMessageContaining(
                                                                                                "already booked");

        verifyNoInteractions(eventProducer);
    }

    @Test
    void createBooking_hotelNotFound_throwsResourceNotFound() {
        var request = new CreateBookingRequest(HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, null);
        when(hotelServiceClient.getHotel(HOTEL_ID)).thenThrow(mock(FeignException.NotFound.class));

        assertThatThrownBy(() -> bookingService.createBooking(request, GUEST_ID, EMAIL)).isInstanceOf(
                                                                                                ResourceNotFoundException.class)
                                                                                        .hasMessageContaining(
                                                                                                "Hotel not found");

        verifyNoInteractions(bookingRepository);
    }

    @Test
    void createBooking_roomNotFound_throwsResourceNotFound() {
        var request = new CreateBookingRequest(HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, null);

        when(hotelServiceClient.getHotel(HOTEL_ID)).thenReturn(hotelSummary);
        when(hotelServiceClient.getRoom(HOTEL_ID, ROOM_ID)).thenThrow(mock(FeignException.NotFound.class));

        assertThatThrownBy(() -> bookingService.createBooking(request, GUEST_ID, EMAIL)).isInstanceOf(
                                                                                                ResourceNotFoundException.class)
                                                                                        .hasMessageContaining(
                                                                                                "Room not found");

        verifyNoInteractions(bookingRepository);
    }

    // ── cancelBooking ─────────────────────────────────────────────────────────

    @Test
    void cancelBooking_asBookingOwner_returnsCancelledResponse() {
        var cancelledEntity = buildEntity(BOOKING_ID, GUEST_ID, EMAIL, HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, 450.0,
                CANCELLED, null, null);
        var cancelledResponse = buildResponse(BOOKING_ID, GUEST_ID, EMAIL, HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT,
                450.0, CANCELLED, null);

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(savedEntity));
        when(hotelServiceClient.getHotel(HOTEL_ID)).thenReturn(hotelSummary);
        when(bookingRepository.save(savedEntity)).thenReturn(cancelledEntity);
        when(bookingMapper.toResponse(cancelledEntity)).thenReturn(cancelledResponse);

        var result = bookingService.cancelBooking(BOOKING_ID, GUEST_ID, "GUEST");

        assertThat(result.status()).isEqualTo(CANCELLED);
        verify(eventProducer).publishBookingCancelled(any());
    }

    @Test
    void cancelBooking_asHotelManager_succeeds() {
        // MANAGER_ID matches savedEntity.managerId set in setUp()
        var cancelledEntity = buildEntity(BOOKING_ID, GUEST_ID, EMAIL, HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, 450.0,
                CANCELLED, null, MANAGER_ID.toString());

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(savedEntity));
        when(hotelServiceClient.getHotel(HOTEL_ID)).thenReturn(hotelSummary);
        when(bookingRepository.save(savedEntity)).thenReturn(cancelledEntity);
        when(bookingMapper.toResponse(cancelledEntity)).thenReturn(
                buildResponse(BOOKING_ID, GUEST_ID, EMAIL, HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, 450.0, CANCELLED,
                        null));

        var result = bookingService.cancelBooking(BOOKING_ID, MANAGER_ID, "HOTEL_MANAGER");

        assertThat(result.status()).isEqualTo(CANCELLED);
    }

    @Test
    void cancelBooking_asHotelManagerOfDifferentHotel_throwsForbidden() {
        // Booking belongs to MANAGER_ID; a different manager tries to cancel
        var differentManager = UUID.randomUUID();
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(savedEntity));

        assertThatThrownBy(
                () -> bookingService.cancelBooking(BOOKING_ID, differentManager, "HOTEL_MANAGER")).isInstanceOf(
                                                                                                          ForbiddenException.class)
                                                                                                  .hasMessageContaining(
                                                                                                          "not authorized");

        verifyNoInteractions(eventProducer);
    }

    @Test
    void cancelBooking_asAdmin_succeeds() {
        var adminId = UUID.randomUUID();
        var cancelledEntity = buildEntity(BOOKING_ID, GUEST_ID, EMAIL, HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, 450.0,
                CANCELLED, null, null);

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(savedEntity));
        when(hotelServiceClient.getHotel(HOTEL_ID)).thenReturn(hotelSummary);
        when(bookingRepository.save(savedEntity)).thenReturn(cancelledEntity);
        when(bookingMapper.toResponse(cancelledEntity)).thenReturn(
                buildResponse(BOOKING_ID, GUEST_ID, EMAIL, HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, 450.0, CANCELLED,
                        null));

        var result = bookingService.cancelBooking(BOOKING_ID, adminId, "ADMIN");

        assertThat(result.status()).isEqualTo(CANCELLED);
    }

    @Test
    void cancelBooking_bookingNotFound_throwsResourceNotFound() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelBooking(BOOKING_ID, GUEST_ID, "GUEST")).isInstanceOf(
                                                                                                     ResourceNotFoundException.class)
                                                                                             .hasMessageContaining(
                                                                                                     "Booking not found");
    }

    @Test
    void cancelBooking_alreadyCancelled_throwsConflict() {
        var alreadyCancelled = buildEntity(BOOKING_ID, GUEST_ID, EMAIL, HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, 450.0,
                CANCELLED, null, null);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(alreadyCancelled));

        assertThatThrownBy(() -> bookingService.cancelBooking(BOOKING_ID, GUEST_ID, "GUEST")).isInstanceOf(
                                                                                                     BookingConflictException.class)
                                                                                             .hasMessageContaining(
                                                                                                     "already cancelled");

        verifyNoInteractions(eventProducer);
    }

    @Test
    void cancelBooking_differentGuestNoPrivilegedRole_throwsForbidden() {
        var otherGuestId = UUID.randomUUID();
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(savedEntity));

        assertThatThrownBy(() -> bookingService.cancelBooking(BOOKING_ID, otherGuestId, "GUEST")).isInstanceOf(
                                                                                                         ForbiddenException.class)
                                                                                                 .hasMessageContaining(
                                                                                                         "not authorized");

        verifyNoInteractions(eventProducer);
    }

    // ── getMyBookings ─────────────────────────────────────────────────────────

    @Test
    void getMyBookings_returnsMappedPage() {
        var pageable = PageRequest.of(0, 20);
        when(bookingRepository.findByGuestId(GUEST_ID, pageable)).thenReturn(new PageImpl<>(List.of(savedEntity)));
        when(bookingMapper.toResponse(savedEntity)).thenReturn(bookingResponse);

        var result = bookingService.getMyBookings(GUEST_ID, pageable);

        assertThat(result.getContent()).hasSize(1)
                                       .containsExactly(bookingResponse);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ── getHotelBookings ──────────────────────────────────────────────────────

    @Test
    void getHotelBookings_asOwningManager_noStatusFilter_returnsMappedList() {
        when(hotelServiceClient.getHotel(HOTEL_ID)).thenReturn(hotelSummary); // managerId = MANAGER_ID
        when(bookingRepository.findByHotelId(HOTEL_ID)).thenReturn(List.of(savedEntity));
        when(bookingMapper.toListResponse(List.of(savedEntity))).thenReturn(List.of(bookingResponse));

        var result = bookingService.getHotelBookings(HOTEL_ID, null, MANAGER_ID, "HOTEL_MANAGER");

        assertThat(result).hasSize(1)
                          .containsExactly(bookingResponse);
        verify(bookingRepository).findByHotelId(HOTEL_ID);
    }

    @Test
    void getHotelBookings_asOwningManager_withStatusFilter_delegatesToFilteredQuery() {
        when(hotelServiceClient.getHotel(HOTEL_ID)).thenReturn(hotelSummary);
        when(bookingRepository.findByHotelIdAndStatus(HOTEL_ID, CONFIRMED)).thenReturn(List.of(savedEntity));
        when(bookingMapper.toListResponse(List.of(savedEntity))).thenReturn(List.of(bookingResponse));

        var result = bookingService.getHotelBookings(HOTEL_ID, CONFIRMED, MANAGER_ID, "HOTEL_MANAGER");

        assertThat(result).hasSize(1)
                          .containsExactly(bookingResponse);
        verify(bookingRepository).findByHotelIdAndStatus(HOTEL_ID, CONFIRMED);
    }

    @Test
    void getHotelBookings_asManagerOfDifferentHotel_throwsForbidden() {
        var differentManager = UUID.randomUUID();
        when(hotelServiceClient.getHotel(HOTEL_ID)).thenReturn(hotelSummary); // managerId = MANAGER_ID

        assertThatThrownBy(
                () -> bookingService.getHotelBookings(HOTEL_ID, null, differentManager, "HOTEL_MANAGER")).isInstanceOf(
                                                                                                                 ForbiddenException.class)
                                                                                                         .hasMessageContaining(
                                                                                                                 "not authorized");

        verifyNoInteractions(bookingRepository);
    }

    // ── checkRoomAvailability ─────────────────────────────────────────────────

    @Test
    void checkRoomAvailability_noOverlap_returnsTrue() {
        when(bookingRepository.existsOverlappingBooking(ROOM_ID, CHECK_IN, CHECK_OUT, CANCELLED)).thenReturn(false);

        assertThat(bookingService.checkRoomAvailability(ROOM_ID, CHECK_IN, CHECK_OUT)).isTrue();
    }

    @Test
    void checkRoomAvailability_overlappingBooking_returnsFalse() {
        when(bookingRepository.existsOverlappingBooking(ROOM_ID, CHECK_IN, CHECK_OUT, CANCELLED)).thenReturn(true);

        assertThat(bookingService.checkRoomAvailability(ROOM_ID, CHECK_IN, CHECK_OUT)).isFalse();
    }

    // ── guestIsStayed ─────────────────────────────────────────────────────────

    @Test
    void guestIsStayed_hasPastCompletedBooking_returnsTrue() {
        when(bookingRepository.findFirstByGuestIdAndHotelIdAndStatusNotAndCheckOutLessThanEqual(eq(GUEST_ID),
                eq(HOTEL_ID), eq(CANCELLED), any(LocalDate.class))).thenReturn(Optional.of(savedEntity));

        assertThat(bookingService.guestIsStayed(GUEST_ID, HOTEL_ID)).isTrue();
    }

    @Test
    void guestIsStayed_noPastBooking_returnsFalse() {
        when(bookingRepository.findFirstByGuestIdAndHotelIdAndStatusNotAndCheckOutLessThanEqual(eq(GUEST_ID),
                eq(HOTEL_ID), eq(CANCELLED), any(LocalDate.class))).thenReturn(Optional.empty());

        assertThat(bookingService.guestIsStayed(GUEST_ID, HOTEL_ID)).isFalse();
    }

    // ── cancelAllActiveBookingsForHotel ───────────────────────────────────────

    @Test
    void cancelAllActiveBookingsForHotel_cancelsAllAndPublishesEvents() {
        var b1 = buildEntity(UUID.randomUUID(), GUEST_ID, EMAIL, HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, 450.0,
                CONFIRMED, null, MANAGER_ID.toString());
        var b2 = buildEntity(UUID.randomUUID(), UUID.randomUUID(), "other@test.com", HOTEL_ID, "room-2",
                CHECK_IN.plusDays(5), CHECK_OUT.plusDays(5), 300.0, CONFIRMED, null, MANAGER_ID.toString());

        when(bookingRepository.findByHotelIdAndStatusNot(HOTEL_ID, CANCELLED)).thenReturn(List.of(b1, b2));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        bookingService.cancelAllActiveBookingsForHotel(HOTEL_ID, "Grand Hotel");

        assertThat(b1.getStatus()).isEqualTo(CANCELLED);
        assertThat(b2.getStatus()).isEqualTo(CANCELLED);
        verify(bookingRepository, times(2)).save(any());
        verify(eventProducer, times(2)).publishBookingCancelled(any());
    }

    @Test
    void cancelAllActiveBookingsForHotel_noActiveBookings_doesNothing() {
        when(bookingRepository.findByHotelIdAndStatusNot(HOTEL_ID, CANCELLED)).thenReturn(List.of());

        bookingService.cancelAllActiveBookingsForHotel(HOTEL_ID, "Grand Hotel");

        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(eventProducer);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static BookingEntity buildEntity(UUID id,
                                     UUID guestId,
                                     String email,
                                     String hotelId,
                                     String roomId,
                                     LocalDate checkIn,
                                     LocalDate checkOut,
                                     double price,
                                     BookingStatus status,
                                     String specialRequests,
                                     String managerId) {
        var entity = new BookingEntity();
        entity.setId(id);
        entity.setGuestId(guestId);
        entity.setGuestEmail(email);
        entity.setHotelId(hotelId);
        entity.setRoomId(roomId);
        entity.setCheckIn(checkIn);
        entity.setCheckOut(checkOut);
        entity.setTotalPrice(price);
        entity.setStatus(status);
        entity.setSpecialRequests(specialRequests);
        entity.setManagerId(managerId);
        return entity;
    }

    static BookingResponse buildResponse(UUID id,
                                         UUID guestId,
                                         String email,
                                         String hotelId,
                                         String roomId,
                                         LocalDate checkIn,
                                         LocalDate checkOut,
                                         double price,
                                         BookingStatus status,
                                         String specialRequests) {
        return new BookingResponse(id, guestId, email, hotelId, roomId, checkIn, checkOut, price, status,
                specialRequests, OffsetDateTime.now(), OffsetDateTime.now());
    }
}
