package com.smartstay.hotel.document;

import java.util.List;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.smartstay.hotel.enums.Type;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Document(collection = "rooms")
public class RoomDocument extends BaseDocument {

    @Indexed
    private String hotelId;

    private int roomNumber;

    private Type type;

    private double pricePerNight;

    private int maxOccupancy;

    private List<String> amenities;

    private List<String> images;

    private boolean available;

    private int floor;
}
