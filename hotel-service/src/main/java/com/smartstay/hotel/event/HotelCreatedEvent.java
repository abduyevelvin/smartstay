package com.smartstay.hotel.event;

/**
 * Published to the {@code hotel.created} Kafka topic when a hotel is created or updated.
 * AI-assistant-service consumes this event to immediately re-index the hotel in the
 * vector store so it becomes searchable via the AI chat without waiting for the
 * hourly scheduled re-index.
 */
public record HotelCreatedEvent(String hotelId, String managerId) {

}
