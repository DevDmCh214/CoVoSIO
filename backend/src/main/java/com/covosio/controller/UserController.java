package com.covosio.controller;

import com.covosio.dto.ChangePasswordRequest;
import com.covosio.dto.PublicUserResponse;
import com.covosio.dto.UpdateProfileRequest;
import com.covosio.dto.UserProfileResponse;
import com.covosio.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "UC-C05 to UC-C08 — profile management and public profiles")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get own profile (UC-C05)",
               description = "Returns the full profile of the currently authenticated user.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile returned"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(userService.getMyProfile(principal.getUsername()));
    }

    @Operation(summary = "Update own profile (UC-C06)",
               description = "Updates firstName, lastName, phone, and avatarUrl of the authenticated user.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile updated"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateMyProfile(principal.getUsername(), request));
    }

    @Operation(summary = "Change password (UC-C07)",
               description = "Verifies the current password then sets a new one.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Password changed"),
        @ApiResponse(responseCode = "400", description = "Current password incorrect or validation error"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.getUsername(), request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get public profile (UC-C08)",
               description = "Returns the public profile of any user by their UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Public profile returned"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/{id}")
    public ResponseEntity<PublicUserResponse> getPublicProfile(
            @Parameter(description = "Target user UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(userService.getPublicProfile(id));
    }
}
