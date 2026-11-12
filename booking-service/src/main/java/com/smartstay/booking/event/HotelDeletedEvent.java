package com.smartstay.booking.event;

/**
 * Consumed from the {@code hotel.deleted} Kafka topic.
 * Published by hotel-service when a hotel is permanently deleted.
 * booking-service uses this to cancel all active bookings for that hotel.
 */
public record HotelDeletedEvent(String hotelId, String hotelName, String managerId) {

}
