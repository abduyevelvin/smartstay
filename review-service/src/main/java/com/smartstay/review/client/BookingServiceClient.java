package com.smartstay.review.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.smartstay.review.client.dto.VerifyStayResponse;

@FeignClient(name = "booking-service", url = "${feign.booking-service.url}")
public interface BookingServiceClient {

    @GetMapping("/api/v1/bookings/verify-stay")
    VerifyStayResponse verifyStay(@RequestParam UUID guestId,
                                  @RequestParam String hotelId);
}
