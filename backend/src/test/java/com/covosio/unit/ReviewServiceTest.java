package com.covosio.unit;

import com.covosio.dto.ReviewRequest;
import com.covosio.dto.ReviewResponse;
import com.covosio.entity.*;
import com.covosio.exception.BusinessException;
import com.covosio.exception.ResourceNotFoundException;
import com.covosio.repository.ReservationRepository;
import com.covosio.repository.ReviewRepository;
import com.covosio.repository.UserRepository;
import com.covosio.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository      reviewRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private UserRepository        userRepository;

    @InjectMocks
    private ReviewService reviewService;

    // --- createReview — nominal cases ---

    @Test
    void createReview_shouldCreateReview_whenPassengerReviewsDriver() {
        Driver    driver      = buildDriver("driver@test.com");
        Passenger passenger   = buildPassenger("pass@test.com");
        Trip      trip        = buildTrip(driver, TripStatus.COMPLETED);
        Reservation reservation = buildReservation(trip, passenger);

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(reviewRepository.existsByReservation_IdAndDirection(
                reservation.getId(), ReviewDirection.PASSENGER_TO_DRIVER)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(LocalDateTime.now());
            return r;
        });
        when(reviewRepository.findAverageRatingByTargetId(driver.getId()))
                .thenReturn(Optional.of(4.5));
        when(userRepository.save(driver)).thenReturn(driver);

        ReviewResponse response = reviewService.createReview(
                "pass@test.com", reservation.getId(), buildRequest(5, "Great driver!"));

        assertThat(response.getDirection()).isEqualTo(ReviewDirection.PASSENGER_TO_DRIVER);
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getComment()).isEqualTo("Great driver!");
        assertThat(driver.getAvgRating()).isEqualByComparingTo("4.50");
        verify(reviewRepository).save(any(Review.class));
        verify(userRepository).save(driver);
    }

    @Test
    void createReview_shouldCreateReview_whenDriverReviewsPassenger() {
        Driver    driver      = buildDriver("driver@test.com");
        Passenger passenger   = buildPassenger("pass@test.com");
        Trip      trip        = buildTrip(driver, TripStatus.COMPLETED);
        Reservation reservation = buildReservation(trip, passenger);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(reviewRepository.existsByReservation_IdAndDirection(
                reservation.getId(), ReviewDirection.DRIVER_TO_PASSENGER)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(LocalDateTime.now());
            return r;
        });

        ReviewResponse response = reviewService.createReview(
                "driver@test.com", reservation.getId(), buildRequest(4, "Good passenger"));

        assertThat(response.getDirection()).isEqualTo(ReviewDirection.DRIVER_TO_PASSENGER);
        assertThat(response.getRating()).isEqualTo(4);
        // Driver's own avg_rating must NOT be recalculated
        verify(userRepository, never()).save(any());
    }

    // --- R04 ---

    @Test
    void createReview_shouldThrowBusinessException_whenTripNotCompleted() {
        Driver    driver      = buildDriver("driver@test.com");
        Passenger passenger   = buildPassenger("pass@test.com");
        Trip      trip        = buildTrip(driver, TripStatus.AVAILABLE); // not completed
        Reservation reservation = buildReservation(trip, passenger);

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() ->
                reviewService.createReview("pass@test.com", reservation.getId(), buildRequest(5, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("R04");
    }

    // --- R05 ---

    @Test
    void createReview_shouldThrowBusinessException_whenReviewAlreadyExists() {
        Driver    driver      = buildDriver("driver@test.com");
        Passenger passenger   = buildPassenger("pass@test.com");
        Trip      trip        = buildTrip(driver, TripStatus.COMPLETED);
        Reservation reservation = buildReservation(trip, passenger);

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(reviewRepository.existsByReservation_IdAndDirection(
                reservation.getId(), ReviewDirection.PASSENGER_TO_DRIVER)).thenReturn(true);

        assertThatThrownBy(() ->
                reviewService.createReview("pass@test.com", reservation.getId(), buildRequest(5, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("R05");
    }

    // --- authorization ---

    @Test
    void createReview_shouldThrowAccessDeniedException_whenPassengerNotOwner() {
        Driver    driver      = buildDriver("driver@test.com");
        Passenger passenger   = buildPassenger("pass@test.com");
        Passenger other       = buildPassenger("other@test.com");
        Trip      trip        = buildTrip(driver, TripStatus.COMPLETED);
        Reservation reservation = buildReservation(trip, other); // owned by 'other'

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() ->
                reviewService.createReview("pass@test.com", reservation.getId(), buildRequest(4, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createReview_shouldThrowAccessDeniedException_whenDriverNotOwner() {
        Driver    driver      = buildDriver("driver@test.com");
        Driver    otherDriver = buildDriver("other@test.com");
        Passenger passenger   = buildPassenger("pass@test.com");
        Trip      trip        = buildTrip(otherDriver, TripStatus.COMPLETED); // owned by other driver
        Reservation reservation = buildReservation(trip, passenger);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() ->
                reviewService.createReview("driver@test.com", reservation.getId(), buildRequest(3, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createReview_shouldThrowResourceNotFoundException_whenReservationNotFound() {
        Passenger passenger     = buildPassenger("pass@test.com");
        UUID      reservationId = UUID.randomUUID();

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                reviewService.createReview("pass@test.com", reservationId, buildRequest(5, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(reservationId.toString());
    }

    // --- helpers ---

    private Driver buildDriver(String email) {
        Driver d = new Driver();
        d.setId(UUID.randomUUID());
        d.setEmail(email);
        d.setPasswordHash("hashed");
        d.setFirstName("Jean");
        d.setLastName("Dupont");
        d.setIsActive(true);
        d.setLicenseVerified(true);
        d.setTotalTripsDriven(0);
        d.setAvgRating(BigDecimal.ZERO);
        return d;
    }

    private Passenger buildPassenger(String email) {
        Passenger p = new Passenger();
        p.setId(UUID.randomUUID());
        p.setEmail(email);
        p.setPasswordHash("hashed");
        p.setFirstName("Alice");
        p.setLastName("Martin");
        p.setIsActive(true);
        p.setTotalTripsDone(0);
        p.setAvgRating(BigDecimal.ZERO);
        return p;
    }

    private Trip buildTrip(Driver driver, TripStatus status) {
        Trip t = new Trip();
        t.setId(UUID.randomUUID());
        t.setDriver(driver);
        t.setOriginLabel("Paris, France");
        t.setOriginLat(BigDecimal.valueOf(48.8566));
        t.setOriginLng(BigDecimal.valueOf(2.3522));
        t.setDestinationLabel("Lyon, France");
        t.setDestLat(BigDecimal.valueOf(45.7640));
        t.setDestLng(BigDecimal.valueOf(4.8357));
        t.setDepartureAt(LocalDateTime.now().minusDays(1));
        t.setSeatsAvailable(0);
        t.setPricePerSeat(BigDecimal.valueOf(15.00));
        t.setPetsAllowed(false);
        t.setSmokingAllowed(false);
        t.setStatus(status);
        t.setCreatedAt(LocalDateTime.now().minusDays(8));
        return t;
    }

    private Reservation buildReservation(Trip trip, Passenger passenger) {
        Reservation r = new Reservation();
        r.setId(UUID.randomUUID());
        r.setTrip(trip);
        r.setPassenger(passenger);
        r.setSeatsBooked(1);
        r.setStatus(ReservationStatus.CONFIRMED);
        r.setCreatedAt(LocalDateTime.now().minusDays(7));
        return r;
    }

    private ReviewRequest buildRequest(int rating, String comment) {
        ReviewRequest req = new ReviewRequest();
        req.setRating(rating);
        req.setComment(comment);
        return req;
    }
}
