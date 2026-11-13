package com.smartstay.ai.event;

/**
 * Consumed from the {@code hotel.created} Kafka topic.
 * Published by hotel-service whenever a hotel is created or updated.
 */
public record HotelCreatedEvent(String hotelId, String managerId) {

}
