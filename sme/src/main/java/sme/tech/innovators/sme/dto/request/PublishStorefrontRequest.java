package sme.tech.innovators.sme.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PublishStorefrontRequest {

    @NotNull(message = "confirm is required")
    private Boolean confirm;

    private String notes;
}
