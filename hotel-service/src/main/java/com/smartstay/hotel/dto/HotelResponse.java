package com.smartstay.hotel.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record HotelResponse(String id,

                            String name,

                            String description,

                            String city,

                            String country,

                            String address,

                            double starRating,

                            String managerId,

                            double averageRating,

                            int reviewCount,

                            String checkInTime,

                            String checkOutTime,

                            List<String> amenities,

                            List<String> images,

                            Map<String, String> policies,

                            OffsetDateTime createdAt,

                            OffsetDateTime updatedAt) {

}
