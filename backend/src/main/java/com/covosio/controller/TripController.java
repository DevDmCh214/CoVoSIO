package com.covosio.controller;

import com.covosio.dto.TripMapResponse;
import com.covosio.dto.TripRequest;
import com.covosio.dto.TripResponse;
import com.covosio.service.TripService;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
@Tag(name = "Trips", description = "UC-D04 to UC-D07, UC-P01, UC-P02, UC-P07, UC-D10 — trip lifecycle")
public class TripController {

    private final TripService tripService;

    // ── Exact sub-paths first to avoid clash with /{id} ──────────────────────

    @Operation(summary = "List my trips (UC-D07)",
               description = "Returns the authenticated driver's published trips, newest first.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of trips returned"),
        @ApiResponse(responseCode = "403", description = "Not a driver")
    })
    @GetMapping("/me")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Page<TripResponse>> getMyTrips(
            @AuthenticationPrincipal UserDetails principal,
            @PageableDefault(size = 10, sort = "departureAt") Pageable pageable) {
        return ResponseEntity.ok(tripService.getMyTrips(principal.getUsername(), pageable));
    }

    @Operation(summary = "Passenger map — available trips (UC-P07)",
               description = "Returns AVAILABLE trips with free seats for Leaflet map display.")
    @ApiResponse(responseCode = "200", description = "Page of map markers returned")
    @GetMapping("/map")
    public ResponseEntity<Page<TripMapResponse>> getMapTrips(
            @PageableDefault(size = 100, sort = "departureAt") Pageable pageable) {
        return ResponseEntity.ok(tripService.getMapTrips(pageable));
    }

    @Operation(summary = "Driver map — own trips (UC-D10)",
               description = "Returns all of the authenticated driver's trips for the driver map view.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of map markers returned"),
        @ApiResponse(responseCode = "403", description = "Not a driver")
    })
    @GetMapping("/map/me")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Page<TripMapResponse>> getMyMapTrips(
            @AuthenticationPrincipal UserDetails principal,
            @PageableDefault(size = 100, sort = "departureAt") Pageable pageable) {
        return ResponseEntity.ok(tripService.getMyMapTrips(principal.getUsername(), pageable));
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @Operation(summary = "Publish a trip (UC-D04)",
               description = "Creates a new trip. Requires licenseVerified = true AND car registrationVerified = true (R08).")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Trip created"),
        @ApiResponse(responseCode = "400", description = "Validation error or R08 violation"),
        @ApiResponse(responseCode = "403", description = "Not a driver"),
        @ApiResponse(responseCode = "404", description = "Car not found")
    })
    @PostMapping
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<TripResponse> createTrip(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody TripRequest request) {
        TripResponse response = tripService.createTrip(principal.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Search trips (UC-P01)",
               description = "Paginated search for AVAILABLE trips with free seats. All filters are optional.")
    @ApiResponse(responseCode = "200", description = "Page of matching trips returned")
    @GetMapping
    public ResponseEntity<Page<TripResponse>> searchTrips(
            @Parameter(description = "Origin city name (partial, case-insensitive)")
            @RequestParam(required = false) String origin,
            @Parameter(description = "Destination city name (partial, case-insensitive)")
            @RequestParam(required = false) String destination,
            @Parameter(description = "Departure date (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PageableDefault(size = 10, sort = "departureAt") Pageable pageable) {
        return ResponseEntity.ok(tripService.searchTrips(origin, destination, date, pageable));
    }

    @Operation(summary = "Get trip details (UC-P02)",
               description = "Returns full details for a single trip including driver and car info.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Trip returned"),
        @ApiResponse(responseCode = "404", description = "Trip not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TripResponse> getTripById(
            @Parameter(description = "Trip UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(tripService.getTripById(id));
    }

    @Operation(summary = "Edit a trip (UC-D05)",
               description = "Updates trip fields. R07: if CONFIRMED reservations exist, only the origin (meeting point) is editable.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Trip updated"),
        @ApiResponse(responseCode = "400", description = "Validation error or trip not AVAILABLE"),
        @ApiResponse(responseCode = "403", description = "Not the trip owner or not a driver"),
        @ApiResponse(responseCode = "404", description = "Trip or car not found")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<TripResponse> updateTrip(
            @Parameter(description = "Trip UUID") @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody TripRequest request) {
        return ResponseEntity.ok(tripService.updateTrip(id, principal.getUsername(), request));
    }

    @Operation(summary = "Cancel a trip (UC-D06)",
               description = "Sets trip status to CANCELLED and cancels all reservations in cascade (R06).")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Trip cancelled"),
        @ApiResponse(responseCode = "400", description = "Trip already cancelled"),
        @ApiResponse(responseCode = "403", description = "Not the trip owner or not a driver"),
        @ApiResponse(responseCode = "404", description = "Trip not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Void> cancelTrip(
            @Parameter(description = "Trip UUID") @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {
        tripService.cancelTrip(id, principal.getUsername());
        return ResponseEntity.noContent().build();
    }
}
