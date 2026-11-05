package com.smartstay.hotel.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.smartstay.hotel.document.HotelDocument;

public interface HotelRepository extends MongoRepository<HotelDocument, String>,
                                          HotelRepositoryCustom {

    List<HotelDocument> findByManagerId(String managerId);
}
