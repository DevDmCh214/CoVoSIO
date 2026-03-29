package com.covosio.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "passenger_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PassengerProfile {

    /** PK is the same UUID as the owning User — set via @MapsId. */
    @Id
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "avg_rating", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal avgRating = BigDecimal.ZERO;

    /** Number of reviews received as a passenger (DRIVER_TO_PASSENGER direction). */
    @Column(name = "rating_count")
    @Builder.Default
    private Integer ratingCount = 0;

    @Column(name = "total_trips_done")
    @Builder.Default
    private Integer totalTripsDone = 0;

    @Column(name = "last_search_at")
    private LocalDateTime lastSearchAt;
}
