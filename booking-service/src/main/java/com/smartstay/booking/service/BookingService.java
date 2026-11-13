package com.smartstay.booking.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.smartstay.booking.dto.BookingResponse;
import com.smartstay.booking.dto.CreateBookingRequest;
import com.smartstay.booking.enums.BookingStatus;

public interface BookingService {

    BookingResponse createBooking(CreateBookingRequest request,
                                  UUID guestId,
                                  String guestEmail);

    BookingResponse cancelBooking(UUID bookingId,
                                  UUID userId,
                                  String userRole);

    Page<BookingResponse> getMyBookings(UUID guestId,
                                        Pageable pageable);

    List<BookingResponse> getHotelBookings(String hotelId,
                                           BookingStatus status,
                                           UUID principalId,
                                           String userRole);

    boolean checkRoomAvailability(String roomId,
                                  LocalDate checkIn,
                                  LocalDate checkOut);

    /**
     * Bulk availability check for multiple rooms over the same date range.
     * Returns a map of roomId → available (true/false).
     * Used by hotel-service to power the availability search endpoint.
     */
    Map<String, Boolean> checkBulkAvailability(List<String> roomIds,
                                               LocalDate checkIn,
                                               LocalDate checkOut);

    boolean guestIsStayed(UUID guestId,
                          String hotelId);

    /**
     * Cancels every non-cancelled booking for the given hotel and publishes a
     * {@code booking.cancelled} event for each, triggering guest notification emails.
     * Called when a {@code hotel.deleted} Kafka event is received.
     *
     * @param hotelId   the deleted hotel's ID
     * @param hotelName the deleted hotel's name (used in notification emails)
     */
    void cancelAllActiveBookingsForHotel(String hotelId,
                                         String hotelName);
}
