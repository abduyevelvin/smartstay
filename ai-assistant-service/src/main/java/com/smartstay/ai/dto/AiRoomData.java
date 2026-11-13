package com.smartstay.ai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured-output DTO for a single room produced by the LLM
 * during random hotel generation.
 * <p>
 * Room type must be one of: SINGLE, DOUBLE, SUITE, PENTHOUSE.
 */
public record AiRoomData(

        @JsonProperty("roomNumber") int roomNumber,

        /** Must be SINGLE, DOUBLE, SUITE, or PENTHOUSE. */
        @JsonProperty("type") String type,

        @JsonProperty("pricePerNight") double pricePerNight,

        @JsonProperty("maxOccupancy") int maxOccupancy,

        @JsonProperty("floor") int floor,

        @JsonProperty("amenities") List<String> amenities) {

}
