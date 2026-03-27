package com.covosio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Aggregated platform statistics for the admin dashboard (UC-A11). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformStatsResponse {

    private long totalUsers;
    private long totalDrivers;
    private long totalPassengers;
    private long totalAdmins;

    private long totalTrips;
    private long totalAvailableTrips;
    private long totalCompletedTrips;

    private long totalReservations;
    private long totalConfirmedReservations;

    private long totalReviews;
    private long totalPendingDocuments;
}
