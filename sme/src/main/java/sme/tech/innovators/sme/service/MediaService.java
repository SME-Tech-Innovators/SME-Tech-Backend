package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.request.ConfirmMediaUploadRequest;
import sme.tech.innovators.sme.dto.request.CreateUploadUrlRequest;
import sme.tech.innovators.sme.dto.response.MediaAssetDto;
import sme.tech.innovators.sme.dto.response.PageResponse;
import sme.tech.innovators.sme.dto.response.UploadUrlDto;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.*;
import sme.tech.innovators.sme.repository.MediaAssetRepository;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.repository.WorkspaceRepository;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final MediaAssetRepository mediaAssetRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.aws.s3.bucket}")
    private String bucket;

    @Value("${app.aws.s3.default-folder:workspaces}")
    private String defaultFolder;

    @Value("${app.aws.s3.base-url:}")
    private String baseUrl;

    @Value("${app.aws.s3.max-size-bytes:5242880}")
    private long maxSizeBytes;

    @Value("${app.aws.s3.upload-url-expiry-minutes:15}")
    private long uploadUrlExpiryMinutes;

    @Transactional
    public UploadUrlDto createUploadUrl(UUID workspaceId, UUID userId, CreateUploadUrlRequest request) {
        String mimeType = normalizeMimeType(request.getMimeType());
        validateMimeType(mimeType);
        validateSize(request.getSizeBytes());

        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new WorkspaceNotFoundException("User not found"));

        String safeFilename = sanitizeFilename(request.getFilename());
        UUID mediaId = UUID.randomUUID();
        String storageKey = buildStorageKey(workspaceId, mediaId, safeFilename);
        String publicUrl = buildPublicUrl(storageKey);

        MediaAsset media = MediaAsset.builder()
                .id(mediaId)
                .workspace(workspace)
                .uploadedBy(user)
                .url(publicUrl)
                .storageKey(storageKey)
                .originalFilename(safeFilename)
                .mimeType(mimeType)
                .sizeBytes(request.getSizeBytes())
                .status(MediaStatus.PENDING)
                .build();

        // Use assigned UUID — persist without regenerating
        media = mediaAssetRepository.save(media);

        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(uploadUrlExpiryMinutes));
        String uploadUrl = generatePresignedPutUrl(storageKey, mimeType);

        log.info("Created pending media={} key={} workspace={}", media.getId(), storageKey, workspaceId);

        return UploadUrlDto.builder()
                .mediaId(media.getId())
                .uploadUrl(uploadUrl)
                .storageKey(storageKey)
                .expiresAt(expiresAt)
                .build();
    }

    @Transactional
    public MediaAssetDto confirmUpload(UUID workspaceId,
                                        UUID mediaId,
                                        UUID userId,
                                        ConfirmMediaUploadRequest request) {
        loadOwnedWorkspace(workspaceId, userId);
        MediaAsset media = loadMedia(workspaceId, mediaId);

        if (media.getStatus() == MediaStatus.DELETED) {
            throw new MediaAlreadyDeletedException("Media has been deleted");
        }
        if (media.getStatus() == MediaStatus.READY) {
            return toDto(media);
        }

        verifyObjectExists(media.getStorageKey());

        if (request != null) {
            media.setWidth(request.getWidth());
            media.setHeight(request.getHeight());
        }
        media.setStatus(MediaStatus.READY);
        media = mediaAssetRepository.save(media);

        log.info("Confirmed media={} workspace={}", mediaId, workspaceId);
        return toDto(media);
    }

    @Transactional(readOnly = true)
    public PageResponse<MediaAssetDto> listMedia(UUID workspaceId,
                                                  UUID userId,
                                                  String type,
                                                  int page,
                                                  int limit) {
        loadOwnedWorkspace(workspaceId, userId);
        int safePage = Math.max(page, 0);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        boolean imageOnly = type == null || type.isBlank() || "image".equalsIgnoreCase(type);

        Page<MediaAsset> result = mediaAssetRepository.findLibrary(
                workspaceId, imageOnly, PageRequest.of(safePage, safeLimit));

        List<MediaAssetDto> items = result.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return PageResponse.<MediaAssetDto>builder()
                .items(items)
                .page(safePage)
                .limit(safeLimit)
                .totalItems(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public MediaAssetDto getMedia(UUID workspaceId, UUID mediaId, UUID userId) {
        loadOwnedWorkspace(workspaceId, userId);
        MediaAsset media = loadMedia(workspaceId, mediaId);
        if (media.getStatus() == MediaStatus.DELETED) {
            throw new MediaAlreadyDeletedException("Media has been deleted");
        }
        return toDto(media);
    }

    @Transactional
    public void deleteMedia(UUID workspaceId, UUID mediaId, UUID userId) {
        loadOwnedWorkspace(workspaceId, userId);
        MediaAsset media = loadMedia(workspaceId, mediaId);
        if (media.getStatus() == MediaStatus.DELETED) {
            throw new MediaAlreadyDeletedException("Media is already deleted");
        }

        media.setStatus(MediaStatus.DELETED);
        mediaAssetRepository.save(media);

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(media.getStorageKey())
                    .build());
        } catch (S3Exception ex) {
            log.warn("Soft-deleted media={} but S3 delete failed: {}", mediaId, ex.getMessage());
        }

        log.info("Deleted media={} workspace={}", mediaId, workspaceId);
    }

    /** Loads READY media owned by workspace, for product attachment. */
    @Transactional(readOnly = true)
    public MediaAsset requireReadyMedia(UUID workspaceId, UUID mediaId) {
        MediaAsset media = loadMedia(workspaceId, mediaId);
        if (media.getStatus() == MediaStatus.DELETED) {
            throw new MediaAlreadyDeletedException("Deleted media cannot be attached");
        }
        if (media.getStatus() != MediaStatus.READY) {
            throw new MediaNotReadyException("Media is not ready yet. Confirm the upload first.");
        }
        return media;
    }

    private String generatePresignedPutUrl(String storageKey, String mimeType) {
        try {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(storageKey)
                    .contentType(mimeType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(uploadUrlExpiryMinutes))
                    .putObjectRequest(objectRequest)
                    .build();

            PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
            return presigned.url().toString();
        } catch (Exception ex) {
            throw new UploadUrlFailedException("Failed to create signed upload URL", ex);
        }
    }

    private void verifyObjectExists(String storageKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(storageKey)
                    .build());
        } catch (S3Exception ex) {
            throw new MediaNotReadyException(
                    "Upload not found in storage. Upload the file to the signed URL first.");
        }
    }

    private void validateMimeType(String mimeType) {
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new InvalidMediaTypeException(
                    "Unsupported mimeType '" + mimeType + "'. Allowed: " + ALLOWED_MIME_TYPES);
        }
    }

    private void validateSize(Long sizeBytes) {
        if (sizeBytes == null || sizeBytes <= 0) {
            throw new MediaTooLargeException("sizeBytes must be > 0");
        }
        if (sizeBytes > maxSizeBytes) {
            throw new MediaTooLargeException(
                    "File too large. Max allowed is " + maxSizeBytes + " bytes");
        }
    }

    private String normalizeMimeType(String mimeType) {
        return mimeType == null ? "" : mimeType.trim().toLowerCase(Locale.ROOT);
    }

    private String sanitizeFilename(String filename) {
        String name = filename == null ? "file" : filename.trim();
        name = name.replaceAll("[\\\\/]+", "-");
        name = name.replaceAll("[^a-zA-Z0-9._-]", "-");
        name = name.replaceAll("-{2,}", "-");
        if (name.isBlank()) {
            name = "file";
        }
        if (name.length() > 200) {
            name = name.substring(name.length() - 200);
        }
        return name;
    }

    private String buildStorageKey(UUID workspaceId, UUID mediaId, String filename) {
        return defaultFolder + "/" + workspaceId + "/media/" + mediaId + "/" + filename;
    }

    private String buildPublicUrl(String storageKey) {
        String base = (baseUrl == null || baseUrl.isBlank())
                ? "https://" + bucket + ".s3.amazonaws.com"
                : baseUrl.replaceAll("/$", "");
        return base + "/" + storageKey;
    }

    private Workspace loadOwnedWorkspace(UUID workspaceId, UUID userId) {
        return workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Workspace not found or you do not have access to it"));
    }

    private MediaAsset loadMedia(UUID workspaceId, UUID mediaId) {
        return mediaAssetRepository.findByIdAndWorkspaceId(mediaId, workspaceId)
                .orElseThrow(() -> new MediaNotFoundException("Media not found: " + mediaId));
    }

    MediaAssetDto toDto(MediaAsset media) {
        return MediaAssetDto.builder()
                .id(media.getId())
                .workspaceId(media.getWorkspace().getId())
                .url(media.getUrl())
                .storageKey(media.getStorageKey())
                .originalFilename(media.getOriginalFilename())
                .mimeType(media.getMimeType())
                .sizeBytes(media.getSizeBytes())
                .width(media.getWidth())
                .height(media.getHeight())
                .status(media.getStatus() != null ? media.getStatus().name().toLowerCase(Locale.ROOT) : null)
                .createdAt(media.getCreatedAt())
                .updatedAt(media.getUpdatedAt())
                .build();
    }
}
