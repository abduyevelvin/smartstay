package com.smartstay.ai.service;

public interface RecommendationService {

    String recommend(String userPreferences);

    String summarizeHotel(String hotelId);
}
