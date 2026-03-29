package com.covosio.unit;

import com.covosio.dto.TripMapResponse;
import com.covosio.dto.TripRequest;
import com.covosio.dto.TripResponse;
import com.covosio.entity.*;
import com.covosio.exception.BusinessException;
import com.covosio.exception.ResourceNotFoundException;
import com.covosio.repository.CarRepository;
import com.covosio.repository.DriverProfileRepository;
import com.covosio.repository.ReservationRepository;
import com.covosio.repository.TripRepository;
import com.covosio.repository.UserRepository;
import com.covosio.service.TripService;
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
class TripServiceTest {

    @Mock private TripRepository          tripRepository;
    @Mock private ReservationRepository   reservationRepository;
    @Mock private UserRepository          userRepository;
    @Mock private CarRepository           carRepository;
    @Mock private DriverProfileRepository driverProfileRepository;

    @InjectMocks
    private TripService tripService;

    // --- createTrip (UC-D04) ---

    @Test
    void createTrip_shouldPublishTrip_whenDriverAndCarAreVerified() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        Car car = buildCar(UUID.randomUUID(), driverProfile, true);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(carRepository.findById(car.getId())).thenReturn(Optional.of(car));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> {
            Trip t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            t.setCreatedAt(LocalDateTime.now());
            return t;
        });

        TripResponse response = tripService.createTrip("driver@test.com", buildRequest(car.getId()));

        assertThat(response.getStatus()).isEqualTo(TripStatus.AVAILABLE);
        assertThat(response.getOriginLabel()).isEqualTo("Paris, France");
        assertThat(response.getSeatsAvailable()).isEqualTo(3);
        verify(tripRepository).save(any(Trip.class));
    }

    @Test
    void createTrip_shouldThrowResourceNotFoundException_whenUserNotFound() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.createTrip("ghost@test.com", buildRequest(UUID.randomUUID())))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost@test.com");
    }

    @Test
    void createTrip_shouldThrowBusinessException_whenCarNotVerified() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        Car car = buildCar(UUID.randomUUID(), driverProfile, false); // registrationVerified = false

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(carRepository.findById(car.getId())).thenReturn(Optional.of(car));

        assertThatThrownBy(() -> tripService.createTrip("driver@test.com", buildRequest(car.getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("R08");
    }

    @Test
    void createTrip_shouldThrowAccessDeniedException_whenCarBelongsToAnotherDriver() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        User otherUser = buildUser("other@test.com");
        DriverProfile otherProfile = buildDriverProfile(otherUser);
        Car car = buildCar(UUID.randomUUID(), otherProfile, true);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(carRepository.findById(car.getId())).thenReturn(Optional.of(car));

        assertThatThrownBy(() -> tripService.createTrip("driver@test.com", buildRequest(car.getId())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createTrip_shouldThrowResourceNotFoundException_whenCarNotFound() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        UUID carId = UUID.randomUUID();

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(carRepository.findById(carId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.createTrip("driver@test.com", buildRequest(carId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(carId.toString());
    }

    @Test
    void createTrip_shouldThrowAccessDeniedException_whenUserIsNotDriver() {
        User user = buildUser("pass@test.com");
        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.createTrip("pass@test.com", buildRequest(UUID.randomUUID())))
                .isInstanceOf(AccessDeniedException.class);
    }

    // --- searchTrips (UC-P01) ---

    @Test
    void searchTrips_shouldReturnPagedTrips_whenFiltersMatch() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        Car car = buildCar(UUID.randomUUID(), driverProfile, true);
        Trip trip = buildTrip(driverProfile, car);
        Page<Trip> page = new PageImpl<>(List.of(trip));

        when(tripRepository.search(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);

        Page<TripResponse> result = tripService.searchTrips("Paris", "Lyon", null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getOriginLabel()).isEqualTo("Paris, France");
    }

    // --- getTripById (UC-P02) ---

    @Test
    void getTripById_shouldReturnTrip_whenTripExists() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        Car car = buildCar(UUID.randomUUID(), driverProfile, true);
        Trip trip = buildTrip(driverProfile, car);

        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));

        TripResponse response = tripService.getTripById(trip.getId());

        assertThat(response.getId()).isEqualTo(trip.getId());
        assertThat(response.getDriverFirstName()).isEqualTo("Jean");
    }

    @Test
    void getTripById_shouldThrowResourceNotFoundException_whenTripNotFound() {
        UUID tripId = UUID.randomUUID();
        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.getTripById(tripId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(tripId.toString());
    }

    // --- updateTrip (UC-D05) ---

    @Test
    void updateTrip_shouldUpdateAllFields_whenNoConfirmedReservations() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        Car car = buildCar(UUID.randomUUID(), driverProfile, true);
        Trip trip = buildTrip(driverProfile, car);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));
        when(reservationRepository.countByTrip_IdAndStatus(trip.getId(), ReservationStatus.CONFIRMED)).thenReturn(0L);
        when(carRepository.findById(car.getId())).thenReturn(Optional.of(car));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        TripRequest updateRequest = buildRequest(car.getId());
        updateRequest.setOriginLabel("Bordeaux, France");
        updateRequest.setSeatsAvailable(2);

        TripResponse response = tripService.updateTrip(trip.getId(), "driver@test.com", updateRequest);

        assertThat(response.getOriginLabel()).isEqualTo("Bordeaux, France");
        assertThat(response.getSeatsAvailable()).isEqualTo(2);
    }

    @Test
    void updateTrip_shouldUpdateOnlyOrigin_whenConfirmedReservationsExist() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        Car car = buildCar(UUID.randomUUID(), driverProfile, true);
        Trip trip = buildTrip(driverProfile, car);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));
        when(reservationRepository.countByTrip_IdAndStatus(trip.getId(), ReservationStatus.CONFIRMED)).thenReturn(2L);
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        TripRequest updateRequest = buildRequest(car.getId());
        updateRequest.setOriginLabel("Place de la Bastille");
        updateRequest.setSeatsAvailable(1); // should be ignored (R07)

        TripResponse response = tripService.updateTrip(trip.getId(), "driver@test.com", updateRequest);

        assertThat(response.getOriginLabel()).isEqualTo("Place de la Bastille");
        assertThat(response.getSeatsAvailable()).isEqualTo(3); // original value preserved
    }

    @Test
    void updateTrip_shouldThrowAccessDeniedException_whenNotTripOwner() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        User otherUser = buildUser("other@test.com");
        DriverProfile otherProfile = buildDriverProfile(otherUser);
        Car car = buildCar(UUID.randomUUID(), otherProfile, true);
        Trip trip = buildTrip(otherProfile, car);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.updateTrip(trip.getId(), "driver@test.com", buildRequest(car.getId())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateTrip_shouldThrowResourceNotFoundException_whenTripNotFound() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        UUID tripId = UUID.randomUUID();

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.updateTrip(tripId, "driver@test.com", buildRequest(UUID.randomUUID())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- cancelTrip (UC-D06) ---

    @Test
    void cancelTrip_shouldCancelTripAndReservations_whenOwner() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        Car car = buildCar(UUID.randomUUID(), driverProfile, true);
        Trip trip = buildTrip(driverProfile, car);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));
        when(reservationRepository.cancelAllByTripId(trip.getId(), ReservationStatus.CANCELLED)).thenReturn(0);
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        tripService.cancelTrip(trip.getId(), "driver@test.com");

        assertThat(trip.getStatus()).isEqualTo(TripStatus.CANCELLED);
        verify(reservationRepository).cancelAllByTripId(trip.getId(), ReservationStatus.CANCELLED);
        verify(tripRepository).save(trip);
    }

    @Test
    void cancelTrip_shouldThrowAccessDeniedException_whenNotTripOwner() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        User otherUser = buildUser("other@test.com");
        DriverProfile otherProfile = buildDriverProfile(otherUser);
        Car car = buildCar(UUID.randomUUID(), otherProfile, true);
        Trip trip = buildTrip(otherProfile, car);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.cancelTrip(trip.getId(), "driver@test.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancelTrip_shouldThrowBusinessException_whenAlreadyCancelled() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        Car car = buildCar(UUID.randomUUID(), driverProfile, true);
        Trip trip = buildTrip(driverProfile, car);
        trip.setStatus(TripStatus.CANCELLED);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.cancelTrip(trip.getId(), "driver@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already cancelled");
    }

    // --- getMyTrips (UC-D07) ---

    @Test
    void getMyTrips_shouldReturnDriverTrips_whenDriverHasTrips() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        Car car = buildCar(UUID.randomUUID(), driverProfile, true);
        Page<Trip> page = new PageImpl<>(List.of(buildTrip(driverProfile, car)));

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(tripRepository.findByDriver_UserIdOrderByDepartureAtDesc(eq(driverProfile.getUserId()), any(Pageable.class)))
                .thenReturn(page);

        Page<TripResponse> result = tripService.getMyTrips("driver@test.com", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getDriverId()).isEqualTo(driverProfile.getUserId());
    }

    // --- getMapTrips (UC-P07) ---

    @Test
    void getMapTrips_shouldReturnAvailableTripsWithFreeSeats() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        Car car = buildCar(UUID.randomUUID(), driverProfile, true);
        Page<Trip> page = new PageImpl<>(List.of(buildTrip(driverProfile, car)));

        when(tripRepository.findByStatusAndSeatsAvailableGreaterThan(
                eq(TripStatus.AVAILABLE), eq(0), any(Pageable.class))).thenReturn(page);

        Page<TripMapResponse> result = tripService.getMapTrips(PageRequest.of(0, 100));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(TripStatus.AVAILABLE);
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

    private Car buildCar(UUID id, DriverProfile driverProfile, boolean registrationVerified) {
        Car car = new Car();
        car.setId(id);
        car.setDriver(driverProfile);
        car.setBrand("Renault");
        car.setModel("Clio");
        car.setColor("Blue");
        car.setPlate("AB-123-CD");
        car.setTotalSeats(4);
        car.setRegistrationVerified(registrationVerified);
        car.setIsActive(true);
        return car;
    }

    private Trip buildTrip(DriverProfile driverProfile, Car car) {
        Trip t = new Trip();
        t.setId(UUID.randomUUID());
        t.setDriver(driverProfile);
        t.setCar(car);
        t.setOriginLabel("Paris, France");
        t.setOriginLat(BigDecimal.valueOf(48.8566));
        t.setOriginLng(BigDecimal.valueOf(2.3522));
        t.setDestinationLabel("Lyon, France");
        t.setDestLat(BigDecimal.valueOf(45.7640));
        t.setDestLng(BigDecimal.valueOf(4.8357));
        t.setDepartureAt(LocalDateTime.now().plusDays(7));
        t.setSeatsAvailable(3);
        t.setPricePerSeat(BigDecimal.valueOf(15.00));
        t.setPetsAllowed(false);
        t.setSmokingAllowed(false);
        t.setStatus(TripStatus.AVAILABLE);
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }

    private TripRequest buildRequest(UUID carId) {
        TripRequest req = new TripRequest();
        req.setCarId(carId);
        req.setOriginLabel("Paris, France");
        req.setOriginLat(BigDecimal.valueOf(48.8566));
        req.setOriginLng(BigDecimal.valueOf(2.3522));
        req.setDestinationLabel("Lyon, France");
        req.setDestLat(BigDecimal.valueOf(45.7640));
        req.setDestLng(BigDecimal.valueOf(4.8357));
        req.setDepartureAt(LocalDateTime.now().plusDays(7));
        req.setSeatsAvailable(3);
        req.setPricePerSeat(BigDecimal.valueOf(15.00));
        req.setPetsAllowed(false);
        req.setSmokingAllowed(false);
        return req;
    }
}
