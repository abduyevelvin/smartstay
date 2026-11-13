package com.smartstay.hotel.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.smartstay.hotel.dto.CreateHotelRequest;
import com.smartstay.hotel.dto.CreateRoomRequest;
import com.smartstay.hotel.dto.HotelResponse;
import com.smartstay.hotel.dto.RoomResponse;
import com.smartstay.hotel.dto.UpdateHotelRequest;

public interface HotelService {

    HotelResponse createHotel(CreateHotelRequest request,
                              String managerId);

    HotelResponse getHotelById(String id);

    Page<HotelResponse> searchHotels(String city,
                                     String country,
                                     Double minStars,
                                     Pageable pageable);

    HotelResponse updateHotel(String id,
                              UpdateHotelRequest request,
                              String managerId);

    void deleteHotel(String id,
                     String managerId);

    RoomResponse addRoom(String hotelId,
                         CreateRoomRequest request,
                         String managerId);

    RoomResponse getRoomByHotelAndById(String hotelId,
                                       String roomId);

    List<RoomResponse> getRoomsByHotel(String hotelId);

    List<RoomResponse> getAvailableRooms(String hotelId,
                                         LocalDate checkIn,
                                         LocalDate checkOut);

    /**
     * Internal — called by review-service via Feign after a review is analyzed
     * to update the hotel's cached averageRating and reviewCount.
     */
    void updateRating(String hotelId,
                      double averageRating,
                      int reviewCount);
}
