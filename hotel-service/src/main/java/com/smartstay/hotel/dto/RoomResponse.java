package com.smartstay.hotel.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.smartstay.hotel.enums.Type;

public record RoomResponse(String id,

                           String hotelId,

                           int roomNumber,

                           Type type,

                           double pricePerNight,

                           int maxOccupancy,

                           int floor,

                           boolean available,

                           List<String> amenities,

                           List<String> images,

                           OffsetDateTime createdAt,

                           OffsetDateTime updatedAt) {

}
