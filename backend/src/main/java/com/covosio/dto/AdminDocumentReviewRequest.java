package com.covosio.dto;

import com.covosio.entity.DocumentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminDocumentReviewRequest {

    /** APPROVED or REJECTED (PENDING is not a valid review outcome). */
    @NotNull
    private DocumentStatus status;

    /** Required when status is REJECTED. */
    private String rejectionReason;
}
