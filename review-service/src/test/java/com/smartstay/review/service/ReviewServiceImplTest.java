package com.smartstay.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.smartstay.review.client.BookingServiceClient;
import com.smartstay.review.client.dto.VerifyStayResponse;
import com.smartstay.review.document.ReviewDocument;
import com.smartstay.review.dto.CreateReviewRequest;
import com.smartstay.review.dto.ReviewResponse;
import com.smartstay.review.event.ReviewEventProducer;
import com.smartstay.review.exceptions.AlreadyReviewedException;
import com.smartstay.review.exceptions.ForbiddenException;
import com.smartstay.review.exceptions.ResourceNotFoundException;
import com.smartstay.review.exceptions.UnverifiedStayException;
import com.smartstay.review.mapper.ReviewMapper;
import com.smartstay.review.repository.ReviewRepository;

import feign.FeignException;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    ReviewRepository reviewRepository;
    @Mock
    BookingServiceClient bookingServiceClient;
    @Mock
    ReviewEventProducer eventProducer;
    @Mock
    ReviewMapper reviewMapper;

    @InjectMocks
    ReviewServiceImpl reviewService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    static final UUID GUEST_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID ADMIN_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    static final String HOTEL_ID = "hotel-abc";
    static final String REVIEW_ID = "review-001";
    static final String GUEST_NAME = "John Doe";
    static final LocalDate STAY_DATE = LocalDate.now()
                                                .minusDays(30);

    CreateReviewRequest request;
    ReviewDocument savedDocument;
    ReviewResponse reviewResponse;

    @BeforeEach
    void setUp() {
        request = new CreateReviewRequest(HOTEL_ID, 5, "Excellent!", "Very clean and friendly staff.", STAY_DATE);

        savedDocument = ReviewDocument.builder()
                                      .hotelId(HOTEL_ID)
                                      .guestId(GUEST_ID.toString())
                                      .guestName(GUEST_NAME)
                                      .rating(5)
                                      .title("Excellent!")
                                      .body("Very clean and friendly staff.")
                                      .stayDate(STAY_DATE)
                                      .build();
        savedDocument.setId(REVIEW_ID);

        reviewResponse = new ReviewResponse(REVIEW_ID, HOTEL_ID, GUEST_ID.toString(), GUEST_NAME, 5, "Excellent!",
                "Very clean and friendly staff.", null, null, STAY_DATE, false, OffsetDateTime.now());
    }

    // ── createReview ──────────────────────────────────────────────────────────

    @Test
    void createReview_success_savesAndPublishesEvent() {
        when(bookingServiceClient.verifyStay(GUEST_ID, HOTEL_ID)).thenReturn(new VerifyStayResponse(true));
        when(reviewRepository.existsByHotelIdAndGuestId(HOTEL_ID, GUEST_ID.toString())).thenReturn(false);
        when(reviewRepository.save(any())).thenReturn(savedDocument);
        when(reviewMapper.toResponse(savedDocument)).thenReturn(reviewResponse);

        var result = reviewService.createReview(request, GUEST_ID, GUEST_NAME);

        assertThat(result).isEqualTo(reviewResponse);
        verify(eventProducer).publishReviewCreated(any());
    }

    @Test
    void createReview_savesCorrectGuestIdAndName() {
        when(bookingServiceClient.verifyStay(GUEST_ID, HOTEL_ID)).thenReturn(new VerifyStayResponse(true));
        when(reviewRepository.existsByHotelIdAndGuestId(HOTEL_ID, GUEST_ID.toString())).thenReturn(false);
        when(reviewRepository.save(any())).thenReturn(savedDocument);
        when(reviewMapper.toResponse(savedDocument)).thenReturn(reviewResponse);

        reviewService.createReview(request, GUEST_ID, GUEST_NAME);

        verify(reviewRepository).save(argThat(doc -> GUEST_ID.toString()
                                                             .equals(doc.getGuestId()) && GUEST_NAME.equals(
                doc.getGuestName()) && doc.getRating() == 5 && HOTEL_ID.equals(doc.getHotelId())));
    }

    @Test
    void createReview_stayNotVerified_throwsUnverifiedStayException() {
        when(bookingServiceClient.verifyStay(GUEST_ID, HOTEL_ID)).thenReturn(new VerifyStayResponse(false));

        assertThatThrownBy(() -> reviewService.createReview(request, GUEST_ID, GUEST_NAME)).isInstanceOf(
                                                                                                   UnverifiedStayException.class)
                                                                                           .hasMessageContaining(
                                                                                                   "only review hotels you have stayed at");

        verifyNoInteractions(reviewRepository);
        verifyNoInteractions(eventProducer);
    }

    @Test
    void createReview_feignException_throwsUnverifiedStayException() {
        when(bookingServiceClient.verifyStay(GUEST_ID, HOTEL_ID)).thenThrow(mock(FeignException.class));

        assertThatThrownBy(() -> reviewService.createReview(request, GUEST_ID, GUEST_NAME)).isInstanceOf(
                                                                                                   UnverifiedStayException.class)
                                                                                           .hasMessageContaining(
                                                                                                   "Could not verify");

        verifyNoInteractions(reviewRepository);
        verifyNoInteractions(eventProducer);
    }

    @Test
    void createReview_alreadyReviewed_throwsAlreadyReviewedException() {
        when(bookingServiceClient.verifyStay(GUEST_ID, HOTEL_ID)).thenReturn(new VerifyStayResponse(true));
        when(reviewRepository.existsByHotelIdAndGuestId(HOTEL_ID, GUEST_ID.toString())).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(request, GUEST_ID, GUEST_NAME)).isInstanceOf(
                                                                                                   AlreadyReviewedException.class)
                                                                                           .hasMessageContaining(
                                                                                                   "already reviewed");

        verifyNoInteractions(eventProducer);
    }

    // ── getHotelReviews ───────────────────────────────────────────────────────

    @Test
    void getHotelReviews_returnsMappedPage() {
        var pageable = PageRequest.of(0, 20);
        when(reviewRepository.findByHotelIdOrderByCreatedAtDesc(HOTEL_ID, pageable)).thenReturn(
                new PageImpl<>(List.of(savedDocument)));
        when(reviewMapper.toResponse(savedDocument)).thenReturn(reviewResponse);

        var result = reviewService.getHotelReviews(HOTEL_ID, pageable);

        assertThat(result.getContent()).hasSize(1)
                                       .containsExactly(reviewResponse);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ── getMyReviews ──────────────────────────────────────────────────────────

    @Test
    void getMyReviews_returnsMappedList() {
        when(reviewRepository.findByGuestId(GUEST_ID.toString())).thenReturn(List.of(savedDocument));
        when(reviewMapper.toListResponse(List.of(savedDocument))).thenReturn(List.of(reviewResponse));

        var result = reviewService.getMyReviews(GUEST_ID);

        assertThat(result).hasSize(1)
                          .containsExactly(reviewResponse);
    }

    // ── deleteReview ──────────────────────────────────────────────────────────

    @Test
    void deleteReview_asOwner_deletesDocument() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(savedDocument));

        reviewService.deleteReview(REVIEW_ID, GUEST_ID, "GUEST");

        verify(reviewRepository).delete(savedDocument);
    }

    @Test
    void deleteReview_asAdmin_deletesDocument() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(savedDocument));

        reviewService.deleteReview(REVIEW_ID, ADMIN_ID, "ADMIN");

        verify(reviewRepository).delete(savedDocument);
    }

    @Test
    void deleteReview_notFound_throwsResourceNotFoundException() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.deleteReview(REVIEW_ID, GUEST_ID, "GUEST")).isInstanceOf(
                                                                                                  ResourceNotFoundException.class)
                                                                                          .hasMessageContaining(
                                                                                                  "Review not found");
    }

    @Test
    void deleteReview_differentGuestNotAdmin_throwsForbiddenException() {
        var otherGuestId = UUID.randomUUID();
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(savedDocument));

        assertThatThrownBy(() -> reviewService.deleteReview(REVIEW_ID, otherGuestId, "GUEST")).isInstanceOf(
                                                                                                      ForbiddenException.class)
                                                                                              .hasMessageContaining(
                                                                                                      "not authorized");

        verify(reviewRepository).findById(REVIEW_ID);
    }
}
