package com.smartstay.review.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.smartstay.review.document.ReviewDocument;
import com.smartstay.review.dto.ReviewResponse;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    ReviewResponse toResponse(ReviewDocument document);

    List<ReviewResponse> toListResponse(List<ReviewDocument> documents);
}
