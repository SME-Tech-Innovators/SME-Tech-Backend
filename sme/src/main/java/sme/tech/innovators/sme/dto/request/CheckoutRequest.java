package sme.tech.innovators.sme.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CheckoutRequest {

    @NotBlank(message = "cartId is required")
    private String cartId;

    @Valid
    @NotNull(message = "customer is required")
    private CustomerInfo customer;

    @Valid
    @NotNull(message = "shippingAddress is required")
    private ShippingAddress shippingAddress;

    @Data
    public static class CustomerInfo {
        @NotBlank(message = "customer name is required")
        private String name;
        private String email;
        @NotBlank(message = "customer phone is required")
        private String phone;
    }

    @Data
    public static class ShippingAddress {
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
}
