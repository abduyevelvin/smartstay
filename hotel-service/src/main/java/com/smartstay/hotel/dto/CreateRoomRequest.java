package com.smartstay.hotel.dto;

import java.util.List;

import com.smartstay.hotel.enums.Type;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateRoomRequest(@Positive int roomNumber,

                                @NotNull Type type,

                                @Positive double pricePerNight,

                                @Positive int maxOccupancy,

                                @Positive int floor,

                                List<String> amenities,

                                List<String> images) {

}
