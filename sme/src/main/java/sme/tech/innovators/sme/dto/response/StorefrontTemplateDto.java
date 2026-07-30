package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StorefrontTemplateDto {
    private String id;
    private String name;
    private String description;
    private String vibe;
    /** Lowercase API status: available | coming_soon | disabled */
    private String status;
    private Integer latestVersion;
    private String previewImageUrl;
    private List<String> supportedThemeIds;
}
