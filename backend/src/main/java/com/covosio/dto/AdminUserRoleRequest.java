package com.covosio.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUserRoleRequest {

    /** Target role: PASSENGER, DRIVER, or ADMIN. */
    @NotBlank
    private String role;
}
