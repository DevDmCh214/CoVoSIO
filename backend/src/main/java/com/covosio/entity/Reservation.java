package com.covosio.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Reservation entity stub — Phase 5.
 * Maps only the fields required for R06 (cascade cancel) and R07 (confirm check).
 * Full entity (passenger, seatsBooked, createdAt) is completed in Phase 6.
 */
@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    /** PENDING, CONFIRMED, or CANCELLED. */
    @Column(nullable = false, length = 20)
    private String status;
}
