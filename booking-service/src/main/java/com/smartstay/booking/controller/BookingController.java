package com.smartstay.booking.controller;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartstay.booking.dto.BookingResponse;
import com.smartstay.booking.dto.BulkAvailabilityRequest;
import com.smartstay.booking.dto.CreateBookingRequest;
import com.smartstay.booking.enums.BookingStatus;
import com.smartstay.booking.security.UserPrincipal;
import com.smartstay.booking.service.BookingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @Value("${app.internal-secret}")
    private String internalSecret;

    @PostMapping
    @PreAuthorize("hasRole('GUEST')")
    public ResponseEntity<BookingResponse> createBooking(@RequestBody @Valid CreateBookingRequest request,
                                                         Authentication auth) {
        final var principal = (UserPrincipal) auth.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(bookingService.createBooking(request, principal.userId(), principal.email()));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('GUEST')")
    public ResponseEntity<Page<BookingResponse>> getMyBookings(Authentication auth,
                                                               @PageableDefault(size = 20) Pageable pageable) {
        final var principal = (UserPrincipal) auth.getPrincipal();
        return ResponseEntity.ok(bookingService.getMyBookings(principal.userId(), pageable));
    }

    @GetMapping("/hotel/{hotelId}")
    @PreAuthorize("hasRole('HOTEL_MANAGER')")
    public ResponseEntity<java.util.List<BookingResponse>> getHotelBookings(@PathVariable String hotelId,
                                                                            @RequestParam(
                                                                                    required = false) BookingStatus status,
                                                                            Authentication auth) {
        final var principal = (UserPrincipal) auth.getPrincipal();
        final String userRole = auth.getAuthorities()
                                    .stream()
                                    .findFirst()
                                    .map(a -> a.getAuthority()
                                               .replace("ROLE_", ""))
                                    .orElse("");
        return ResponseEntity.ok(bookingService.getHotelBookings(hotelId, status, principal.userId(), userRole));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(@PathVariable UUID id,
                                                         Authentication auth) {
        final var principal = (UserPrincipal) auth.getPrincipal();
        final String userRole = auth.getAuthorities()
                                    .stream()
                                    .findFirst()
                                    .map(a -> a.getAuthority()
                                               .replace("ROLE_", ""))
                                    .orElse("");
        return ResponseEntity.ok(bookingService.cancelBooking(id, principal.userId(), userRole));
    }

    @GetMapping("/availability")
    public ResponseEntity<Map<String, Boolean>> checkAvailability(@RequestParam String roomId,
                                                                  @RequestParam @DateTimeFormat(
                                                                          iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                                                                  @RequestParam @DateTimeFormat(
                                                                          iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {

        boolean available = bookingService.checkRoomAvailability(roomId, checkIn, checkOut);
        return ResponseEntity.ok(Map.of("available", available));
    }

    /**
     * Bulk availability check — called internally by hotel-service.
     * Accepts a list of room IDs and a date range; returns a map of roomId → available.
     */
    @PostMapping("/bulk-availability")
    public ResponseEntity<Map<String, Boolean>> checkBulkAvailability(@RequestBody BulkAvailabilityRequest request) {

        return ResponseEntity.ok(
                bookingService.checkBulkAvailability(request.roomIds(), request.checkIn(), request.checkOut()));
    }

    @GetMapping("/verify-stay")
    public ResponseEntity<Map<String, Boolean>> verifyStay(@RequestParam UUID guestId,
                                                           @RequestParam String hotelId,
                                                           @RequestHeader(value = "X-Internal-Secret",
                                                                   required = false) String secret) {
        if (!internalSecret.equals(secret)) {
            log.warn("Rejected verify-stay for guestId=[{}] hotelId=[{}] — missing or invalid X-Internal-Secret",
                    guestId, hotelId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                 .build();
        }
        return ResponseEntity.ok(Map.of("isStayed", bookingService.guestIsStayed(guestId, hotelId)));
    }
}
