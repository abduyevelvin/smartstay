package com.smartstay.hotel.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.smartstay.hotel.document.HotelDocument;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class HotelRepositoryImpl implements HotelRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<HotelDocument> searchHotels(final String city,
                                            final String country,
                                            final Double minStars,
                                            final Pageable pageable) {
        log.debug("Searching hotels — city={}, country={}, minStars={}, page={}, size={}", city, country, minStars,
                pageable.getPageNumber(), pageable.getPageSize());

        final var criteria = new Criteria();

        if (city != null && !city.isBlank()) {
            criteria.and("city")
                    .regex(city, "i");
        }
        if (country != null && !country.isBlank()) {
            criteria.and("country")
                    .regex(country, "i");
        }
        if (minStars != null) {
            criteria.and("starRating")
                    .gte(minStars);
        }

        final var query = new Query(criteria).with(pageable);
        final var results = mongoTemplate.find(query, HotelDocument.class);
        final var total = mongoTemplate.count(new Query(criteria), HotelDocument.class);

        log.debug("Hotel search returned {} result(s) (total={})", results.size(), total);

        return new PageImpl<>(results, pageable, total);
    }
}
