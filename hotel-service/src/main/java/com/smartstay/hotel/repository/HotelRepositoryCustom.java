package com.smartstay.hotel.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.smartstay.hotel.document.HotelDocument;

public interface HotelRepositoryCustom {

    Page<HotelDocument> searchHotels(String city, String country,
                                     Double minStars, Pageable pageable);
}
