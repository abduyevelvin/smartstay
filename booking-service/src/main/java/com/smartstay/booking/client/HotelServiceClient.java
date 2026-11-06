package com.smartstay.booking.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.smartstay.booking.client.dto.HotelSummary;
import com.smartstay.booking.client.dto.RoomSummary;

@FeignClient(name = "hotel-service", url = "${feign.hotel-service.url}")
public interface HotelServiceClient {

    @GetMapping("/api/v1/hotels/{hotelId}")
    HotelSummary getHotel(@PathVariable String hotelId);

    @GetMapping("/api/v1/hotels/{hotelId}/rooms/{roomId}")
    RoomSummary getRoom(@PathVariable String hotelId,
                        @PathVariable String roomId);
}
