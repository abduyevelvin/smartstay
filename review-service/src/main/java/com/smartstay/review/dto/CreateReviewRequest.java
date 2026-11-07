package com.smartstay.review.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateReviewRequest(

        @NotBlank String hotelId,

        @Min(1) @Max(5) int rating,

        @NotBlank String title,

        @NotBlank String body,

        @NotNull LocalDate stayDate) {

}
