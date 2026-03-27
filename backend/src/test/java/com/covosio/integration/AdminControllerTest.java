package com.covosio.integration;

import com.covosio.dto.AdminDocumentReviewRequest;
import com.covosio.dto.AdminReviewModerationRequest;
import com.covosio.dto.AdminUserRoleRequest;
import com.covosio.dto.AdminUserStatusRequest;
import com.covosio.dto.LoginRequest;
import com.covosio.entity.*;
import com.covosio.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminControllerTest {

    @Autowired private MockMvc               mockMvc;
    @Autowired private ObjectMapper          objectMapper;
    @Autowired private UserRepository        userRepository;
    @Autowired private CarRepository         carRepository;
    @Autowired private TripRepository        tripRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReviewRepository      reviewRepository;
    @Autowired private DriverDocumentRepository documentRepository;
    @Autowired private PasswordEncoder       passwordEncoder;
    @PersistenceContext private EntityManager entityManager;

    private Admin     admin;
    private Driver    driver;
    private Passenger passenger;
    private Car       car;
    private Trip      trip;
    private Reservation reservation;
    private Review      review;
    private DriverDocument document;

    private String adminToken;
    private String driverToken;
    private String passengerToken;

    @BeforeEach
    void setUp() throws Exception {
        reviewRepository.deleteAll();
        documentRepository.deleteAll();
        reservationRepository.deleteAll();
        tripRepository.deleteAll();
        carRepository.deleteAll();
        userRepository.deleteAll();

        // Admin
        admin = new Admin();
        admin.setEmail("admin@test.com");
        admin.setPasswordHash(passwordEncoder.encode("password123"));
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setIsActive(true);
        admin.setAvgRating(BigDecimal.ZERO);
        admin = (Admin) userRepository.save(admin);

        // Driver
        driver = new Driver();
        driver.setEmail("driver@test.com");
        driver.setPasswordHash(passwordEncoder.encode("password123"));
        driver.setFirstName("Jean");
        driver.setLastName("Driver");
        driver.setIsActive(true);
        driver.setLicenseVerified(false);
        driver.setTotalTripsDriven(0);
        driver.setAvgRating(BigDecimal.ZERO);
        driver = (Driver) userRepository.save(driver);

        // Car
        car = new Car();
        car.setDriver(driver);
        car.setBrand("Renault");
        car.setModel("Clio");
        car.setColor("Blue");
        car.setPlate("AB-123-CD");
        car.setTotalSeats(4);
        car.setRegistrationVerified(false);
        car.setIsActive(true);
        car = carRepository.save(car);

        // Passenger
        passenger = new Passenger();
        passenger.setEmail("passenger@test.com");
        passenger.setPasswordHash(passwordEncoder.encode("password123"));
        passenger.setFirstName("Alice");
        passenger.setLastName("Passenger");
        passenger.setIsActive(true);
        passenger.setTotalTripsDone(0);
        passenger.setAvgRating(BigDecimal.ZERO);
        passenger = (Passenger) userRepository.save(passenger);

        // Trip
        trip = new Trip();
        trip.setDriver(driver);
        trip.setCar(car);
        trip.setOriginLabel("Paris, France");
        trip.setOriginLat(BigDecimal.valueOf(48.8566));
        trip.setOriginLng(BigDecimal.valueOf(2.3522));
        trip.setDestinationLabel("Lyon, France");
        trip.setDestLat(BigDecimal.valueOf(45.7640));
        trip.setDestLng(BigDecimal.valueOf(4.8357));
        trip.setDepartureAt(LocalDateTime.now().plusDays(2));
        trip.setSeatsAvailable(3);
        trip.setPricePerSeat(BigDecimal.valueOf(15.00));
        trip.setPetsAllowed(false);
        trip.setSmokingAllowed(false);
        trip.setStatus(TripStatus.AVAILABLE);
        trip = tripRepository.save(trip);

        // Reservation
        reservation = new Reservation();
        reservation.setTrip(trip);
        reservation.setPassenger(passenger);
        reservation.setSeatsBooked(1);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation = reservationRepository.save(reservation);

        // Review (completed trip context — mark trip as completed for the review)
        trip.setStatus(TripStatus.COMPLETED);
        tripRepository.save(trip);

        review = new Review();
        review.setReservation(reservation);
        review.setAuthor(passenger);
        review.setTarget(driver);
        review.setDirection(ReviewDirection.PASSENGER_TO_DRIVER);
        review.setRating(4);
        review.setComment("Good driver");
        review = reviewRepository.save(review);

        // Reset trip to AVAILABLE for other tests
        trip.setStatus(TripStatus.AVAILABLE);
        tripRepository.save(trip);

        // Driver document
        document = new DriverDocument();
        document.setDriver(driver);
        document.setType(DocumentType.LICENSE);
        document.setFilePath("/tmp/test-doc.jpg");
        document.setMimeType("image/jpeg");
        document.setStatus(DocumentStatus.PENDING);
        document = documentRepository.save(document);

        adminToken     = fetchToken("admin@test.com",     "password123");
        driverToken    = fetchToken("driver@test.com",    "password123");
        passengerToken = fetchToken("passenger@test.com", "password123");
    }

    // -----------------------------------------------------------------------
    // Access control — non-admin users must be rejected from /admin/**
    // -----------------------------------------------------------------------

    @Test
    void adminEndpoints_shouldReturn403_whenCalledByPassenger() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoints_shouldReturn403_whenCalledByDriver() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoints_shouldReturn403_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // UC-A01 — GET /admin/users
    // -----------------------------------------------------------------------

    @Test
    void getAllUsers_shouldReturn200WithPagedUsers_whenAdmin() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(3)); // admin + driver + passenger
    }

    // -----------------------------------------------------------------------
    // UC-A02 — GET /admin/users/{id}
    // -----------------------------------------------------------------------

    @Test
    void getUserById_shouldReturn200WithDriverFields_whenAdmin() throws Exception {
        mockMvc.perform(get("/admin/users/{id}", driver.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("driver@test.com"))
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.licenseVerified").value(false));
    }

    @Test
    void getUserById_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(get("/admin/users/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // UC-A05 — PUT /admin/users/{id}/status (suspend / activate + R11)
    // -----------------------------------------------------------------------

    @Test
    void changeUserStatus_shouldSuspend_andReturnIsActiveFalse() throws Exception {
        AdminUserStatusRequest req = new AdminUserStatusRequest();
        req.setActive(false);

        mockMvc.perform(put("/admin/users/{id}/status", driver.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        Driver updated = (Driver) userRepository.findById(driver.getId()).orElseThrow();
        assertThat(updated.getIsActive()).isFalse();
    }

    @Test
    void changeUserStatus_shouldActivate_whenCurrentlySuspended() throws Exception {
        driver.setIsActive(false);
        userRepository.save(driver);

        AdminUserStatusRequest req = new AdminUserStatusRequest();
        req.setActive(true);

        mockMvc.perform(put("/admin/users/{id}/status", driver.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));
    }

    // -----------------------------------------------------------------------
    // UC-A06 — DELETE /admin/users/{id}
    // -----------------------------------------------------------------------

    @Test
    void softDeleteUser_shouldReturn204_andSetIsActiveFalse() throws Exception {
        mockMvc.perform(delete("/admin/users/{id}", passenger.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        Passenger updated = (Passenger) userRepository.findById(passenger.getId()).orElseThrow();
        assertThat(updated.getIsActive()).isFalse();
    }

    @Test
    void softDeleteUser_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(delete("/admin/users/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // UC-A03–A05 — PUT /admin/users/{id}/role
    // -----------------------------------------------------------------------

    @Test
    void changeUserRole_shouldReturn200WithNewRole_whenUserHasNoDependentData() throws Exception {
        // Use a fresh passenger with no reservations — the existing passenger has a reservation
        Passenger fresh = new Passenger();
        fresh.setEmail("fresh@test.com");
        fresh.setPasswordHash(passwordEncoder.encode("password123"));
        fresh.setFirstName("Fresh");
        fresh.setLastName("Passenger");
        fresh.setIsActive(true);
        fresh.setTotalTripsDone(0);
        fresh.setAvgRating(BigDecimal.ZERO);
        fresh = (Passenger) userRepository.save(fresh);

        AdminUserRoleRequest req = new AdminUserRoleRequest();
        req.setRole("DRIVER");

        mockMvc.perform(put("/admin/users/{id}/role", fresh.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.licenseVerified").value(false));
    }

    @Test
    void changeUserRole_shouldReturn400_whenPassengerHasReservations() throws Exception {
        // The main passenger has a reservation — role change must be rejected
        AdminUserRoleRequest req = new AdminUserRoleRequest();
        req.setRole("DRIVER");

        mockMvc.perform(put("/admin/users/{id}/role", passenger.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void changeUserRole_shouldReturn400_whenInvalidRole() throws Exception {
        AdminUserRoleRequest req = new AdminUserRoleRequest();
        req.setRole("SUPERADMIN");

        mockMvc.perform(put("/admin/users/{id}/role", passenger.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // UC-A07 — GET /admin/trips
    // -----------------------------------------------------------------------

    @Test
    void getAllTrips_shouldReturn200WithAllTrips_whenAdmin() throws Exception {
        mockMvc.perform(get("/admin/trips")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // -----------------------------------------------------------------------
    // UC-A08 — DELETE /admin/trips/{id}
    // -----------------------------------------------------------------------

    @Test
    void adminCancelTrip_shouldReturn204_andCancelReservations() throws Exception {
        trip.setStatus(TripStatus.AVAILABLE);
        tripRepository.save(trip);

        mockMvc.perform(delete("/admin/trips/{id}", trip.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Flush pending changes then evict the first-level cache so subsequent
        // findById calls read the DB state, not stale pre-bulk-update entries.
        entityManager.flush();
        entityManager.clear();

        Trip cancelled = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(TripStatus.CANCELLED);

        Reservation updated = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void adminCancelTrip_shouldReturn400_whenAlreadyCancelled() throws Exception {
        trip.setStatus(TripStatus.CANCELLED);
        tripRepository.save(trip);

        mockMvc.perform(delete("/admin/trips/{id}", trip.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void adminCancelTrip_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(delete("/admin/trips/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // UC-A12 — GET /admin/trips/map
    // -----------------------------------------------------------------------

    @Test
    void getGlobalMapTrips_shouldReturn200WithAllStatuses_whenAdmin() throws Exception {
        mockMvc.perform(get("/admin/trips/map")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].originLabel").value("Paris, France"));
    }

    // -----------------------------------------------------------------------
    // UC-A09 — GET /admin/reservations
    // -----------------------------------------------------------------------

    @Test
    void getAllReservations_shouldReturn200WithAllReservations_whenAdmin() throws Exception {
        mockMvc.perform(get("/admin/reservations")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // -----------------------------------------------------------------------
    // UC-A10 — PUT /admin/reviews/{id}
    // -----------------------------------------------------------------------

    @Test
    void updateReview_shouldReturn200WithNewRating_whenAdmin() throws Exception {
        AdminReviewModerationRequest req = new AdminReviewModerationRequest();
        req.setRating(2);
        req.setComment("Updated by admin");

        mockMvc.perform(put("/admin/reviews/{id}", review.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(2))
                .andExpect(jsonPath("$.comment").value("Updated by admin"));
    }

    @Test
    void updateReview_shouldReturn404_whenNotFound() throws Exception {
        AdminReviewModerationRequest req = new AdminReviewModerationRequest();
        req.setRating(3);

        mockMvc.perform(put("/admin/reviews/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // UC-A10 — DELETE /admin/reviews/{id}
    // -----------------------------------------------------------------------

    @Test
    void deleteReview_shouldReturn204_whenAdmin() throws Exception {
        mockMvc.perform(delete("/admin/reviews/{id}", review.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(reviewRepository.existsById(review.getId())).isFalse();
    }

    @Test
    void deleteReview_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(delete("/admin/reviews/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // UC-A11 — GET /admin/stats
    // -----------------------------------------------------------------------

    @Test
    void getPlatformStats_shouldReturn200WithCounts_whenAdmin() throws Exception {
        mockMvc.perform(get("/admin/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(3))
                .andExpect(jsonPath("$.totalDrivers").value(1))
                .andExpect(jsonPath("$.totalPassengers").value(1))
                .andExpect(jsonPath("$.totalAdmins").value(1))
                .andExpect(jsonPath("$.totalTrips").value(1))
                .andExpect(jsonPath("$.totalPendingDocuments").value(1));
    }

    // -----------------------------------------------------------------------
    // UC-A13 — GET /admin/documents
    // -----------------------------------------------------------------------

    @Test
    void getDocuments_shouldReturnAll_whenNoFilter() throws Exception {
        mockMvc.perform(get("/admin/documents")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].driverId").value(driver.getId().toString()));
    }

    @Test
    void getDocuments_shouldFilterByStatus_whenStatusParamProvided() throws Exception {
        mockMvc.perform(get("/admin/documents").param("status", "PENDING")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getDocuments_shouldReturnEmpty_whenNoMatchingStatus() throws Exception {
        mockMvc.perform(get("/admin/documents").param("status", "APPROVED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getDocuments_shouldReturn400_whenInvalidStatus() throws Exception {
        mockMvc.perform(get("/admin/documents").param("status", "INVALID")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // UC-A14 — PUT /admin/documents/{id}/review
    // -----------------------------------------------------------------------

    @Test
    void reviewDocument_shouldApprove_andSetLicenseVerified() throws Exception {
        AdminDocumentReviewRequest req = new AdminDocumentReviewRequest();
        req.setStatus(DocumentStatus.APPROVED);

        mockMvc.perform(put("/admin/documents/{id}/review", document.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        Driver updatedDriver = (Driver) userRepository.findById(driver.getId()).orElseThrow();
        assertThat(updatedDriver.getLicenseVerified()).isTrue();
    }

    @Test
    void reviewDocument_shouldReject_whenRejectionReasonProvided() throws Exception {
        AdminDocumentReviewRequest req = new AdminDocumentReviewRequest();
        req.setStatus(DocumentStatus.REJECTED);
        req.setRejectionReason("Photo is blurry");

        mockMvc.perform(put("/admin/documents/{id}/review", document.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Photo is blurry"));
    }

    @Test
    void reviewDocument_shouldReturn400_whenRejectingWithoutReason() throws Exception {
        AdminDocumentReviewRequest req = new AdminDocumentReviewRequest();
        req.setStatus(DocumentStatus.REJECTED);
        // no rejectionReason

        mockMvc.perform(put("/admin/documents/{id}/review", document.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void reviewDocument_shouldReturn400_whenAlreadyReviewed() throws Exception {
        document.setStatus(DocumentStatus.APPROVED);
        documentRepository.save(document);

        AdminDocumentReviewRequest req = new AdminDocumentReviewRequest();
        req.setStatus(DocumentStatus.APPROVED);

        mockMvc.perform(put("/admin/documents/{id}/review", document.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void reviewDocument_shouldReturn404_whenNotFound() throws Exception {
        AdminDocumentReviewRequest req = new AdminDocumentReviewRequest();
        req.setStatus(DocumentStatus.APPROVED);

        mockMvc.perform(put("/admin/documents/{id}/review", UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // UC-A15 — POST /admin/documents/{id}/notify
    // -----------------------------------------------------------------------

    @Test
    void notifyDriver_shouldReturn200_whenDocumentExists() throws Exception {
        mockMvc.perform(post("/admin/documents/{id}/notify", document.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void notifyDriver_shouldReturn404_whenDocumentNotFound() throws Exception {
        mockMvc.perform(post("/admin/documents/{id}/notify", UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private String fetchToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
