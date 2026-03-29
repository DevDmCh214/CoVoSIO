package com.covosio.service;

import com.covosio.dto.DocumentFileResult;
import com.covosio.dto.DocumentResponse;
import com.covosio.entity.*;
import com.covosio.exception.BusinessException;
import com.covosio.exception.ResourceNotFoundException;
import com.covosio.repository.CarRepository;
import com.covosio.repository.DriverApplicationRepository;
import com.covosio.repository.DriverDocumentRepository;
import com.covosio.repository.DriverProfileRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles document use cases: upload (UC-D11) and view own documents (UC-D12).
 * <p>
 * LICENSE: any authenticated user (PASSENGER) may upload — creates a DriverApplication.
 * CAR_REGISTRATION: DRIVER users only — linked to a specific car.
 * Files are stored at {uploadDir}/documents/{userId}/{uuid}.{ext} and are never served
 * directly — always via a secured endpoint.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB

    private final DriverDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final CarRepository carRepository;
    private final DriverApplicationRepository applicationRepository;
    private final DriverProfileRepository driverProfileRepository;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * Uploads a document for the authenticated user (UC-D11).
     * <ul>
     *   <li>LICENSE: allowed for any authenticated user. Creates or reuses a PENDING DriverApplication.</li>
     *   <li>CAR_REGISTRATION: allowed for DRIVER users only; carId is mandatory.</li>
     * </ul>
     * Validates: 5 MB limit, allowed MIME type (JPEG/PNG/PDF), and magic signature.
     * The file is renamed with a UUID and stored outside the public folder.
     *
     * @param userEmail the authenticated user's email (from JWT subject)
     * @param file      the uploaded file (JPEG, PNG, or PDF)
     * @param typeRaw   document type string: "LICENSE" or "CAR_REGISTRATION"
     * @param carId     required when type is CAR_REGISTRATION
     * @return DocumentResponse reflecting the newly stored document
     * @throws ResourceNotFoundException if the user or car is not found
     * @throws AccessDeniedException     if a non-driver attempts to upload CAR_REGISTRATION,
     *                                   or if the car belongs to a different driver
     * @throws BusinessException         if the file fails size, type, or magic-signature
     *                                   validation, or if carId is missing for CAR_REGISTRATION
     */
    @Transactional
    public DocumentResponse upload(String userEmail, MultipartFile file, String typeRaw, UUID carId) {
        User user = loadUser(userEmail);
        DocumentType type = parseType(typeRaw);

        if (file.isEmpty()) {
            throw new BusinessException("File must not be empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException("File size exceeds the 5 MB limit");
        }

        byte[] header = readHeader(file);
        String mimeType = detectMimeType(header);

        if (type == DocumentType.LICENSE) {
            return uploadLicenseForApplication(user, file, mimeType);
        } else {
            return uploadCarRegistration(user, file, mimeType, carId);
        }
    }

    /**
     * Returns all documents uploaded by the authenticated user (UC-D12).
     * Includes LICENSE documents linked to applications and CAR_REGISTRATION documents for driver cars.
     *
     * @param userEmail the authenticated user's email (from JWT subject)
     * @return list of the user's documents, newest first
     * @throws ResourceNotFoundException if the user does not exist
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> getMyDocuments(String userEmail) {
        User user = loadUser(userEmail);
        List<DriverDocument> docs = new ArrayList<>();

        // License docs via application
        applicationRepository.findByUser_IdAndStatus(user.getId(), ApplicationStatus.PENDING)
                .ifPresent(app -> docs.addAll(
                        documentRepository.findByApplication_IdOrderByUploadedAtDesc(app.getId())));

        // Car registration docs if driver
        driverProfileRepository.findByUserId(user.getId()).ifPresent(dp ->
                carRepository.findByDriver_UserIdAndIsActiveTrue(dp.getUserId()).forEach(car ->
                        docs.addAll(documentRepository.findByCar_IdOrderByUploadedAtDesc(car.getId()))));

        return docs.stream().map(this::toResponse).toList();
    }

    /**
     * Returns the file resource for a document uploaded by the authenticated user (UC-D12).
     * The file is served only after verifying the requester owns the document.
     *
     * @param documentId the document UUID
     * @param userEmail  the authenticated user's email (from JWT subject)
     * @return DocumentFileResult containing the Resource and its MIME type
     * @throws ResourceNotFoundException if the document does not exist
     * @throws AccessDeniedException     if the document belongs to a different user
     */
    @Transactional(readOnly = true)
    public DocumentFileResult getFile(UUID documentId, String userEmail) {
        User user = loadUser(userEmail);

        DriverDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        // Verify ownership
        if (!isOwner(document, user)) {
            throw new AccessDeniedException("Action not authorized");
        }

        Path filePath = Paths.get(document.getFilePath());
        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists()) {
            throw new ResourceNotFoundException("File not found on disk: " + documentId);
        }

        return new DocumentFileResult(resource, document.getMimeType());
    }

    // --- private upload helpers ---

    private DocumentResponse uploadLicenseForApplication(User user, MultipartFile file, String mimeType) {
        // Check not already a verified driver
        if (driverProfileRepository.existsByUserId(user.getId())) {
            throw new BusinessException("You are already a verified driver");
        }

        // Get or create PENDING application
        DriverApplication application = applicationRepository
                .findByUser_IdAndStatus(user.getId(), ApplicationStatus.PENDING)
                .orElseGet(() -> {
                    DriverApplication app = DriverApplication.builder().user(user).build();
                    return applicationRepository.save(app);
                });

        String extension = extensionFor(mimeType);
        String filename  = UUID.randomUUID() + "." + extension;
        Path targetDir   = Paths.get(uploadDir, "documents", user.getId().toString());
        Path targetPath  = targetDir.resolve(filename);
        storeFile(file, targetDir, targetPath);

        DriverDocument document = DriverDocument.builder()
                .application(application)
                .type(DocumentType.LICENSE)
                .filePath(targetPath.toString())
                .mimeType(mimeType)
                .status(DocumentStatus.PENDING)
                .build();

        return toResponse(documentRepository.save(document));
    }

    private DocumentResponse uploadCarRegistration(User user, MultipartFile file, String mimeType, UUID carId) {
        DriverProfile driverProfile = driverProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AccessDeniedException("Only drivers can upload car registration documents"));

        if (carId == null) {
            throw new BusinessException("carId is required for CAR_REGISTRATION documents");
        }
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new ResourceNotFoundException("Car not found: " + carId));
        if (!car.getDriver().getUserId().equals(driverProfile.getUserId())) {
            throw new AccessDeniedException("Action not authorized");
        }

        String extension = extensionFor(mimeType);
        String filename  = UUID.randomUUID() + "." + extension;
        Path targetDir   = Paths.get(uploadDir, "documents", user.getId().toString());
        Path targetPath  = targetDir.resolve(filename);
        storeFile(file, targetDir, targetPath);

        DriverDocument document = DriverDocument.builder()
                .car(car)
                .type(DocumentType.CAR_REGISTRATION)
                .filePath(targetPath.toString())
                .mimeType(mimeType)
                .status(DocumentStatus.PENDING)
                .build();

        return toResponse(documentRepository.save(document));
    }

    // --- ownership check ---

    private boolean isOwner(DriverDocument document, User user) {
        if (document.getApplication() != null) {
            return document.getApplication().getUser().getId().equals(user.getId());
        }
        if (document.getCar() != null) {
            return document.getCar().getDriver().getUserId().equals(user.getId());
        }
        return false;
    }

    // --- common helpers ---

    private User loadUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
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
        UUID uploderId = null;
        String uploaderFirstName = null;
        String uploaderLastName = null;
        if (doc.getApplication() != null && doc.getApplication().getUser() != null) {
            uploderId = doc.getApplication().getUser().getId();
            uploaderFirstName = doc.getApplication().getUser().getFirstName();
            uploaderLastName = doc.getApplication().getUser().getLastName();
        } else if (doc.getCar() != null && doc.getCar().getDriver() != null) {
            uploderId = doc.getCar().getDriver().getUserId();
            uploaderFirstName = doc.getCar().getDriver().getUser().getFirstName();
            uploaderLastName = doc.getCar().getDriver().getUser().getLastName();
        }
        return DocumentResponse.builder()
                .id(doc.getId())
                .type(doc.getType())
                .mimeType(doc.getMimeType())
                .status(doc.getStatus())
                .rejectionReason(doc.getRejectionReason())
                .carId(doc.getCar() != null ? doc.getCar().getId() : null)
                .driverId(uploderId)
                .driverFirstName(uploaderFirstName)
                .driverLastName(uploaderLastName)
                .uploadedAt(doc.getUploadedAt())
                .reviewedAt(doc.getReviewedAt())
                .build();
    }
}
