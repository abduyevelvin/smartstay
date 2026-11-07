package com.smartstay.hotel.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.smartstay.hotel.dto.CreateHotelRequest;
import com.smartstay.hotel.dto.CreateRoomRequest;
import com.smartstay.hotel.dto.HotelResponse;
import com.smartstay.hotel.dto.RoomResponse;
import com.smartstay.hotel.dto.UpdateRatingRequest;
import com.smartstay.hotel.enums.Type;
import com.smartstay.hotel.repository.HotelRepository;
import com.smartstay.hotel.repository.RoomRepository;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HotelControllerIT {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> mongo.getConnectionString() + "/hotel_db_test");
    }

    @Autowired
    TestRestTemplate restTemplate;
    @Autowired
    HotelRepository hotelRepository;
    @Autowired
    RoomRepository roomRepository;

    @BeforeEach
    void cleanup() {
        roomRepository.deleteAll();
        hotelRepository.deleteAll();
    }

    // ── POST /api/v1/hotels ───────────────────────────────────────────────────

    @Test
    void createHotel_shouldReturn201ForHotelManager() {
        ResponseEntity<HotelResponse> response = restTemplate.exchange("/api/v1/hotels", HttpMethod.POST,
                new HttpEntity<>(validHotelRequest(), managerHeaders("mgr-1")), HotelResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()
                           .name()).isEqualTo("Grand Hotel");
        assertThat(response.getBody()
                           .managerId()).isEqualTo("mgr-1");
    }

    @Test
    void createHotel_shouldReturn403ForGuest() {
        ResponseEntity<String> response = restTemplate.exchange("/api/v1/hotels", HttpMethod.POST,
                new HttpEntity<>(validHotelRequest(), guestHeaders("guest-1")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createHotel_shouldReturn400WhenNameBlank() {
        CreateHotelRequest invalid = new CreateHotelRequest("", "Desc", "Baku", "Azerbaijan", "Main St", 5.0, "14:00",
                "12:00", null, null, null);

        ResponseEntity<String> response = restTemplate.exchange("/api/v1/hotels", HttpMethod.POST,
                new HttpEntity<>(invalid, managerHeaders("mgr-1")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── GET /api/v1/hotels/{id} ───────────────────────────────────────────────

    @Test
    void getHotelById_shouldReturn200() {
        HotelResponse created = createHotel("mgr-1");

        ResponseEntity<HotelResponse> response = restTemplate.getForEntity("/api/v1/hotels/" + created.id(),
                HotelResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()
                           .id()).isEqualTo(created.id());
    }

    @Test
    void getHotelById_shouldReturn404WhenNotFound() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/hotels/nonexistent-id", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── GET /api/v1/hotels ────────────────────────────────────────────────────

    @Test
    void searchHotels_shouldReturn200WithResults() {
        createHotel("mgr-1");

        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/hotels?city=Baku", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void searchHotels_shouldReturn200WithNoFilters() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/hotels", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── PUT /api/v1/hotels/{id} ───────────────────────────────────────────────

    @Test
    void updateHotel_shouldReturn200ForOwner() {
        HotelResponse created = createHotel("mgr-1");

        var update = new com.smartstay.hotel.dto.UpdateHotelRequest("Updated Hotel", null, null, null, null, null, null,
                null, null, null, null);

        ResponseEntity<HotelResponse> response = restTemplate.exchange("/api/v1/hotels/" + created.id(), HttpMethod.PUT,
                new HttpEntity<>(update, managerHeaders("mgr-1")), HotelResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()
                           .name()).isEqualTo("Updated Hotel");
    }

    @Test
    void updateHotel_shouldReturn403ForNonOwner() {
        HotelResponse created = createHotel("mgr-1");

        var update = new com.smartstay.hotel.dto.UpdateHotelRequest("Hacked", null, null, null, null, null, null, null,
                null, null, null);

        ResponseEntity<String> response = restTemplate.exchange("/api/v1/hotels/" + created.id(), HttpMethod.PUT,
                new HttpEntity<>(update, managerHeaders("mgr-2")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── DELETE /api/v1/hotels/{id} ────────────────────────────────────────────

    @Test
    void deleteHotel_shouldReturn204ForOwner() {
        HotelResponse created = createHotel("mgr-1");

        ResponseEntity<Void> response = restTemplate.exchange("/api/v1/hotels/" + created.id(), HttpMethod.DELETE,
                new HttpEntity<>(managerHeaders("mgr-1")), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(hotelRepository.findById(created.id())).isEmpty();
    }

    @Test
    void deleteHotel_shouldReturn403ForNonOwner() {
        HotelResponse created = createHotel("mgr-1");

        ResponseEntity<String> response = restTemplate.exchange("/api/v1/hotels/" + created.id(), HttpMethod.DELETE,
                new HttpEntity<>(managerHeaders("mgr-2")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── POST /api/v1/hotels/{id}/rooms ────────────────────────────────────────

    @Test
    void addRoom_shouldReturn201ForOwner() {
        HotelResponse created = createHotel("mgr-1");

        ResponseEntity<RoomResponse> response = restTemplate.exchange("/api/v1/hotels/" + created.id() + "/rooms",
                HttpMethod.POST, new HttpEntity<>(validRoomRequest(101), managerHeaders("mgr-1")), RoomResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()
                           .roomNumber()).isEqualTo(101);
        assertThat(response.getBody()
                           .available()).isTrue();
    }

    @Test
    void addRoom_shouldReturn409ForDuplicateRoomNumber() {
        HotelResponse created = createHotel("mgr-1");

        restTemplate.exchange("/api/v1/hotels/" + created.id() + "/rooms", HttpMethod.POST,
                new HttpEntity<>(validRoomRequest(101), managerHeaders("mgr-1")), RoomResponse.class);

        ResponseEntity<String> second = restTemplate.exchange("/api/v1/hotels/" + created.id() + "/rooms",
                HttpMethod.POST, new HttpEntity<>(validRoomRequest(101), managerHeaders("mgr-1")), String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ── GET /api/v1/hotels/{id}/rooms ─────────────────────────────────────────

    @Test
    void getRooms_shouldReturn200() {
        HotelResponse created = createHotel("mgr-1");
        restTemplate.exchange("/api/v1/hotels/" + created.id() + "/rooms", HttpMethod.POST,
                new HttpEntity<>(validRoomRequest(101), managerHeaders("mgr-1")), RoomResponse.class);

        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/hotels/" + created.id() + "/rooms",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── GET /api/v1/hotels/{id}/availability ──────────────────────────────────

    @Test
    void getAvailableRooms_shouldReturn200() {
        HotelResponse created = createHotel("mgr-1");

        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/hotels/" + created.id() + "/availability",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── PATCH /api/v1/hotels/{id}/rating ─────────────────────────────────────

    @Test
    void updateRating_shouldReturn204AndPersistChanges() {
        HotelResponse created = createHotel("mgr-1");
        var request = new UpdateRatingRequest(4.5, 10);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/hotels/" + created.id() + "/rating",
                HttpMethod.PATCH,
                new HttpEntity<>(request),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var updated = hotelRepository.findById(created.id()).orElseThrow();
        assertThat(updated.getAverageRating()).isEqualTo(4.5);
        assertThat(updated.getReviewCount()).isEqualTo(10);
    }

    @Test
    void updateRating_shouldReturn404WhenHotelNotFound() {
        var request = new UpdateRatingRequest(4.5, 10);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/hotels/nonexistent-id/rating",
                HttpMethod.PATCH,
                new HttpEntity<>(request),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HttpHeaders managerHeaders(String managerId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", managerId);
        headers.set("X-User-Role", "HOTEL_MANAGER");
        return headers;
    }

    private HttpHeaders guestHeaders(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId);
        headers.set("X-User-Role", "GUEST");
        return headers;
    }

    private HotelResponse createHotel(String managerId) {
        ResponseEntity<HotelResponse> response = restTemplate.exchange("/api/v1/hotels", HttpMethod.POST,
                new HttpEntity<>(validHotelRequest(), managerHeaders(managerId)), HotelResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private CreateHotelRequest validHotelRequest() {
        return new CreateHotelRequest("Grand Hotel", "Luxury hotel in Baku", "Baku", "Azerbaijan", "12 Neftchilar Ave",
                5.0, "14:00", "12:00", List.of("WiFi", "Pool"), List.of(), null);
    }

    private CreateRoomRequest validRoomRequest(int roomNumber) {
        return new CreateRoomRequest(roomNumber, Type.DOUBLE, 150.0, 2, 1, List.of("AC"), List.of());
    }
}
