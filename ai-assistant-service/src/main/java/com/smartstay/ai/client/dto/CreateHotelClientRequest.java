package com.smartstay.ai.client.dto;

import java.util.List;
import java.util.Map;

/**
 * Mirrors hotel-service's {@code CreateHotelRequest} DTO for Feign calls
 * made from the AI assistant service.
 */
public record CreateHotelClientRequest(

        String name, String description, String city, String country, String address, Double starRating,
        String checkInTime, String checkOutTime, List<String> amenities, List<String> images,
        Map<String, String> policies) {

}
