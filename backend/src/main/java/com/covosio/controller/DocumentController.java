package com.covosio.controller;

import com.covosio.dto.DocumentFileResult;
import com.covosio.dto.DocumentResponse;
import com.covosio.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Documents", description = "UC-D11, UC-D12 — driver document upload and status")
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "Upload a document (UC-D11)",
               description = "Uploads a driver's license (LICENSE) or car registration (CAR_REGISTRATION). " +
                             "Validates JPEG/PNG/PDF, 5 MB max, magic signature. File stored privately.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Document uploaded"),
        @ApiResponse(responseCode = "400", description = "Validation error (size, type, signature)"),
        @ApiResponse(responseCode = "403", description = "Not a driver"),
        @ApiResponse(responseCode = "404", description = "Car not found (CAR_REGISTRATION)")
    })
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DocumentResponse> upload(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "File to upload (JPEG, PNG, or PDF — max 5 MB)")
            @RequestParam MultipartFile file,
            @Parameter(description = "Document type: LICENSE or CAR_REGISTRATION")
            @RequestParam String type,
            @Parameter(description = "Car UUID — required when type is CAR_REGISTRATION")
            @RequestParam(required = false) UUID carId) {
        DocumentResponse response = documentService.upload(principal.getUsername(), file, type, carId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List own documents (UC-D12)",
               description = "Returns all documents uploaded by the authenticated driver, newest first.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Document list returned"),
        @ApiResponse(responseCode = "403", description = "Not a driver")
    })
    @GetMapping("/users/me/documents")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<List<DocumentResponse>> getMyDocuments(
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(documentService.getMyDocuments(principal.getUsername()));
    }

    @Operation(summary = "Download own document file (UC-D12)",
               description = "Returns the raw file for a document owned by the authenticated driver. " +
                             "Requires JWT — file is never served directly from disk.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File returned"),
        @ApiResponse(responseCode = "403", description = "Not the document owner or not a driver"),
        @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @GetMapping("/users/me/documents/{id}/file")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<org.springframework.core.io.Resource> getFile(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Document UUID") @PathVariable UUID id) {
        DocumentFileResult result = documentService.getFile(id, principal.getUsername());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(result.mimeType()));
        headers.setContentDisposition(ContentDisposition.inline().build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(result.resource());
    }
}
