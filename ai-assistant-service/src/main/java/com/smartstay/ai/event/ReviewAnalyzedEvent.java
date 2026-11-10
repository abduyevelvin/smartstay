package com.smartstay.ai.event;

import java.util.List;

// Published to review.analyzed — consumed by review-service to update sentiment + tags
// Sentiment serializes to "POSITIVE"/"NEUTRAL"/"NEGATIVE" — review-service String field handles it fine
public record ReviewAnalyzedEvent(String reviewId, String hotelId, Sentiment sentiment, List<String> tags) {

}
