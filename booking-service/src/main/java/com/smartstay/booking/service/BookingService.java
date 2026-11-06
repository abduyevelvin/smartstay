package com.smartstay.booking.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.smartstay.booking.dto.BookingResponse;
import com.smartstay.booking.dto.CreateBookingRequest;

public interface BookingService {

    BookingResponse createBooking(CreateBookingRequest request,
                                  UUID guestId,
                                  String guestEmail);

    BookingResponse cancelBooking(UUID bookingId,
                                  UUID userId,
                                  String userRole);

    Page<BookingResponse> getMyBookings(UUID guestId,
                                        Pageable pageable);

    List<BookingResponse> getHotelBookings(String hotelId);

    boolean checkRoomAvailability(String roomId,
                                  LocalDate checkIn,
                                  LocalDate checkOut);
}
