package com.covosio.unit;

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

    @Mock private ReviewRepository         reviewRepository;
    @Mock private ReservationRepository    reservationRepository;
    @Mock private UserRepository           userRepository;
    @Mock private DriverProfileRepository  driverProfileRepository;
    @Mock private PassengerProfileRepository passengerProfileRepository;

    @InjectMocks
    private ReviewService reviewService;

    // --- createReview — nominal cases ---

    @Test
    void createReview_shouldCreateReview_whenPassengerReviewsDriver() {
        User driverUser    = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(driverUser);
        User passenger     = buildUser("pass@test.com");
        Trip trip          = buildTrip(driverProfile, TripStatus.COMPLETED);
        Reservation reservation = buildReservation(trip, passenger);

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        // passenger is not a driver
        when(driverProfileRepository.existsByUserId(passenger.getId())).thenReturn(false);
        when(reviewRepository.existsByReservation_IdAndDirection(
                reservation.getId(), ReviewDirection.PASSENGER_TO_DRIVER)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(LocalDateTime.now());
            return r;
        });
        when(reviewRepository.findAverageRatingByTargetId(driverUser.getId()))
                .thenReturn(Optional.of(4.5));
        when(driverProfileRepository.findByUserId(driverUser.getId()))
                .thenReturn(Optional.of(driverProfile));

        ReviewResponse response = reviewService.createReview(
                "pass@test.com", reservation.getId(), buildRequest(5, "Great driver!"));

        assertThat(response.getDirection()).isEqualTo(ReviewDirection.PASSENGER_TO_DRIVER);
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getComment()).isEqualTo("Great driver!");
        assertThat(driverProfile.getAvgRating()).isEqualByComparingTo("4.50");
        verify(reviewRepository).save(any(Review.class));
        verify(driverProfileRepository).save(driverProfile);
    }

    @Test
    void createReview_shouldCreateReview_whenDriverReviewsPassenger() {
        User driverUser    = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(driverUser);
        User passenger     = buildUser("pass@test.com");
        Trip trip          = buildTrip(driverProfile, TripStatus.COMPLETED);
        Reservation reservation = buildReservation(trip, passenger);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driverUser));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        // driver is a driver and owns the trip
        when(driverProfileRepository.existsByUserId(driverUser.getId())).thenReturn(true);
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
        // Driver's avg_rating must NOT be recalculated on DRIVER_TO_PASSENGER review
        verify(userRepository, never()).save(any());
    }

    // --- R04 ---

    @Test
    void createReview_shouldThrowBusinessException_whenTripNotCompleted() {
        User driverUser    = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(driverUser);
        User passenger     = buildUser("pass@test.com");
        Trip trip          = buildTrip(driverProfile, TripStatus.AVAILABLE); // not completed
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
        User driverUser    = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(driverUser);
        User passenger     = buildUser("pass@test.com");
        Trip trip          = buildTrip(driverProfile, TripStatus.COMPLETED);
        Reservation reservation = buildReservation(trip, passenger);

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(driverProfileRepository.existsByUserId(passenger.getId())).thenReturn(false);
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
        User driverUser    = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(driverUser);
        User passenger     = buildUser("pass@test.com");
        User other         = buildUser("other@test.com");
        Trip trip          = buildTrip(driverProfile, TripStatus.COMPLETED);
        Reservation reservation = buildReservation(trip, other); // owned by 'other'

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(driverProfileRepository.existsByUserId(passenger.getId())).thenReturn(false);

        assertThatThrownBy(() ->
                reviewService.createReview("pass@test.com", reservation.getId(), buildRequest(4, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createReview_shouldThrowAccessDeniedException_whenDriverNotOwner() {
        User driverUser     = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(driverUser);
        User otherDriverUser = buildUser("other@test.com");
        DriverProfile otherProfile = buildDriverProfile(otherDriverUser);
        User passenger      = buildUser("pass@test.com");
        Trip trip           = buildTrip(otherProfile, TripStatus.COMPLETED); // owned by other driver
        Reservation reservation = buildReservation(trip, passenger);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driverUser));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        // driver@test.com has a driver profile, but is NOT the trip driver
        when(driverProfileRepository.existsByUserId(driverUser.getId())).thenReturn(true);

        assertThatThrownBy(() ->
                reviewService.createReview("driver@test.com", reservation.getId(), buildRequest(3, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createReview_shouldThrowResourceNotFoundException_whenReservationNotFound() {
        User passenger     = buildUser("pass@test.com");
        UUID reservationId = UUID.randomUUID();

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                reviewService.createReview("pass@test.com", reservationId, buildRequest(5, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(reservationId.toString());
    }

    // --- helpers ---

    private User buildUser(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("hashed")
                .firstName("Jean")
                .lastName("Dupont")
                .isActive(true)
                .build();
    }

    private DriverProfile buildDriverProfile(User user) {
        return DriverProfile.builder()
                .userId(user.getId())
                .user(user)
                .avgRating(BigDecimal.ZERO)
                .totalTripsDriven(0)
                .build();
    }

    private Trip buildTrip(DriverProfile driverProfile, TripStatus status) {
        Trip t = new Trip();
        t.setId(UUID.randomUUID());
        t.setDriver(driverProfile);
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

    private Reservation buildReservation(Trip trip, User passenger) {
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
