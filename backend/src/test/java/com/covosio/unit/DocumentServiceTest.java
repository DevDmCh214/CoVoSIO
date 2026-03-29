package com.covosio.unit;

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
import com.covosio.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock private DriverDocumentRepository   documentRepository;
    @Mock private UserRepository             userRepository;
    @Mock private CarRepository              carRepository;
    @Mock private DriverApplicationRepository applicationRepository;
    @Mock private DriverProfileRepository    driverProfileRepository;

    @InjectMocks
    private DocumentService documentService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(documentService, "uploadDir", tempDir.toString());
    }

    // --- upload LICENSE (UC-D11) ---

    @Test
    void upload_shouldSaveDocument_whenFileIsValidJpeg() {
        User user = buildUser("user@test.com");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.existsByUserId(user.getId())).thenReturn(false);
        when(applicationRepository.findByUser_IdAndStatus(user.getId(), ApplicationStatus.PENDING))
                .thenReturn(Optional.empty());
        when(applicationRepository.save(any(DriverApplication.class))).thenAnswer(inv -> {
            DriverApplication a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        when(documentRepository.save(any(DriverDocument.class))).thenAnswer(inv -> {
            DriverDocument d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            d.setUploadedAt(LocalDateTime.now());
            return d;
        });

        MockMultipartFile file = jpegFile("license.jpg");

        DocumentResponse response = documentService.upload("user@test.com", file, "LICENSE", null);

        assertThat(response.getType()).isEqualTo(DocumentType.LICENSE);
        assertThat(response.getMimeType()).isEqualTo("image/jpeg");
        assertThat(response.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(response.getCarId()).isNull();
        verify(documentRepository).save(any(DriverDocument.class));
    }

    @Test
    void upload_shouldSaveDocument_whenFileIsValidPdfAndTypeIsCarRegistration() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        UUID carId = UUID.randomUUID();
        Car car = buildCar(carId, driverProfile);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));
        when(documentRepository.save(any(DriverDocument.class))).thenAnswer(inv -> {
            DriverDocument d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            d.setUploadedAt(LocalDateTime.now());
            return d;
        });

        MockMultipartFile file = pdfFile("reg.pdf");

        DocumentResponse response = documentService.upload("driver@test.com", file, "CAR_REGISTRATION", carId);

        assertThat(response.getType()).isEqualTo(DocumentType.CAR_REGISTRATION);
        assertThat(response.getMimeType()).isEqualTo("application/pdf");
        assertThat(response.getCarId()).isEqualTo(carId);
    }

    @Test
    void upload_shouldThrowResourceNotFoundException_whenUserNotFound() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.upload("ghost@test.com", jpegFile("f.jpg"), "LICENSE", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost@test.com");
    }

    @Test
    void upload_shouldThrowBusinessException_whenUserIsAlreadyADriver() {
        User user = buildUser("driver@test.com");
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.existsByUserId(user.getId())).thenReturn(true);

        assertThatThrownBy(() -> documentService.upload("driver@test.com", jpegFile("f.jpg"), "LICENSE", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already a verified driver");
    }

    @Test
    void upload_shouldThrowAccessDeniedException_whenUserIsNotDriverForCarRegistration() {
        User user = buildUser("user@test.com");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.upload("user@test.com", pdfFile("f.pdf"), "CAR_REGISTRATION", UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void upload_shouldThrowBusinessException_whenFileTooLarge() {
        User user = buildUser("user@test.com");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        byte[] oversized = new byte[6 * 1024 * 1024]; // 6 MB — fill first 3 bytes with JPEG magic
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", oversized);

        assertThatThrownBy(() -> documentService.upload("user@test.com", file, "LICENSE", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    void upload_shouldThrowBusinessException_whenFileTypeNotAllowed() {
        User user = buildUser("user@test.com");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        // GIF magic: 47 49 46 38
        byte[] gifBytes = {0x47, 0x49, 0x46, 0x38, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "image.gif", "image/gif", gifBytes);

        assertThatThrownBy(() -> documentService.upload("user@test.com", file, "LICENSE", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JPEG, PNG, and PDF");
    }

    @Test
    void upload_shouldThrowBusinessException_whenMagicSignatureInvalid() {
        User user = buildUser("user@test.com");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        byte[] fakeBytes = "not-a-real-file-header-here".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", fakeBytes);

        assertThatThrownBy(() -> documentService.upload("user@test.com", file, "LICENSE", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JPEG, PNG, and PDF");
    }

    @Test
    void upload_shouldThrowBusinessException_whenCarRegistrationMissingCarId() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));

        assertThatThrownBy(() -> documentService.upload("driver@test.com", pdfFile("f.pdf"), "CAR_REGISTRATION", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("carId");
    }

    @Test
    void upload_shouldThrowResourceNotFoundException_whenCarNotFound() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        UUID carId = UUID.randomUUID();
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(carRepository.findById(carId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.upload("driver@test.com", pdfFile("f.pdf"), "CAR_REGISTRATION", carId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(carId.toString());
    }

    @Test
    void upload_shouldThrowAccessDeniedException_whenCarBelongsToAnotherDriver() {
        User user = buildUser("driver@test.com");
        DriverProfile driverProfile = buildDriverProfile(user);
        User otherUser = buildUser("other@test.com");
        DriverProfile otherProfile = buildDriverProfile(otherUser);
        UUID carId = UUID.randomUUID();
        Car car = buildCar(carId, otherProfile);

        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(driverProfile));
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));

        assertThatThrownBy(() -> documentService.upload("driver@test.com", pdfFile("f.pdf"), "CAR_REGISTRATION", carId))
                .isInstanceOf(AccessDeniedException.class);
    }

    // --- getMyDocuments (UC-D12) ---

    @Test
    void getMyDocuments_shouldReturnDocuments_whenUserHasPendingApplication() {
        User user = buildUser("user@test.com");
        DriverApplication app = buildDriverApplication(user);
        DriverDocument doc = buildDocument(app, DocumentType.LICENSE, null);

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(applicationRepository.findByUser_IdAndStatus(user.getId(), ApplicationStatus.PENDING))
                .thenReturn(Optional.of(app));
        when(documentRepository.findByApplication_IdOrderByUploadedAtDesc(app.getId()))
                .thenReturn(List.of(doc));
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        List<DocumentResponse> result = documentService.getMyDocuments("user@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(DocumentType.LICENSE);
        assertThat(result.get(0).getStatus()).isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void getMyDocuments_shouldReturnEmptyList_whenUserHasNoDocuments() {
        User user = buildUser("user@test.com");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(applicationRepository.findByUser_IdAndStatus(user.getId(), ApplicationStatus.PENDING))
                .thenReturn(Optional.empty());
        when(driverProfileRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        assertThat(documentService.getMyDocuments("user@test.com")).isEmpty();
    }

    @Test
    void getMyDocuments_shouldThrowResourceNotFoundException_whenUserNotFound() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getMyDocuments("ghost@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getFile (UC-D12) ---

    @Test
    void getFile_shouldReturnResource_whenDocumentBelongsToUser() throws Exception {
        User user = buildUser("user@test.com");
        DriverApplication app = buildDriverApplication(user);
        UUID docId = UUID.randomUUID();

        // Write a real file to tempDir so the resource exists
        Path fakeFile = tempDir.resolve("test.jpg");
        java.nio.file.Files.write(fakeFile, jpegBytes());

        DriverDocument doc = buildDocument(app, DocumentType.LICENSE, null);
        doc.setId(docId);
        doc.setFilePath(fakeFile.toString());
        doc.setMimeType("image/jpeg");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        DocumentFileResult result = documentService.getFile(docId, "user@test.com");

        assertThat(result.resource().exists()).isTrue();
        assertThat(result.mimeType()).isEqualTo("image/jpeg");
    }

    @Test
    void getFile_shouldThrowResourceNotFoundException_whenDocumentNotFound() {
        User user = buildUser("user@test.com");
        UUID docId = UUID.randomUUID();

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(documentRepository.findById(docId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getFile(docId, "user@test.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(docId.toString());
    }

    @Test
    void getFile_shouldThrowAccessDeniedException_whenDocumentBelongsToAnotherUser() {
        User user = buildUser("user@test.com");
        User otherUser = buildUser("other@test.com");
        DriverApplication otherApp = buildDriverApplication(otherUser);
        UUID docId = UUID.randomUUID();
        DriverDocument doc = buildDocument(otherApp, DocumentType.LICENSE, null);
        doc.setId(docId);

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.getFile(docId, "user@test.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // --- helpers ---

    private User buildUser(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("hashed")
                .firstName("Jean")
                .lastName("Dupont")
                .isActive(true)
                .build();
    }

    private DriverProfile buildDriverProfile(User user) {
        return DriverProfile.builder()
                .userId(user.getId())
                .user(user)
                .avgRating(BigDecimal.ZERO)
                .totalTripsDriven(0)
                .build();
    }

    private DriverApplication buildDriverApplication(User user) {
        DriverApplication app = new DriverApplication();
        app.setId(UUID.randomUUID());
        app.setUser(user);
        app.setStatus(ApplicationStatus.PENDING);
        return app;
    }

    private Car buildCar(UUID id, DriverProfile driverProfile) {
        Car car = new Car();
        car.setId(id);
        car.setDriver(driverProfile);
        car.setBrand("Renault");
        car.setModel("Clio");
        car.setColor("Blue");
        car.setPlate("AB-123-CD");
        car.setTotalSeats(4);
        car.setRegistrationVerified(false);
        car.setIsActive(true);
        return car;
    }

    private DriverDocument buildDocument(DriverApplication app, DocumentType type, Car car) {
        DriverDocument doc = new DriverDocument();
        doc.setId(UUID.randomUUID());
        doc.setApplication(app);
        doc.setCar(car);
        doc.setType(type);
        doc.setFilePath(tempDir.resolve("dummy.jpg").toString());
        doc.setMimeType("image/jpeg");
        doc.setStatus(DocumentStatus.PENDING);
        doc.setUploadedAt(LocalDateTime.now());
        return doc;
    }

    /** 3-byte JPEG magic header followed by minimal padding. */
    private byte[] jpegBytes() {
        byte[] b = new byte[16];
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0xD8;
        b[2] = (byte) 0xFF;
        return b;
    }

    private MockMultipartFile jpegFile(String name) {
        return new MockMultipartFile("file", name, "image/jpeg", jpegBytes());
    }

    private MockMultipartFile pdfFile(String name) {
        byte[] b = new byte[16];
        b[0] = 0x25; // %
        b[1] = 0x50; // P
        b[2] = 0x44; // D
        b[3] = 0x46; // F
        return new MockMultipartFile("file", name, "application/pdf", b);
    }
}
