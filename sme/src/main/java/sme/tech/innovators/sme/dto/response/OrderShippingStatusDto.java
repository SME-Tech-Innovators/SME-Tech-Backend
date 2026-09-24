package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderShippingStatusDto {
    private boolean canCancel;
    private String provider;
    private String status;
    private String statusLabel;
    private String trackingReference;
    /** Bob Go public tracking page when provider is bobgo and reference exists. */
    private String trackingUrl;
    private String carrierName;
    private String shippingOptionLabel;
    private String lastError;
    private String bobgoShipmentId;
}
