package com.smartstay.ai.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HotelPage(List<HotelSummary> content, int totalPages, long totalElements) {

}
