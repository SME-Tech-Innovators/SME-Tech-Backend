package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import sme.tech.innovators.sme.dto.request.CreateUploadUrlRequest;
import sme.tech.innovators.sme.exception.InvalidMediaTypeException;
import sme.tech.innovators.sme.exception.MediaTooLargeException;
import sme.tech.innovators.sme.repository.MediaAssetRepository;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.repository.WorkspaceRepository;
import sme.tech.innovators.sme.service.MediaService;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class MediaServiceValidationTest {

    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaService(
                mock(MediaAssetRepository.class),
                mock(WorkspaceRepository.class),
                mock(UserRepository.class),
                mock(S3Client.class),
                mock(S3Presigner.class)
        );
        ReflectionTestUtils.setField(mediaService, "bucket", "sme-operations-media");
        ReflectionTestUtils.setField(mediaService, "defaultFolder", "workspaces");
        ReflectionTestUtils.setField(mediaService, "baseUrl", "https://sme-operations-media.s3.us-east-1.amazonaws.com");
        ReflectionTestUtils.setField(mediaService, "maxSizeBytes", 5_242_880L);
        ReflectionTestUtils.setField(mediaService, "uploadUrlExpiryMinutes", 15L);
    }

    @Test
    void rejectsUnsupportedMimeType() {
        CreateUploadUrlRequest request = new CreateUploadUrlRequest();
        request.setFilename("doc.pdf");
        request.setMimeType("application/pdf");
        request.setSizeBytes(1000L);

        assertThrows(InvalidMediaTypeException.class,
                () -> mediaService.createUploadUrl(UUID.randomUUID(), UUID.randomUUID(), request));
    }

    @Test
    void rejectsOversizedFile() {
        CreateUploadUrlRequest request = new CreateUploadUrlRequest();
        request.setFilename("big.jpg");
        request.setMimeType("image/jpeg");
        request.setSizeBytes(10_000_000L);

        assertThrows(MediaTooLargeException.class,
                () -> mediaService.createUploadUrl(UUID.randomUUID(), UUID.randomUUID(), request));
    }
}
