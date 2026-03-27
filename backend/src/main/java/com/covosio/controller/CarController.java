package com.covosio.controller;

import com.covosio.dto.CarRequest;
import com.covosio.dto.CarResponse;
import com.covosio.service.CarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cars")
@RequiredArgsConstructor
@Tag(name = "Cars", description = "UC-D01, UC-D01b — driver car management")
public class CarController {

    private final CarService carService;

    @Operation(summary = "Add a car (UC-D01)",
               description = "Creates a new car for the authenticated driver.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Car created"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "403", description = "Not a driver")
    })
    @PostMapping
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<CarResponse> addCar(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CarRequest request) {
        CarResponse response = carService.addCar(principal.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Delete a car (UC-D01b)",
               description = "Soft-deletes a car. Blocked if a future AVAILABLE trip uses it (R09).")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Car deleted"),
        @ApiResponse(responseCode = "400", description = "Future AVAILABLE trip attached (R09)"),
        @ApiResponse(responseCode = "403", description = "Not the owner or not a driver"),
        @ApiResponse(responseCode = "404", description = "Car not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Void> deleteCar(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Car UUID") @PathVariable UUID id) {
        carService.deleteCar(id, principal.getUsername());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List my cars",
               description = "Returns all active cars belonging to the authenticated driver. Used in the trip creation dropdown (UC-D04).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Car list returned"),
        @ApiResponse(responseCode = "403", description = "Not a driver")
    })
    @GetMapping("/me")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<List<CarResponse>> getMyCars(
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(carService.getMyCars(principal.getUsername()));
    }
}
