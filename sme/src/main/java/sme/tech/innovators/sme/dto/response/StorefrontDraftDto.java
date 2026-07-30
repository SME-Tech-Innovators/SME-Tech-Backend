package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class StorefrontDraftDto {
    private UUID workspaceId;
    private UUID storefrontId;
    private String templateId;
    private Integer templateVersion;
    private Integer configVersion;
    private Map<String, Object> config;
    private LocalDateTime templateSetupCompletedAt;
    private LocalDateTime updatedAt;
}
