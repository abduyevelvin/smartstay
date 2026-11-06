package com.smartstay.booking.event;

import java.time.LocalDate;
import java.util.UUID;

public record BookingCancelledEvent(UUID bookingId, String guestEmail, String hotelName, LocalDate checkIn,
                                    LocalDate checkOut) {

}
