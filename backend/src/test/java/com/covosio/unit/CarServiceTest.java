package com.covosio.unit;

import com.covosio.dto.CarRequest;
import com.covosio.dto.CarResponse;
import com.covosio.entity.Car;
import com.covosio.entity.Driver;
import com.covosio.entity.Passenger;
import com.covosio.exception.BusinessException;
import com.covosio.exception.ResourceNotFoundException;
import com.covosio.repository.CarRepository;
import com.covosio.repository.UserRepository;
import com.covosio.service.CarService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarServiceTest {

    @Mock private CarRepository carRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private CarService carService;

    // --- addCar (UC-D01) ---

    @Test
    void addCar_shouldCreateCar_whenDriverIsValid() {
        Driver driver = buildDriver("driver@test.com");
        CarRequest request = new CarRequest("Renault", "Clio", "Blue", "AB-123-CD", 4);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
        when(carRepository.save(any(Car.class))).thenAnswer(inv -> {
            Car c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setCreatedAt(LocalDateTime.now());
            return c;
        });

        CarResponse response = carService.addCar("driver@test.com", request);

        assertThat(response.getBrand()).isEqualTo("Renault");
        assertThat(response.getModel()).isEqualTo("Clio");
        assertThat(response.getPlate()).isEqualTo("AB-123-CD");
        assertThat(response.getTotalSeats()).isEqualTo(4);
        assertThat(response.getRegistrationVerified()).isFalse();
        verify(carRepository).save(any(Car.class));
    }

    @Test
    void addCar_shouldThrowResourceNotFoundException_whenUserNotFound() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> carService.addCar("ghost@test.com",
                new CarRequest("Renault", "Clio", "Blue", "XX-000-XX", 4)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost@test.com");
    }

    @Test
    void addCar_shouldThrowAccessDeniedException_whenUserIsNotDriver() {
        Passenger passenger = buildPassenger("passenger@test.com");
        when(userRepository.findByEmail("passenger@test.com")).thenReturn(Optional.of(passenger));

        assertThatThrownBy(() -> carService.addCar("passenger@test.com",
                new CarRequest("Renault", "Clio", "Blue", "XX-000-XX", 4)))
                .isInstanceOf(AccessDeniedException.class);
    }

    // --- deleteCar (UC-D01b) ---

    @Test
    void deleteCar_shouldSoftDelete_whenCarBelongsToDriverAndNoFutureTrips() {
        Driver driver = buildDriver("driver@test.com");
        UUID carId = UUID.randomUUID();
        Car car = buildCar(carId, driver);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));
        when(carRepository.countFutureAvailableTripsForCar(carId)).thenReturn(0L);
        when(carRepository.save(any(Car.class))).thenAnswer(inv -> inv.getArgument(0));

        carService.deleteCar(carId, "driver@test.com");

        assertThat(car.getIsActive()).isFalse();
        verify(carRepository).save(car);
    }

    @Test
    void deleteCar_shouldThrowResourceNotFoundException_whenCarNotFound() {
        Driver driver = buildDriver("driver@test.com");
        UUID carId = UUID.randomUUID();

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
        when(carRepository.findById(carId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> carService.deleteCar(carId, "driver@test.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(carId.toString());
    }

    @Test
    void deleteCar_shouldThrowAccessDeniedException_whenCarBelongsToAnotherDriver() {
        Driver driver = buildDriver("driver@test.com");
        Driver otherDriver = buildDriver("other@test.com");
        UUID carId = UUID.randomUUID();
        Car car = buildCar(carId, otherDriver);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));

        assertThatThrownBy(() -> carService.deleteCar(carId, "driver@test.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deleteCar_shouldThrowBusinessException_whenFutureAvailableTripExists() {
        Driver driver = buildDriver("driver@test.com");
        UUID carId = UUID.randomUUID();
        Car car = buildCar(carId, driver);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));
        when(carRepository.countFutureAvailableTripsForCar(carId)).thenReturn(1L);

        assertThatThrownBy(() -> carService.deleteCar(carId, "driver@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("R09");
    }

    @Test
    void deleteCar_shouldThrowAccessDeniedException_whenUserIsNotDriver() {
        Passenger passenger = buildPassenger("passenger@test.com");
        when(userRepository.findByEmail("passenger@test.com")).thenReturn(Optional.of(passenger));

        assertThatThrownBy(() -> carService.deleteCar(UUID.randomUUID(), "passenger@test.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // --- getMyCars ---

    @Test
    void getMyCars_shouldReturnDriverCars_whenDriverHasCars() {
        Driver driver = buildDriver("driver@test.com");
        Car car1 = buildCar(UUID.randomUUID(), driver);
        Car car2 = buildCar(UUID.randomUUID(), driver);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
        when(carRepository.findByDriver_IdAndIsActiveTrue(driver.getId()))
                .thenReturn(List.of(car1, car2));

        List<CarResponse> result = carService.getMyCars("driver@test.com");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getBrand()).isEqualTo("Renault");
    }

    @Test
    void getMyCars_shouldReturnEmptyList_whenDriverHasNoCars() {
        Driver driver = buildDriver("driver@test.com");

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driver));
        when(carRepository.findByDriver_IdAndIsActiveTrue(driver.getId())).thenReturn(List.of());

        List<CarResponse> result = carService.getMyCars("driver@test.com");

        assertThat(result).isEmpty();
    }

    @Test
    void getMyCars_shouldThrowResourceNotFoundException_whenUserNotFound() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> carService.getMyCars("ghost@test.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost@test.com");
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
        d.setLicenseVerified(false);
        d.setTotalTripsDriven(0);
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

    private Car buildCar(UUID id, Driver driver) {
        Car car = new Car();
        car.setId(id);
        car.setDriver(driver);
        car.setBrand("Renault");
        car.setModel("Clio");
        car.setColor("Blue");
        car.setPlate("AB-123-CD");
        car.setTotalSeats(4);
        car.setRegistrationVerified(false);
        car.setIsActive(true);
        car.setCreatedAt(LocalDateTime.now());
        return car;
    }
}
