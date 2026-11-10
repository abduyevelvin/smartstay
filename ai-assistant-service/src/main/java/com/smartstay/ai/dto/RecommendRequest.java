package com.smartstay.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record RecommendRequest(@NotBlank(message = "preferences must not be blank") String preferences) {

}
