package com.covosio.integration;

import com.covosio.dto.LoginRequest;
import com.covosio.dto.RefreshTokenRequest;
import com.covosio.dto.RegisterRequest;
import com.covosio.entity.Passenger;
import com.covosio.entity.RefreshToken;
import com.covosio.repository.RefreshTokenRepository;
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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --- POST /auth/register ---

    @Test
    void register_shouldReturn201_whenDataIsValid() throws Exception {
        RegisterRequest req = new RegisterRequest("alice@test.com", "password123", "Alice", "Smith", "0600000000");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("PASSENGER"))
                .andExpect(jsonPath("$.email").value("alice@test.com"));
    }

    @Test
    void register_shouldReturn400_whenEmailAlreadyExists() throws Exception {
        createPassengerInDb("existing@test.com", "password123");

        RegisterRequest req = new RegisterRequest("existing@test.com", "password123", "Bob", "Dup", null);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void register_shouldReturn400_whenPasswordTooShort() throws Exception {
        RegisterRequest req = new RegisterRequest("short@test.com", "abc", "Short", "Pass", null);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // --- POST /auth/login ---

    @Test
    void login_shouldReturn200_whenCredentialsAreValid() throws Exception {
        createPassengerInDb("user@test.com", "password123");

        LoginRequest req = new LoginRequest("user@test.com", "password123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("PASSENGER"));
    }

    @Test
    void login_shouldReturn400_whenPasswordIsWrong() throws Exception {
        createPassengerInDb("user@test.com", "password123");

        LoginRequest req = new LoginRequest("user@test.com", "wrongpassword");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    // --- POST /auth/refresh ---

    @Test
    void refresh_shouldReturn200_whenTokenIsValid() throws Exception {
        Passenger passenger = createPassengerInDb("user@test.com", "password123");
        String tokenValue = createRefreshTokenInDb(passenger, LocalDateTime.now().plusDays(7));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(tokenValue))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void refresh_shouldReturn400_whenTokenIsRevoked() throws Exception {
        Passenger passenger = createPassengerInDb("user@test.com", "password123");
        String tokenValue = createRevokedRefreshTokenInDb(passenger);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(tokenValue))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    // --- POST /auth/logout ---

    @Test
    void logout_shouldReturn204_whenTokenIsValid() throws Exception {
        Passenger passenger = createPassengerInDb("user@test.com", "password123");
        String tokenValue = createRefreshTokenInDb(passenger, LocalDateTime.now().plusDays(7));

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(tokenValue))))
                .andExpect(status().isNoContent());

        RefreshToken stored = refreshTokenRepository.findByToken(tokenValue).orElseThrow();
        assertThat(stored.getRevoked()).isTrue();
    }

    @Test
    void logout_shouldReturn400_whenTokenIsUnknown() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("ghost-token"))))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private Passenger createPassengerInDb(String email, String rawPassword) {
        Passenger p = new Passenger();
        p.setEmail(email);
        p.setPasswordHash(passwordEncoder.encode(rawPassword));
        p.setFirstName("Test");
        p.setLastName("User");
        p.setIsActive(true);
        p.setTotalTripsDone(0);
        return (Passenger) userRepository.save(p);
    }

    private String createRefreshTokenInDb(Passenger passenger, LocalDateTime expiresAt) {
        String value = UUID.randomUUID().toString();
        RefreshToken rt = RefreshToken.builder()
                .user(passenger)
                .token(value)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();
        refreshTokenRepository.save(rt);
        return value;
    }

    private String createRevokedRefreshTokenInDb(Passenger passenger) {
        String value = UUID.randomUUID().toString();
        RefreshToken rt = RefreshToken.builder()
                .user(passenger)
                .token(value)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(true)
                .build();
        refreshTokenRepository.save(rt);
        return value;
    }
}
