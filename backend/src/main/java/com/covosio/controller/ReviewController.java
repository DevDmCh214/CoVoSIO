package com.covosio.controller;

import com.covosio.dto.ReviewRequest;
import com.covosio.dto.ReviewResponse;
import com.covosio.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "UC-P06, UC-D09 — post-trip reviews")
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(
        summary = "Submit a review (UC-P06 / UC-D09)",
        description = "Passenger reviews the driver (PASSENGER_TO_DRIVER) or driver reviews the passenger "
                    + "(DRIVER_TO_PASSENGER). Direction is inferred from the caller's role. "
                    + "Enforces R04 (trip COMPLETED) and R05 (one review per direction per reservation).")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Review created"),
        @ApiResponse(responseCode = "400", description = "R04/R05 violation or validation error"),
        @ApiResponse(responseCode = "403", description = "Caller is not a party to the reservation"),
        @ApiResponse(responseCode = "404", description = "Reservation not found")
    })
    @PostMapping("/reservations/{reservationId}/review")
    public ResponseEntity<ReviewResponse> createReview(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Reservation UUID") @PathVariable UUID reservationId,
            @Valid @RequestBody ReviewRequest request) {
        ReviewResponse response = reviewService.createReview(
                principal.getUsername(), reservationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
