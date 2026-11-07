package com.smartstay.review.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import com.smartstay.review.client.dto.UpdateRatingRequest;

@FeignClient(name = "hotel-service", url = "${feign.hotel-service.url}")
public interface HotelServiceClient {

    @PatchMapping("/api/v1/hotels/{hotelId}/rating")
    void updateRating(@PathVariable String hotelId,
                      @RequestBody UpdateRatingRequest request);
}
