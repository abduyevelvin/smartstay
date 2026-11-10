package com.smartstay.ai.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.kafka.core.KafkaTemplate;

import com.smartstay.ai.event.ReviewAnalysis;
import com.smartstay.ai.event.ReviewAnalyzedEvent;
import com.smartstay.ai.event.ReviewCreatedEvent;
import com.smartstay.ai.event.Sentiment;

@ExtendWith(MockitoExtension.class)
class ReviewAnalysisConsumerTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private ReviewAnalysisConsumer reviewAnalysisConsumer;

    @Test
    void onReviewCreated_success_publishesAnalyzedEventWithCorrectFields() {
        var event = new ReviewCreatedEvent("rev1", "hotel1", "guest1", 5, "Amazing stay, very clean!");
        var analysis = new ReviewAnalysis(Sentiment.POSITIVE, List.of("clean", "friendly", "great view"));

        when(chatClient.prompt()
                       .user(any(Consumer.class))
                       .call()
                       .entity(ReviewAnalysis.class)).thenReturn(analysis);

        reviewAnalysisConsumer.onReviewCreated(event);

        verify(kafkaTemplate).send(eq("review.analyzed"), eq("rev1"), argThat(payload -> {
            var ae = (ReviewAnalyzedEvent) payload;
            return ae.reviewId()
                     .equals("rev1") && ae.hotelId()
                                          .equals("hotel1") && ae.sentiment() == Sentiment.POSITIVE && ae.tags()
                                                                                                         .contains(
                                                                                                                 "clean");
        }));
    }

    @Test
    void onReviewCreated_negativeReview_publishesNegativeSentiment() {
        var event = new ReviewCreatedEvent("rev2", "hotel1", "guest2", 1, "Terrible experience.");
        var analysis = new ReviewAnalysis(Sentiment.NEGATIVE, List.of("noisy", "dirty"));

        when(chatClient.prompt()
                       .user(any(Consumer.class))
                       .call()
                       .entity(ReviewAnalysis.class)).thenReturn(analysis);

        reviewAnalysisConsumer.onReviewCreated(event);

        verify(kafkaTemplate).send(eq("review.analyzed"), eq("rev2"),
                argThat(payload -> ((ReviewAnalyzedEvent) payload).sentiment() == Sentiment.NEGATIVE));
    }

    @Test
    void onReviewCreated_llmThrows_exceptionPropagatesForRetry() {
        var event = new ReviewCreatedEvent("rev3", "hotel1", "guest3", 4, "Good stay.");

        when(chatClient.prompt()
                       .user(any(Consumer.class))
                       .call()
                       .entity(ReviewAnalysis.class)).thenThrow(new RuntimeException("LLM timeout"));

        // Exception must propagate so @RetryableTopic can retry
        RuntimeException ex = assertThrows(RuntimeException.class, () -> reviewAnalysisConsumer.onReviewCreated(event));

        assertThat(ex.getMessage()).isEqualTo("LLM timeout");
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void onDlt_doesNotThrowAndDoesNotPublish() {
        var event = new ReviewCreatedEvent("rev4", "hotel1", "guest4", 3, "Average.");

        assertDoesNotThrow(() -> reviewAnalysisConsumer.onDlt(event, "review.created.DLT"));

        verifyNoInteractions(kafkaTemplate);
    }
}
