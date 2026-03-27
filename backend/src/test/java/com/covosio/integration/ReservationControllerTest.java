package com.covosio.integration;

import com.covosio.dto.LoginRequest;
import com.covosio.dto.ReservationRequest;
import com.covosio.entity.*;
import com.covosio.repository.CarRepository;
import com.covosio.repository.ReservationRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReservationControllerTest {

    @Autowired private MockMvc               mockMvc;
    @Autowired private ObjectMapper          objectMapper;
    @Autowired private UserRepository        userRepository;
    @Autowired private CarRepository         carRepository;
    @Autowired private TripRepository        tripRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PasswordEncoder       passwordEncoder;

    private Driver    driver;
    private Passenger passenger;
    private Car       car;
    private Trip      trip;
    private String    driverToken;
    private String    passengerToken;

    @BeforeEach
    void setUp() throws Exception {
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

        trip = createTripInDb(3, LocalDateTime.now().plusDays(7));

        driverToken    = fetchToken("driver@test.com",    "password123");
        passengerToken = fetchToken("passenger@test.com", "password123");
    }

    // --- POST /reservations ---

    @Test
    void createReservation_shouldReturn201_whenValid() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + passengerToken)
                        .content(objectMapper.writeValueAsString(buildRequest(trip.getId(), 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.seatsBooked").value(2))
                .andExpect(jsonPath("$.passengerFirstName").value("Alice"));

        Trip updated = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(updated.getSeatsAvailable()).isEqualTo(1); // 3 - 2
    }

    @Test
    void createReservation_shouldReturn400_whenNotEnoughSeats() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + passengerToken)
                        .content(objectMapper.writeValueAsString(buildRequest(trip.getId(), 5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void createReservation_shouldReturn403_whenUserIsDriver() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + driverToken)
                        .content(objectMapper.writeValueAsString(buildRequest(trip.getId(), 1))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createReservation_shouldReturn404_whenTripNotFound() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + passengerToken)
                        .content(objectMapper.writeValueAsString(buildRequest(UUID.randomUUID(), 1))))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /reservations/{id} ---

    @Test
    void cancelReservation_shouldReturn204_whenValid() throws Exception {
        Reservation reservation = createReservationInDb(trip, passenger, 2, ReservationStatus.PENDING);

        mockMvc.perform(delete("/reservations/{id}", reservation.getId())
                        .header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isNoContent());

        Reservation updated = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancelReservation_shouldReturn400_whenTooCloseToDepart() throws Exception {
        Trip soonTrip = createTripInDb(3, LocalDateTime.now().plusMinutes(90));
        Reservation reservation = createReservationInDb(soonTrip, passenger, 1, ReservationStatus.PENDING);

        mockMvc.perform(delete("/reservations/{id}", reservation.getId())
                        .header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void cancelReservation_shouldReturn403_whenNotOwner() throws Exception {
        Passenger other = new Passenger();
        other.setEmail("other@test.com");
        other.setPasswordHash(passwordEncoder.encode("password123"));
        other.setFirstName("Bob");
        other.setLastName("Other");
        other.setIsActive(true);
        other.setTotalTripsDone(0);
        other.setAvgRating(BigDecimal.ZERO);
        other = (Passenger) userRepository.save(other);
        String otherToken = fetchToken("other@test.com", "password123");

        Reservation reservation = createReservationInDb(trip, passenger, 1, ReservationStatus.PENDING);

        mockMvc.perform(delete("/reservations/{id}", reservation.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    // --- GET /reservations/me ---

    @Test
    void getMyReservations_shouldReturn200WithReservations() throws Exception {
        createReservationInDb(trip, passenger, 2, ReservationStatus.PENDING);

        mockMvc.perform(get("/reservations/me")
                        .header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].seatsBooked").value(2));
    }

    // --- GET /trips/{id}/reservations ---

    @Test
    void getTripReservations_shouldReturn200_whenTripOwner() throws Exception {
        createReservationInDb(trip, passenger, 2, ReservationStatus.PENDING);

        mockMvc.perform(get("/trips/{id}/reservations", trip.getId())
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getTripReservations_shouldReturn403_whenPassengerAccesses() throws Exception {
        mockMvc.perform(get("/trips/{id}/reservations", trip.getId())
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

    private Trip createTripInDb(int seats, LocalDateTime departureAt) {
        Trip t = new Trip();
        t.setDriver(driver);
        t.setCar(car);
        t.setOriginLabel("Paris, France");
        t.setOriginLat(BigDecimal.valueOf(48.8566));
        t.setOriginLng(BigDecimal.valueOf(2.3522));
        t.setDestinationLabel("Lyon, France");
        t.setDestLat(BigDecimal.valueOf(45.7640));
        t.setDestLng(BigDecimal.valueOf(4.8357));
        t.setDepartureAt(departureAt);
        t.setSeatsAvailable(seats);
        t.setPricePerSeat(BigDecimal.valueOf(15.00));
        t.setPetsAllowed(false);
        t.setSmokingAllowed(false);
        t.setStatus(TripStatus.AVAILABLE);
        return tripRepository.save(t);
    }

    private Reservation createReservationInDb(Trip t, Passenger p,
                                              int seats, ReservationStatus status) {
        Reservation r = new Reservation();
        r.setTrip(t);
        r.setPassenger(p);
        r.setSeatsBooked(seats);
        r.setStatus(status);
        return reservationRepository.save(r);
    }

    private ReservationRequest buildRequest(UUID tripId, int seats) {
        ReservationRequest req = new ReservationRequest();
        req.setTripId(tripId);
        req.setSeatsBooked(seats);
        return req;
    }
}
