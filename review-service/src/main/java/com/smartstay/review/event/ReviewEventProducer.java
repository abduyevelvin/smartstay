package com.smartstay.review.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewEventProducer {

    private static final String TOPIC_CREATED = "review.created";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishReviewCreated(ReviewCreatedEvent event) {
        kafkaTemplate.send(TOPIC_CREATED, event.reviewId(), event);

        log.info("Published review.created - reviewId={}", event.reviewId());
    }
}
