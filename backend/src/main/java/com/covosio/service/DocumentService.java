package com.covosio.service;

import com.covosio.dto.DocumentFileResult;
import com.covosio.dto.DocumentResponse;
import com.covosio.entity.*;
import com.covosio.exception.BusinessException;
import com.covosio.exception.ResourceNotFoundException;
import com.covosio.repository.CarRepository;
import com.covosio.repository.DriverDocumentRepository;
import com.covosio.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Handles driver document use cases: upload (UC-D11) and view own documents (UC-D12).
 * Files are stored at {uploadDir}/documents/{driverId}/{uuid}.{ext}.
 * Access is secured — files are never served directly.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB

    private final DriverDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final CarRepository carRepository;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * Uploads a document for the authenticated driver (UC-D11).
     * Validates: 5 MB limit, allowed MIME type, and magic signature.
     * For CAR_REGISTRATION, the car must exist and belong to the driver.
     * The file is renamed with a UUID and stored outside the public folder.
     *
     * @param driverEmail the authenticated driver's email (from JWT subject)
     * @param file        the uploaded file (JPEG, PNG, or PDF)
     * @param typeRaw     document type string: "LICENSE" or "CAR_REGISTRATION"
     * @param carId       required when type is CAR_REGISTRATION
     * @return DocumentResponse reflecting the newly stored document
     * @throws ResourceNotFoundException if the driver or car is not found
     * @throws AccessDeniedException     if the user is not a driver, or the car belongs to another driver
     * @throws BusinessException         if the file fails size, type, or magic-signature validation,
     *                                   or if carId is missing for a CAR_REGISTRATION document
     */
    @Transactional
    public DocumentResponse upload(String driverEmail, MultipartFile file, String typeRaw, UUID carId) {
        Driver driver = loadDriver(driverEmail);

        DocumentType type = parseType(typeRaw);

        if (file.isEmpty()) {
            throw new BusinessException("File must not be empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException("File size exceeds the 5 MB limit");
        }

        byte[] header = readHeader(file);
        String mimeType = detectMimeType(header);

        Car car = null;
        if (type == DocumentType.CAR_REGISTRATION) {
            if (carId == null) {
                throw new BusinessException("carId is required for CAR_REGISTRATION documents");
            }
            car = carRepository.findById(carId)
                    .orElseThrow(() -> new ResourceNotFoundException("Car not found: " + carId));
            if (!car.getDriver().getId().equals(driver.getId())) {
                throw new AccessDeniedException("Action not authorized");
            }
        }

        String extension = extensionFor(mimeType);
        String filename   = UUID.randomUUID() + "." + extension;
        Path   targetDir  = Paths.get(uploadDir, "documents", driver.getId().toString());
        Path   targetPath = targetDir.resolve(filename);

        storeFile(file, targetDir, targetPath);

        DriverDocument document = DriverDocument.builder()
                .driver(driver)
                .car(car)
                .type(type)
                .filePath(targetPath.toString())
                .mimeType(mimeType)
                .status(DocumentStatus.PENDING)
                .build();

        return toResponse(documentRepository.save(document));
    }

    /**
     * Returns all documents uploaded by the authenticated driver (UC-D12).
     *
     * @param driverEmail the authenticated driver's email (from JWT subject)
     * @return list of the driver's documents, newest first
     * @throws ResourceNotFoundException if the user does not exist
     * @throws AccessDeniedException     if the user is not a driver
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> getMyDocuments(String driverEmail) {
        Driver driver = loadDriver(driverEmail);
        return documentRepository.findByDriver_IdOrderByUploadedAtDesc(driver.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns the file resource for a document owned by the authenticated driver (UC-D12).
     * The file is served only after verifying the requester is the document owner.
     *
     * @param documentId  the document UUID
     * @param driverEmail the authenticated driver's email (from JWT subject)
     * @return DocumentFileResult containing the Resource and its MIME type
     * @throws ResourceNotFoundException if the document does not exist
     * @throws AccessDeniedException     if the document belongs to another driver, or user is not a driver
     */
    @Transactional(readOnly = true)
    public DocumentFileResult getFile(UUID documentId, String driverEmail) {
        Driver driver = loadDriver(driverEmail);

        DriverDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        if (!document.getDriver().getId().equals(driver.getId())) {
            throw new AccessDeniedException("Action not authorized");
        }

        Path filePath = Paths.get(document.getFilePath());
        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists()) {
            throw new ResourceNotFoundException("File not found on disk: " + documentId);
        }

        return new DocumentFileResult(resource, document.getMimeType());
    }

    // --- helpers ---

    private Driver loadDriver(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        if (!(user instanceof Driver driver)) {
            throw new AccessDeniedException("Only drivers can manage documents");
        }
        return driver;
    }

    private DocumentType parseType(String raw) {
        try {
            return DocumentType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid document type: " + raw + ". Allowed: LICENSE, CAR_REGISTRATION");
        }
    }

    /**
     * Reads the first 8 bytes of the uploaded file to verify the magic signature.
     */
    private byte[] readHeader(MultipartFile file) {
        try {
            byte[] allBytes = file.getBytes();
            int len = Math.min(allBytes.length, 8);
            byte[] header = new byte[len];
            System.arraycopy(allBytes, 0, header, 0, len);
            return header;
        } catch (IOException e) {
            throw new BusinessException("Could not read uploaded file");
        }
    }

    /**
     * Detects the actual MIME type from the file's magic signature.
     * The client-declared content type is ignored — only the bytes decide.
     *
     * <ul>
     *   <li>JPEG  : FF D8 FF</li>
     *   <li>PNG   : 89 50 4E 47 0D 0A 1A 0A</li>
     *   <li>PDF   : 25 50 44 46 (%PDF)</li>
     * </ul>
     *
     * @throws BusinessException if no known signature matches
     */
    private String detectMimeType(byte[] header) {
        if (header.length >= 3
                && header[0] == (byte) 0xFF
                && header[1] == (byte) 0xD8
                && header[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        if (header.length >= 8
                && header[0] == (byte) 0x89 && header[1] == 0x50
                && header[2] == 0x4E       && header[3] == 0x47
                && header[4] == 0x0D       && header[5] == 0x0A
                && header[6] == 0x1A       && header[7] == 0x0A) {
            return "image/png";
        }
        if (header.length >= 4
                && header[0] == 0x25 && header[1] == 0x50
                && header[2] == 0x44 && header[3] == 0x46) {
            return "application/pdf";
        }
        throw new BusinessException("File type not allowed. Only JPEG, PNG, and PDF are accepted");
    }

    private String extensionFor(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg"      -> "jpg";
            case "image/png"       -> "png";
            case "application/pdf" -> "pdf";
            default -> throw new BusinessException("Unsupported MIME type: " + mimeType);
        };
    }

    private void storeFile(MultipartFile file, Path targetDir, Path targetPath) {
        try {
            Files.createDirectories(targetDir);
            file.transferTo(targetPath.toFile());
        } catch (IOException e) {
            throw new BusinessException("Failed to store file: " + e.getMessage());
        }
    }

    private DocumentResponse toResponse(DriverDocument doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .type(doc.getType())
                .mimeType(doc.getMimeType())
                .status(doc.getStatus())
                .rejectionReason(doc.getRejectionReason())
                .carId(doc.getCar() != null ? doc.getCar().getId() : null)
                .uploadedAt(doc.getUploadedAt())
                .reviewedAt(doc.getReviewedAt())
                .build();
    }
}
