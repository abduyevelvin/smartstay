package com.smartstay.hotel.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.smartstay.hotel.document.RoomDocument;

public interface RoomRepository extends MongoRepository<RoomDocument, String> {

    List<RoomDocument> findByHotelId(String hotelId);

    List<RoomDocument> findByHotelIdAndAvailable(String hotelId,
                                                 boolean available);

    boolean existsByHotelIdAndRoomNumber(String hotelId,
                                         int roomNumber);

    void deleteAllByHotelId(String hotelId);
}
