package com.smartstay.review.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstay.review.client.BookingServiceClient;
import com.smartstay.review.client.HotelServiceClient;
import com.smartstay.review.client.dto.VerifyStayResponse;
import com.smartstay.review.document.ReviewDocument;
import com.smartstay.review.dto.CreateReviewRequest;
import com.smartstay.review.repository.ReviewRepository;

import feign.FeignException;

/**
 * Full-stack integration test for the Review Service HTTP layer.
 *
 * <p>Starts the full Spring context with:
 * <ul>
 *   <li>Testcontainers MongoDB — real DB with Spring Data auditing enabled</li>
 *   <li>{@code @EmbeddedKafka} — in-process Kafka for event publishing</li>
 *   <li>{@code @MockBean BookingServiceClient} — mocks Feign call to booking-service</li>
 *   <li>{@code @MockBean HotelServiceClient} — mocks Feign call to hotel-service (used by ReviewAnalyzedConsumer)</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"review.created", "review.analyzed"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@ActiveProfiles("test")
class ReviewControllerIT {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    ReviewRepository reviewRepository;

    @MockBean
    BookingServiceClient bookingServiceClient;
    @MockBean
    HotelServiceClient hotelServiceClient;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    static final UUID GUEST_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID OTHER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    static final String HOTEL_ID = "hotel-abc";
    static final String EMAIL = "guest@smartstay.test";
    static final String NAME = "John Doe";
    static final LocalDate STAY_DATE = LocalDate.now()
                                                .minusDays(30);

    @BeforeEach
    void setUp() {
        reviewRepository.deleteAll();
    }

    // ── POST /api/v1/reviews ──────────────────────────────────────────────────

    @Test
    void createReview_asGuest_returns201AndPersists() throws Exception {
        given(bookingServiceClient.verifyStay(GUEST_ID, HOTEL_ID)).willReturn(new VerifyStayResponse(true));

        var request = new CreateReviewRequest(HOTEL_ID, 5, "Amazing!", "Loved every moment.", STAY_DATE);

        mockMvc.perform(post("/api/v1/reviews").header("X-User-Id", GUEST_ID.toString())
                                               .header("X-User-Email", EMAIL)
                                               .header("X-User-Role", "GUEST")
                                               .header("X-User-Name", NAME)
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.hotelId").value(HOTEL_ID))
               .andExpect(jsonPath("$.guestName").value(NAME))
               .andExpect(jsonPath("$.rating").value(5))
               .andExpect(jsonPath("$.title").value("Amazing!"))
               .andExpect(jsonPath("$.id").isNotEmpty());

        assertThat(reviewRepository.findAll()).hasSize(1);
    }

    @Test
    void createReview_asHotelManager_returns403() throws Exception {
        var request = new CreateReviewRequest(HOTEL_ID, 4, "Nice", "Good hotel.", STAY_DATE);

        mockMvc.perform(post("/api/v1/reviews").header("X-User-Id", UUID.randomUUID()
                                                                        .toString())
                                               .header("X-User-Email", "mgr@test.com")
                                               .header("X-User-Role", "HOTEL_MANAGER")
                                               .header("X-User-Name", "Manager")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isForbidden());
    }

    @Test
    void createReview_stayNotVerified_returns403() throws Exception {
        given(bookingServiceClient.verifyStay(GUEST_ID, HOTEL_ID)).willReturn(new VerifyStayResponse(false));

        var request = new CreateReviewRequest(HOTEL_ID, 3, "Meh", "Okay-ish.", STAY_DATE);

        mockMvc.perform(post("/api/v1/reviews").header("X-User-Id", GUEST_ID.toString())
                                               .header("X-User-Email", EMAIL)
                                               .header("X-User-Role", "GUEST")
                                               .header("X-User-Name", NAME)
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.message").value("You can only review hotels you have stayed at"));
    }

    @Test
    void createReview_bookingServiceDown_returns403() throws Exception {
        given(bookingServiceClient.verifyStay(any(), any())).willThrow(mock(FeignException.class));

        var request = new CreateReviewRequest(HOTEL_ID, 4, "Good", "Nice stay.", STAY_DATE);

        mockMvc.perform(post("/api/v1/reviews").header("X-User-Id", GUEST_ID.toString())
                                               .header("X-User-Email", EMAIL)
                                               .header("X-User-Role", "GUEST")
                                               .header("X-User-Name", NAME)
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.message").value("Could not verify your stay at this hotel"));
    }

    @Test
    void createReview_alreadyReviewed_returns409() throws Exception {
        given(bookingServiceClient.verifyStay(GUEST_ID, HOTEL_ID)).willReturn(new VerifyStayResponse(true));
        // Pre-save a review for the same guest + hotel
        saveReview(GUEST_ID, HOTEL_ID, 4, "Good");

        var request = new CreateReviewRequest(HOTEL_ID, 5, "Amazing!", "Best hotel ever.", STAY_DATE);

        mockMvc.perform(post("/api/v1/reviews").header("X-User-Id", GUEST_ID.toString())
                                               .header("X-User-Email", EMAIL)
                                               .header("X-User-Role", "GUEST")
                                               .header("X-User-Name", NAME)
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.message").value("You have already reviewed this hotel"));
    }

    @Test
    void createReview_blankTitle_returns400() throws Exception {
        String invalidBody = """
                {"hotelId":"%s","rating":4,"title":"","body":"Nice.","stayDate":"%s"}
                """.formatted(HOTEL_ID, STAY_DATE);

        mockMvc.perform(post("/api/v1/reviews").header("X-User-Id", GUEST_ID.toString())
                                               .header("X-User-Email", EMAIL)
                                               .header("X-User-Role", "GUEST")
                                               .header("X-User-Name", NAME)
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .content(invalidBody))
               .andExpect(status().isBadRequest());
    }

    @Test
    void createReview_ratingOutOfRange_returns400() throws Exception {
        String invalidBody = """
                {"hotelId":"%s","rating":6,"title":"Great","body":"Nice.","stayDate":"%s"}
                """.formatted(HOTEL_ID, STAY_DATE);

        mockMvc.perform(post("/api/v1/reviews").header("X-User-Id", GUEST_ID.toString())
                                               .header("X-User-Email", EMAIL)
                                               .header("X-User-Role", "GUEST")
                                               .header("X-User-Name", NAME)
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .content(invalidBody))
               .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/reviews/hotel/{hotelId} ───────────────────────────────────

    @Test
    void getHotelReviews_returns200WithPaginatedContent() throws Exception {
        saveReview(GUEST_ID, HOTEL_ID, 5, "Amazing!");
        saveReview(OTHER_ID, HOTEL_ID, 4, "Good");
        // Review for a different hotel — must not appear
        saveReview(GUEST_ID, "other-hotel", 3, "Okay");

        mockMvc.perform(get("/api/v1/reviews/hotel/{hotelId}", HOTEL_ID))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.content").isArray())
               .andExpect(jsonPath("$.content.length()").value(2))
               .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getHotelReviews_emptyHotel_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/v1/reviews/hotel/{hotelId}", "no-such-hotel"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ── GET /api/v1/reviews/my ────────────────────────────────────────────────

    @Test
    void getMyReviews_asGuest_returnsOnlyOwnReviews() throws Exception {
        saveReview(GUEST_ID, HOTEL_ID, 5, "Great!");
        saveReview(GUEST_ID, "other-hotel", 3, "Alright");
        // Another guest's review — must not appear
        saveReview(OTHER_ID, HOTEL_ID, 4, "Good");

        mockMvc.perform(get("/api/v1/reviews/my").header("X-User-Id", GUEST_ID.toString())
                                                 .header("X-User-Email", EMAIL)
                                                 .header("X-User-Role", "GUEST")
                                                 .header("X-User-Name", NAME))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getMyReviews_asHotelManager_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reviews/my").header("X-User-Id", UUID.randomUUID()
                                                                          .toString())
                                                 .header("X-User-Email", "mgr@test.com")
                                                 .header("X-User-Role", "HOTEL_MANAGER")
                                                 .header("X-User-Name", "Manager"))
               .andExpect(status().isForbidden());
    }

    // ── DELETE /api/v1/reviews/{id} ───────────────────────────────────────────

    @Test
    void deleteReview_asOwner_returns204AndRemovesDocument() throws Exception {
        var review = saveReview(GUEST_ID, HOTEL_ID, 5, "Amazing!");

        mockMvc.perform(delete("/api/v1/reviews/{id}", review.getId()).header("X-User-Id", GUEST_ID.toString())
                                                                      .header("X-User-Email", EMAIL)
                                                                      .header("X-User-Role", "GUEST")
                                                                      .header("X-User-Name", NAME))
               .andExpect(status().isNoContent());

        assertThat(reviewRepository.findById(review.getId())).isEmpty();
    }

    @Test
    void deleteReview_asAdmin_returns204() throws Exception {
        var review = saveReview(GUEST_ID, HOTEL_ID, 5, "Amazing!");
        var adminId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/reviews/{id}", review.getId()).header("X-User-Id", adminId.toString())
                                                                      .header("X-User-Email", "admin@test.com")
                                                                      .header("X-User-Role", "ADMIN")
                                                                      .header("X-User-Name", "Admin"))
               .andExpect(status().isNoContent());

        assertThat(reviewRepository.findById(review.getId())).isEmpty();
    }

    @Test
    void deleteReview_asDifferentGuest_returns403() throws Exception {
        var review = saveReview(GUEST_ID, HOTEL_ID, 5, "Amazing!");

        mockMvc.perform(delete("/api/v1/reviews/{id}", review.getId()).header("X-User-Id", OTHER_ID.toString())
                                                                      .header("X-User-Email", "other@test.com")
                                                                      .header("X-User-Role", "GUEST")
                                                                      .header("X-User-Name", "Other"))
               .andExpect(status().isForbidden());

        // Document must still exist
        assertThat(reviewRepository.findById(review.getId())).isPresent();
    }

    @Test
    void deleteReview_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/reviews/{id}", "non-existent-id").header("X-User-Id", GUEST_ID.toString())
                                                                         .header("X-User-Email", EMAIL)
                                                                         .header("X-User-Role", "GUEST")
                                                                         .header("X-User-Name", NAME))
               .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ReviewDocument saveReview(UUID guestId,
                                      String hotelId,
                                      int rating,
                                      String title) {
        var doc = ReviewDocument.builder()
                                .hotelId(hotelId)
                                .guestId(guestId.toString())
                                .guestName(NAME)
                                .rating(rating)
                                .title(title)
                                .body("Some review body.")
                                .stayDate(STAY_DATE)
                                .build();
        return reviewRepository.save(doc);
    }
}
