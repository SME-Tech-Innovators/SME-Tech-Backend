package sme.tech.innovators.sme.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import sme.tech.innovators.sme.entity.StorefrontTemplateStatus;

import java.util.List;
import java.util.Map;

/**
 * Deserializes a storefront template seed from {@code storefront-templates/*.json}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorefrontTemplateSeedDefinition {

    private String id;
    private String name;
    private String description;
    private String vibe;
    private StorefrontTemplateStatus status;
    private Integer latestVersion;
    private String previewImageUrl;
    private List<String> supportedThemes;
    private List<String> supportedSections;
    private Map<String, Object> defaultConfig;
}
