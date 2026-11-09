package com.smartstay.review.event;

import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.smartstay.review.client.HotelServiceClient;
import com.smartstay.review.client.dto.UpdateRatingRequest;
import com.smartstay.review.document.ReviewDocument;
import com.smartstay.review.repository.ReviewRepository;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewAnalyzedConsumer {

    private final ReviewRepository reviewRepository;
    private final HotelServiceClient hotelServiceClient;

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 2000, multiplier = 2.0), autoCreateTopics = "true")
    @KafkaListener(topics = "review.analyzed", groupId = "review-service")
    public void consumeReviewAnalyzed(ReviewAnalyzedEvent event) {
        log.info("Received review.analyzed — reviewId={}, hotelId={}", event.reviewId(), event.hotelId());

        var review = reviewRepository.findById(event.reviewId())
                                     .orElse(null);
        if (review == null) {
            log.warn("Review not found for analyzed event — reviewId={}", event.reviewId());
            return;
        }

        review.setSentiment(event.sentiment());
        review.setTags(event.tags());
        reviewRepository.save(review);
        log.debug("Sentiment and tags updated — reviewId={}", event.reviewId());

        var allReviews = reviewRepository.findByHotelId(event.hotelId());
        double avg = allReviews.stream()
                               .mapToInt(ReviewDocument::getRating)
                               .average()
                               .orElse(0.0);
        avg = Math.round(avg * 10.0) / 10.0;
        int count = allReviews.size();

        try {
            hotelServiceClient.updateRating(event.hotelId(), new UpdateRatingRequest(avg, count));
            log.info("Hotel rating updated — hotelId={}, avg={}, count={}", event.hotelId(), avg, count);
        } catch (FeignException.NotFound e) {
            log.warn("Hotel not found when updating rating — hotelId={}", event.hotelId());
        } catch (FeignException e) {
            log.error("Failed to update hotel rating — hotelId={}, error={}", event.hotelId(), e.getMessage());
            throw e;  // let @RetryableTopic retry the message
        }
    }

    @DltHandler
    public void onDeadLetter(Object event) {
        log.error("Dead-letter — review.analyzed message exhausted all retries and was dropped: {}", event);
    }
}
