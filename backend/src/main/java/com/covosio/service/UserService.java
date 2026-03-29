package com.covosio.service;

import com.covosio.dto.ChangePasswordRequest;
import com.covosio.dto.PublicUserResponse;
import com.covosio.dto.UpdateProfileRequest;
import com.covosio.dto.UserProfileResponse;
import com.covosio.entity.Role;
import com.covosio.entity.User;
import com.covosio.exception.BusinessException;
import com.covosio.exception.ResourceNotFoundException;
import com.covosio.repository.DriverProfileRepository;
import com.covosio.repository.PassengerProfileRepository;
import com.covosio.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Handles user profile use cases: view own profile (UC-C05), update own profile (UC-C06),
 * change password (UC-C07), and view a public profile (UC-C08).
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository              userRepository;
    private final DriverProfileRepository     driverProfileRepository;
    private final PassengerProfileRepository  passengerProfileRepository;
    private final PasswordEncoder             passwordEncoder;

    /**
     * Returns the full profile of the currently authenticated user (UC-C05).
     *
     * @param email the authenticated user's email (from JWT subject)
     * @return UserProfileResponse with all profile fields
     * @throws ResourceNotFoundException if the user no longer exists
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(String email) {
        User user = loadByEmail(email);
        return toProfileResponse(user);
    }

    /**
     * Updates the profile fields of the currently authenticated user (UC-C06).
     * Only firstName, lastName, phone, and avatarUrl are editable.
     *
     * @param email   the authenticated user's email (from JWT subject)
     * @param request the new profile values
     * @return UserProfileResponse reflecting the updated state
     * @throws ResourceNotFoundException if the user no longer exists
     */
    @Transactional
    public UserProfileResponse updateMyProfile(String email, UpdateProfileRequest request) {
        User user = loadByEmail(email);
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());
        user.setAvatarUrl(request.getAvatarUrl());
        userRepository.save(user);
        return toProfileResponse(user);
    }

    /**
     * Changes the password of the currently authenticated user (UC-C07).
     * The current password is verified before applying the change.
     *
     * @param email   the authenticated user's email (from JWT subject)
     * @param request contains currentPassword and newPassword
     * @throws ResourceNotFoundException if the user no longer exists
     * @throws BusinessException         if the current password is incorrect
     */
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = loadByEmail(email);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /**
     * Returns the public profile of any user by their ID (UC-C08).
     * Exposes only non-sensitive fields: name, avatar, role, and avgRating.
     *
     * @param id the target user's UUID
     * @return PublicUserResponse with public fields only
     * @throws ResourceNotFoundException if no user exists with the given ID
     */
    @Transactional(readOnly = true)
    public PublicUserResponse getPublicProfile(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        return toPublicResponse(user);
    }

    // --- helpers ---

    private User loadByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private UserProfileResponse toProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .avgRating(resolveAvgRating(user))
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .role(user.getRole().name())
                .build();
    }

    private PublicUserResponse toPublicResponse(User user) {
        return PublicUserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .avatarUrl(user.getAvatarUrl())
                .avgRating(resolveAvgRating(user))
                .role(user.getRole().name())
                .build();
    }

    /**
     * Fetches avgRating from the correct profile table based on the user's role.
     * Returns null if the profile is not found.
     */
    private BigDecimal resolveAvgRating(User user) {
        if (user.getRole() == Role.DRIVER) {
            return driverProfileRepository.findByUserId(user.getId())
                    .map(dp -> dp.getAvgRating())
                    .orElse(null);
        }
        return passengerProfileRepository.findByUserId(user.getId())
                .map(pp -> pp.getAvgRating())
                .orElse(null);
    }
}
