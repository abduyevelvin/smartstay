package com.smartstay.booking.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smartstay.booking.entity.BookingEntity;
import com.smartstay.booking.enums.BookingStatus;

import jakarta.persistence.LockModeType;

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

    /**
     * Same overlap predicate as {@link #existsOverlappingBooking} but acquires a
     * row-level exclusive lock (SELECT … FOR UPDATE) on every matching row.
     *
     * <p>Used inside the {@code createBooking} transaction so that a concurrent
     * thread blocks here instead of racing past the check and producing a duplicate
     * booking. Must be called within an active transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM BookingEntity b
            WHERE b.roomId = :roomId
              AND b.status <> :cancelled
              AND b.checkIn  < :checkOut
              AND b.checkOut > :checkIn
            """)
    List<BookingEntity> findOverlappingForUpdate(@Param("roomId") String roomId,
                                                 @Param("checkIn") LocalDate checkIn,
                                                 @Param("checkOut") LocalDate checkOut,
                                                 @Param("cancelled") BookingStatus cancelled);

    List<BookingEntity> findByHotelIdAndStatusNot(String hotelId,
                                                  BookingStatus status);

    Optional<BookingEntity> findFirstByGuestIdAndHotelIdAndStatusNotAndCheckOutLessThanEqual(UUID guestId,
                                                                                             String hotelId,
                                                                                             BookingStatus status,
                                                                                             LocalDate checkOut);
}
