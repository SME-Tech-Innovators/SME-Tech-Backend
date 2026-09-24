package sme.tech.innovators.sme.dto.request;

import jakarta.validation.Valid;
import lombok.Data;
import sme.tech.innovators.sme.dto.common.ShippingAddressDto;

import java.math.BigDecimal;

@Data
public class UpdateShippingSettingsRequest {
    private String provider;
    private Boolean enabled;
    @Valid
    private ShippingAddressDto collectionAddress;
    private BigDecimal fallbackFlatRateAmount;
    private String fallbackFlatRateCurrency;
    private Boolean allowPickup;
    private String pickupLabel;
}
