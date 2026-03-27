package com.covosio.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "drivers")
@DiscriminatorValue("DRIVER")
@PrimaryKeyJoinColumn(name = "user_id")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Driver extends User {

    @Column(name = "license_number", length = 50)
    private String licenseNumber;

    /** Set to true once an admin has approved the driver's license document. */
    @Column(name = "license_verified", nullable = false)
    private Boolean licenseVerified = false;

    @Column(name = "total_trips_driven")
    private Integer totalTripsDriven = 0;

    @Column(name = "acceptance_rate", precision = 5, scale = 2)
    private BigDecimal acceptanceRate = BigDecimal.ZERO;
}
