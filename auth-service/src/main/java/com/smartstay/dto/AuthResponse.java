package com.smartstay.dto;

public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

}
