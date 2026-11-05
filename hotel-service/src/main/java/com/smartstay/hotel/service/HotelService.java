package com.smartstay.hotel.service;

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

    List<RoomResponse> getRoomsByHotel(String hotelId);

    List<RoomResponse> getAvailableRooms(String hotelId);
}
