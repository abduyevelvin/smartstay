package com.smartstay.hotel.client;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.smartstay.hotel.client.dto.BulkAvailabilityRequest;

@FeignClient(name = "booking-service", url = "${feign.booking-service.url}")
public interface BookingServiceClient {

    @PostMapping("/api/v1/bookings/bulk-availability")
    Map<String, Boolean> checkBulkAvailability(@RequestBody BulkAvailabilityRequest request);
}
