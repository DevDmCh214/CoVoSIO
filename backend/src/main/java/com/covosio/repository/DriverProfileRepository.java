package com.covosio.repository;

import com.covosio.entity.DriverProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {
    boolean existsByUserId(UUID userId);
    Optional<DriverProfile> findByUserId(UUID userId);
}
