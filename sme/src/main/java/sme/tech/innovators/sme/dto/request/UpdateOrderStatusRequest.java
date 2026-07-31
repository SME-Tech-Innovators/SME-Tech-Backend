package sme.tech.innovators.sme.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateOrderStatusRequest {

    /** Allowed: processing | fulfilled | cancelled */
    @NotBlank(message = "status is required")
    private String status;
}
