package sme.tech.innovators.sme.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateStorefrontDraftRequest {

    @NotBlank(message = "templateId is required")
    private String templateId;

    @NotNull(message = "templateVersion is required")
    private Integer templateVersion;

    @NotNull(message = "configVersion is required")
    private Integer configVersion;

    @NotNull(message = "config is required")
    private Map<String, Object> config;
}
