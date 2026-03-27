package com.covosio.dto;

import com.covosio.entity.ReviewDirection;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ReviewResponse {

    private UUID id;
    private UUID reservationId;

    // Author (who wrote the review)
    private UUID authorId;
    private String authorFirstName;
    private String authorLastName;

    // Target (who was reviewed)
    private UUID targetId;
    private String targetFirstName;
    private String targetLastName;

    private ReviewDirection direction;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}
