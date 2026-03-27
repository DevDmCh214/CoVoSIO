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

/**
 * Minimal trip data for Leaflet map markers (UC-P07, UC-D10).
 * Contains coordinates, basic info for the popup, and status for color-coding.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripMapResponse {

    private UUID   id;
    private String originLabel;
    private BigDecimal originLat;
    private BigDecimal originLng;
    private String destinationLabel;
    private BigDecimal destLat;
    private BigDecimal destLng;
    private Integer    seatsAvailable;
    private BigDecimal pricePerSeat;
    private LocalDateTime departureAt;
    private TripStatus status;
}
