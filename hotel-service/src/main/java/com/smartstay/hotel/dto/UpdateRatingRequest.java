package com.smartstay.hotel.dto;

/**
 * Internal request sent by review-service after a review is analyzed
 * to keep the hotel's cached average rating in sync.
 */
public record UpdateRatingRequest(double averageRating, int reviewCount) {

}
