package com.covosio.integration;

import com.covosio.dto.LoginRequest;
import com.covosio.dto.TripRequest;
import com.covosio.entity.*;
import com.covosio.repository.CarRepository;
import com.covosio.repository.TripRepository;
import com.covosio.repository.UserRepository;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TripControllerTest {

    @Autowired private MockMvc        mockMvc;
    @Autowired private ObjectMapper   objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CarRepository  carRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Driver   driver;
    private Passenger passenger;
    private Car      car;
    private String   driverToken;
    private String   passengerToken;

    @BeforeEach
    void setUp() throws Exception {
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

        driverToken    = fetchToken("driver@test.com",    "password123");
        passengerToken = fetchToken("passenger@test.com", "password123");
    }

    // --- POST /trips ---

    @Test
    void createTrip_shouldReturn201_whenDriverIsVerified() throws Exception {
        mockMvc.perform(post("/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + driverToken)
                        .content(objectMapper.writeValueAsString(buildRequest(car.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.originLabel").value("Paris, France"))
                .andExpect(jsonPath("$.driverFirstName").value("Jean"))
                .andExpect(jsonPath("$.carBrand").value("Renault"));
    }

    @Test
    void createTrip_shouldReturn400_whenLicenseNotVerified() throws Exception {
        driver.setLicenseVerified(false);
        userRepository.save(driver);

        mockMvc.perform(post("/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + driverToken)
                        .content(objectMapper.writeValueAsString(buildRequest(car.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void createTrip_shouldReturn403_whenUserIsNotDriver() throws Exception {
        mockMvc.perform(post("/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + passengerToken)
                        .content(objectMapper.writeValueAsString(buildRequest(car.getId()))))
                .andExpect(status().isForbidden());
    }

    // --- GET /trips ---

    @Test
    void searchTrips_shouldReturn200WithPaginatedResults() throws Exception {
        createTripInDb();

        mockMvc.perform(get("/trips")
                        .header("Authorization", "Bearer " + passengerToken)
                        .param("origin", "Paris"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].originLabel").value("Paris, France"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void searchTrips_shouldReturn200WithEmptyResults_whenNoMatch() throws Exception {
        createTripInDb();

        mockMvc.perform(get("/trips")
                        .header("Authorization", "Bearer " + passengerToken)
                        .param("origin", "Marseille"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // --- GET /trips/{id} ---

    @Test
    void getTripById_shouldReturn200_whenTripExists() throws Exception {
        Trip trip = createTripInDb();

        mockMvc.perform(get("/trips/{id}", trip.getId())
                        .header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(trip.getId().toString()))
                .andExpect(jsonPath("$.seatsAvailable").value(3));
    }

    @Test
    void getTripById_shouldReturn404_whenTripNotFound() throws Exception {
        mockMvc.perform(get("/trips/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isNotFound());
    }

    // --- GET /trips/me ---

    @Test
    void getMyTrips_shouldReturn200WithDriverTrips() throws Exception {
        createTripInDb();

        mockMvc.perform(get("/trips/me")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // --- DELETE /trips/{id} ---

    @Test
    void cancelTrip_shouldReturn204_whenOwner() throws Exception {
        Trip trip = createTripInDb();

        mockMvc.perform(delete("/trips/{id}", trip.getId())
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isNoContent());

        Trip cancelled = tripRepository.findById(trip.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(cancelled.getStatus()).isEqualTo(TripStatus.CANCELLED);
    }

    @Test
    void cancelTrip_shouldReturn403_whenNotOwner() throws Exception {
        Trip trip = createTripInDb();

        mockMvc.perform(delete("/trips/{id}", trip.getId())
                        .header("Authorization", "Bearer " + passengerToken))
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

    private Trip createTripInDb() {
        Trip trip = new Trip();
        trip.setDriver(driver);
        trip.setCar(car);
        trip.setOriginLabel("Paris, France");
        trip.setOriginLat(BigDecimal.valueOf(48.8566));
        trip.setOriginLng(BigDecimal.valueOf(2.3522));
        trip.setDestinationLabel("Lyon, France");
        trip.setDestLat(BigDecimal.valueOf(45.7640));
        trip.setDestLng(BigDecimal.valueOf(4.8357));
        trip.setDepartureAt(LocalDateTime.now().plusDays(7));
        trip.setSeatsAvailable(3);
        trip.setPricePerSeat(BigDecimal.valueOf(15.00));
        trip.setPetsAllowed(false);
        trip.setSmokingAllowed(false);
        trip.setStatus(TripStatus.AVAILABLE);
        return tripRepository.save(trip);
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
