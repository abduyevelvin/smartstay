package com.smartstay.booking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HotelSummary(String id, String name, String city, String country, String managerId) {

}
