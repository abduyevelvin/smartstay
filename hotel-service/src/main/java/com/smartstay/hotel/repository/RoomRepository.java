package com.smartstay.hotel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.smartstay.hotel.document.RoomDocument;

public interface RoomRepository extends MongoRepository<RoomDocument, String> {

    Optional<RoomDocument> findByHotelIdAndId(String hotelId,
                                              String id);

    List<RoomDocument> findByHotelId(String hotelId);

    List<RoomDocument> findByHotelIdAndAvailable(String hotelId,
                                                 boolean available);

    boolean existsByHotelIdAndRoomNumber(String hotelId,
                                         int roomNumber);

    void deleteAllByHotelId(String hotelId);
}
