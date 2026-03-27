package com.covosio.unit;

import com.covosio.dto.TripMapResponse;
import com.covosio.dto.TripRequest;
import com.covosio.dto.TripResponse;
import com.covosio.entity.*;
import com.covosio.exception.BusinessException;
import com.covosio.exception.ResourceNotFoundException;
import com.covosio.repository.CarRepository;
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

    @Mock private TripRepository        tripRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private UserRepository        userRepository;
    @Mock private CarRepository         carRepository;

    @InjectMocks
    private TripService tripService;

    // --- createTrip (UC-D04) ---

    @Test
    void createTrip_shouldPublishTrip_whenDriverAndCarAreVerified() {
        Driver driver = buildDriver("driver@test.com", true);
        Car    car    = buildCar(UUID.randomUUID(), driver, true);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
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
    void createTrip_shouldThrowBusinessException_whenLicenseNotVerified() {
        Driver driver = buildDriver("driver@test.com", false); // licenseVerified = false
        Car    car    = buildCar(UUID.randomUUID(), driver, true);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
        when(carRepository.findById(car.getId())).thenReturn(Optional.of(car));

        assertThatThrownBy(() -> tripService.createTrip("driver@test.com", buildRequest(car.getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("R08");
    }

    @Test
    void createTrip_shouldThrowBusinessException_whenCarNotVerified() {
        Driver driver = buildDriver("driver@test.com", true);
        Car    car    = buildCar(UUID.randomUUID(), driver, false); // registrationVerified = false

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
        when(carRepository.findById(car.getId())).thenReturn(Optional.of(car));

        assertThatThrownBy(() -> tripService.createTrip("driver@test.com", buildRequest(car.getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("R08");
    }

    @Test
    void createTrip_shouldThrowAccessDeniedException_whenCarBelongsToAnotherDriver() {
        Driver driver      = buildDriver("driver@test.com", true);
        Driver otherDriver = buildDriver("other@test.com", true);
        Car    car         = buildCar(UUID.randomUUID(), otherDriver, true);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
        when(carRepository.findById(car.getId())).thenReturn(Optional.of(car));

        assertThatThrownBy(() -> tripService.createTrip("driver@test.com", buildRequest(car.getId())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createTrip_shouldThrowResourceNotFoundException_whenCarNotFound() {
        Driver driver = buildDriver("driver@test.com", true);
        UUID   carId  = UUID.randomUUID();

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
        when(carRepository.findById(carId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.createTrip("driver@test.com", buildRequest(carId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(carId.toString());
    }

    @Test
    void createTrip_shouldThrowAccessDeniedException_whenUserIsNotDriver() {
        Passenger passenger = buildPassenger("pass@test.com");
        when(userRepository.findByEmail("pass@test.com")).thenReturn(Optional.of(passenger));

        assertThatThrownBy(() -> tripService.createTrip("pass@test.com", buildRequest(UUID.randomUUID())))
                .isInstanceOf(AccessDeniedException.class);
    }

    // --- searchTrips (UC-P01) ---

    @Test
    void searchTrips_shouldReturnPagedTrips_whenFiltersMatch() {
        Driver driver = buildDriver("driver@test.com", true);
        Car    car    = buildCar(UUID.randomUUID(), driver, true);
        Trip   trip   = buildTrip(driver, car);
        Page<Trip> page = new PageImpl<>(List.of(trip));

        when(tripRepository.search(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);

        Page<TripResponse> result = tripService.searchTrips("Paris", "Lyon", null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getOriginLabel()).isEqualTo("Paris, France");
    }

    // --- getTripById (UC-P02) ---

    @Test
    void getTripById_shouldReturnTrip_whenTripExists() {
        Driver driver = buildDriver("driver@test.com", true);
        Car    car    = buildCar(UUID.randomUUID(), driver, true);
        Trip   trip   = buildTrip(driver, car);

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
        Driver driver = buildDriver("driver@test.com", true);
        Car    car    = buildCar(UUID.randomUUID(), driver, true);
        Trip   trip   = buildTrip(driver, car);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
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
        Driver driver = buildDriver("driver@test.com", true);
        Car    car    = buildCar(UUID.randomUUID(), driver, true);
        Trip   trip   = buildTrip(driver, car);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
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
        Driver driver      = buildDriver("driver@test.com", true);
        Driver otherDriver = buildDriver("other@test.com", true);
        Car    car         = buildCar(UUID.randomUUID(), otherDriver, true);
        Trip   trip        = buildTrip(otherDriver, car);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.updateTrip(trip.getId(), "driver@test.com", buildRequest(car.getId())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateTrip_shouldThrowResourceNotFoundException_whenTripNotFound() {
        Driver driver = buildDriver("driver@test.com", true);
        UUID   tripId = UUID.randomUUID();

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.updateTrip(tripId, "driver@test.com", buildRequest(UUID.randomUUID())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- cancelTrip (UC-D06) ---

    @Test
    void cancelTrip_shouldCancelTripAndReservations_whenOwner() {
        Driver driver = buildDriver("driver@test.com", true);
        Car    car    = buildCar(UUID.randomUUID(), driver, true);
        Trip   trip   = buildTrip(driver, car);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
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
        Driver driver      = buildDriver("driver@test.com", true);
        Driver otherDriver = buildDriver("other@test.com", true);
        Car    car         = buildCar(UUID.randomUUID(), otherDriver, true);
        Trip   trip        = buildTrip(otherDriver, car);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.cancelTrip(trip.getId(), "driver@test.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancelTrip_shouldThrowBusinessException_whenAlreadyCancelled() {
        Driver driver = buildDriver("driver@test.com", true);
        Car    car    = buildCar(UUID.randomUUID(), driver, true);
        Trip   trip   = buildTrip(driver, car);
        trip.setStatus(TripStatus.CANCELLED);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.cancelTrip(trip.getId(), "driver@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already cancelled");
    }

    // --- getMyTrips (UC-D07) ---

    @Test
    void getMyTrips_shouldReturnDriverTrips_whenDriverHasTrips() {
        Driver driver = buildDriver("driver@test.com", true);
        Car    car    = buildCar(UUID.randomUUID(), driver, true);
        Page<Trip> page = new PageImpl<>(List.of(buildTrip(driver, car)));

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
        when(tripRepository.findByDriver_IdOrderByDepartureAtDesc(eq(driver.getId()), any(Pageable.class)))
                .thenReturn(page);

        Page<TripResponse> result = tripService.getMyTrips("driver@test.com", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getDriverId()).isEqualTo(driver.getId());
    }

    // --- getMapTrips (UC-P07) ---

    @Test
    void getMapTrips_shouldReturnAvailableTripsWithFreeSeats() {
        Driver driver = buildDriver("driver@test.com", true);
        Car    car    = buildCar(UUID.randomUUID(), driver, true);
        Page<Trip> page = new PageImpl<>(List.of(buildTrip(driver, car)));

        when(tripRepository.findByStatusAndSeatsAvailableGreaterThan(
                eq(TripStatus.AVAILABLE), eq(0), any(Pageable.class))).thenReturn(page);

        Page<TripMapResponse> result = tripService.getMapTrips(PageRequest.of(0, 100));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(TripStatus.AVAILABLE);
    }

    // --- helpers ---

    private Driver buildDriver(String email, boolean licenseVerified) {
        Driver d = new Driver();
        d.setId(UUID.randomUUID());
        d.setEmail(email);
        d.setPasswordHash("hashed");
        d.setFirstName("Jean");
        d.setLastName("Dupont");
        d.setIsActive(true);
        d.setLicenseVerified(licenseVerified);
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
        return p;
    }

    private Car buildCar(UUID id, Driver driver, boolean registrationVerified) {
        Car car = new Car();
        car.setId(id);
        car.setDriver(driver);
        car.setBrand("Renault");
        car.setModel("Clio");
        car.setColor("Blue");
        car.setPlate("AB-123-CD");
        car.setTotalSeats(4);
        car.setRegistrationVerified(registrationVerified);
        car.setIsActive(true);
        return car;
    }

    private Trip buildTrip(Driver driver, Car car) {
        Trip t = new Trip();
        t.setId(UUID.randomUUID());
        t.setDriver(driver);
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
