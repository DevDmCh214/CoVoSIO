package com.covosio.unit;

import com.covosio.dto.LoginRequest;
import com.covosio.dto.RefreshTokenRequest;
import com.covosio.dto.RegisterRequest;
import com.covosio.dto.AuthResponse;
import com.covosio.entity.RefreshToken;
import com.covosio.entity.User;
import com.covosio.exception.BusinessException;
import com.covosio.repository.AdminRepository;
import com.covosio.repository.DriverProfileRepository;
import com.covosio.repository.PassengerProfileRepository;
import com.covosio.repository.RefreshTokenRepository;
import com.covosio.repository.UserRepository;
import com.covosio.security.JwtUtil;
import com.covosio.security.UserDetailsServiceImpl;
import com.covosio.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository             userRepository;
    @Mock private AdminRepository            adminRepository;
    @Mock private DriverProfileRepository    driverProfileRepository;
    @Mock private PassengerProfileRepository passengerProfileRepository;
    @Mock private RefreshTokenRepository     refreshTokenRepository;
    @Mock private PasswordEncoder            passwordEncoder;
    @Mock private JwtUtil                    jwtUtil;
    @Mock private AuthenticationManager      authenticationManager;
    @Mock private UserDetailsServiceImpl     userDetailsService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshExpirationMs", 604800000L);
    }

    // --- register ---

    @Test
    void register_shouldReturnAuthResponse_whenDataIsValid() {
        RegisterRequest req = new RegisterRequest("alice@test.com", "password123", "Alice", "Smith", "0600000000");

        when(userRepository.existsByEmail("alice@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserDetails ud = buildUserDetails("alice@test.com", "ROLE_PASSENGER");
        when(userDetailsService.loadUserByUsername("alice@test.com")).thenReturn(ud);
        when(jwtUtil.generateAccessToken(ud)).thenReturn("access-token");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.register(req);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRole()).isEqualTo("PASSENGER");
        assertThat(response.getEmail()).isEqualTo("alice@test.com");
    }

    @Test
    void register_shouldThrowBusinessException_whenEmailAlreadyInUse() {
        RegisterRequest req = new RegisterRequest("dup@test.com", "password123", "Bob", "Dup", null);
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Email already in use");
    }

    @Test
    void register_shouldThrowBusinessException_whenEmailMatchesAdminEmail() {
        RegisterRequest req = new RegisterRequest("admin@test.com", "password123", "Bob", "Dup", null);
        when(userRepository.existsByEmail("admin@test.com")).thenReturn(false);
        when(adminRepository.existsByEmail("admin@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Email already in use");
    }

    // --- login ---

    @Test
    void login_shouldReturnAuthResponse_whenCredentialsAreValid() {
        LoginRequest req = new LoginRequest("alice@test.com", "password123");

        when(adminRepository.findByEmail("alice@test.com")).thenReturn(Optional.empty());
        User user = buildPlatformUser("alice@test.com");
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));

        UserDetails ud = buildUserDetails("alice@test.com", "ROLE_PASSENGER");
        when(userDetailsService.loadUserByUsername("alice@test.com")).thenReturn(ud);
        when(jwtUtil.generateAccessToken(ud)).thenReturn("access-token");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login(req);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRole()).isEqualTo("PASSENGER");
    }

    @Test
    void login_shouldReturnAdminAuthResponse_whenAdminCredentialsAreValid() {
        LoginRequest req = new LoginRequest("admin@test.com", "password123");

        com.covosio.entity.Admin admin = com.covosio.entity.Admin.builder()
                .id(UUID.randomUUID())
                .email("admin@test.com")
                .passwordHash("hashed")
                .firstName("Admin")
                .lastName("User")
                .isActive(true)
                .build();
        when(adminRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        UserDetails ud = buildUserDetails("admin@test.com", "ROLE_ADMIN");
        when(userDetailsService.loadUserByUsername("admin@test.com")).thenReturn(ud);
        when(jwtUtil.generateAccessToken(ud)).thenReturn("admin-access-token");

        AuthResponse response = authService.login(req);

        assertThat(response.getAccessToken()).isEqualTo("admin-access-token");
        assertThat(response.getRole()).isEqualTo("ADMIN");
        assertThat(response.getRefreshToken()).isEmpty();
    }

    @Test
    void login_shouldThrowBusinessException_whenCredentialsAreInvalid() {
        LoginRequest req = new LoginRequest("alice@test.com", "wrongpass");
        doThrow(new BadCredentialsException("bad credentials"))
                .when(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_shouldThrowBusinessException_whenAccountIsSuspended() {
        LoginRequest req = new LoginRequest("suspended@test.com", "password123");

        when(adminRepository.findByEmail("suspended@test.com")).thenReturn(Optional.empty());
        User suspended = buildPlatformUser("suspended@test.com");
        suspended.setIsActive(false);
        when(userRepository.findByEmail("suspended@test.com")).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("suspended");
    }

    // --- refresh ---

    @Test
    void refresh_shouldReturnNewAccessToken_whenTokenIsValid() {
        User user = buildPlatformUser("alice@test.com");
        RefreshToken stored = buildRefreshToken(user, false, LocalDateTime.now().plusDays(7));

        when(refreshTokenRepository.findByToken("valid-refresh")).thenReturn(Optional.of(stored));
        UserDetails ud = buildUserDetails("alice@test.com", "ROLE_PASSENGER");
        when(userDetailsService.loadUserByUsername("alice@test.com")).thenReturn(ud);
        when(jwtUtil.generateAccessToken(ud)).thenReturn("new-access-token");

        AuthResponse response = authService.refresh(new RefreshTokenRequest("valid-refresh"));

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("valid-refresh");
    }

    @Test
    void refresh_shouldThrowBusinessException_whenTokenIsRevoked() {
        User user = buildPlatformUser("alice@test.com");
        RefreshToken revoked = buildRefreshToken(user, true, LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("revoked-token")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void refresh_shouldThrowBusinessException_whenTokenIsExpired() {
        User user = buildPlatformUser("alice@test.com");
        RefreshToken expired = buildRefreshToken(user, false, LocalDateTime.now().minusDays(1));
        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("expired-token")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void refresh_shouldThrowBusinessException_whenTokenDoesNotExist() {
        when(refreshTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("unknown")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    // --- logout ---

    @Test
    void logout_shouldRevokeToken_whenTokenIsValid() {
        User user = buildPlatformUser("alice@test.com");
        RefreshToken stored = buildRefreshToken(user, false, LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(stored));

        authService.logout(new RefreshTokenRequest("valid-token"));

        assertThat(stored.getRevoked()).isTrue();
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void logout_shouldThrowBusinessException_whenTokenDoesNotExist() {
        when(refreshTokenRepository.findByToken("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.logout(new RefreshTokenRequest("ghost")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    // --- helpers ---

    private User buildPlatformUser(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("hashed")
                .firstName("Alice")
                .lastName("Smith")
                .isActive(true)
                .build();
    }

    private UserDetails buildUserDetails(String email, String role) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(email)
                .password("hashed")
                .authorities(List.of(new SimpleGrantedAuthority(role)))
                .build();
    }

    private RefreshToken buildRefreshToken(User user, boolean revoked, LocalDateTime expiresAt) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .token(revoked ? "revoked-token" : "valid-refresh")
                .revoked(revoked)
                .expiresAt(expiresAt)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
