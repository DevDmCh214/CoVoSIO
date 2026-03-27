package com.covosio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarResponse {

    private UUID id;
    private String brand;
    private String model;
    private String color;
    private String plate;
    private Integer totalSeats;
    private Boolean registrationVerified;
    private LocalDateTime createdAt;
}
