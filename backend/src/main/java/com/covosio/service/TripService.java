package com.covosio.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Handles trip management use cases: publish (UC-D04), search (UC-P01), detail (UC-P02),
 * edit (UC-D05), cancel (UC-D06), list own trips (UC-D07), and map views (UC-P07, UC-D10).
 */
@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository        tripRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository        userRepository;
    private final CarRepository         carRepository;
    private final DriverProfileRepository driverProfileRepository;

    /**
     * Publishes a new trip for the authenticated driver (UC-D04).
     * Enforces R08: requires car registrationVerified = true.
     * (License is verified implicitly — DriverProfile existence means license was approved.)
     *
     * @param driverEmail authenticated driver's email
     * @param request     trip creation payload
     * @return TripResponse for the newly created trip
     * @throws ResourceNotFoundException if driver or car is not found
     * @throws AccessDeniedException     if user is not a driver, or car belongs to another driver
     * @throws BusinessException         if R08 is violated (unverified car registration)
     */
    @Transactional
    public TripResponse createTrip(String driverEmail, TripRequest request) {
        DriverProfile driverProfile = loadDriverProfile(driverEmail);

        Car car = carRepository.findById(request.getCarId())
                .orElseThrow(() -> new ResourceNotFoundException("Car not found: " + request.getCarId()));

        if (!car.getDriver().getUserId().equals(driverProfile.getUserId())) {
            throw new AccessDeniedException("Action not authorized");
        }

        // R08 — car registration must be verified before publishing a trip
        if (!car.getRegistrationVerified()) {
            throw new BusinessException("Car registration must be verified before publishing a trip (R08)");
        }

        Trip trip = Trip.builder()
                .driver(driverProfile)
                .car(car)
                .originLabel(request.getOriginLabel())
                .originLat(request.getOriginLat())
                .originLng(request.getOriginLng())
                .destinationLabel(request.getDestinationLabel())
                .destLat(request.getDestLat())
                .destLng(request.getDestLng())
                .departureAt(request.getDepartureAt())
                .seatsAvailable(request.getSeatsAvailable())
                .pricePerSeat(request.getPricePerSeat())
                .petsAllowed(Boolean.TRUE.equals(request.getPetsAllowed()))
                .smokingAllowed(Boolean.TRUE.equals(request.getSmokingAllowed()))
                .build();

        return toResponse(tripRepository.save(trip));
    }

    /**
     * Searches for available trips with optional filters (UC-P01).
     * All parameters are optional. Date is expanded to a day range for portability.
     *
     * @param origin      partial city name for origin (case-insensitive, optional)
     * @param destination partial city name for destination (case-insensitive, optional)
     * @param date        departure date filter (optional)
     * @param pageable    pagination and sorting
     * @return paginated page of matching trips
     */
    @Transactional(readOnly = true)
    public Page<TripResponse> searchTrips(String origin, String destination, LocalDate date, Pageable pageable) {
        LocalDateTime dateStart = date != null ? date.atStartOfDay()             : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime dateEnd   = date != null ? date.plusDays(1).atStartOfDay() : LocalDateTime.of(2100, 1, 1, 0, 0);
        return tripRepository.search(TripStatus.AVAILABLE, origin, destination, dateStart, dateEnd, pageable)
                .map(this::toResponse);
    }

    /**
     * Returns trip details visible to any authenticated user (UC-P02).
     *
     * @param id trip UUID
     * @return TripResponse with full trip + driver + car info
     * @throws ResourceNotFoundException if the trip does not exist
     */
    @Transactional(readOnly = true)
    public TripResponse getTripById(UUID id) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + id));
        return toResponse(trip);
    }

    /**
     * Updates a trip belonging to the authenticated driver (UC-D05).
     * Enforces R07: if CONFIRMED reservations exist, only the origin (meeting point) is editable.
     *
     * @param id          trip UUID
     * @param driverEmail authenticated driver's email
     * @param request     updated trip fields
     * @return TripResponse reflecting the updated state
     * @throws ResourceNotFoundException if trip or driver not found
     * @throws AccessDeniedException     if the trip belongs to another driver, or user is not a driver
     * @throws BusinessException         if the trip is not in AVAILABLE status
     */
    @Transactional
    public TripResponse updateTrip(UUID id, String driverEmail, TripRequest request) {
        DriverProfile driverProfile = loadDriverProfile(driverEmail);
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + id));

        if (!trip.getDriver().getUserId().equals(driverProfile.getUserId())) {
            throw new AccessDeniedException("Action not authorized");
        }
        if (trip.getStatus() != TripStatus.AVAILABLE) {
            throw new BusinessException("Only AVAILABLE trips can be edited");
        }

        // R07 — if CONFIRMED reservations exist, only the meeting point (origin) is editable
        boolean hasConfirmedReservations =
                reservationRepository.countByTrip_IdAndStatus(id, ReservationStatus.CONFIRMED) > 0;

        if (hasConfirmedReservations) {
            trip.setOriginLabel(request.getOriginLabel());
            trip.setOriginLat(request.getOriginLat());
            trip.setOriginLng(request.getOriginLng());
        } else {
            Car car = carRepository.findById(request.getCarId())
                    .orElseThrow(() -> new ResourceNotFoundException("Car not found: " + request.getCarId()));
            if (!car.getDriver().getUserId().equals(driverProfile.getUserId())) {
                throw new AccessDeniedException("Action not authorized");
            }
            trip.setCar(car);
            trip.setOriginLabel(request.getOriginLabel());
            trip.setOriginLat(request.getOriginLat());
            trip.setOriginLng(request.getOriginLng());
            trip.setDestinationLabel(request.getDestinationLabel());
            trip.setDestLat(request.getDestLat());
            trip.setDestLng(request.getDestLng());
            trip.setDepartureAt(request.getDepartureAt());
            trip.setSeatsAvailable(request.getSeatsAvailable());
            trip.setPricePerSeat(request.getPricePerSeat());
            trip.setPetsAllowed(Boolean.TRUE.equals(request.getPetsAllowed()));
            trip.setSmokingAllowed(Boolean.TRUE.equals(request.getSmokingAllowed()));
        }

        return toResponse(tripRepository.save(trip));
    }

    /**
     * Cancels a trip and all its reservations in cascade (UC-D06, R06).
     * Sets trip status to CANCELLED and bulk-updates all non-cancelled reservations.
     *
     * @param id          trip UUID
     * @param driverEmail authenticated driver's email
     * @throws ResourceNotFoundException if trip or driver not found
     * @throws AccessDeniedException     if the trip belongs to another driver, or user is not a driver
     * @throws BusinessException         if the trip is already cancelled
     */
    @Transactional
    public void cancelTrip(UUID id, String driverEmail) {
        DriverProfile driverProfile = loadDriverProfile(driverEmail);
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + id));

        if (!trip.getDriver().getUserId().equals(driverProfile.getUserId())) {
            throw new AccessDeniedException("Action not authorized");
        }
        if (trip.getStatus() == TripStatus.CANCELLED) {
            throw new BusinessException("Trip is already cancelled");
        }

        // R06 — cascade cancel all reservations
        reservationRepository.cancelAllByTripId(id, ReservationStatus.CANCELLED);

        trip.setStatus(TripStatus.CANCELLED);
        tripRepository.save(trip);
    }

    /**
     * Returns all trips published by the authenticated driver (UC-D07).
     *
     * @param driverEmail authenticated driver's email
     * @param pageable    pagination (default: page=0, size=10, sort=departureAt,desc)
     * @return paginated page of the driver's trips
     */
    @Transactional(readOnly = true)
    public Page<TripResponse> getMyTrips(String driverEmail, Pageable pageable) {
        DriverProfile driverProfile = loadDriverProfile(driverEmail);
        return tripRepository.findByDriver_UserIdOrderByDepartureAtDesc(driverProfile.getUserId(), pageable)
                .map(this::toResponse);
    }

    /**
     * Returns AVAILABLE trips with free seats for the Leaflet map (UC-P07).
     *
     * @param pageable pagination (use a large page size for map display)
     * @return paginated page of minimal trip data for map markers
     */
    @Transactional(readOnly = true)
    public Page<TripMapResponse> getMapTrips(Pageable pageable) {
        return tripRepository.findByStatusAndSeatsAvailableGreaterThan(TripStatus.AVAILABLE, 0, pageable)
                .map(this::toMapResponse);
    }

    /**
     * Returns all trips published by the authenticated driver for the driver's map view (UC-D10).
     *
     * @param driverEmail authenticated driver's email
     * @param pageable    pagination
     * @return paginated page of minimal trip data for the driver's map
     */
    @Transactional(readOnly = true)
    public Page<TripMapResponse> getMyMapTrips(String driverEmail, Pageable pageable) {
        DriverProfile driverProfile = loadDriverProfile(driverEmail);
        return tripRepository.findByDriver_UserId(driverProfile.getUserId(), pageable)
                .map(this::toMapResponse);
    }

    // --- helpers ---

    private DriverProfile loadDriverProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        return driverProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AccessDeniedException("Only drivers can manage trips"));
    }

    private TripResponse toResponse(Trip t) {
        return TripResponse.builder()
                .id(t.getId())
                .driverId(t.getDriver().getUserId())
                .driverFirstName(t.getDriver().getUser().getFirstName())
                .driverLastName(t.getDriver().getUser().getLastName())
                .driverAvgRating(t.getDriver().getAvgRating())
                .carId(t.getCar()    != null ? t.getCar().getId()    : null)
                .carBrand(t.getCar() != null ? t.getCar().getBrand() : null)
                .carModel(t.getCar() != null ? t.getCar().getModel() : null)
                .carColor(t.getCar() != null ? t.getCar().getColor() : null)
                .originLabel(t.getOriginLabel())
                .originLat(t.getOriginLat())
                .originLng(t.getOriginLng())
                .destinationLabel(t.getDestinationLabel())
                .destLat(t.getDestLat())
                .destLng(t.getDestLng())
                .departureAt(t.getDepartureAt())
                .seatsAvailable(t.getSeatsAvailable())
                .pricePerSeat(t.getPricePerSeat())
                .petsAllowed(t.getPetsAllowed())
                .smokingAllowed(t.getSmokingAllowed())
                .status(t.getStatus())
                .createdAt(t.getCreatedAt())
                .build();
    }

    private TripMapResponse toMapResponse(Trip t) {
        return TripMapResponse.builder()
                .id(t.getId())
                .originLabel(t.getOriginLabel())
                .originLat(t.getOriginLat())
                .originLng(t.getOriginLng())
                .destinationLabel(t.getDestinationLabel())
                .destLat(t.getDestLat())
                .destLng(t.getDestLng())
                .seatsAvailable(t.getSeatsAvailable())
                .pricePerSeat(t.getPricePerSeat())
                .departureAt(t.getDepartureAt())
                .status(t.getStatus())
                .build();
    }
}
