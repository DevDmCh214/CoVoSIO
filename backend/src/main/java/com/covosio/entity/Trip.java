package com.covosio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    /** Nullable — SET NULL if the car is later hard-deleted. Validated non-null at service level. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id")
    private Car car;

    @Column(name = "origin_label", nullable = false, length = 255)
    private String originLabel;

    @Column(name = "origin_lat", nullable = false, precision = 9, scale = 6)
    private BigDecimal originLat;

    @Column(name = "origin_lng", nullable = false, precision = 9, scale = 6)
    private BigDecimal originLng;

    @Column(name = "destination_label", nullable = false, length = 255)
    private String destinationLabel;

    @Column(name = "dest_lat", nullable = false, precision = 9, scale = 6)
    private BigDecimal destLat;

    @Column(name = "dest_lng", nullable = false, precision = 9, scale = 6)
    private BigDecimal destLng;

    @Column(name = "departure_at", nullable = false)
    private LocalDateTime departureAt;

    @Column(name = "seats_available", nullable = false)
    private Integer seatsAvailable;

    @Column(name = "price_per_seat", nullable = false, precision = 8, scale = 2)
    private BigDecimal pricePerSeat;

    @Column(name = "pets_allowed", nullable = false)
    @Builder.Default
    private Boolean petsAllowed = false;

    @Column(name = "smoking_allowed", nullable = false)
    @Builder.Default
    private Boolean smokingAllowed = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TripStatus status = TripStatus.AVAILABLE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
