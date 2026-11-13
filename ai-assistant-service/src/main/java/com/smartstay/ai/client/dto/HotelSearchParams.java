package com.smartstay.ai.client.dto;

public record HotelSearchParams(String city, String country, Double minStars, Integer page, Integer size) {

}
