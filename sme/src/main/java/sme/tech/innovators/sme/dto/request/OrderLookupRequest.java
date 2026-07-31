package sme.tech.innovators.sme.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OrderLookupRequest {

    @NotBlank(message = "orderNumber is required")
    private String orderNumber;

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid email address")
    private String email;
}
