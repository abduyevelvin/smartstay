package com.smartstay.hotel.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record UpdateHotelRequest(String name,

                                 String description,

                                 String city,

                                 String country,

                                 String address,

                                 @DecimalMin("1.0") @DecimalMax("5.0") Double starRating,

                                 String checkInTime,

                                 String checkOutTime,

                                 List<String> amenities,

                                 List<String> images,

                                 Map<String, String> policies) {

}
