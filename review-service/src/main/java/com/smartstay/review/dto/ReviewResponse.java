package com.smartstay.review.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ReviewResponse(String id, String hotelId, String guestId, String guestName, int rating, String title,
                             String body, String sentiment, List<String> tags, LocalDate stayDate, boolean verified,
                             OffsetDateTime createdAt) {

}
