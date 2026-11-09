package com.smartstay.notification.consumer;

import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.smartstay.notification.event.BookingCancelledEvent;
import com.smartstay.notification.event.BookingConfirmedEvent;
import com.smartstay.notification.service.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventConsumer {

    private final EmailService emailService;

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 2000, multiplier = 2.0), autoCreateTopics = "true")
    @KafkaListener(topics = "booking.confirmed", groupId = "notification-service")
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Received booking.confirmed — bookingId={}", event.bookingId());
        emailService.sendBookingConfirmation(event);
    }

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 2000, multiplier = 2.0), autoCreateTopics = "true")
    @KafkaListener(topics = "booking.cancelled", groupId = "notification-service")
    public void onBookingCancelled(BookingCancelledEvent event) {
        log.info("Received booking.cancelled — bookingId={}", event.bookingId());
        emailService.sendBookingCancellation(event);
    }

    @DltHandler
    public void onDeadLetter(Object event) {
        log.error("Dead-letter — message exhausted all retries and was dropped: {}", event);
    }
}
