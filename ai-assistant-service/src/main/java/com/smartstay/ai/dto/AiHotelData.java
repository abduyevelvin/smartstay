package com.smartstay.ai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured-output DTO for a complete hotel (with rooms) produced by the LLM
 * during random hotel generation.
 * <p>
 * Star rating must be between 1.0 and 5.0.
 * Check-in / check-out times must be in HH:MM 24-hour format.
 * Room types must be one of: SINGLE, DOUBLE, SUITE, PENTHOUSE.
 */
public record AiHotelData(

        @JsonProperty("name") String name,

        @JsonProperty("description") String description,

        @JsonProperty("city") String city,

        @JsonProperty("country") String country,

        @JsonProperty("address") String address,

        /** 1.0 – 5.0 (may be decimal, e.g. 4.5). */
        @JsonProperty("starRating") double starRating,

        /** HH:MM 24-hour format, e.g. "14:00". */
        @JsonProperty("checkInTime") String checkInTime,

        /** HH:MM 24-hour format, e.g. "11:00". */
        @JsonProperty("checkOutTime") String checkOutTime,

        @JsonProperty("amenities") List<String> amenities,

        /** 3 – 5 rooms; all should be marked available. */
        @JsonProperty("rooms") List<AiRoomData> rooms) {

}
