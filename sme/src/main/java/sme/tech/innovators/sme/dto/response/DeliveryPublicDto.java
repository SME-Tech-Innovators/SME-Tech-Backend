package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DeliveryPublicDto {
    private String id;
    private String status;
    private String carrierName;
    private String trackingNumber;
    private String trackingUrl;
    private String publicNotes;
    private LocalDateTime estimatedDeliveryAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime deliveredAt;
}
