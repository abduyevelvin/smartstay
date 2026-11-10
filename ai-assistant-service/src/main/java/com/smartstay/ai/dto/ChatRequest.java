package com.smartstay.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank(message = "sessionId must not be blank") String sessionId,

                          @NotBlank(message = "message must not be blank") String message) {

}
