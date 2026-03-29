package com.covosio.controller;

import com.covosio.dto.*;
import com.covosio.service.AdminService;
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
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin-only endpoints: user/trip/review/document management and platform stats")
public class AdminController {

    private final AdminService adminService;

    // -----------------------------------------------------------------------
    // Users
    // -----------------------------------------------------------------------

    @Operation(summary = "List all users (UC-A01)", description = "Returns a paginated list of all users.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of users"),
        @ApiResponse(responseCode = "403", description = "Caller is not an admin")
    })
    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserResponse>> getAllUsers(
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllUsers(pageable));
    }

    @Operation(summary = "Get a user by ID (UC-A02)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User found"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserResponse> getUserById(
            @Parameter(description = "User UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getUserById(id));
    }

    @Operation(
        summary = "Change a user's role (UC-A03–A05)",
        description = "Creates or removes profile rows to assign the user PASSENGER or DRIVER role. "
                    + "Admin accounts are managed separately.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Role updated"),
        @ApiResponse(responseCode = "400", description = "Invalid role value"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/users/{id}/role")
    public ResponseEntity<AdminUserResponse> changeUserRole(
            @Parameter(description = "User UUID") @PathVariable UUID id,
            @Valid @RequestBody AdminUserRoleRequest request) {
        return ResponseEntity.ok(adminService.changeUserRole(id, request.getRole()));
    }

    @Operation(
        summary = "Suspend or activate a user (UC-A05)",
        description = "Sets isActive on the account. Suspending revokes all refresh tokens (R11).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status updated"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/users/{id}/status")
    public ResponseEntity<AdminUserResponse> changeUserStatus(
            @Parameter(description = "User UUID") @PathVariable UUID id,
            @Valid @RequestBody AdminUserStatusRequest request) {
        return ResponseEntity.ok(adminService.changeUserStatus(id, request.getActive()));
    }

    @Operation(
        summary = "Soft-delete a user (UC-A06)",
        description = "Sets isActive = false and revokes all tokens. The record is retained.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "User soft-deleted"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> softDeleteUser(
            @Parameter(description = "User UUID") @PathVariable UUID id) {
        adminService.softDeleteUser(id);
        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------------------------------
    // Trips
    // -----------------------------------------------------------------------

    @Operation(summary = "List all trips (UC-A07)", description = "Returns all trips regardless of status.")
    @ApiResponse(responseCode = "200", description = "Page of trips")
    @GetMapping("/trips")
    public ResponseEntity<Page<TripResponse>> getAllTrips(
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllTrips(pageable));
    }

    @Operation(
        summary = "Cancel a trip (UC-A08)",
        description = "Cancels the trip and cascade-cancels all its pending/confirmed reservations (R06).")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Trip cancelled"),
        @ApiResponse(responseCode = "400", description = "Trip already cancelled"),
        @ApiResponse(responseCode = "404", description = "Trip not found")
    })
    @DeleteMapping("/trips/{id}")
    public ResponseEntity<Void> adminCancelTrip(
            @Parameter(description = "Trip UUID") @PathVariable UUID id) {
        adminService.adminCancelTrip(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Global map — all trips (UC-A12)",
        description = "Returns minimal trip data for map markers, all statuses included.")
    @ApiResponse(responseCode = "200", description = "Page of map-ready trip data")
    @GetMapping("/trips/map")
    public ResponseEntity<Page<TripMapResponse>> getGlobalMapTrips(
            @PageableDefault(size = 200) Pageable pageable) {
        return ResponseEntity.ok(adminService.getGlobalMapTrips(pageable));
    }

    // -----------------------------------------------------------------------
    // Reservations
    // -----------------------------------------------------------------------

    @Operation(summary = "List all reservations (UC-A09)")
    @ApiResponse(responseCode = "200", description = "Page of reservations")
    @GetMapping("/reservations")
    public ResponseEntity<Page<ReservationResponse>> getAllReservations(
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllReservations(pageable));
    }

    // -----------------------------------------------------------------------
    // Reviews
    // -----------------------------------------------------------------------

    @Operation(
        summary = "Moderate a review — update (UC-A10)",
        description = "Overwrites rating and/or comment. Null fields keep their existing value.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Review updated"),
        @ApiResponse(responseCode = "404", description = "Review not found")
    })
    @PutMapping("/reviews/{id}")
    public ResponseEntity<ReviewResponse> updateReview(
            @Parameter(description = "Review UUID") @PathVariable UUID id,
            @Valid @RequestBody AdminReviewModerationRequest request) {
        return ResponseEntity.ok(adminService.updateReview(id, request));
    }

    @Operation(summary = "Moderate a review — delete (UC-A10)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Review deleted"),
        @ApiResponse(responseCode = "404", description = "Review not found")
    })
    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> deleteReview(
            @Parameter(description = "Review UUID") @PathVariable UUID id) {
        adminService.deleteReview(id);
        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------------------------------
    // Stats
    // -----------------------------------------------------------------------

    @Operation(summary = "Platform statistics (UC-A11)", description = "Aggregated counters for the admin dashboard.")
    @ApiResponse(responseCode = "200", description = "Stats payload")
    @GetMapping("/stats")
    public ResponseEntity<PlatformStatsResponse> getPlatformStats() {
        return ResponseEntity.ok(adminService.getPlatformStats());
    }

    // -----------------------------------------------------------------------
    // Documents
    // -----------------------------------------------------------------------

    @Operation(
        summary = "List driver documents (UC-A13)",
        description = "Returns paginated documents. Optional ?status=PENDING|APPROVED|REJECTED filter.")
    @ApiResponse(responseCode = "200", description = "Page of documents")
    @GetMapping("/documents")
    public ResponseEntity<Page<DocumentResponse>> getDocuments(
            @Parameter(description = "Status filter (optional)") @RequestParam(required = false) String status,
            @PageableDefault(size = 10, sort = "uploadedAt") Pageable pageable) {
        return ResponseEntity.ok(adminService.getDocuments(status, pageable));
    }

    @Operation(
        summary = "Approve or reject a CAR_REGISTRATION document (UC-A14)",
        description = "Reviews a car registration document. APPROVED sets registrationVerified=true (R08). "
                    + "For LICENSE documents (driver promotion) use PUT /admin/applications/{id}/review.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Document reviewed"),
        @ApiResponse(responseCode = "400", description = "Already reviewed, missing rejection reason, or wrong document type"),
        @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @PutMapping("/documents/{id}/review")
    public ResponseEntity<DocumentResponse> reviewDocument(
            @Parameter(description = "Document UUID") @PathVariable UUID id,
            @Valid @RequestBody AdminDocumentReviewRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(
                adminService.reviewDocument(id, request, principal.getUsername()));
    }

    // -----------------------------------------------------------------------
    // Driver applications (promotion flow)
    // -----------------------------------------------------------------------

    @Operation(
        summary = "List driver applications",
        description = "Returns paginated driver applications. Optional ?status=PENDING|APPROVED|REJECTED filter.")
    @ApiResponse(responseCode = "200", description = "Page of applications")
    @GetMapping("/applications")
    public ResponseEntity<Page<DriverApplicationResponse>> getApplications(
            @Parameter(description = "Status filter (optional)") @RequestParam(required = false) String status,
            @PageableDefault(size = 10, sort = "appliedAt") Pageable pageable) {
        return ResponseEntity.ok(adminService.getApplications(status, pageable));
    }

    @Operation(
        summary = "Approve or reject a driver application",
        description = "APPROVED: creates a driver_profiles row — user gains ROLE_DRIVER on next login. "
                    + "REJECTED: rejectionReason is mandatory.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Application reviewed"),
        @ApiResponse(responseCode = "400", description = "Already reviewed or missing rejection reason"),
        @ApiResponse(responseCode = "404", description = "Application not found")
    })
    @PutMapping("/applications/{id}/review")
    public ResponseEntity<DriverApplicationResponse> reviewApplication(
            @Parameter(description = "Application UUID") @PathVariable UUID id,
            @Valid @RequestBody AdminApplicationReviewRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(
                adminService.reviewApplication(id, request, principal.getUsername()));
    }

    @Operation(
        summary = "Re-notify driver about a pending document (UC-A15)",
        description = "Validates that the document exists and triggers a notification to the driver.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notification queued"),
        @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @PostMapping("/documents/{id}/notify")
    public ResponseEntity<Void> notifyDriver(
            @Parameter(description = "Document UUID") @PathVariable UUID id) {
        adminService.notifyDriver(id);
        return ResponseEntity.ok().build();
    }
}
