package com.smartstay.review.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartstay.review.dto.CreateReviewRequest;
import com.smartstay.review.dto.ReviewResponse;
import com.smartstay.review.security.UserPrincipal;
import com.smartstay.review.service.ReviewService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // POST / — submit a review (GUEST only)
    @PostMapping
    @PreAuthorize("hasRole('GUEST')")
    public ResponseEntity<ReviewResponse> createReview(@RequestBody @Valid CreateReviewRequest request,
                                                       Authentication auth) {
        final var principal = (UserPrincipal) auth.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(reviewService.createReview(request, principal.userId(), principal.name()));
    }

    // GET /hotel/{hotelId} — paginated hotel reviews (public)
    @GetMapping("/hotel/{hotelId}")
    public ResponseEntity<Page<ReviewResponse>> getHotelReviews(@PathVariable String hotelId,
                                                                 @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(reviewService.getHotelReviews(hotelId, pageable));
    }

    // GET /my — my reviews (GUEST only)
    @GetMapping("/my")
    @PreAuthorize("hasRole('GUEST')")
    public ResponseEntity<List<ReviewResponse>> getMyReviews(Authentication auth) {
        final var principal = (UserPrincipal) auth.getPrincipal();
        return ResponseEntity.ok(reviewService.getMyReviews(principal.userId()));
    }

    // DELETE /{id} — owner or ADMIN
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable String id,
                                             Authentication auth) {
        final var principal = (UserPrincipal) auth.getPrincipal();
        final String userRole = auth.getAuthorities()
                                    .stream()
                                    .findFirst()
                                    .map(a -> a.getAuthority().replace("ROLE_", ""))
                                    .orElse("");
        reviewService.deleteReview(id, principal.userId(), userRole);
        return ResponseEntity.noContent().build();
    }
}
