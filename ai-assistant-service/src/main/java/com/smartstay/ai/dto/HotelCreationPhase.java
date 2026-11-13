package com.smartstay.ai.dto;

/**
 * State machine phases for the AI-guided hotel creation conversation.
 */
public enum HotelCreationPhase {

    /**
     * Asked the manager: random or customized?
     */
    AWAITING_MODE_CHOICE,

    /**
     * Random hotel data generated — waiting for manager to confirm or cancel.
     */
    AWAITING_RANDOM_CONFIRMATION,

    /* ── Custom / step-by-step collection ────────────────────────────── */
    COLLECTING_NAME,
    COLLECTING_DESCRIPTION,
    COLLECTING_CITY,
    COLLECTING_COUNTRY,
    COLLECTING_ADDRESS,
    COLLECTING_STAR_RATING,
    COLLECTING_CHECKIN_TIME,
    COLLECTING_CHECKOUT_TIME,

    /**
     * All custom fields collected — waiting for manager to confirm or cancel.
     */
    AWAITING_CUSTOM_CONFIRMATION
}
