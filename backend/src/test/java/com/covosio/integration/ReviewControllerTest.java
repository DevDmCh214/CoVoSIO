package com.covosio.integration;

import com.covosio.dto.LoginRequest;
import com.covosio.dto.ReviewRequest;
import com.covosio.entity.*;
import com.covosio.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewControllerTest {

    @Autowired private MockMvc               mockMvc;
    @Autowired private ObjectMapper          objectMapper;
    @Autowired private UserRepository        userRepository;
    @Autowired private CarRepository         carRepository;
    @Autowired private TripRepository        tripRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReviewRepository      reviewRepository;
    @Autowired private PasswordEncoder       passwordEncoder;

    private Driver    driver;
    private Passenger passenger;
    private Car       car;
    private Trip      completedTrip;
    private Reservation reservation;
    private String    driverToken;
    private String    passengerToken;

    @BeforeEach
    void setUp() throws Exception {
        reviewRepository.deleteAll();
        reservationRepository.deleteAll();
        tripRepository.deleteAll();
        carRepository.deleteAll();
        userRepository.deleteAll();

        driver = new Driver();
        driver.setEmail("driver@test.com");
        driver.setPasswordHash(passwordEncoder.encode("password123"));
        driver.setFirstName("Jean");
        driver.setLastName("Driver");
        driver.setIsActive(true);
        driver.setLicenseVerified(true);
        driver.setTotalTripsDriven(0);
        driver.setAvgRating(BigDecimal.ZERO);
        driver = (Driver) userRepository.save(driver);

        car = new Car();
        car.setDriver(driver);
        car.setBrand("Renault");
        car.setModel("Clio");
        car.setColor("Blue");
        car.setPlate("AB-123-CD");
        car.setTotalSeats(4);
        car.setRegistrationVerified(true);
        car.setIsActive(true);
        car = carRepository.save(car);

        passenger = new Passenger();
        passenger.setEmail("passenger@test.com");
        passenger.setPasswordHash(passwordEncoder.encode("password123"));
        passenger.setFirstName("Alice");
        passenger.setLastName("Passenger");
        passenger.setIsActive(true);
        passenger.setTotalTripsDone(0);
        passenger.setAvgRating(BigDecimal.ZERO);
        passenger = (Passenger) userRepository.save(passenger);

        // A completed trip
        completedTrip = new Trip();
        completedTrip.setDriver(driver);
        completedTrip.setCar(car);
        completedTrip.setOriginLabel("Paris, France");
        completedTrip.setOriginLat(BigDecimal.valueOf(48.8566));
        completedTrip.setOriginLng(BigDecimal.valueOf(2.3522));
        completedTrip.setDestinationLabel("Lyon, France");
        completedTrip.setDestLat(BigDecimal.valueOf(45.7640));
        completedTrip.setDestLng(BigDecimal.valueOf(4.8357));
        completedTrip.setDepartureAt(LocalDateTime.now().minusDays(1));
        completedTrip.setSeatsAvailable(0);
        completedTrip.setPricePerSeat(BigDecimal.valueOf(15.00));
        completedTrip.setPetsAllowed(false);
        completedTrip.setSmokingAllowed(false);
        completedTrip.setStatus(TripStatus.COMPLETED);
        completedTrip = tripRepository.save(completedTrip);

        reservation = new Reservation();
        reservation.setTrip(completedTrip);
        reservation.setPassenger(passenger);
        reservation.setSeatsBooked(1);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation = reservationRepository.save(reservation);

        driverToken    = fetchToken("driver@test.com",    "password123");
        passengerToken = fetchToken("passenger@test.com", "password123");
    }

    // --- POST /reservations/{id}/review ---

    @Test
    void createReview_shouldReturn201_whenPassengerReviewsDriver() throws Exception {
        mockMvc.perform(post("/reservations/{id}/review", reservation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + passengerToken)
                        .content(objectMapper.writeValueAsString(buildRequest(5, "Excellent driver!"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("PASSENGER_TO_DRIVER"))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Excellent driver!"))
                .andExpect(jsonPath("$.authorFirstName").value("Alice"))
                .andExpect(jsonPath("$.targetFirstName").value("Jean"));

        // Driver avg_rating must have been recalculated
        Driver updated = (Driver) userRepository.findById(driver.getId()).orElseThrow();
        assertThat(updated.getAvgRating()).isEqualByComparingTo("5.00");
    }

    @Test
    void createReview_shouldReturn201_whenDriverReviewsPassenger() throws Exception {
        mockMvc.perform(post("/reservations/{id}/review", reservation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + driverToken)
                        .content(objectMapper.writeValueAsString(buildRequest(4, "Polite passenger"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("DRIVER_TO_PASSENGER"))
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.authorFirstName").value("Jean"))
                .andExpect(jsonPath("$.targetFirstName").value("Alice"));
    }

    @Test
    void createReview_shouldReturn400_whenTripNotCompleted() throws Exception {
        // Change trip status to AVAILABLE
        completedTrip.setStatus(TripStatus.AVAILABLE);
        tripRepository.save(completedTrip);

        mockMvc.perform(post("/reservations/{id}/review", reservation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + passengerToken)
                        .content(objectMapper.writeValueAsString(buildRequest(5, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void createReview_shouldReturn400_whenDuplicateReview() throws Exception {
        // Submit once
        mockMvc.perform(post("/reservations/{id}/review", reservation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + passengerToken)
                        .content(objectMapper.writeValueAsString(buildRequest(5, null))))
                .andExpect(status().isCreated());

        // Submit again — R05 violation
        mockMvc.perform(post("/reservations/{id}/review", reservation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + passengerToken)
                        .content(objectMapper.writeValueAsString(buildRequest(3, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void createReview_shouldReturn403_whenUserNotPartOfReservation() throws Exception {
        // Third passenger who has nothing to do with this reservation
        Passenger stranger = new Passenger();
        stranger.setEmail("stranger@test.com");
        stranger.setPasswordHash(passwordEncoder.encode("password123"));
        stranger.setFirstName("Bob");
        stranger.setLastName("Stranger");
        stranger.setIsActive(true);
        stranger.setTotalTripsDone(0);
        stranger.setAvgRating(BigDecimal.ZERO);
        userRepository.save(stranger);
        String strangerToken = fetchToken("stranger@test.com", "password123");

        mockMvc.perform(post("/reservations/{id}/review", reservation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + strangerToken)
                        .content(objectMapper.writeValueAsString(buildRequest(1, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createReview_shouldReturn403_whenUnauthenticated() throws Exception {
        // Stateless JWT setup returns 403 (no custom AuthenticationEntryPoint)
        mockMvc.perform(post("/reservations/{id}/review", reservation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest(5, null))))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private String fetchToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private ReviewRequest buildRequest(int rating, String comment) {
        ReviewRequest req = new ReviewRequest();
        req.setRating(rating);
        req.setComment(comment);
        return req;
    }
}
