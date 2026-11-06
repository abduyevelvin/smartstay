package com.smartstay.booking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RoomSummary(String id, String hotelId, int roomNumber, double pricePerNight, int maxOccupancy,
                          boolean available) {

}
