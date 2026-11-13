package com.smartstay.hotel.document;

import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Document(collection = "hotels")
@CompoundIndexes({
        // Performance index: browsing by city + star rating
        @CompoundIndex(name = "idx_city_stars", def = "{'city': 1, 'starRating': 1}"),
        // Uniqueness guard: a manager cannot create the same hotel twice in the same city
        @CompoundIndex(name = "idx_unique_manager_name_city",
                def = "{'managerId': 1, 'name': 1, 'city': 1}",
                unique = true),
})
public class HotelDocument extends BaseDocument {

    private String name;

    private String description;

    private String city;

    private String country;

    private String address;

    private double starRating;

    private List<String> amenities;

    private List<String> images;

    private String managerId;

    private double averageRating;

    private int reviewCount;

    private String checkInTime;

    private String checkOutTime;

    private Map<String, String> policies;
}
