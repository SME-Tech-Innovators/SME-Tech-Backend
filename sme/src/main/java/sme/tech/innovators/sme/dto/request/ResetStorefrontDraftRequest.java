package sme.tech.innovators.sme.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResetStorefrontDraftRequest {

    @NotBlank(message = "templateId is required")
    private String templateId;

    @NotNull(message = "templateVersion is required")
    private Integer templateVersion;
}
