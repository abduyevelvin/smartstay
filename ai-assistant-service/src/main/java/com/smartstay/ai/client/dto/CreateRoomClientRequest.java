package com.smartstay.ai.client.dto;

import java.util.List;

/**
 * Mirrors hotel-service's {@code CreateRoomRequest} DTO for Feign calls
 * made from the AI assistant service.
 * <p>
 * {@code type} must match hotel-service's {@code Type} enum exactly:
 * SINGLE | DOUBLE | SUITE | PENTHOUSE.
 */
public record CreateRoomClientRequest(

        int roomNumber, String type, double pricePerNight, int maxOccupancy, int floor, List<String> amenities,
        List<String> images) {

}
