package com.smartstay.booking.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.smartstay.booking.service.BookingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens for {@code hotel.deleted} events and cancels all active bookings
 * for the deleted hotel, triggering notification emails to affected guests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotelDeletedConsumer {

    private final BookingService bookingService;

    @KafkaListener(topics = "hotel.deleted", groupId = "booking-service")
    public void onHotelDeleted(HotelDeletedEvent event) {
        log.info("Received hotel.deleted event — hotelId={}, hotelName={}", event.hotelId(), event.hotelName());
        bookingService.cancelAllActiveBookingsForHotel(event.hotelId(), event.hotelName());
    }
}
