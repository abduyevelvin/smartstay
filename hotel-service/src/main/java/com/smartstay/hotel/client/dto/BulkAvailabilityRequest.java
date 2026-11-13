package com.smartstay.hotel.client.dto;

import java.time.LocalDate;
import java.util.List;

public record BulkAvailabilityRequest(List<String> roomIds, LocalDate checkIn, LocalDate checkOut) {

}
