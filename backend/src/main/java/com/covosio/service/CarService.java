package com.covosio.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Handles car management use cases for drivers: add a car (UC-D01),
 * delete a car (UC-D01b), and list own cars.
 */
@Service
@RequiredArgsConstructor
public class CarService {

    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;

    /**
     * Adds a new car for the currently authenticated driver (UC-D01).
     *
     * @param driverEmail the authenticated driver's email (from JWT subject)
     * @param request     the car details (brand, model, color, plate, totalSeats)
     * @return CarResponse representing the newly created car
     * @throws ResourceNotFoundException if the user does not exist
     * @throws AccessDeniedException     if the authenticated user is not a driver
     */
    @Transactional
    public CarResponse addCar(String driverEmail, CarRequest request) {
        DriverProfile driverProfile = loadDriverProfile(driverEmail);

        Car car = Car.builder()
                .driver(driverProfile)
                .brand(request.getBrand())
                .model(request.getModel())
                .color(request.getColor())
                .plate(request.getPlate())
                .totalSeats(request.getTotalSeats())
                .registrationVerified(false)
                .isActive(true)
                .build();

        return toResponse(carRepository.save(car));
    }

    /**
     * Soft-deletes a car belonging to the authenticated driver (UC-D01b).
     * Enforces R09: deletion is blocked if a future AVAILABLE trip uses this car.
     *
     * @param carId       the UUID of the car to delete
     * @param driverEmail the authenticated driver's email (from JWT subject)
     * @throws ResourceNotFoundException if the car or user does not exist
     * @throws AccessDeniedException     if the car belongs to another driver,
     *                                   or the user is not a driver
     * @throws BusinessException         if a future AVAILABLE trip is attached (R09)
     */
    @Transactional
    public void deleteCar(UUID carId, String driverEmail) {
        DriverProfile driverProfile = loadDriverProfile(driverEmail);

        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new ResourceNotFoundException("Car not found: " + carId));

        if (!car.getDriver().getUserId().equals(driverProfile.getUserId())) {
            throw new AccessDeniedException("Action not authorized");
        }

        if (carRepository.countFutureAvailableTripsForCar(carId) > 0) {
            throw new BusinessException("Cannot delete a car that has a future AVAILABLE trip attached (R09)");
        }

        car.setIsActive(false);
        carRepository.save(car);
    }

    /**
     * Returns all active cars belonging to the authenticated driver.
     * Used to populate the car dropdown in trip creation (UC-D04).
     *
     * @param driverEmail the authenticated driver's email (from JWT subject)
     * @return list of the driver's active cars
     * @throws ResourceNotFoundException if the user does not exist
     * @throws AccessDeniedException     if the authenticated user is not a driver
     */
    @Transactional(readOnly = true)
    public List<CarResponse> getMyCars(String driverEmail) {
        DriverProfile driverProfile = loadDriverProfile(driverEmail);
        return carRepository.findByDriver_UserIdAndIsActiveTrue(driverProfile.getUserId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // --- helpers ---

    private DriverProfile loadDriverProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        return driverProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AccessDeniedException("Only drivers can manage cars"));
    }

    private CarResponse toResponse(Car car) {
        return CarResponse.builder()
                .id(car.getId())
                .brand(car.getBrand())
                .model(car.getModel())
                .color(car.getColor())
                .plate(car.getPlate())
                .totalSeats(car.getTotalSeats())
                .registrationVerified(car.getRegistrationVerified())
                .createdAt(car.getCreatedAt())
                .build();
    }
}
