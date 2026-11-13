package com.smartstay.booking.service;

import static com.smartstay.booking.enums.BookingStatus.CANCELLED;
import static com.smartstay.booking.enums.BookingStatus.CONFIRMED;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartstay.booking.client.HotelServiceClient;
import com.smartstay.booking.client.dto.HotelSummary;
import com.smartstay.booking.client.dto.RoomSummary;
import com.smartstay.booking.dto.BookingResponse;
import com.smartstay.booking.dto.CreateBookingRequest;
import com.smartstay.booking.entity.BookingEntity;
import com.smartstay.booking.enums.BookingStatus;
import com.smartstay.booking.event.BookingCancelledEvent;
import com.smartstay.booking.event.BookingConfirmedEvent;
import com.smartstay.booking.event.BookingEventProducer;
import com.smartstay.booking.exceptions.BookingConflictException;
import com.smartstay.booking.exceptions.ForbiddenException;
import com.smartstay.booking.exceptions.ResourceNotFoundException;
import com.smartstay.booking.mapper.BookingMapper;
import com.smartstay.booking.repository.BookingRepository;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final HotelServiceClient hotelServiceClient;
    private final BookingEventProducer eventProducer;
    private final BookingMapper bookingMapper;

    @Transactional
    @Override
    public BookingResponse createBooking(final CreateBookingRequest request,
                                         final UUID guestId,
                                         final String guestEmail) {

        log.debug("Creating booking — guestId={}, hotelId={}, roomId={}, checkIn={}, checkOut={}", guestId,
                request.hotelId(), request.roomId(), request.checkIn(), request.checkOut());

        final var hotel = getHotelOrThrow(request.hotelId());
        final var room = getRoomOrThrow(request.hotelId(), request.roomId());

        if (!room.available()) {
            log.warn("Room is disabled — hotelId={}, roomId={}", request.hotelId(), request.roomId());
            throw new BookingConflictException("Room is not available for booking");
        }

        // Locking overlap check: acquires SELECT … FOR UPDATE on any conflicting rows.
        // A second concurrent transaction will block here until this one commits,
        // preventing two requests from both passing the "no overlap" gate simultaneously.
        final var overlapping = bookingRepository.findOverlappingForUpdate(request.roomId(), request.checkIn(),
                request.checkOut(), CANCELLED);
        if (!overlapping.isEmpty()) {
            log.warn("Overlapping booking detected — roomId={}, checkIn={}, checkOut={}", request.roomId(),
                    request.checkIn(), request.checkOut());
            throw new BookingConflictException(
                    "Room is already booked for the requested dates: " + request.checkIn() + " – "
                            + request.checkOut());
        }

        final long nights = ChronoUnit.DAYS.between(request.checkIn(), request.checkOut());
        final double totalPrice = room.pricePerNight() * nights;

        final var booking = new BookingEntity();
        booking.setGuestId(guestId);
        booking.setGuestEmail(guestEmail);
        booking.setHotelId(request.hotelId());
        booking.setRoomId(request.roomId());
        booking.setCheckIn(request.checkIn());
        booking.setCheckOut(request.checkOut());
        booking.setTotalPrice(totalPrice);
        booking.setStatus(CONFIRMED);
        booking.setSpecialRequests(request.specialRequests());
        booking.setManagerId(hotel.managerId());   // snapshot for cancel/ownership checks

        try {
            final var saved = bookingRepository.save(booking);
            log.info("Booking created — id={}, guestId={}, hotelId={}, roomId={}", saved.getId(), guestId,
                    request.hotelId(), request.roomId());

            final var event = new BookingConfirmedEvent(saved.getId(), guestEmail, hotel.name(), request.checkIn(),
                    request.checkOut());
            eventProducer.publishBookingConfirmed(event);

            return bookingMapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            // Safety net: the DB EXCLUDE constraint fired (e.g. a concurrent transaction
            // slipped through before the lock was acquired).  Surface a clean 409.
            log.warn("DB exclude constraint fired — roomId={}, checkIn={}, checkOut={} (concurrent race)",
                    request.roomId(), request.checkIn(), request.checkOut());
            throw new BookingConflictException(
                    "Room is already booked for the requested dates: " + request.checkIn() + " – "
                            + request.checkOut());
        }
    }

    private RoomSummary getRoomOrThrow(String hotelId,
                                       String roomId) {
        try {
            return hotelServiceClient.getRoom(hotelId, roomId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Room not found with id: " + roomId + " in hotel: " + hotelId);
        }
    }

    @Override
    public BookingResponse cancelBooking(final UUID bookingId,
                                         final UUID userId,
                                         final String userRole) {

        final var booking = bookingRepository.findById(bookingId)
                                             .orElseThrow(() -> new ResourceNotFoundException(
                                                     "Booking not found with id: " + bookingId));

        if (booking.getStatus() == CANCELLED) {
            log.warn("Booking already cancelled: id={}", bookingId);
            throw new BookingConflictException("Booking already cancelled");
        }

        final boolean isGuestOwner = booking.getGuestId()
                                            .equals(userId);
        final boolean isAdmin = "ADMIN".equals(userRole);
        // Manager can only cancel bookings at hotels they manage (managerId stored at creation)
        final boolean isHotelOwner =
                "HOTEL_MANAGER".equals(userRole) && booking.getManagerId() != null && booking.getManagerId()
                                                                                             .equals(userId.toString());

        if (!isGuestOwner && !isAdmin && !isHotelOwner) {
            log.warn("Unauthorized cancel attempt — userId={}, role={}, bookingId={}", userId, userRole, bookingId);
            throw new ForbiddenException("You are not authorized to cancel this booking");
        }

        final var hotel = getHotelOrThrow(booking.getHotelId());
        booking.setStatus(CANCELLED);
        final var saved = bookingRepository.save(booking);
        log.info("Booking cancelled — id={}, userId={}", bookingId, userId);

        final var cancelEvent = new BookingCancelledEvent(bookingId, booking.getGuestEmail(), hotel.name(),
                booking.getCheckIn(), booking.getCheckOut());
        eventProducer.publishBookingCancelled(cancelEvent);

        return bookingMapper.toResponse(saved);
    }

    private HotelSummary getHotelOrThrow(String hotelId) {
        try {
            return hotelServiceClient.getHotel(hotelId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Hotel not found with id: " + hotelId);
        }
    }

    @Override
    public Page<BookingResponse> getMyBookings(final UUID guestId,
                                               final Pageable pageable) {
        log.debug("Fetching bookings for guestId={}", guestId);

        return bookingRepository.findByGuestId(guestId, pageable)
                                .map(bookingMapper::toResponse);
    }

    @Override
    public List<BookingResponse> getHotelBookings(final String hotelId,
                                                  final BookingStatus status,
                                                  final UUID principalId,
                                                  final String userRole) {
        log.debug("Fetching bookings for hotelId={}, role={}", hotelId, userRole);

        if ("HOTEL_MANAGER".equals(userRole)) {
            final var hotel = getHotelOrThrow(hotelId);
            if (!principalId.toString()
                            .equals(hotel.managerId())) {
                log.warn("Unauthorized hotel-bookings read — principalId={}, hotelId={}", principalId, hotelId);
                throw new ForbiddenException("You are not authorized to view bookings for this hotel");
            }
        }

        final var bookings = (status != null) ? bookingRepository.findByHotelIdAndStatus(hotelId, status) :
                bookingRepository.findByHotelId(hotelId);
        return bookingMapper.toListResponse(bookings);
    }

    @Override
    public boolean checkRoomAvailability(final String roomId,
                                         final LocalDate checkIn,
                                         final LocalDate checkOut) {
        log.debug("Checking room availability for roomId={}, checkIn={}, checkOut={}", roomId, checkIn, checkOut);

        return !bookingRepository.existsOverlappingBooking(roomId, checkIn, checkOut, CANCELLED);
    }

    @Override
    public Map<String, Boolean> checkBulkAvailability(final List<String> roomIds,
                                                      final LocalDate checkIn,
                                                      final LocalDate checkOut) {
        log.debug("Bulk availability check — {} rooms, checkIn={}, checkOut={}", roomIds.size(), checkIn, checkOut);

        if (roomIds.isEmpty()) {
            log.info("No rooms to check availability for");
            return Map.of();
        }

        final Set<String> bookedIds = bookingRepository.findBookedRoomIds(roomIds, checkIn, checkOut, CANCELLED);

        return roomIds.stream()
                      .collect(Collectors.toMap(id -> id, id -> !bookedIds.contains(id)));
    }

    @Override
    public boolean guestIsStayed(final UUID guestId,
                                 final String hotelId) {

        return bookingRepository.findFirstByGuestIdAndHotelIdAndStatusNotAndCheckOutLessThanEqual(guestId, hotelId,
                                        CANCELLED, LocalDate.now())
                                .isPresent();
    }

    @Transactional
    @Override
    public void cancelAllActiveBookingsForHotel(final String hotelId,
                                                final String hotelName) {
        final var activeBookings = bookingRepository.findByHotelIdAndStatusNot(hotelId, CANCELLED);
        if (activeBookings.isEmpty()) {
            log.info("No active bookings to cancel for deleted hotel hotelId={}", hotelId);
            return;
        }

        for (final var booking : activeBookings) {
            booking.setStatus(CANCELLED);
            bookingRepository.save(booking);
            eventProducer.publishBookingCancelled(
                    new BookingCancelledEvent(booking.getId(), booking.getGuestEmail(), hotelName, booking.getCheckIn(),
                            booking.getCheckOut()));
        }

        log.info("Cancelled {} active booking(s) for deleted hotel hotelId={}", activeBookings.size(), hotelId);
    }
}
