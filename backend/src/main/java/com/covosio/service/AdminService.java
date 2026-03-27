package com.covosio.service;

import com.covosio.dto.*;
import com.covosio.entity.*;
import com.covosio.exception.BusinessException;
import com.covosio.exception.ResourceNotFoundException;
import com.covosio.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Handles all admin use cases: user management (UC-A01–A06), trip moderation (UC-A07–A08),
 * reservation listing (UC-A09), review moderation (UC-A10), platform stats (UC-A11),
 * global map (UC-A12), and document review workflow (UC-A13–A15).
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository           userRepository;
    private final TripRepository           tripRepository;
    private final ReservationRepository    reservationRepository;
    private final ReviewRepository         reviewRepository;
    private final DriverDocumentRepository documentRepository;
    private final RefreshTokenRepository   refreshTokenRepository;
    private final CarRepository            carRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // -----------------------------------------------------------------------
    // UC-A01 — list all users (paginated)
    // -----------------------------------------------------------------------

    /**
     * Returns all users, paginated (UC-A01).
     *
     * @param pageable pagination and sorting
     * @return paginated list of all users with role-specific fields
     */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toAdminUserResponse);
    }

    // -----------------------------------------------------------------------
    // UC-A02 — get a specific user
    // -----------------------------------------------------------------------

    /**
     * Returns full details for a single user (UC-A02).
     *
     * @param userId the user UUID
     * @return AdminUserResponse with role-specific fields
     * @throws ResourceNotFoundException if no user exists with that ID
     */
    @Transactional(readOnly = true)
    public AdminUserResponse getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return toAdminUserResponse(user);
    }

    // -----------------------------------------------------------------------
    // UC-A03–A05 — change a user's role
    // -----------------------------------------------------------------------

    /**
     * Changes a user's role (PASSENGER / DRIVER / ADMIN) by restructuring the TPT
     * sub-tables via native SQL (UC-A03–A05).
     * If the user already has the requested role the call is a no-op.
     *
     * @param userId     the target user UUID
     * @param newRoleStr desired role string (case-insensitive)
     * @return updated AdminUserResponse
     * @throws ResourceNotFoundException if the user does not exist
     * @throws BusinessException         if the role string is invalid
     */
    @Transactional
    public AdminUserResponse changeUserRole(UUID userId, String newRoleStr) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        String newRole = newRoleStr.trim().toUpperCase();
        if (!newRole.equals("PASSENGER") && !newRole.equals("DRIVER") && !newRole.equals("ADMIN")) {
            throw new BusinessException(
                    "Invalid role: " + newRoleStr + ". Allowed: PASSENGER, DRIVER, ADMIN");
        }

        String currentRole = resolveRole(user);
        if (currentRole.equals(newRole)) {
            return toAdminUserResponse(user);
        }

        // Guard: cannot restructure TPT rows while dependent data references the subtype PK
        if (user instanceof Passenger && reservationRepository.existsByPassenger_Id(userId)) {
            throw new BusinessException(
                    "Cannot change role: passenger has existing reservations. Remove them first.");
        }
        if (user instanceof Driver && tripRepository.existsByDriver_Id(userId)) {
            throw new BusinessException(
                    "Cannot change role: driver has existing trips. Remove them first.");
        }

        // Restructure TPT tables: delete old subtype row, insert new one, update dtype
        deleteFromSubtypeTable(userId, currentRole);
        insertIntoSubtypeTable(userId, newRole);
        entityManager.createNativeQuery("UPDATE users SET dtype = :dtype WHERE id = :id")
                .setParameter("dtype", newRole)
                .setParameter("id", userId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        User updated = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found after role change"));
        return toAdminUserResponse(updated);
    }

    // -----------------------------------------------------------------------
    // UC-A05 — suspend / activate a user (R11)
    // -----------------------------------------------------------------------

    /**
     * Activates or suspends a user account (UC-A05).
     * R11: suspending an account immediately revokes all its refresh tokens.
     *
     * @param userId the target user UUID
     * @param active true = activate, false = suspend
     * @return updated AdminUserResponse
     * @throws ResourceNotFoundException if the user does not exist
     */
    @Transactional
    public AdminUserResponse changeUserStatus(UUID userId, boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        user.setIsActive(active);
        User saved = userRepository.save(user);

        if (!active) {
            // R11 — suspension invalidates all active refresh tokens
            refreshTokenRepository.revokeAllByUser(saved);
        }

        return toAdminUserResponse(saved);
    }

    // -----------------------------------------------------------------------
    // UC-A06 — soft-delete a user
    // -----------------------------------------------------------------------

    /**
     * Soft-deletes a user by setting isActive = false and revoking all tokens (UC-A06).
     * The user record is retained in the database.
     *
     * @param userId the target user UUID
     * @throws ResourceNotFoundException if the user does not exist
     */
    @Transactional
    public void softDeleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setIsActive(false);
        User saved = userRepository.save(user);
        refreshTokenRepository.revokeAllByUser(saved); // R11
    }

    // -----------------------------------------------------------------------
    // UC-A07 — list all trips
    // -----------------------------------------------------------------------

    /**
     * Returns all trips regardless of status, paginated (UC-A07).
     *
     * @param pageable pagination and sorting
     * @return paginated list of all trips
     */
    @Transactional(readOnly = true)
    public Page<TripResponse> getAllTrips(Pageable pageable) {
        return tripRepository.findAll(pageable).map(this::toTripResponse);
    }

    // -----------------------------------------------------------------------
    // UC-A08 — admin cancel a trip
    // -----------------------------------------------------------------------

    /**
     * Cancels a trip on behalf of an admin (UC-A08).
     * Cascade-cancels all non-cancelled reservations (R06).
     *
     * @param tripId the trip UUID
     * @throws ResourceNotFoundException if the trip does not exist
     * @throws BusinessException         if the trip is already cancelled
     */
    @Transactional
    public void adminCancelTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));
        if (trip.getStatus() == TripStatus.CANCELLED) {
            throw new BusinessException("Trip is already cancelled");
        }
        reservationRepository.cancelAllByTripId(tripId, ReservationStatus.CANCELLED); // R06
        trip.setStatus(TripStatus.CANCELLED);
        tripRepository.save(trip);
    }

    // -----------------------------------------------------------------------
    // UC-A09 — list all reservations
    // -----------------------------------------------------------------------

    /**
     * Returns all reservations regardless of status, paginated (UC-A09).
     *
     * @param pageable pagination and sorting
     * @return paginated list of all reservations
     */
    @Transactional(readOnly = true)
    public Page<ReservationResponse> getAllReservations(Pageable pageable) {
        return reservationRepository.findAll(pageable).map(this::toReservationResponse);
    }

    // -----------------------------------------------------------------------
    // UC-A10 — review moderation
    // -----------------------------------------------------------------------

    /**
     * Updates a review's rating and/or comment for moderation purposes (UC-A10).
     * Fields present in the request overwrite the existing values; null fields are ignored.
     *
     * @param reviewId the review UUID
     * @param request  moderation payload (rating and/or comment)
     * @return updated ReviewResponse
     * @throws ResourceNotFoundException if the review does not exist
     */
    @Transactional
    public ReviewResponse updateReview(UUID reviewId, AdminReviewModerationRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId));
        if (request.getRating() != null) {
            review.setRating(request.getRating());
        }
        if (request.getComment() != null) {
            review.setComment(request.getComment());
        }
        return toReviewResponse(reviewRepository.save(review));
    }

    /**
     * Deletes a review (UC-A10).
     *
     * @param reviewId the review UUID
     * @throws ResourceNotFoundException if the review does not exist
     */
    @Transactional
    public void deleteReview(UUID reviewId) {
        if (!reviewRepository.existsById(reviewId)) {
            throw new ResourceNotFoundException("Review not found: " + reviewId);
        }
        reviewRepository.deleteById(reviewId);
    }

    // -----------------------------------------------------------------------
    // UC-A11 — platform stats
    // -----------------------------------------------------------------------

    /**
     * Returns aggregated platform statistics (UC-A11).
     * Uses individual JPQL/native count queries compatible with both PostgreSQL and H2.
     *
     * @return PlatformStatsResponse with all counters
     */
    @Transactional(readOnly = true)
    public PlatformStatsResponse getPlatformStats() {
        return PlatformStatsResponse.builder()
                .totalUsers(userRepository.count())
                .totalDrivers(userRepository.countByDtype("DRIVER"))
                .totalPassengers(userRepository.countByDtype("PASSENGER"))
                .totalAdmins(userRepository.countByDtype("ADMIN"))
                .totalTrips(tripRepository.count())
                .totalAvailableTrips(tripRepository.countByStatus(TripStatus.AVAILABLE))
                .totalCompletedTrips(tripRepository.countByStatus(TripStatus.COMPLETED))
                .totalReservations(reservationRepository.count())
                .totalConfirmedReservations(reservationRepository.countByStatus(ReservationStatus.CONFIRMED))
                .totalReviews(reviewRepository.count())
                .totalPendingDocuments(documentRepository.countByStatus(DocumentStatus.PENDING))
                .build();
    }

    // -----------------------------------------------------------------------
    // UC-A12 — global map (all trips, all statuses)
    // -----------------------------------------------------------------------

    /**
     * Returns all trips for the admin global map view (UC-A12).
     *
     * @param pageable pagination (use a large page size for map display)
     * @return paginated page of minimal trip data for map markers
     */
    @Transactional(readOnly = true)
    public Page<TripMapResponse> getGlobalMapTrips(Pageable pageable) {
        return tripRepository.findAll(pageable).map(this::toTripMapResponse);
    }

    // -----------------------------------------------------------------------
    // UC-A13 — list documents by status
    // -----------------------------------------------------------------------

    /**
     * Returns driver documents filtered by status (UC-A13).
     * If statusStr is null or blank, returns all documents.
     *
     * @param statusStr document status filter (PENDING, APPROVED, REJECTED) — optional
     * @param pageable  pagination and sorting
     * @return paginated page of documents with driver info
     * @throws BusinessException if an invalid status string is provided
     */
    @Transactional(readOnly = true)
    public Page<DocumentResponse> getDocuments(String statusStr, Pageable pageable) {
        if (statusStr == null || statusStr.isBlank()) {
            return documentRepository.findAll(pageable).map(this::toDocumentResponse);
        }
        DocumentStatus status;
        try {
            status = DocumentStatus.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "Invalid document status: " + statusStr + ". Allowed: PENDING, APPROVED, REJECTED");
        }
        return documentRepository.findByStatus(status, pageable).map(this::toDocumentResponse);
    }

    // -----------------------------------------------------------------------
    // UC-A14 — approve or reject a document (R08, R11)
    // -----------------------------------------------------------------------

    /**
     * Reviews a driver document as an admin: approves or rejects it (UC-A14).
     * On APPROVED:
     *   — LICENSE: sets driver.licenseVerified = true (R08)
     *   — CAR_REGISTRATION: sets car.registrationVerified = true (R08)
     * A rejectionReason is mandatory when the status is REJECTED.
     *
     * @param documentId the document UUID
     * @param request    review outcome (APPROVED or REJECTED) and optional rejection reason
     * @param adminEmail the authenticated admin's email (for reviewed_by)
     * @return updated DocumentResponse
     * @throws ResourceNotFoundException if the document does not exist
     * @throws BusinessException         if the document was already reviewed,
     *                                   or the rejection reason is missing when rejecting
     */
    @Transactional
    public DocumentResponse reviewDocument(UUID documentId,
                                           AdminDocumentReviewRequest request,
                                           String adminEmail) {
        Admin admin = loadAdmin(adminEmail);

        DriverDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        if (document.getStatus() != DocumentStatus.PENDING) {
            throw new BusinessException("Document has already been reviewed");
        }

        if (request.getStatus() == DocumentStatus.REJECTED
                && (request.getRejectionReason() == null || request.getRejectionReason().isBlank())) {
            throw new BusinessException("A rejection reason is required when rejecting a document");
        }

        if (request.getStatus() == DocumentStatus.PENDING) {
            throw new BusinessException("PENDING is not a valid review outcome");
        }

        document.setStatus(request.getStatus());
        document.setRejectionReason(request.getRejectionReason());
        document.setReviewedBy(admin);
        document.setReviewedAt(LocalDateTime.now());

        // R08 — set verification flags when a document is approved
        if (request.getStatus() == DocumentStatus.APPROVED) {
            Driver driver = document.getDriver();
            if (document.getType() == DocumentType.LICENSE) {
                driver.setLicenseVerified(true);
                userRepository.save(driver);
            } else if (document.getType() == DocumentType.CAR_REGISTRATION
                    && document.getCar() != null) {
                Car car = document.getCar();
                car.setRegistrationVerified(true);
                carRepository.save(car);
            }
        }

        return toDocumentResponse(documentRepository.save(document));
    }

    // -----------------------------------------------------------------------
    // UC-A15 — notify driver about a pending document
    // -----------------------------------------------------------------------

    /**
     * Sends a re-notification to a driver about their pending document (UC-A15).
     * This validates the document exists; the actual notification channel (email, push)
     * is handled externally and is out of scope for this backend implementation.
     *
     * @param documentId the document UUID
     * @throws ResourceNotFoundException if the document does not exist
     */
    @Transactional(readOnly = true)
    public void notifyDriver(UUID documentId) {
        documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
        // Notification dispatch is handled by an external service; endpoint returns 200 OK.
    }

    // -----------------------------------------------------------------------
    // private helpers
    // -----------------------------------------------------------------------

    private Admin loadAdmin(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        if (!(user instanceof Admin admin)) {
            throw new AccessDeniedException("Only admins can perform this action");
        }
        return admin;
    }

    private String resolveRole(User user) {
        if (user instanceof Passenger) return "PASSENGER";
        if (user instanceof Driver)    return "DRIVER";
        if (user instanceof Admin)     return "ADMIN";
        throw new BusinessException("Unknown user type");
    }

    private void deleteFromSubtypeTable(UUID id, String role) {
        String table = switch (role) {
            case "PASSENGER" -> "passengers";
            case "DRIVER"    -> "drivers";
            case "ADMIN"     -> "admins";
            default -> throw new BusinessException("Unknown role: " + role);
        };
        entityManager.createNativeQuery("DELETE FROM " + table + " WHERE user_id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }

    private void insertIntoSubtypeTable(UUID id, String role) {
        switch (role) {
            case "PASSENGER" -> entityManager
                    .createNativeQuery(
                            "INSERT INTO passengers (user_id, total_trips_done) VALUES (:id, 0)")
                    .setParameter("id", id)
                    .executeUpdate();
            case "DRIVER" -> entityManager
                    .createNativeQuery(
                            "INSERT INTO drivers (user_id, license_verified, total_trips_driven, acceptance_rate)"
                            + " VALUES (:id, false, 0, 0.00)")
                    .setParameter("id", id)
                    .executeUpdate();
            case "ADMIN" -> entityManager
                    .createNativeQuery(
                            "INSERT INTO admins (user_id) VALUES (:id)")
                    .setParameter("id", id)
                    .executeUpdate();
            default -> throw new BusinessException("Unknown role: " + role);
        }
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        AdminUserResponse.AdminUserResponseBuilder builder = AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .avgRating(user.getAvgRating())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt());

        if (user instanceof Driver d) {
            builder.role("DRIVER")
                   .licenseVerified(d.getLicenseVerified())
                   .licenseNumber(d.getLicenseNumber());
        } else if (user instanceof Passenger) {
            builder.role("PASSENGER");
        } else if (user instanceof Admin a) {
            builder.role("ADMIN")
                   .permissions(a.getPermissions());
        }

        return builder.build();
    }

    private TripResponse toTripResponse(Trip t) {
        return TripResponse.builder()
                .id(t.getId())
                .driverId(t.getDriver().getId())
                .driverFirstName(t.getDriver().getFirstName())
                .driverLastName(t.getDriver().getLastName())
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

    private TripMapResponse toTripMapResponse(Trip t) {
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

    private ReservationResponse toReservationResponse(Reservation r) {
        return ReservationResponse.builder()
                .id(r.getId())
                .tripId(r.getTrip().getId())
                .tripOriginLabel(r.getTrip().getOriginLabel())
                .tripDestinationLabel(r.getTrip().getDestinationLabel())
                .tripDepartureAt(r.getTrip().getDepartureAt())
                .tripPricePerSeat(r.getTrip().getPricePerSeat())
                .passengerId(r.getPassenger().getId())
                .passengerFirstName(r.getPassenger().getFirstName())
                .passengerLastName(r.getPassenger().getLastName())
                .seatsBooked(r.getSeatsBooked())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private ReviewResponse toReviewResponse(Review r) {
        return ReviewResponse.builder()
                .id(r.getId())
                .reservationId(r.getReservation().getId())
                .authorId(r.getAuthor().getId())
                .authorFirstName(r.getAuthor().getFirstName())
                .authorLastName(r.getAuthor().getLastName())
                .targetId(r.getTarget().getId())
                .targetFirstName(r.getTarget().getFirstName())
                .targetLastName(r.getTarget().getLastName())
                .direction(r.getDirection())
                .rating(r.getRating())
                .comment(r.getComment())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private DocumentResponse toDocumentResponse(DriverDocument doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .type(doc.getType())
                .mimeType(doc.getMimeType())
                .status(doc.getStatus())
                .rejectionReason(doc.getRejectionReason())
                .carId(doc.getCar() != null ? doc.getCar().getId() : null)
                .driverId(doc.getDriver().getId())
                .driverFirstName(doc.getDriver().getFirstName())
                .driverLastName(doc.getDriver().getLastName())
                .uploadedAt(doc.getUploadedAt())
                .reviewedAt(doc.getReviewedAt())
                .build();
    }
}
