package com.smartstay.hotel.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotelEventProducer {

    private static final String TOPIC_HOTEL_DELETED = "hotel.deleted";
    private static final String TOPIC_HOTEL_CREATED = "hotel.created";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishHotelDeleted(HotelDeletedEvent event) {
        kafkaTemplate.send(TOPIC_HOTEL_DELETED, event.hotelId(), event);
        log.info("Published hotel.deleted — hotelId={}", event.hotelId());
    }

    /**
     * Published when a hotel is created or updated so that the AI service
     * can immediately re-index the hotel in the vector store.
     */
    public void publishHotelCreated(HotelCreatedEvent event) {
        kafkaTemplate.send(TOPIC_HOTEL_CREATED, event.hotelId(), event);
        log.info("Published hotel.created — hotelId={}", event.hotelId());
    }
}
