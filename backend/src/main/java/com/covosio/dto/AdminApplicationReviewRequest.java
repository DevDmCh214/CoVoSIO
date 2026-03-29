package com.covosio.dto;

import com.covosio.entity.ApplicationStatus;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminApplicationReviewRequest {

    /** APPROVED or REJECTED — PENDING is not a valid outcome. */
    private ApplicationStatus status;

    /** Required when status is REJECTED. */
    private String rejectionReason;
}
