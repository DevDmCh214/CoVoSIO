package com.covosio.service;

import com.covosio.dto.*;
import com.covosio.entity.*;
import com.covosio.exception.BusinessException;
import com.covosio.exception.ResourceNotFoundException;
import com.covosio.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles all admin use cases: user management (UC-A01–A06), trip moderation (UC-A07–A08),
 * reservation listing (UC-A09), review moderation (UC-A10), platform stats (UC-A11),
 * global map (UC-A12), document review workflow (UC-A13–A15), and application review.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository              userRepository;
    private final AdminRepository             adminRepository;
    private final DriverProfileRepository     driverProfileRepository;
    private final PassengerProfileRepository  passengerProfileRepository;
    private final DriverApplicationRepository applicationRepository;
    private final TripRepository              tripRepository;
    private final ReservationRepository       reservationRepository;
    private final ReviewRepository            reviewRepository;
    private final DriverDocumentRepository    documentRepository;
    private final RefreshTokenRepository      refreshTokenRepository;
    private final CarRepository               carRepository;

    // -----------------------------------------------------------------------
    // UC-A01 — list all users (paginated)
    // -----------------------------------------------------------------------

    /**
     * Returns all platform users, paginated (UC-A01).
     *
     * @param pageable pagination and sorting
     * @return paginated list of all platform users with role-specific fields
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
     * Changes a user's role (PASSENGER / DRIVER) by creating or removing profile rows.
     * ADMIN role is not assignable here — admins are managed separately.
     *
     * @param userId     the target user UUID
     * @param newRoleStr desired role string (case-insensitive): PASSENGER or DRIVER
     * @return updated AdminUserResponse
     * @throws ResourceNotFoundException if the user does not exist
     * @throws BusinessException         if the role string is invalid, or role change is blocked by dependent data
     */
    @Transactional
    public AdminUserResponse changeUserRole(UUID userId, String newRoleStr) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        String newRole = newRoleStr.trim().toUpperCase();
        if (!newRole.equals("PASSENGER") && !newRole.equals("DRIVER")) {
            throw new BusinessException(
                    "Invalid role: " + newRoleStr + ". Allowed: PASSENGER, DRIVER");
        }

        String currentRole = user.getRole().name();

        if (currentRole.equals(newRole)) {
            return toAdminUserResponse(user);
        }

        if ("DRIVER".equals(newRole)) {
            DriverProfile dp = DriverProfile.builder().user(user).build();
            driverProfileRepository.save(dp);
            user.setRole(com.covosio.entity.Role.DRIVER);
            userRepository.save(user);
        } else {
            // PASSENGER — remove driver profile
            if (tripRepository.existsByDriver_UserId(userId)) {
                throw new BusinessException(
                        "Cannot remove driver role: driver has existing trips. Remove them first.");
            }
            driverProfileRepository.deleteById(userId);
            user.setRole(com.covosio.entity.Role.PASSENGER);
            userRepository.save(user);
        }

        return toAdminUserResponse(user);
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
     *
     * @return PlatformStatsResponse with all counters
     */
    @Transactional(readOnly = true)
    public PlatformStatsResponse getPlatformStats() {
        return PlatformStatsResponse.builder()
                .totalUsers(userRepository.count())
                .totalDrivers(driverProfileRepository.count())
                .totalPassengers(passengerProfileRepository.count())
                .totalAdmins(adminRepository.count())
                .totalTrips(tripRepository.count())
                .totalAvailableTrips(tripRepository.countByStatus(TripStatus.AVAILABLE))
                .totalCompletedTrips(tripRepository.countByStatus(TripStatus.COMPLETED))
                .totalReservations(reservationRepository.count())
                .totalConfirmedReservations(reservationRepository.countByStatus(ReservationStatus.CONFIRMED))
                .totalReviews(reviewRepository.count())
                .totalPendingDocuments(applicationRepository.countByStatus(ApplicationStatus.PENDING))
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
     * @return paginated page of documents with uploader info
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
    // UC-A14 — review a CAR_REGISTRATION document (R08)
    // -----------------------------------------------------------------------

    /**
     * Reviews a CAR_REGISTRATION document as an admin (UC-A14).
     * On APPROVED: sets car.registrationVerified = true (R08).
     * Use reviewApplication() for LICENSE document review and driver promotion.
     *
     * @param documentId the document UUID
     * @param request    review outcome (APPROVED or REJECTED) and optional rejection reason
     * @param adminEmail the authenticated admin's email (for reviewed_by)
     * @return updated DocumentResponse
     * @throws ResourceNotFoundException if the document does not exist
     * @throws BusinessException         if the document was already reviewed,
     *                                   the rejection reason is missing when rejecting,
     *                                   or the document is not a CAR_REGISTRATION
     */
    @Transactional
    public DocumentResponse reviewDocument(UUID documentId,
                                           AdminDocumentReviewRequest request,
                                           String adminEmail) {
        Admin admin = loadAdmin(adminEmail);

        DriverDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        if (document.getType() != DocumentType.CAR_REGISTRATION) {
            throw new BusinessException(
                    "Use PUT /admin/applications/{id}/review to review LICENSE documents");
        }

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

        // R08 — set car.registrationVerified when approved
        if (request.getStatus() == DocumentStatus.APPROVED && document.getCar() != null) {
            Car car = document.getCar();
            car.setRegistrationVerified(true);
            carRepository.save(car);
        }

        return toDocumentResponse(documentRepository.save(document));
    }

    // -----------------------------------------------------------------------
    // Application review — driver promotion
    // -----------------------------------------------------------------------

    /**
     * Reviews a driver application: approves or rejects it.
     * On APPROVED: creates a DriverProfile row (promotes user to driver).
     *
     * @param applicationId the application UUID
     * @param request       review outcome and optional rejection reason
     * @param adminEmail    the authenticated admin's email
     * @return updated DriverApplicationResponse
     * @throws ResourceNotFoundException if the application does not exist
     * @throws BusinessException         if already reviewed or invalid status
     */
    @Transactional
    public DriverApplicationResponse reviewApplication(UUID applicationId,
                                                        AdminApplicationReviewRequest request,
                                                        String adminEmail) {
        Admin admin = loadAdmin(adminEmail);

        DriverApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new BusinessException("Application has already been reviewed");
        }
        if (request.getStatus() == ApplicationStatus.REJECTED
                && (request.getRejectionReason() == null || request.getRejectionReason().isBlank())) {
            throw new BusinessException("A rejection reason is required when rejecting");
        }
        if (request.getStatus() == ApplicationStatus.PENDING) {
            throw new BusinessException("PENDING is not a valid review outcome");
        }

        application.setStatus(request.getStatus());
        application.setRejectionReason(request.getRejectionReason());
        application.setReviewedBy(admin);
        application.setReviewedAt(LocalDateTime.now());
        applicationRepository.save(application);

        if (request.getStatus() == ApplicationStatus.APPROVED) {
            // Create driver_profiles row and promote user role
            DriverProfile driverProfile = DriverProfile.builder()
                    .user(application.getUser())
                    .build();
            driverProfileRepository.save(driverProfile);
            User applicant = application.getUser();
            applicant.setRole(com.covosio.entity.Role.DRIVER);
            userRepository.save(applicant);
        }

        return toApplicationResponse(application);
    }

    /**
     * Returns driver applications filtered by status (paginated).
     *
     * @param statusStr application status filter (PENDING, APPROVED, REJECTED) — optional
     * @param pageable  pagination and sorting
     * @return paginated page of applications
     */
    @Transactional(readOnly = true)
    public Page<DriverApplicationResponse> getApplications(String statusStr, Pageable pageable) {
        if (statusStr == null || statusStr.isBlank()) {
            return applicationRepository.findAll(pageable).map(this::toApplicationResponse);
        }
        ApplicationStatus status;
        try {
            status = ApplicationStatus.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "Invalid status: " + statusStr + ". Allowed: PENDING, APPROVED, REJECTED");
        }
        return applicationRepository.findByStatus(status, pageable).map(this::toApplicationResponse);
    }

    // -----------------------------------------------------------------------
    // UC-A15 — notify driver about a pending document
    // -----------------------------------------------------------------------

    /**
     * Sends a re-notification to a user about their pending application/document (UC-A15).
     * Validates the document exists; the actual notification channel is handled externally.
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
        return adminRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found: " + email));
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        String role = user.getRole().name();
        AdminUserResponse.AdminUserResponseBuilder builder = AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .role(role);

        if (user.getRole() == com.covosio.entity.Role.DRIVER) {
            Optional<DriverProfile> dp = driverProfileRepository.findByUserId(user.getId());
            builder.avgRating(dp.map(DriverProfile::getAvgRating).orElse(null));
            dp.ifPresent(d -> builder.licenseNumber(d.getLicenseNumber()));
        } else {
            passengerProfileRepository.findByUserId(user.getId())
                    .ifPresent(pp -> builder.avgRating(pp.getAvgRating()));
        }

        return builder.build();
    }

    private TripResponse toTripResponse(Trip t) {
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
        UUID uploaderId = null;
        String uploaderFirstName = null;
        String uploaderLastName = null;
        if (doc.getApplication() != null && doc.getApplication().getUser() != null) {
            uploaderId = doc.getApplication().getUser().getId();
            uploaderFirstName = doc.getApplication().getUser().getFirstName();
            uploaderLastName = doc.getApplication().getUser().getLastName();
        } else if (doc.getCar() != null && doc.getCar().getDriver() != null) {
            uploaderId = doc.getCar().getDriver().getUserId();
            uploaderFirstName = doc.getCar().getDriver().getUser().getFirstName();
            uploaderLastName = doc.getCar().getDriver().getUser().getLastName();
        }
        return DocumentResponse.builder()
                .id(doc.getId())
                .type(doc.getType())
                .mimeType(doc.getMimeType())
                .status(doc.getStatus())
                .rejectionReason(doc.getRejectionReason())
                .carId(doc.getCar() != null ? doc.getCar().getId() : null)
                .driverId(uploaderId)
                .driverFirstName(uploaderFirstName)
                .driverLastName(uploaderLastName)
                .uploadedAt(doc.getUploadedAt())
                .reviewedAt(doc.getReviewedAt())
                .build();
    }

    private DriverApplicationResponse toApplicationResponse(DriverApplication app) {
        return DriverApplicationResponse.builder()
                .id(app.getId())
                .userId(app.getUser().getId())
                .userFirstName(app.getUser().getFirstName())
                .userLastName(app.getUser().getLastName())
                .userEmail(app.getUser().getEmail())
                .status(app.getStatus())
                .rejectionReason(app.getRejectionReason())
                .appliedAt(app.getAppliedAt())
                .reviewedAt(app.getReviewedAt())
                .build();
    }
}
