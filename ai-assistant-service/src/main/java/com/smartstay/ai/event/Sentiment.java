package com.smartstay.ai.event;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Sentiment {
    POSITIVE,
    NEUTRAL,
    NEGATIVE;

    // Case-insensitive deserialization — handles "positive", "Positive", "POSITIVE"
    @JsonCreator
    public static Sentiment fromValue(final String value) {
        return valueOf(value.toUpperCase());
    }
}
