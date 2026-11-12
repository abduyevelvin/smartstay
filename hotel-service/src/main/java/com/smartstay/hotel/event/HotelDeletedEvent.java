package com.smartstay.hotel.event;

/**
 * Published to the {@code hotel.deleted} Kafka topic when a hotel is permanently deleted.
 * Booking-service consumes this event to cancel all active bookings for the hotel and
 * trigger notification emails to affected guests.
 */
public record HotelDeletedEvent(String hotelId, String hotelName, String managerId) {

}
