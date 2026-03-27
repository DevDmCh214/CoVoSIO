package com.covosio.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUserStatusRequest {

    /** true = activate the account, false = suspend it. */
    @NotNull
    private Boolean active;
}
