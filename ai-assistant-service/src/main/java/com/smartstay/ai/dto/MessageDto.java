package com.smartstay.ai.dto;

// Represents a single message in a chat history response
public record MessageDto(String role,     // "user" | "assistant" | "system"
                         String content) {

}
