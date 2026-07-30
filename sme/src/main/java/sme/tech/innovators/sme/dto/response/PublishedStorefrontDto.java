package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;
import sme.tech.innovators.sme.entity.WorkspaceStatus;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class PublishedStorefrontDto {
    private UUID workspaceId;
    private UUID storefrontId;
    private UUID publishedSnapshotId;
    private String templateId;
    private Integer templateVersion;
    private Integer configVersion;
    private Map<String, Object> config;
    private WorkspaceStatus status;
    private String publicSlug;
    private LocalDateTime publishedAt;
    private String notes;
}
