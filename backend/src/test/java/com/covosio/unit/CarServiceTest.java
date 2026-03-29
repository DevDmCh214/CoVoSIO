package com.covosio.unit;

import com.covosio.dto.CarRequest;
import com.covosio.dto.CarResponse;
import com.covosio.entity.Car;
import com.covosio.entity.DriverProfile;
import com.covosio.entity.User;
import com.covosio.exception.BusinessException;
import com.covosio.exception.ResourceNotFoundException;
import com.covosio.repository.CarRepository;
import com.covosio.repository.DriverProfileRepository;
import com.covosio.repository.UserRepository;
import com.covosio.service.CarService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarServiceTest {

    @Mock private CarRepository           carRepository;
    @Mock private UserRepository          userRepository;
    @Mock private DriverProfileRepository driverProfileRepository;

    @InjectMocks
    private CarService carService;

    // --- addCar (UC-D01) ---

    @Test
    void addCar_shouldCreateCar_whenDriverIsValid() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        CarRequest request = new CarRequest("Renault", "Clio", "Blue", "AB-123-CD", 4);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
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
        User user = buildUser("passenger@test.com");
        when(userRepository.findByEmail("passenger@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> carService.addCar("passenger@test.com",
                new CarRequest("Renault", "Clio", "Blue", "XX-000-XX", 4)))
                .isInstanceOf(AccessDeniedException.class);
    }

    // --- deleteCar (UC-D01b) ---

    @Test
    void deleteCar_shouldSoftDelete_whenCarBelongsToDriverAndNoFutureTrips() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        UUID carId = UUID.randomUUID();
        Car car = buildCar(carId, driverProfile);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));
        when(carRepository.countFutureAvailableTripsForCar(carId)).thenReturn(0L);
        when(carRepository.save(any(Car.class))).thenAnswer(inv -> inv.getArgument(0));

        carService.deleteCar(carId, "driver@test.com");

        assertThat(car.getIsActive()).isFalse();
        verify(carRepository).save(car);
    }

    @Test
    void deleteCar_shouldThrowResourceNotFoundException_whenCarNotFound() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        UUID carId = UUID.randomUUID();

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(carRepository.findById(carId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> carService.deleteCar(carId, "driver@test.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(carId.toString());
    }

    @Test
    void deleteCar_shouldThrowAccessDeniedException_whenCarBelongsToAnotherDriver() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        User otherUser = buildUser("other@test.com");
        DriverProfile otherProfile = buildDriverProfile(otherUser);
        UUID carId = UUID.randomUUID();
        Car car = buildCar(carId, otherProfile);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));

        assertThatThrownBy(() -> carService.deleteCar(carId, "driver@test.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deleteCar_shouldThrowBusinessException_whenFutureAvailableTripExists() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        UUID carId = UUID.randomUUID();
        Car car = buildCar(carId, driverProfile);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));
        when(carRepository.countFutureAvailableTripsForCar(carId)).thenReturn(1L);

        assertThatThrownBy(() -> carService.deleteCar(carId, "driver@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("R09");
    }

    @Test
    void deleteCar_shouldThrowAccessDeniedException_whenUserIsNotDriver() {
        User user = buildUser("passenger@test.com");
        when(userRepository.findByEmail("passenger@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> carService.deleteCar(UUID.randomUUID(), "passenger@test.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // --- getMyCars ---

    @Test
    void getMyCars_shouldReturnDriverCars_whenDriverHasCars() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        Car car1 = buildCar(UUID.randomUUID(), driverProfile);
        Car car2 = buildCar(UUID.randomUUID(), driverProfile);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(carRepository.findByDriver_UserIdAndIsActiveTrue(driverProfile.getUserId()))
                .thenReturn(List.of(car1, car2));

        List<CarResponse> result = carService.getMyCars("driver@test.com");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getBrand()).isEqualTo("Renault");
    }

    @Test
    void getMyCars_shouldReturnEmptyList_whenDriverHasNoCars() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(carRepository.findByDriver_UserIdAndIsActiveTrue(driverProfile.getUserId())).thenReturn(List.of());

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

    private Car buildCar(UUID id, DriverProfile driverProfile) {
        Car car = new Car();
        car.setId(id);
        car.setDriver(driverProfile);
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
