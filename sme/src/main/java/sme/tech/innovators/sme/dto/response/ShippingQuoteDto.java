package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ShippingQuoteDto {
    private List<ShippingOptionDto> options;

    @Data
    @Builder
    public static class ShippingOptionDto {
        private String id;
        private String provider;
        private String label;
        /** Minor units (cents). */
        private int amount;
        private String currency;
        private Integer estimatedDays;
        /** Opaque token for checkout when provider is bobgo. */
        private String bobgoRateToken;
    }
}
