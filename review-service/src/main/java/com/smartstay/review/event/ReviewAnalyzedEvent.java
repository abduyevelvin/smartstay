package com.smartstay.review.event;

import java.util.List;

public record ReviewAnalyzedEvent(String reviewId, String hotelId, String sentiment, List<String> tags) {

}
