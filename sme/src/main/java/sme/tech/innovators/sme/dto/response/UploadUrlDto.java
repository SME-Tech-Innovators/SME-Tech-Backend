package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class UploadUrlDto {
    private UUID mediaId;
    private String uploadUrl;
    private String storageKey;
    private Instant expiresAt;
}
