package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DeliveryDto {
    private String id;
    private String orderId;
    private String status;
    private String carrierName;
    private String trackingNumber;
    private String parcelTrackingNumber;
    private String provider;
    private String externalShipmentId;
    private String serviceLevelCode;
    private String serviceLevelName;
    private BigDecimal quotedAmount;
    private String trackingUrl;
    private String deliveryNotes;
    private String publicNotes;
    private LocalDateTime estimatedDeliveryAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime deliveredAt;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<DeliveryEventDto> events;
}
