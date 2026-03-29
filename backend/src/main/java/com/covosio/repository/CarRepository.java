package com.covosio.repository;

import com.covosio.entity.Car;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CarRepository extends JpaRepository<Car, UUID> {

    List<Car> findByDriver_UserIdAndIsActiveTrue(UUID driverId);

    /**
     * Counts future AVAILABLE trips that use the given car.
     * Used to enforce R09: deletion blocked if a future AVAILABLE trip is attached.
     */
    @Query(value = """
            SELECT COUNT(*) FROM trips
             WHERE car_id   = :carId
               AND status   = 'AVAILABLE'
               AND departure_at > NOW()
            """, nativeQuery = true)
    long countFutureAvailableTripsForCar(@Param("carId") UUID carId);
}
