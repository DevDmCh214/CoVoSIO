package com.covosio.repository;

import com.covosio.entity.DriverDocument;
import com.covosio.entity.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DriverDocumentRepository extends JpaRepository<DriverDocument, UUID> {

    List<DriverDocument> findByApplication_IdOrderByUploadedAtDesc(UUID applicationId);

    List<DriverDocument> findByCar_IdOrderByUploadedAtDesc(UUID carId);

    /** Admin: paginated documents filtered by status (UC-A13). */
    Page<DriverDocument> findByStatus(DocumentStatus status, Pageable pageable);

    /** Admin stats: count documents by status (UC-A11). */
    long countByStatus(DocumentStatus status);
}
