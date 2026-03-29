package com.covosio.service;

import com.covosio.dto.ReservationRequest;
import com.covosio.dto.ReservationResponse;
import com.covosio.entity.*;
import com.covosio.exception.BusinessException;
import com.covosio.exception.ResourceNotFoundException;
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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Handles reservation use cases: book a trip (UC-P03), cancel a reservation (UC-P04),
 * list own reservations (UC-P05), and list reservations for a trip (UC-D08).
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final TripRepository        tripRepository;
    private final UserRepository        userRepository;
    private final DriverProfileRepository driverProfileRepository;

    /**
     * Creates a reservation for the authenticated user (UC-P03).
     * Enforces R01 (no self-booking) and R02 (seat availability, atomic decrement).
     *
     * @param passengerEmail authenticated user's email
     * @param request        reservation payload (tripId, seatsBooked)
     * @return ReservationResponse for the newly created reservation
     * @throws ResourceNotFoundException if trip not found
     * @throws AccessDeniedException     if user not found
     * @throws BusinessException         if R01 or R02 is violated, or trip is not AVAILABLE
     */
    @Transactional
    public ReservationResponse createReservation(String passengerEmail, ReservationRequest request) {
        User passenger = loadUser(passengerEmail);

        // Pessimistic write lock ensures R02 is atomic across concurrent requests
        Trip trip = tripRepository.findByIdForUpdate(request.getTripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + request.getTripId()));

        // R01 — passenger cannot book their own trip
        if (trip.getDriver().getUserId().equals(passenger.getId())) {
            throw new BusinessException("You cannot book your own trip (R01)");
        }

        if (trip.getStatus() != TripStatus.AVAILABLE) {
            throw new BusinessException("This trip is not available for booking");
        }

        // R02 — seats must be available (checked atomically under lock)
        if (trip.getSeatsAvailable() < request.getSeatsBooked()) {
            throw new BusinessException(
                    "Not enough seats available: requested " + request.getSeatsBooked()
                    + ", available " + trip.getSeatsAvailable() + " (R02)");
        }

        trip.setSeatsAvailable(trip.getSeatsAvailable() - request.getSeatsBooked());
        tripRepository.save(trip);

        Reservation reservation = Reservation.builder()
                .trip(trip)
                .passenger(passenger)
                .seatsBooked(request.getSeatsBooked())
                .status(ReservationStatus.PENDING)
                .build();

        return toResponse(reservationRepository.save(reservation));
    }

    /**
     * Cancels a reservation belonging to the authenticated user (UC-P04).
     * Enforces R03: cancellation is blocked within 2 hours of departure.
     * Restores the seats to the trip on successful cancellation.
     *
     * @param passengerEmail  authenticated user's email
     * @param reservationId   UUID of the reservation to cancel
     * @throws ResourceNotFoundException if reservation not found
     * @throws AccessDeniedException     if user does not own the reservation
     * @throws BusinessException         if reservation is already cancelled, or R03 is violated
     */
    @Transactional
    public void cancelReservation(String passengerEmail, UUID reservationId) {
        User passenger = loadUser(passengerEmail);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        if (!reservation.getPassenger().getId().equals(passenger.getId())) {
            throw new AccessDeniedException("Action not authorized");
        }

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new BusinessException("Reservation is already cancelled");
        }

        // R03 — cancellation blocked within 2 hours before departure
        Trip trip = reservation.getTrip();
        if (trip.getDepartureAt().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new BusinessException(
                    "Reservation cannot be cancelled less than 2 hours before departure (R03)");
        }

        // Restore seats to trip
        trip.setSeatsAvailable(trip.getSeatsAvailable() + reservation.getSeatsBooked());
        tripRepository.save(trip);

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
    }

    /**
     * Returns the authenticated user's reservations, newest first (UC-P05).
     *
     * @param passengerEmail authenticated user's email
     * @param pageable       pagination
     * @return paginated page of the user's reservations
     */
    @Transactional(readOnly = true)
    public Page<ReservationResponse> getMyReservations(String passengerEmail, Pageable pageable) {
        User passenger = loadUser(passengerEmail);
        return reservationRepository
                .findByPassenger_IdOrderByCreatedAtDesc(passenger.getId(), pageable)
                .map(this::toResponse);
    }

    /**
     * Returns all reservations for a trip owned by the authenticated driver (UC-D08).
     *
     * @param driverEmail authenticated driver's email
     * @param tripId      UUID of the trip
     * @param pageable    pagination
     * @return paginated page of reservations for the trip
     * @throws ResourceNotFoundException if trip not found
     * @throws AccessDeniedException     if user is not a driver or does not own the trip
     */
    @Transactional(readOnly = true)
    public Page<ReservationResponse> getTripReservations(String driverEmail, UUID tripId, Pageable pageable) {
        User user = userRepository.findByEmail(driverEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + driverEmail));

        DriverProfile driverProfile = driverProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AccessDeniedException("Only drivers can view trip reservations"));

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));

        if (!trip.getDriver().getUserId().equals(driverProfile.getUserId())) {
            throw new AccessDeniedException("Action not authorized");
        }

        return reservationRepository
                .findByTrip_IdOrderByCreatedAtDesc(tripId, pageable)
                .map(this::toResponse);
    }

    // --- helpers ---

    private User loadUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private ReservationResponse toResponse(Reservation r) {
        Trip trip = r.getTrip();
        return ReservationResponse.builder()
                .id(r.getId())
                .tripId(trip.getId())
                .tripOriginLabel(trip.getOriginLabel())
                .tripDestinationLabel(trip.getDestinationLabel())
                .tripDepartureAt(trip.getDepartureAt())
                .tripPricePerSeat(trip.getPricePerSeat())
                .passengerId(r.getPassenger().getId())
                .passengerFirstName(r.getPassenger().getFirstName())
                .passengerLastName(r.getPassenger().getLastName())
                .seatsBooked(r.getSeatsBooked())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
