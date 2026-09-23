package sme.tech.innovators.sme.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import sme.tech.innovators.sme.dto.common.ShippingAddressDto;

@Data
public class ShippingQuoteRequest {

    @NotBlank(message = "cartId is required")
    private String cartId;

    @Valid
    @NotNull(message = "shippingAddress is required")
    private ShippingAddressDto shippingAddress;
}
