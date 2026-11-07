package com.smartstay.review.document;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reviews")
public class ReviewDocument extends BaseDocument {

    @NotBlank
    @Indexed
    private String hotelId;

    @NotBlank
    private String guestId;

    @NotBlank
    private String guestName;

    @Min(1)
    @Max(5)
    private int rating;

    @NotBlank
    private String title;

    @NotBlank
    private String body;

    /**
     * Filled by AI after review.analyzed event is consumed
     */
    private String sentiment;

    /**
     * Filled by AI after review.analyzed event is consumed (e.g. ["clean","friendly"])
     */
    private List<String> tags;

    @NotNull
    private LocalDate stayDate;

    @Builder.Default
    private boolean verified = false;
}
