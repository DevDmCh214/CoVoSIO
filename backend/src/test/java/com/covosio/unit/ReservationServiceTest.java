package com.covosio.unit;

import com.covosio.dto.ReservationRequest;
import com.covosio.dto.ReservationResponse;
import com.covosio.entity.*;
import com.covosio.exception.BusinessException;
import com.covosio.exception.ResourceNotFoundException;
import com.covosio.repository.DriverProfileRepository;
import com.covosio.repository.ReservationRepository;
import com.covosio.repository.TripRepository;
import com.covosio.repository.UserRepository;
import com.covosio.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private ReservationRepository   reservationRepository;
    @Mock private TripRepository          tripRepository;
    @Mock private UserRepository          userRepository;
    @Mock private DriverProfileRepository driverProfileRepository;

    @InjectMocks
    private ReservationService reservationService;

    // --- createReservation (UC-P03) ---

    @Test
    void createReservation_shouldCreateReservation_whenValid() {
        User passenger = buildUser("pass@test.com");
        User driverUser = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(driverUser);
        Trip trip = buildTrip(driverProfile, 3);

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(tripRepository.findByIdForUpdate(trip.getId())).thenReturn(Optional.of(trip));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(LocalDateTime.now());
            return r;
        });

        ReservationRequest request = buildRequest(trip.getId(), 2);
        ReservationResponse response = reservationService.createReservation("pass@test.com", request);

        assertThat(response.getSeatsBooked()).isEqualTo(2);
        assertThat(response.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(trip.getSeatsAvailable()).isEqualTo(1); // 3 - 2 = 1
        verify(tripRepository).save(trip);
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void createReservation_shouldThrowBusinessException_whenPassengerReservesOwnTrip() {
        // User is also the driver of the trip (dual role scenario)
        User user = buildUser("user@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        Trip trip = buildTrip(driverProfile, 3);

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(tripRepository.findByIdForUpdate(trip.getId())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() ->
                reservationService.createReservation("user@test.com", buildRequest(trip.getId(), 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("R01");
    }

    @Test
    void createReservation_shouldThrowBusinessException_whenNotEnoughSeats() {
        User passenger = buildUser("pass@test.com");
        User driverUser = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(driverUser);
        Trip trip = buildTrip(driverProfile, 1);

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(tripRepository.findByIdForUpdate(trip.getId())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() ->
                reservationService.createReservation("pass@test.com", buildRequest(trip.getId(), 3)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("R02");
    }

    @Test
    void createReservation_shouldThrowBusinessException_whenTripNotAvailable() {
        User passenger = buildUser("pass@test.com");
        User driverUser = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(driverUser);
        Trip trip = buildTrip(driverProfile, 3);
        trip.setStatus(TripStatus.CANCELLED);

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(tripRepository.findByIdForUpdate(trip.getId())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() ->
                reservationService.createReservation("pass@test.com", buildRequest(trip.getId(), 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void createReservation_shouldThrowResourceNotFoundException_whenTripNotFound() {
        User passenger = buildUser("pass@test.com");
        UUID tripId = UUID.randomUUID();

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(tripRepository.findByIdForUpdate(tripId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                reservationService.createReservation("pass@test.com", buildRequest(tripId, 1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(tripId.toString());
    }

    @Test
    void createReservation_shouldThrowResourceNotFoundException_whenUserNotFound() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                reservationService.createReservation("ghost@test.com", buildRequest(UUID.randomUUID(), 1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost@test.com");
    }

    // --- cancelReservation (UC-P04) ---

    @Test
    void cancelReservation_shouldCancelAndRestoreSeats_whenValid() {
        User passenger = buildUser("pass@test.com");
        User driverUser = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(driverUser);
        Trip trip = buildTrip(driverProfile, 1);
        Reservation reservation = buildReservation(trip, passenger, 2, ReservationStatus.PENDING);
        trip.setSeatsAvailable(1);

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(tripRepository.save(trip)).thenReturn(trip);
        when(reservationRepository.save(reservation)).thenReturn(reservation);

        reservationService.cancelReservation("pass@test.com", reservation.getId());

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(trip.getSeatsAvailable()).isEqualTo(3); // 1 + 2 restored
        verify(tripRepository).save(trip);
        verify(reservationRepository).save(reservation);
    }

    @Test
    void cancelReservation_shouldThrowBusinessException_whenTooCloseToDepart() {
        User passenger = buildUser("pass@test.com");
        User driverUser = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(driverUser);
        Trip trip = buildTrip(driverProfile, 2);
        trip.setDepartureAt(LocalDateTime.now().plusMinutes(90)); // only 1.5h away
        Reservation reservation = buildReservation(trip, passenger, 1, ReservationStatus.PENDING);

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() ->
                reservationService.cancelReservation("pass@test.com", reservation.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("R03");
    }

    @Test
    void cancelReservation_shouldThrowBusinessException_whenAlreadyCancelled() {
        User passenger = buildUser("pass@test.com");
        User driverUser = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(driverUser);
        Trip trip = buildTrip(driverProfile, 3);
        Reservation reservation = buildReservation(trip, passenger, 1, ReservationStatus.CANCELLED);

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() ->
                reservationService.cancelReservation("pass@test.com", reservation.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already cancelled");
    }

    @Test
    void cancelReservation_shouldThrowAccessDeniedException_whenNotOwner() {
        User passenger = buildUser("pass@test.com");
        User other = buildUser("other@test.com");
        User driverUser = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(driverUser);
        Trip trip = buildTrip(driverProfile, 3);
        Reservation reservation = buildReservation(trip, other, 1, ReservationStatus.PENDING);

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() ->
                reservationService.cancelReservation("pass@test.com", reservation.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancelReservation_shouldThrowResourceNotFoundException_whenNotFound() {
        User passenger = buildUser("pass@test.com");
        UUID reservationId = UUID.randomUUID();

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                reservationService.cancelReservation("pass@test.com", reservationId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(reservationId.toString());
    }

    // --- getMyReservations (UC-P05) ---

    @Test
    void getMyReservations_shouldReturnPassengerReservations() {
        User passenger = buildUser("pass@test.com");
        User driverUser = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(driverUser);
        Trip trip = buildTrip(driverProfile, 3);
        Reservation reservation = buildReservation(trip, passenger, 2, ReservationStatus.PENDING);
        Page<Reservation> page = new PageImpl<>(List.of(reservation));

        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));
        when(reservationRepository.findByPassenger_IdOrderByCreatedAtDesc(
                eq(passenger.getId()), any(Pageable.class))).thenReturn(page);

        Page<ReservationResponse> result =
                reservationService.getMyReservations("pass@test.com", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getPassengerId()).isEqualTo(passenger.getId());
    }

    // --- getTripReservations (UC-D08) ---

    @Test
    void getTripReservations_shouldReturnReservations_whenTripOwner() {
        User driverUser = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(driverUser);
        User passenger = buildUser("pass@test.com");
        Trip trip = buildTrip(driverProfile, 3);
        Reservation reservation = buildReservation(trip, passenger, 2, ReservationStatus.PENDING);
        Page<Reservation> page = new PageImpl<>(List.of(reservation));

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driverUser));
        when(driverProfileRepository.findByUserId(driverUser.getId())).thenReturn(Optional.of(driverProfile));
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));
        when(reservationRepository.findByTrip_IdOrderByCreatedAtDesc(
                eq(trip.getId()), any(Pageable.class))).thenReturn(page);

        Page<ReservationResponse> result =
                reservationService.getTripReservations("driver@test.com", trip.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTripId()).isEqualTo(trip.getId());
    }

    @Test
    void getTripReservations_shouldThrowAccessDeniedException_whenNotTripOwner() {
        User driverUser = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(driverUser);
        User otherUser = buildUser("other@test.com");
        DriverProfile otherProfile = buildDriverProfile(otherUser);
        Trip trip = buildTrip(otherProfile, 3);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driverUser));
        when(driverProfileRepository.findByUserId(driverUser.getId())).thenReturn(Optional.of(driverProfile));
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() ->
                reservationService.getTripReservations("driver@test.com", trip.getId(), PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
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

    private Trip buildTrip(DriverProfile driverProfile, int seats) {
        Trip t = new Trip();
        t.setId(UUID.randomUUID());
        t.setDriver(driverProfile);
        t.setOriginLabel("Paris, France");
        t.setOriginLat(BigDecimal.valueOf(48.8566));
        t.setOriginLng(BigDecimal.valueOf(2.3522));
        t.setDestinationLabel("Lyon, France");
        t.setDestLat(BigDecimal.valueOf(45.7640));
        t.setDestLng(BigDecimal.valueOf(4.8357));
        t.setDepartureAt(LocalDateTime.now().plusDays(7));
        t.setSeatsAvailable(seats);
        t.setPricePerSeat(BigDecimal.valueOf(15.00));
        t.setPetsAllowed(false);
        t.setSmokingAllowed(false);
        t.setStatus(TripStatus.AVAILABLE);
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }

    private Reservation buildReservation(Trip trip, User passenger,
                                         int seats, ReservationStatus status) {
        Reservation r = new Reservation();
        r.setId(UUID.randomUUID());
        r.setTrip(trip);
        r.setPassenger(passenger);
        r.setSeatsBooked(seats);
        r.setStatus(status);
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    private ReservationRequest buildRequest(UUID tripId, int seats) {
        ReservationRequest req = new ReservationRequest();
        req.setTripId(tripId);
        req.setSeatsBooked(seats);
        return req;
    }
}
