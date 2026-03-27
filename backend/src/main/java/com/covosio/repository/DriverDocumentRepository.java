package com.covosio.repository;

import com.covosio.entity.DriverDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DriverDocumentRepository extends JpaRepository<DriverDocument, UUID> {

    List<DriverDocument> findByDriver_IdOrderByUploadedAtDesc(UUID driverId);
}
