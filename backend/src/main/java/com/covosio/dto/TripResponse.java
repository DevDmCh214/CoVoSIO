package com.covosio.dto;

import com.covosio.entity.TripStatus;
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
public class TripResponse {

    private UUID id;

    // Driver summary (UC-P02)
    private UUID    driverId;
    private String  driverFirstName;
    private String  driverLastName;
    private BigDecimal driverAvgRating;

    // Car summary (nullable — car may have been deleted)
    private UUID   carId;
    private String carBrand;
    private String carModel;
    private String carColor;

    // Trip fields
    private String     originLabel;
    private BigDecimal originLat;
    private BigDecimal originLng;
    private String     destinationLabel;
    private BigDecimal destLat;
    private BigDecimal destLng;
    private LocalDateTime departureAt;
    private Integer    seatsAvailable;
    private BigDecimal pricePerSeat;
    private Boolean    petsAllowed;
    private Boolean    smokingAllowed;
    private TripStatus status;
    private LocalDateTime createdAt;
}
