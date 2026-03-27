package com.covosio.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ReservationRequest {

    @NotNull(message = "Trip ID is required")
    private UUID tripId;

    @NotNull(message = "Number of seats is required")
    @Min(value = 1, message = "At least one seat must be booked")
    private Integer seatsBooked;
}
