package com.covosio.repository;

import com.covosio.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Reservation repository stub — Phase 5.
 * Provides only the queries needed by TripService for R06 and R07.
 * Full passenger-facing queries are added in Phase 6.
 */
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /** R07: counts CONFIRMED reservations for a trip to decide edit restrictions. */
    long countByTrip_IdAndStatus(UUID tripId, String status);

    /**
     * R06: bulk-cancels all non-cancelled reservations when a trip is cancelled.
     *
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE Reservation r SET r.status = 'CANCELLED' WHERE r.trip.id = :tripId AND r.status <> 'CANCELLED'")
    int cancelAllByTripId(@Param("tripId") UUID tripId);
}
