package com.covosio.repository;

import com.covosio.entity.PassengerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PassengerProfileRepository extends JpaRepository<PassengerProfile, UUID> {
    boolean existsByUserId(UUID userId);
    Optional<PassengerProfile> findByUserId(UUID userId);
}
