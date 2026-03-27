package com.covosio.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
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
public class TripRequest {

    @NotNull(message = "Car ID is required")
    private UUID carId;

    @NotBlank(message = "Origin label is required")
    @Size(max = 255, message = "Origin label must not exceed 255 characters")
    private String originLabel;

    @NotNull(message = "Origin latitude is required")
    private BigDecimal originLat;

    @NotNull(message = "Origin longitude is required")
    private BigDecimal originLng;

    @NotBlank(message = "Destination label is required")
    @Size(max = 255, message = "Destination label must not exceed 255 characters")
    private String destinationLabel;

    @NotNull(message = "Destination latitude is required")
    private BigDecimal destLat;

    @NotNull(message = "Destination longitude is required")
    private BigDecimal destLng;

    @NotNull(message = "Departure time is required")
    @Future(message = "Departure time must be in the future")
    private LocalDateTime departureAt;

    @NotNull(message = "Seats available is required")
    @Min(value = 1, message = "At least 1 seat must be available")
    private Integer seatsAvailable;

    @NotNull(message = "Price per seat is required")
    @DecimalMin(value = "0.0", message = "Price per seat must be zero or greater")
    private BigDecimal pricePerSeat;

    private Boolean petsAllowed    = false;
    private Boolean smokingAllowed = false;
}
