package com.covosio.controller;

import com.covosio.dto.ReservationRequest;
import com.covosio.dto.ReservationResponse;
import com.covosio.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "UC-P03, UC-P04, UC-P05, UC-D08 — reservation lifecycle")
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "Book a trip (UC-P03)",
               description = "Creates a reservation for the authenticated passenger. "
                             + "Enforces R01 (no self-booking) and R02 (seat availability).")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Reservation created"),
        @ApiResponse(responseCode = "400", description = "R01/R02 violation or trip not AVAILABLE"),
        @ApiResponse(responseCode = "403", description = "Not a passenger"),
        @ApiResponse(responseCode = "404", description = "Trip not found")
    })
    @PostMapping("/reservations")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<ReservationResponse> createReservation(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody ReservationRequest request) {
        ReservationResponse response = reservationService.createReservation(principal.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Cancel a reservation (UC-P04)",
               description = "Cancels a reservation and restores seats to the trip. "
                             + "Enforces R03: blocked within 2 hours of departure.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Reservation cancelled"),
        @ApiResponse(responseCode = "400", description = "Already cancelled or R03 violation"),
        @ApiResponse(responseCode = "403", description = "Not the reservation owner or not a passenger"),
        @ApiResponse(responseCode = "404", description = "Reservation not found")
    })
    @DeleteMapping("/reservations/{id}")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<Void> cancelReservation(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Reservation UUID") @PathVariable UUID id) {
        reservationService.cancelReservation(principal.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List my reservations (UC-P05)",
               description = "Returns the authenticated passenger's reservations, newest first.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of reservations returned"),
        @ApiResponse(responseCode = "403", description = "Not a passenger")
    })
    @GetMapping("/reservations/me")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<Page<ReservationResponse>> getMyReservations(
            @AuthenticationPrincipal UserDetails principal,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(reservationService.getMyReservations(principal.getUsername(), pageable));
    }

    @Operation(summary = "List reservations for a trip (UC-D08)",
               description = "Returns all reservations for a trip owned by the authenticated driver.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of reservations returned"),
        @ApiResponse(responseCode = "403", description = "Not the trip owner or not a driver"),
        @ApiResponse(responseCode = "404", description = "Trip not found")
    })
    @GetMapping("/trips/{tripId}/reservations")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Page<ReservationResponse>> getTripReservations(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Trip UUID") @PathVariable UUID tripId,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(
                reservationService.getTripReservations(principal.getUsername(), tripId, pageable));
    }
}
