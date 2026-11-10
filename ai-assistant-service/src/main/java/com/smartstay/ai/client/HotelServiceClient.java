package com.smartstay.ai.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.smartstay.ai.client.dto.HotelPage;
import com.smartstay.ai.client.dto.HotelSearchParams;
import com.smartstay.ai.client.dto.HotelSummary;
import com.smartstay.ai.client.dto.RoomSummary;

@FeignClient(name = "hotel-service", url = "${feign.hotel-service.url}")
public interface HotelServiceClient {

    // Returns a Spring Data Page — use HotelPage wrapper to extract content[]
    @GetMapping("/api/v1/hotels")
    HotelPage getAllHotels(@SpringQueryMap HotelSearchParams params);

    @GetMapping("/api/v1/hotels/{hotelId}")
    HotelSummary getHotelById(@PathVariable String hotelId);

    @GetMapping("/api/v1/hotels/{hotelId}/rooms")
    List<RoomSummary> getRooms(@PathVariable String hotelId);
}
