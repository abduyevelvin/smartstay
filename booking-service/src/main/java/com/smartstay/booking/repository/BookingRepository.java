package com.smartstay.booking.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smartstay.booking.entity.BookingEntity;
import com.smartstay.booking.enums.BookingStatus;

public interface BookingRepository extends JpaRepository<BookingEntity, UUID> {

    Page<BookingEntity> findByGuestId(UUID guestId,
                                      Pageable pageable);

    List<BookingEntity> findByHotelIdAndStatus(String hotelId,
                                               BookingStatus status);

    List<BookingEntity> findByHotelId(String hotelId);

    @Query("""
            SELECT CASE WHEN COUNT(b) > 0 THEN TRUE ELSE FALSE END
            FROM BookingEntity b
            WHERE b.roomId = :roomId
              AND b.status <> :cancelled
              AND b.checkIn  < :checkOut
              AND b.checkOut > :checkIn
            """)
    boolean existsOverlappingBooking(@Param("roomId") String roomId,
                                     @Param("checkIn") LocalDate checkIn,
                                     @Param("checkOut") LocalDate checkOut,
                                     @Param("cancelled") BookingStatus cancelled);
}
