package com.covosio.repository;

import com.covosio.entity.Reservation;
import com.covosio.entity.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /** R07: counts CONFIRMED reservations for a trip to decide edit restrictions. */
    long countByTrip_IdAndStatus(UUID tripId, ReservationStatus status);

    /**
     * R06: bulk-cancels all non-cancelled reservations when a trip is cancelled.
     *
     * @param tripId    the trip being cancelled
     * @param cancelled the CANCELLED status value
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE Reservation r SET r.status = :cancelled WHERE r.trip.id = :tripId AND r.status <> :cancelled")
    int cancelAllByTripId(@Param("tripId") UUID tripId, @Param("cancelled") ReservationStatus cancelled);

    /** UC-P05: passenger's own reservations, newest first. */
    Page<Reservation> findByPassenger_IdOrderByCreatedAtDesc(UUID passengerId, Pageable pageable);

    /** UC-D08: all reservations for a trip, newest first. */
    Page<Reservation> findByTrip_IdOrderByCreatedAtDesc(UUID tripId, Pageable pageable);

    /** Admin stats: count reservations by status (UC-A11). */
    long countByStatus(ReservationStatus status);

    /** Role-change guard: checks whether a user has any reservations as passenger. */
    boolean existsByPassenger_Id(UUID passengerId);
}
