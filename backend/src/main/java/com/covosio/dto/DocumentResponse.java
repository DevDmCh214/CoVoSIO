package com.covosio.dto;

import com.covosio.entity.DocumentStatus;
import com.covosio.entity.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResponse {

    private UUID id;
    private DocumentType type;
    private String mimeType;
    private DocumentStatus status;
    private String rejectionReason;
    /** UUID of the linked car — null for LICENSE documents. */
    private UUID carId;
    /** Driver who uploaded the document — populated in admin views. */
    private UUID driverId;
    private String driverFirstName;
    private String driverLastName;
    private LocalDateTime uploadedAt;
    private LocalDateTime reviewedAt;
}
