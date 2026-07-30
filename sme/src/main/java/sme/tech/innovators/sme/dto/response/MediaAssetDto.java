package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class MediaAssetDto {
    private UUID id;
    private UUID workspaceId;
    private String url;
    private String storageKey;
    private String originalFilename;
    private String mimeType;
    private Long sizeBytes;
    private Integer width;
    private Integer height;
    /** Lowercase: pending | ready | deleted */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
