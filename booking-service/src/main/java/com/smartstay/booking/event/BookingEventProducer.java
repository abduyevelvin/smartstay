package com.smartstay.booking.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventProducer {

    private static final String TOPIC_CONFIRMED = "booking.confirmed";
    private static final String TOPIC_CANCELLED = "booking.cancelled";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishBookingConfirmed(BookingConfirmedEvent event) {
        kafkaTemplate.send(TOPIC_CONFIRMED, event.bookingId()
                                                 .toString(), event);
        log.info("Published booking.confirmed — bookingId={}", event.bookingId());
    }

    public void publishBookingCancelled(BookingCancelledEvent event) {
        kafkaTemplate.send(TOPIC_CANCELLED, event.bookingId()
                                                 .toString(), event);
        log.info("Published booking.cancelled — bookingId={}", event.bookingId());
    }
}
