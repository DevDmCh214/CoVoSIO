package com.covosio.controller;

import com.covosio.dto.AuthResponse;
import com.covosio.dto.LoginRequest;
import com.covosio.dto.RefreshTokenRequest;
import com.covosio.dto.RegisterRequest;
import com.covosio.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "UC-C01 to UC-C04 — register, login, token refresh, logout")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new user (UC-C01)",
               description = "Creates a Passenger account and returns access + refresh tokens.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User registered"),
        @ApiResponse(responseCode = "400", description = "Validation error or email already taken")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(summary = "Log in (UC-C02)",
               description = "Authenticates with email + password. Returns access + refresh tokens.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "400", description = "Invalid credentials or suspended account")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Refresh access token (UC-C03)",
               description = "Exchanges a valid refresh token for a new access token (R10).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "New access token issued"),
        @ApiResponse(responseCode = "400", description = "Refresh token invalid, expired, or revoked")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @Operation(summary = "Log out (UC-C04)",
               description = "Revokes the given refresh token from the database.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Logged out"),
        @ApiResponse(responseCode = "400", description = "Refresh token not found")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
