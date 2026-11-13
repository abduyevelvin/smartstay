package com.smartstay.ai.dto;

import lombok.Data;

/**
 * Mutable in-memory state for a single hotel-creation conversation.
 * One instance is stored per active manager session.
 */
@Data
public class HotelCreationState {

    private HotelCreationPhase phase = HotelCreationPhase.AWAITING_MODE_CHOICE;

    // ── Custom-mode collected fields ─────────────────────────────────────────
    private String name;
    private String description;
    private String city;
    private String country;
    private String address;
    private Double starRating;
    private String checkInTime;
    private String checkOutTime;

    // ── Random-mode generated data ───────────────────────────────────────────
    /**
     * Populated during random mode; null during custom mode.
     */
    private AiHotelData generatedHotelData;
}
