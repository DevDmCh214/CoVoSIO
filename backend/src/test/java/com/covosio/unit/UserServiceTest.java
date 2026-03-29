package com.covosio.unit;

import com.covosio.dto.ChangePasswordRequest;
import com.covosio.dto.PublicUserResponse;
import com.covosio.dto.UpdateProfileRequest;
import com.covosio.dto.UserProfileResponse;
import com.covosio.entity.User;
import com.covosio.exception.BusinessException;
import com.covosio.exception.ResourceNotFoundException;
import com.covosio.repository.DriverProfileRepository;
import com.covosio.repository.UserRepository;
import com.covosio.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository          userRepository;
    @Mock private DriverProfileRepository driverProfileRepository;
    @Mock private PasswordEncoder         passwordEncoder;

    @InjectMocks
    private UserService userService;

    // --- getMyProfile (UC-C05) ---

    @Test
    void getMyProfile_shouldReturnProfile_whenUserExists() {
        User user = buildUser("alice@test.com");
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.getMyProfile("alice@test.com");

        assertThat(response.getEmail()).isEqualTo("alice@test.com");
        assertThat(response.getFirstName()).isEqualTo("Alice");
        assertThat(response.getRole()).isEqualTo("PASSENGER");
    }

    @Test
    void getMyProfile_shouldThrowResourceNotFoundException_whenUserDoesNotExist() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyProfile("ghost@test.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost@test.com");
    }

    // --- updateMyProfile (UC-C06) ---

    @Test
    void updateMyProfile_shouldUpdateFields_whenRequestIsValid() {
        User user = buildUser("alice@test.com");
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest("Bob", "Jones", "0611111111", null);

        UserProfileResponse response = userService.updateMyProfile("alice@test.com", request);

        assertThat(response.getFirstName()).isEqualTo("Bob");
        assertThat(response.getLastName()).isEqualTo("Jones");
        assertThat(response.getPhone()).isEqualTo("0611111111");
        verify(userRepository).save(user);
    }

    @Test
    void updateMyProfile_shouldThrowResourceNotFoundException_whenUserDoesNotExist() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        UpdateProfileRequest request = new UpdateProfileRequest("X", "Y", null, null);

        assertThatThrownBy(() -> userService.updateMyProfile("ghost@test.com", request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- changePassword (UC-C07) ---

    @Test
    void changePassword_shouldUpdateHash_whenCurrentPasswordIsCorrect() {
        User user = buildUser("alice@test.com");
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldpass", "hashed")).thenReturn(true);
        when(passwordEncoder.encode("newpass123")).thenReturn("new-hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.changePassword("alice@test.com", new ChangePasswordRequest("oldpass", "newpass123"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hashed");
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_shouldThrowBusinessException_whenCurrentPasswordIsWrong() {
        User user = buildUser("alice@test.com");
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword("alice@test.com",
                new ChangePasswordRequest("wrongpass", "newpass123")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Current password is incorrect");
    }

    @Test
    void changePassword_shouldThrowResourceNotFoundException_whenUserDoesNotExist() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword("ghost@test.com",
                new ChangePasswordRequest("oldpass", "newpass123")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getPublicProfile (UC-C08) ---

    @Test
    void getPublicProfile_shouldReturnPublicFields_whenUserExists() {
        UUID userId = UUID.randomUUID();
        User user = buildUser("alice@test.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        PublicUserResponse response = userService.getPublicProfile(userId);

        assertThat(response.getFirstName()).isEqualTo("Alice");
        assertThat(response.getLastName()).isEqualTo("Smith");
        assertThat(response.getRole()).isEqualTo("PASSENGER");
        // email must NOT be exposed in public profile
        assertThat(response).doesNotHave(
                new org.assertj.core.api.Condition<>(
                        r -> r.toString().contains("alice@test.com"), "email exposed"));
    }

    @Test
    void getPublicProfile_shouldThrowResourceNotFoundException_whenUserDoesNotExist() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getPublicProfile(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    // --- helpers ---

    private User buildUser(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("hashed")
                .firstName("Alice")
                .lastName("Smith")
                .phone("0600000000")
                .isActive(true)
                .build();
    }
}
