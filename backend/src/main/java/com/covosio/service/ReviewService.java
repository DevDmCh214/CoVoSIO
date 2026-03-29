package com.covosio.service;

import com.covosio.dto.ReviewRequest;
import com.covosio.dto.ReviewResponse;
import com.covosio.entity.*;
import com.covosio.exception.BusinessException;
import com.covosio.exception.ResourceNotFoundException;
import com.covosio.repository.DriverProfileRepository;
import com.covosio.repository.PassengerProfileRepository;
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

    private final ReviewRepository         reviewRepository;
    private final ReservationRepository    reservationRepository;
    private final UserRepository           userRepository;
    private final DriverProfileRepository  driverProfileRepository;
    private final PassengerProfileRepository passengerProfileRepository;

    /**
     * Submits a review for a completed trip reservation (UC-P06, UC-D09).
     * The review direction (PASSENGER_TO_DRIVER or DRIVER_TO_PASSENGER) is inferred
     * from whether the author has a DriverProfile and is the trip's driver.
     * Enforces R04 (trip must be COMPLETED) and R05 (one review per direction per reservation).
     * Recalculates the target's avg_rating after each review.
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

        // Determine direction and target based on the caller's role (R08: role is the single source)
        ReviewDirection direction;
        User target;

        boolean isDriver = author.getRole() == Role.DRIVER
                && trip.getDriver().getUserId().equals(author.getId());

        if (isDriver) {
            // Driver reviewing the passenger
            direction = ReviewDirection.DRIVER_TO_PASSENGER;
            target    = reservation.getPassenger();
        } else if (reservation.getPassenger().getId().equals(author.getId())) {
            // Passenger reviewing the driver
            direction = ReviewDirection.PASSENGER_TO_DRIVER;
            target    = trip.getDriver().getUser();
        } else {
            throw new AccessDeniedException("Action not authorized");
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

        // Recalculate target's avg_rating
        if (direction == ReviewDirection.PASSENGER_TO_DRIVER) {
            recalculateDriverRating(target);
        } else {
            recalculatePassengerRating(target);
        }

        return toResponse(saved);
    }

    // --- helpers ---

    /**
     * Recomputes the driver's average rating from all reviews they have received
     * and increments their ratingCount. Persists the updated profile.
     */
    private void recalculateDriverRating(User driverUser) {
        driverProfileRepository.findByUserId(driverUser.getId()).ifPresent(dp -> {
            double newAvg = reviewRepository.findAverageRatingByTargetId(driverUser.getId()).orElse(0.0);
            dp.setAvgRating(BigDecimal.valueOf(newAvg).setScale(2, RoundingMode.HALF_UP));
            dp.setRatingCount(dp.getRatingCount() + 1);
            driverProfileRepository.save(dp);
        });
    }

    /**
     * Recomputes the passenger's average rating from all reviews they have received
     * and increments their ratingCount. Persists the updated profile.
     */
    private void recalculatePassengerRating(User passengerUser) {
        passengerProfileRepository.findByUserId(passengerUser.getId()).ifPresent(pp -> {
            double newAvg = reviewRepository.findAverageRatingByTargetId(passengerUser.getId()).orElse(0.0);
            pp.setAvgRating(BigDecimal.valueOf(newAvg).setScale(2, RoundingMode.HALF_UP));
            pp.setRatingCount(pp.getRatingCount() + 1);
            passengerProfileRepository.save(pp);
        });
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
