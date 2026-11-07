package com.smartstay.review.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.smartstay.review.dto.CreateReviewRequest;
import com.smartstay.review.dto.ReviewResponse;

public interface ReviewService {

    ReviewResponse createReview(CreateReviewRequest request,
                                UUID guestId,
                                String guestName);

    Page<ReviewResponse> getHotelReviews(String hotelId,
                                         Pageable pageable);

    List<ReviewResponse> getMyReviews(UUID guestId);

    void deleteReview(String reviewId,
                      UUID userId,
                      String userRole);
}
