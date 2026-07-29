package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;
import sme.tech.innovators.sme.entity.WorkspaceStatus;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class PublicStorefrontDto {
    private UUID workspaceId;
    private String storeSlug;
    private String storeName;
    private WorkspaceStatus status;
    private String templateId;
    private Integer templateVersion;
    private Integer configVersion;
    private Map<String, Object> config;
    private LocalDateTime publishedAt;
    private SeoDto seo;
}
