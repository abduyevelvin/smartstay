package com.smartstay.hotel.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

public record CreateHotelRequest(@NotBlank String name,

                                 @NotBlank String description,

                                 @NotBlank String city,

                                 @NotBlank String country,

                                 @NotBlank String address,

                                 @DecimalMin("1.0") @DecimalMax("5.0") Double starRating,

                                 @NotBlank String checkInTime,

                                 @NotBlank String checkOutTime,

                                 List<String> amenities,

                                 List<String> images,

                                 Map<String, String> policies) {

}
