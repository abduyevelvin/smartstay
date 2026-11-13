package com.smartstay.ai.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.smartstay.ai.client.HotelServiceClient;
import com.smartstay.ai.client.service.HotelIndexingService;
import com.smartstay.ai.event.HotelCreatedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens for {@code hotel.created} events (published on hotel create AND update)
 * and immediately indexes the hotel into the vector store so the AI chat reflects
 * new/updated hotels without waiting for the hourly scheduled re-index.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotelIndexingConsumer {

    private final HotelIndexingService hotelIndexingService;
    private final HotelServiceClient hotelServiceClient;

    @KafkaListener(topics = "hotel.created", groupId = "ai-assistant-service")
    public void onHotelCreated(final HotelCreatedEvent event) {
        log.info("Received hotel.created — hotelId={}", event.hotelId());
        try {
            final var hotel = hotelServiceClient.getHotelById(event.hotelId());
            final var rooms = hotelServiceClient.getRooms(event.hotelId());
            hotelIndexingService.indexHotel(hotel, rooms);
            log.info("Successfully indexed hotel — hotelId={}", event.hotelId());
        } catch (Exception e) {
            // Log and swallow — the hourly scheduler will catch it on next cycle
            log.error("Failed to index hotel hotelId={}: {}", event.hotelId(), e.getMessage(), e);
        }
    }
}
