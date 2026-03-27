package com.covosio.dto;

import com.covosio.entity.ReservationStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ReservationResponse {

    private UUID id;

    // Trip summary
    private UUID tripId;
    private String tripOriginLabel;
    private String tripDestinationLabel;
    private LocalDateTime tripDepartureAt;
    private BigDecimal tripPricePerSeat;

    // Passenger summary
    private UUID passengerId;
    private String passengerFirstName;
    private String passengerLastName;

    private Integer seatsBooked;
    private ReservationStatus status;
    private LocalDateTime createdAt;
}
