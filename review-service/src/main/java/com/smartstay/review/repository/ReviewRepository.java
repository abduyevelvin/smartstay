package com.smartstay.review.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.smartstay.review.document.ReviewDocument;

public interface ReviewRepository extends MongoRepository<ReviewDocument, String> {

    Page<ReviewDocument> findByHotelIdOrderByCreatedAtDesc(String hotelId,
                                                           Pageable pageable);

    List<ReviewDocument> findByGuestId(String guestId);

    boolean existsByHotelIdAndGuestId(String hotelId,
                                      String guestId);

    List<ReviewDocument> findByHotelId(String hotelId);
}
