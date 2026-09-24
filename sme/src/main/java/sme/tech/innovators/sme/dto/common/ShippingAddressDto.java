package sme.tech.innovators.sme.dto.common;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShippingAddressDto {
    @NotBlank(message = "address line1 is required")
    private String line1;
    private String line2;
    @NotBlank(message = "city is required")
    private String city;
    private String province;
    private String postalCode;
    @NotBlank(message = "country is required")
    private String country;
}
