package com.covosio.repository;

import com.covosio.entity.ApplicationStatus;
import com.covosio.entity.DriverApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface DriverApplicationRepository extends JpaRepository<DriverApplication, UUID> {
    Optional<DriverApplication> findByUser_IdAndStatus(UUID userId, ApplicationStatus status);
    boolean existsByUser_IdAndStatus(UUID userId, ApplicationStatus status);
    Page<DriverApplication> findByStatus(ApplicationStatus status, Pageable pageable);
    long countByStatus(ApplicationStatus status);
}
