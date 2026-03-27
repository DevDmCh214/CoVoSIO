package com.covosio.repository;

import com.covosio.entity.Review;
import com.covosio.entity.ReviewDirection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    /** R05: checks that no review with the same direction exists for this reservation. */
    boolean existsByReservation_IdAndDirection(UUID reservationId, ReviewDirection direction);

    /**
     * Returns the average rating of all reviews targeting a given user.
     * Used to recalculate driver avg_rating after each PASSENGER_TO_DRIVER review.
     */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.target.id = :targetId")
    Optional<Double> findAverageRatingByTargetId(@Param("targetId") UUID targetId);
}
