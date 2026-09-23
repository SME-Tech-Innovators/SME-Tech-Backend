package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;
import sme.tech.innovators.sme.dto.common.ShippingAddressDto;

@Data
@Builder
public class ShippingSettingsDto {
    private String provider;
    private boolean enabled;
    private ShippingAddressDto collectionAddress;
    /** Minor units (cents) for frontend parity with quote options. */
    private Integer fallbackFlatRateAmount;
    private String fallbackFlatRateCurrency;
    private boolean allowPickup;
    private String pickupLabel;
}
