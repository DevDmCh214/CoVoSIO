package com.covosio.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "passengers")
@DiscriminatorValue("PASSENGER")
@PrimaryKeyJoinColumn(name = "user_id")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Passenger extends User {

    @Column(name = "total_trips_done")
    private Integer totalTripsDone = 0;

    @Column(name = "last_search_at")
    private LocalDateTime lastSearchAt;
}
