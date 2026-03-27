package com.covosio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String avatarUrl;
    private BigDecimal avgRating;
    private Boolean isActive;
    private LocalDateTime createdAt;

    /** Discriminator value: PASSENGER, DRIVER, or ADMIN. */
    private String role;

    // Driver-specific fields — null for non-drivers
    private Boolean licenseVerified;
    private String licenseNumber;

    // Admin-specific fields — null for non-admins
    private String permissions;
}
