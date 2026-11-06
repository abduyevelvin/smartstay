package com.smartstay.booking.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(@NotBlank String hotelId,

                                   @NotBlank String roomId,

                                   @NotNull @Future LocalDate checkIn,

                                   @NotNull LocalDate checkOut,

                                   String specialRequests) {

    @AssertTrue(message = "checkOut must be after checkIn")
    public boolean isCheckOutAfterCheckIn() {
        if (checkIn == null || checkOut == null) {
            return true;
        }
        return checkOut.isAfter(checkIn);
    }
}
