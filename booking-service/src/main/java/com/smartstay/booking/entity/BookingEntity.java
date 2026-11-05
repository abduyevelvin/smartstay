package com.smartstay.booking.entity;

import java.time.LocalDate;
import java.util.UUID;

import com.smartstay.booking.enums.BookingStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "bookings")
public class BookingEntity extends BaseEntity {

    @Column(name = "guest_id", nullable = false)
    private UUID guestId;

    @Column(name = "guest_email", nullable = false)
    private String guestEmail;

    /**
     * References hotel-service MongoDB ObjectId (stored as VARCHAR).
     */
    @Column(name = "hotel_id", nullable = false)
    private String hotelId;

    /**
     * References hotel-service room MongoDB ObjectId (stored as VARCHAR).
     */
    @Column(name = "room_id", nullable = false)
    private String roomId;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(name = "total_price", nullable = false)
    private double totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "special_requests")
    private String specialRequests;
}
