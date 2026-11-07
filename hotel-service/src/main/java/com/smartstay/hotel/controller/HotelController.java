package com.smartstay.hotel.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartstay.hotel.dto.CreateHotelRequest;
import com.smartstay.hotel.dto.CreateRoomRequest;
import com.smartstay.hotel.dto.HotelResponse;
import com.smartstay.hotel.dto.RoomResponse;
import com.smartstay.hotel.dto.UpdateHotelRequest;
import com.smartstay.hotel.dto.UpdateRatingRequest;
import com.smartstay.hotel.service.HotelService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;

    // ── Hotel endpoints ───────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('HOTEL_MANAGER')")
    public ResponseEntity<HotelResponse> createHotel(@RequestBody @Valid CreateHotelRequest request,
                                                     @RequestHeader("X-User-Id") String managerId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(hotelService.createHotel(request, managerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<HotelResponse> getHotelById(@PathVariable String id) {
        return ResponseEntity.ok(hotelService.getHotelById(id));
    }

    @GetMapping
    public ResponseEntity<Page<HotelResponse>> searchHotels(@RequestParam(required = false) String city,
                                                            @RequestParam(required = false) String country,
                                                            @RequestParam(required = false) Double minStars,
                                                            @PageableDefault(size = 20,
                                                                    sort = "starRating") Pageable pageable) {

        return ResponseEntity.ok(hotelService.searchHotels(city, country, minStars, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HOTEL_MANAGER')")
    public ResponseEntity<HotelResponse> updateHotel(@PathVariable String id,
                                                     @RequestBody @Valid UpdateHotelRequest request,
                                                     @RequestHeader("X-User-Id") String managerId) {

        return ResponseEntity.ok(hotelService.updateHotel(id, request, managerId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HOTEL_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<Void> deleteHotel(@PathVariable String id,
                                            @RequestHeader("X-User-Id") String managerId) {

        hotelService.deleteHotel(id, managerId);
        return ResponseEntity.noContent()
                             .build();
    }

    @PatchMapping("/{id}/rating")
    public ResponseEntity<Void> updateRating(@PathVariable String id,
                                             @RequestBody UpdateRatingRequest request) {
        hotelService.updateRating(id, request.averageRating(), request.reviewCount());
        return ResponseEntity.noContent()
                             .build();
    }

    // ── Room endpoints ────────────────────────────────────────────────────────

    @PostMapping("/{hotelId}/rooms")
    @PreAuthorize("hasRole('HOTEL_MANAGER')")
    public ResponseEntity<RoomResponse> addRoom(@PathVariable String hotelId,
                                                @RequestBody @Valid CreateRoomRequest request,
                                                @RequestHeader("X-User-Id") String managerId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(hotelService.addRoom(hotelId, request, managerId));
    }

    @GetMapping("/{hotelId}/rooms/{roomId}")
    public ResponseEntity<RoomResponse> getRoomById(@PathVariable String hotelId,
                                                    @PathVariable String roomId) {
        return ResponseEntity.ok(hotelService.getRoomByHotelAndById(hotelId, roomId));
    }

    @GetMapping("/{hotelId}/rooms")
    public ResponseEntity<List<RoomResponse>> getRoomsByHotel(@PathVariable String hotelId) {
        return ResponseEntity.ok(hotelService.getRoomsByHotel(hotelId));
    }

    @GetMapping("/{hotelId}/availability")
    public ResponseEntity<List<RoomResponse>> getAvailableRooms(@PathVariable String hotelId) {
        return ResponseEntity.ok(hotelService.getAvailableRooms(hotelId));
    }
}
