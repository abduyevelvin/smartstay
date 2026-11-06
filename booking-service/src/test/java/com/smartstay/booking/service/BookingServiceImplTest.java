package com.smartstay.booking.service;

import static com.smartstay.booking.enums.BookingStatus.CANCELLED;
import static com.smartstay.booking.enums.BookingStatus.CONFIRMED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
        hotelSummary = new HotelSummary(HOTEL_ID, "Grand Hotel", "Baku", "AZ");
        roomSummary = new RoomSummary(ROOM_ID, HOTEL_ID, 101, 150.0, 2, true);
        savedEntity = buildEntity(BOOKING_ID, GUEST_ID, EMAIL, HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, 450.0, CONFIRMED,
                null);
        bookingResponse = buildResponse(BOOKING_ID, GUEST_ID, EMAIL, HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, 450.0,
                CONFIRMED, null);
    }

    // ── createBooking ─────────────────────────────────────────────────────────

    @Test
    void createBooking_success_returnsResponse() {
        var request = new CreateBookingRequest(HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, null);

        when(hotelServiceClient.getHotel(HOTEL_ID)).thenReturn(hotelSummary);
        when(hotelServiceClient.getRoom(HOTEL_ID, ROOM_ID)).thenReturn(roomSummary);
        when(bookingRepository.existsOverlappingBooking(ROOM_ID, CHECK_IN, CHECK_OUT, CANCELLED)).thenReturn(false);
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
        when(bookingRepository.existsOverlappingBooking(any(), any(), any(), any())).thenReturn(false);
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
        when(bookingRepository.existsOverlappingBooking(ROOM_ID, CHECK_IN, CHECK_OUT, CANCELLED)).thenReturn(true);

        assertThatThrownBy(() -> bookingService.createBooking(request, GUEST_ID, EMAIL)).isInstanceOf(
                                                                                                BookingConflictException.class)
                                                                                        .hasMessageContaining(
                                                                                                "already booked");

        verify(bookingRepository).existsOverlappingBooking(eq(ROOM_ID), any(), any(), eq(CANCELLED));
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
                CANCELLED, null);
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
        var managerId = UUID.randomUUID(); // different from GUEST_ID
        var cancelledEntity = buildEntity(BOOKING_ID, GUEST_ID, EMAIL, HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, 450.0,
                CANCELLED, null);

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(savedEntity));
        when(hotelServiceClient.getHotel(HOTEL_ID)).thenReturn(hotelSummary);
        when(bookingRepository.save(savedEntity)).thenReturn(cancelledEntity);
        when(bookingMapper.toResponse(cancelledEntity)).thenReturn(
                buildResponse(BOOKING_ID, GUEST_ID, EMAIL, HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, 450.0, CANCELLED,
                        null));

        var result = bookingService.cancelBooking(BOOKING_ID, managerId, "HOTEL_MANAGER");

        assertThat(result.status()).isEqualTo(CANCELLED);
    }

    @Test
    void cancelBooking_asAdmin_succeeds() {
        var adminId = UUID.randomUUID();
        var cancelledEntity = buildEntity(BOOKING_ID, GUEST_ID, EMAIL, HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT, 450.0,
                CANCELLED, null);

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
                CANCELLED, null);
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
    void getHotelBookings_returnsMappedList() {
        when(bookingRepository.findByHotelId(HOTEL_ID)).thenReturn(List.of(savedEntity));
        when(bookingMapper.toListResponse(List.of(savedEntity))).thenReturn(List.of(bookingResponse));

        var result = bookingService.getHotelBookings(HOTEL_ID);

        assertThat(result).hasSize(1)
                          .containsExactly(bookingResponse);
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
                                     String specialRequests) {
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
