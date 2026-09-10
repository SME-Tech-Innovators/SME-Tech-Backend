package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class StorefrontTemplateVersionDto {
    private String templateId;
    private Integer version;
    private Map<String, Object> defaultConfig;
    private List<String> supportedSections;
    private List<String> supportedThemes;
    private Map<String, Object> configSchema;
}
