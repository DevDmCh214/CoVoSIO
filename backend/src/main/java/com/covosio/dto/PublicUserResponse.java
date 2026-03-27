package com.covosio.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class PublicUserResponse {

    private UUID id;
    private String firstName;
    private String lastName;
    private String avatarUrl;
    private BigDecimal avgRating;
    private String role;
}
