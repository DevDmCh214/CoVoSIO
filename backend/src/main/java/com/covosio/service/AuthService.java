package com.covosio.service;

import com.covosio.dto.AuthResponse;
import com.covosio.dto.LoginRequest;
import com.covosio.dto.RefreshTokenRequest;
import com.covosio.dto.RegisterRequest;
import com.covosio.entity.Passenger;
import com.covosio.entity.RefreshToken;
import com.covosio.entity.User;
import com.covosio.exception.BusinessException;
import com.covosio.exception.ResourceNotFoundException;
import com.covosio.repository.RefreshTokenRepository;
import com.covosio.repository.UserRepository;
import com.covosio.security.JwtUtil;
import com.covosio.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Handles authentication use cases: register (UC-C01), login (UC-C02),
 * token refresh (UC-C03), and logout (UC-C04).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    /**
     * Registers a new user as a Passenger (UC-C01).
     * Email uniqueness is checked before persisting.
     *
     * @param request registration payload (email, password, name, phone)
     * @return AuthResponse with access + refresh tokens
     * @throws BusinessException if the email is already in use
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already in use: " + request.getEmail());
        }

        Passenger passenger = Passenger.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .isActive(true)
                .totalTripsDone(0)
                .build();

        userRepository.save(passenger);

        UserDetails userDetails = userDetailsService.loadUserByUsername(passenger.getEmail());
        String accessToken = jwtUtil.generateAccessToken(userDetails);
        String refreshToken = createAndPersistRefreshToken(passenger);

        return buildAuthResponse(accessToken, refreshToken, passenger, "PASSENGER");
    }

    /**
     * Authenticates a user and issues tokens (UC-C02).
     *
     * @param request login payload (email, password)
     * @return AuthResponse with access + refresh tokens
     * @throws BusinessException if credentials are invalid or the account is suspended
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (LockedException e) {
            throw new BusinessException("Account is suspended");
        } catch (BadCredentialsException e) {
            throw new BusinessException("Invalid email or password");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getEmail()));

        if (!user.getIsActive()) {
            throw new BusinessException("Account is suspended");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtUtil.generateAccessToken(userDetails);
        String refreshToken = createAndPersistRefreshToken(user);
        String role = resolveRole(user);

        return buildAuthResponse(accessToken, refreshToken, user, role);
    }

    /**
     * Issues a new access token from a valid refresh token (UC-C03, R10).
     *
     * @param request contains the refresh token string
     * @return AuthResponse with a new access token (refresh token unchanged)
     * @throws BusinessException if the refresh token is expired, revoked, or unknown
     */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new BusinessException("Invalid refresh token"));

        if (stored.getRevoked()) {
            throw new BusinessException("Refresh token has been revoked");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Refresh token has expired");
        }
        if (!stored.getUser().getIsActive()) {
            throw new BusinessException("Account is suspended");
        }

        User user = stored.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String newAccessToken = jwtUtil.generateAccessToken(userDetails);
        String role = resolveRole(user);

        return buildAuthResponse(newAccessToken, stored.getToken(), user, role);
    }

    /**
     * Revokes the given refresh token, effectively logging the user out (UC-C04).
     *
     * @param request contains the refresh token string to revoke
     * @throws BusinessException if the token does not exist
     */
    @Transactional
    public void logout(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new BusinessException("Invalid refresh token"));
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
    }

    // --- helpers ---

    private String createAndPersistRefreshToken(User user) {
        String tokenValue = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(tokenValue)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
        return tokenValue;
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, User user, String role) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .role(role)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }

    private String resolveRole(User user) {
        return switch (user.getClass().getSimpleName()) {
            case "Passenger" -> "PASSENGER";
            case "Driver"    -> "DRIVER";
            case "Admin"     -> "ADMIN";
            default          -> "PASSENGER";
        };
    }
}
