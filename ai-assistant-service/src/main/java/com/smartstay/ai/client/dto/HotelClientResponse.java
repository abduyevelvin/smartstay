package com.smartstay.ai.client.dto;

/**
 * Minimal projection of hotel-service's {@code HotelResponse} used when the AI
 * assistant service creates a hotel via Feign (only id and name are needed).
 */
public record HotelClientResponse(

        String id, String name, String city, String country) {

}
