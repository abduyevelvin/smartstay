package com.smartstay.ai.consumer;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.smartstay.ai.event.ReviewAnalysis;
import com.smartstay.ai.event.ReviewAnalyzedEvent;
import com.smartstay.ai.event.ReviewCreatedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewAnalysisConsumer {

    private static final String REVIEW_ANALYZED_TOPIC = "review.analyzed";

    private final ChatClient chatClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // 3 total attempts (1 original + 2 retries): waits 2s then 4s before DLT
    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 2000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE, dltTopicSuffix = ".DLT")
    @KafkaListener(topics = "review.created", groupId = "ai-assistant-service")
    public void onReviewCreated(final ReviewCreatedEvent event) {
        log.info("Received review.created — reviewId: {}, hotelId: {}", event.reviewId(), event.hotelId());

        final var analysis = analyzeReview(event.body(), event.rating());

        final var analyzedEvent = new ReviewAnalyzedEvent(event.reviewId(), event.hotelId(), analysis.sentiment(),
                analysis.tags());

        kafkaTemplate.send(REVIEW_ANALYZED_TOPIC, event.reviewId(), analyzedEvent);
        log.info("Published review.analyzed — reviewId: {}, sentiment: {}, tags: {}", event.reviewId(),
                analysis.sentiment(), analysis.tags());
    }

    // Called after all retry attempts are exhausted — message lands in review.created.DLT
    @DltHandler
    public void onDlt(final ReviewCreatedEvent event,
                      @Header(KafkaHeaders.RECEIVED_TOPIC) final String topic) {
        log.error(
                "Review analysis permanently failed after all retries — " + "reviewId: {}, hotelId: {}, DLT topic: {}",
                event.reviewId(), event.hotelId(), topic);
        // TODO: alert / store for manual replay
    }

    private ReviewAnalysis analyzeReview(final String body,
                                         final int rating) {
        log.debug("Sending review to LLM for analysis — rating: {}", rating);

        // Spring AI structured output — .entity() parses the LLM JSON into ReviewAnalysis
        // If LLM returns malformed JSON or wrong fields, Spring AI throws → triggers @RetryableTopic
        return chatClient.prompt()
                         .user(u -> u.text("""
                                             Analyze the following hotel review and return a JSON object only.
                                             No explanation, no markdown — just valid JSON.
                                             
                                             Review: "{body}"
                                             Star rating: {rating} out of 5
                                             
                                             Return format:
                                             {{"sentiment": "POSITIVE|NEUTRAL|NEGATIVE", "tags": ["tag1", "tag2"]}}
                                             
                                             Rules:
                                             - sentiment: POSITIVE if rating >= 4, NEGATIVE if rating <= 2, else NEUTRAL
                                             - tags: 2-5 short lowercase keywords (e.g. "clean", "friendly", "noisy", "great view")
                                             """)
                                     .param("body", body)
                                     .param("rating", String.valueOf(rating)))
                         .call()
                         .entity(ReviewAnalysis.class);
    }
}
