package com.smartstay.ai.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HotelSummary(String id, String name, String description, String city, String country, int starRating,
                           List<String> amenities, double averageRating) {

}
