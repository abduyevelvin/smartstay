package com.smartstay.review.event;

public record ReviewCreatedEvent(String reviewId, String hotelId, String guestId, int rating, String body) {

}
