package com.smartstay.ai.event;

// Mirrors the event published by review-service to the review.created topic
public record ReviewCreatedEvent(String reviewId, String hotelId, String guestId, int rating, String body) {

}
