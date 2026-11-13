package com.smartstay.review.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.smartstay.review.client.BookingServiceClient;
import com.smartstay.review.document.ReviewDocument;
import com.smartstay.review.dto.CreateReviewRequest;
import com.smartstay.review.dto.ReviewResponse;
import com.smartstay.review.event.ReviewCreatedEvent;
import com.smartstay.review.event.ReviewEventProducer;
import com.smartstay.review.exceptions.AlreadyReviewedException;
import com.smartstay.review.exceptions.ForbiddenException;
import com.smartstay.review.exceptions.ResourceNotFoundException;
import com.smartstay.review.exceptions.UnverifiedStayException;
import com.smartstay.review.mapper.ReviewMapper;
import com.smartstay.review.repository.ReviewRepository;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingServiceClient bookingServiceClient;
    private final ReviewEventProducer eventProducer;
    private final ReviewMapper reviewMapper;

    @Override
    public ReviewResponse createReview(final CreateReviewRequest request,
                                       final UUID guestId,
                                       final String guestName) {

        log.debug("Creating review — guestId={}, hotelId={}", guestId, request.hotelId());

        try {
            final var verifyResponse = bookingServiceClient.verifyStay(guestId, request.hotelId());
            if (!verifyResponse.isStayed()) {
                log.warn("Guest has not stayed at hotel — guestId={}, hotelId={}", guestId, request.hotelId());
                throw new UnverifiedStayException("You can only review hotels you have stayed at");
            }
        } catch (FeignException e) {
            log.error("Failed to verify stay — guestId={}, hotelId={}, error={}", guestId, request.hotelId(),
                    e.getMessage());
            throw new UnverifiedStayException("Could not verify your stay at this hotel");
        }

        if (reviewRepository.existsByHotelIdAndGuestId(request.hotelId(), guestId.toString())) {
            log.warn("Guest already reviewed hotel — guestId={}, hotelId={}", guestId, request.hotelId());
            throw new AlreadyReviewedException("You have already reviewed this hotel");
        }

        final var review = ReviewDocument.builder()
                                         .hotelId(request.hotelId())
                                         .guestId(guestId.toString())
                                         .guestName(guestName)
                                         .rating(request.rating())
                                         .title(request.title())
                                         .body(request.body())
                                         .stayDate(request.stayDate())
                                         .verified(true)
                                         .build();

        final var saved = reviewRepository.save(review);
        log.info("Review created — id={}, guestId={}, hotelId={}", saved.getId(), guestId, request.hotelId());

        final var event = new ReviewCreatedEvent(saved.getId(), saved.getHotelId(), saved.getGuestId(),
                saved.getRating(), saved.getBody());
        eventProducer.publishReviewCreated(event);

        return reviewMapper.toResponse(saved);
    }

    @Override
    public Page<ReviewResponse> getHotelReviews(final String hotelId,
                                                final Pageable pageable) {
        log.debug("Fetching reviews for hotelId={}", hotelId);
        return reviewRepository.findByHotelIdOrderByCreatedAtDesc(hotelId, pageable)
                               .map(reviewMapper::toResponse);
    }

    @Override
    public List<ReviewResponse> getMyReviews(final UUID guestId) {
        log.debug("Fetching reviews for guestId={}", guestId);
        return reviewMapper.toListResponse(reviewRepository.findByGuestId(guestId.toString()));
    }

    @Override
    public void deleteReview(final String reviewId,
                             final UUID userId,
                             final String userRole) {

        final var review = reviewRepository.findById(reviewId)
                                           .orElseThrow(() -> new ResourceNotFoundException(
                                                   "Review not found with id: " + reviewId));

        final boolean isOwner = review.getGuestId()
                                      .equals(userId.toString());
        final boolean isAdmin = "ADMIN".equals(userRole);

        if (!isOwner && !isAdmin) {
            log.warn("Unauthorized delete attempt — userId={}, reviewId={}", userId, reviewId);
            throw new ForbiddenException("You are not authorized to delete this review");
        }

        reviewRepository.delete(review);
        log.info("Review deleted — id={}, deletedBy={}", reviewId, userId);
    }
}
