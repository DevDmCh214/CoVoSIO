package com.covosio.repository;

import com.covosio.entity.Trip;
import com.covosio.entity.TripStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    /**
     * Full-text search for available trips (UC-P01).
     * All filters are optional — null values are ignored.
     * Uses case-insensitive LIKE on city labels and a day-range for date filtering.
     */
    @Query(value = """
            SELECT t FROM Trip t
            WHERE t.status = :status
              AND t.seatsAvailable > 0
              AND (:origin      IS NULL OR LOWER(t.originLabel)      LIKE LOWER(CONCAT('%', CAST(:origin      AS string), '%')))
              AND (:destination IS NULL OR LOWER(t.destinationLabel) LIKE LOWER(CONCAT('%', CAST(:destination AS string), '%')))
              AND t.departureAt >= :dateStart
              AND t.departureAt <  :dateEnd
            """,
           countQuery = """
            SELECT COUNT(t) FROM Trip t
            WHERE t.status = :status
              AND t.seatsAvailable > 0
              AND (:origin      IS NULL OR LOWER(t.originLabel)      LIKE LOWER(CONCAT('%', CAST(:origin      AS string), '%')))
              AND (:destination IS NULL OR LOWER(t.destinationLabel) LIKE LOWER(CONCAT('%', CAST(:destination AS string), '%')))
              AND t.departureAt >= :dateStart
              AND t.departureAt <  :dateEnd
            """)
    Page<Trip> search(
            @Param("status")      TripStatus    status,
            @Param("origin")      String        origin,
            @Param("destination") String        destination,
            @Param("dateStart")   LocalDateTime dateStart,
            @Param("dateEnd")     LocalDateTime dateEnd,
            Pageable pageable
    );

    /** Driver's own trips, newest departure first (UC-D07). */
    Page<Trip> findByDriver_UserIdOrderByDepartureAtDesc(UUID driverId, Pageable pageable);

    /** AVAILABLE trips with at least one free seat — passenger map view (UC-P07). */
    Page<Trip> findByStatusAndSeatsAvailableGreaterThan(TripStatus status, int minSeats, Pageable pageable);

    /** All of a driver's trips regardless of status — driver map view (UC-D10). */
    Page<Trip> findByDriver_UserId(UUID driverId, Pageable pageable);

    /**
     * Loads a trip with a pessimistic write lock for R02 (atomic seat check + decrement).
     * Must be called inside a @Transactional method.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Trip t WHERE t.id = :id")
    Optional<Trip> findByIdForUpdate(@Param("id") UUID id);

    /** Admin stats: count trips by status (UC-A11). */
    long countByStatus(TripStatus status);

    /** Role-change guard: checks whether a driver has any trips. */
    boolean existsByDriver_UserId(UUID driverId);
}
