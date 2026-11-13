package com.smartstay.booking.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record BulkAvailabilityRequest(@NotEmpty List<String> roomIds, @NotNull LocalDate checkIn,
                                      @NotNull LocalDate checkOut) {

}
