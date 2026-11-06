package com.smartstay.booking.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.smartstay.booking.enums.BookingStatus;

public record BookingResponse(UUID id,

                              UUID guestId,

                              String guestEmail,

                              String hotelId,

                              String roomId,

                              LocalDate checkIn,

                              LocalDate checkOut,

                              double totalPrice,

                              BookingStatus status,

                              String specialRequests,

                              OffsetDateTime createdAt,

                              OffsetDateTime updatedAt) {

}
