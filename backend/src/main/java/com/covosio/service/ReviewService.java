package com.covosio.service;

import com.covosio.dto.ReviewRequest;
import com.covosio.dto.ReviewResponse;
import com.covosio.entity.*;
import com.covosio.exception.BusinessException;
import com.covosio.exception.ResourceNotFoundException;
import com.covosio.repository.ReservationRepository;
import com.covosio.repository.ReviewRepository;
import com.covosio.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Handles post-trip review use cases: passenger reviews driver (UC-P06)
 * and driver reviews passenger (UC-D09).
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository      reviewRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository        userRepository;

    /**
     * Submits a review for a completed trip reservation (UC-P06, UC-D09).
     * The review direction (PASSENGER_TO_DRIVER or DRIVER_TO_PASSENGER) is inferred
     * from the authenticated user's role.
     * Enforces R04 (trip must be COMPLETED) and R05 (one review per direction per reservation).
     * Recalculates the driver's avg_rating after a PASSENGER_TO_DRIVER review.
     *
     * @param authorEmail   authenticated user's email (passenger or driver)
     * @param reservationId UUID of the reservation being reviewed
     * @param request       review payload (rating 1–5, optional comment)
     * @return ReviewResponse for the created review
     * @throws ResourceNotFoundException if reservation not found
     * @throws AccessDeniedException     if the caller is not a party to the reservation
     * @throws BusinessException         if R04 or R05 is violated
     */
    @Transactional
    public ReviewResponse createReview(String authorEmail, UUID reservationId, ReviewRequest request) {
        User author = userRepository.findByEmail(authorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + authorEmail));

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        // R04 — review only allowed after trip is COMPLETED
        Trip trip = reservation.getTrip();
        if (trip.getStatus() != TripStatus.COMPLETED) {
            throw new BusinessException(
                    "Reviews can only be submitted after the trip is completed (R04)");
        }

        // Determine direction and target based on the caller's type
        ReviewDirection direction;
        User target;

        if (author instanceof Passenger passenger) {
            if (!reservation.getPassenger().getId().equals(passenger.getId())) {
                throw new AccessDeniedException("Action not authorized");
            }
            direction = ReviewDirection.PASSENGER_TO_DRIVER;
            target    = trip.getDriver();

        } else if (author instanceof Driver driver) {
            if (!trip.getDriver().getId().equals(driver.getId())) {
                throw new AccessDeniedException("Action not authorized");
            }
            direction = ReviewDirection.DRIVER_TO_PASSENGER;
            target    = reservation.getPassenger();

        } else {
            throw new AccessDeniedException("Only passengers and drivers can submit reviews");
        }

        // R05 — one review per reservation per direction
        if (reviewRepository.existsByReservation_IdAndDirection(reservationId, direction)) {
            throw new BusinessException(
                    "A review for this reservation in this direction already exists (R05)");
        }

        Review review = Review.builder()
                .reservation(reservation)
                .author(author)
                .target(target)
                .direction(direction)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        Review saved = reviewRepository.save(review);

        // Recalculate driver's avg_rating after a passenger-to-driver review
        // When direction is PASSENGER_TO_DRIVER, target is the driver
        if (direction == ReviewDirection.PASSENGER_TO_DRIVER) {
            recalculateDriverRating((Driver) target);
        }

        return toResponse(saved);
    }

    // --- helpers ---

    /**
     * Recomputes the driver's average rating from all reviews they have received
     * and persists the updated value.
     */
    private void recalculateDriverRating(Driver driver) {
        double newAvg = reviewRepository.findAverageRatingByTargetId(driver.getId())
                .orElse(0.0);
        driver.setAvgRating(BigDecimal.valueOf(newAvg).setScale(2, RoundingMode.HALF_UP));
        userRepository.save(driver);
    }

    private ReviewResponse toResponse(Review r) {
        return ReviewResponse.builder()
                .id(r.getId())
                .reservationId(r.getReservation().getId())
                .authorId(r.getAuthor().getId())
                .authorFirstName(r.getAuthor().getFirstName())
                .authorLastName(r.getAuthor().getLastName())
                .targetId(r.getTarget().getId())
                .targetFirstName(r.getTarget().getFirstName())
                .targetLastName(r.getTarget().getLastName())
                .direction(r.getDirection())
                .rating(r.getRating())
                .comment(r.getComment())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
