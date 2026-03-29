package com.covosio.dto;

import com.covosio.entity.ApplicationStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverApplicationResponse {

    private UUID id;
    private UUID userId;
    private String userFirstName;
    private String userLastName;
    private String userEmail;
    private ApplicationStatus status;
    private String rejectionReason;
    private LocalDateTime appliedAt;
    private LocalDateTime reviewedAt;
}
