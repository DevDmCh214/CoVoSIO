package com.covosio.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminReviewModerationRequest {

    /** New star rating (1–5). Null means keep the existing value. */
    @Min(1)
    @Max(5)
    private Integer rating;

    /** New comment text. Null means keep the existing value. */
    private String comment;
}
