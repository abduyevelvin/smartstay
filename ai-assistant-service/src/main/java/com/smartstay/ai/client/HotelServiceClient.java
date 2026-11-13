package com.smartstay.ai.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.smartstay.ai.client.dto.CreateHotelClientRequest;
import com.smartstay.ai.client.dto.CreateRoomClientRequest;
import com.smartstay.ai.client.dto.HotelClientResponse;
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

    /**
     * Creates a hotel on behalf of a manager. The AI service calls hotel-service
     * directly (not through the gateway), so it must supply the identity headers
     * that hotel-service's HeaderAuthenticationFilter expects.
     */
    @PostMapping("/api/v1/hotels")
    HotelClientResponse createHotel(@RequestBody CreateHotelClientRequest request,
                                    @RequestHeader("X-User-Id") String managerId,
                                    @RequestHeader("X-User-Role") String userRole);

    /**
     * Adds a room to an existing hotel on behalf of a manager.
     * Return value is ignored — we only care that the call succeeds.
     */
    @PostMapping("/api/v1/hotels/{hotelId}/rooms")
    void addRoom(@PathVariable String hotelId,
                 @RequestBody CreateRoomClientRequest request,
                 @RequestHeader("X-User-Id") String managerId,
                 @RequestHeader("X-User-Role") String userRole);
}
