package sme.tech.innovators.sme.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateDeliveryRequest {
    @Size(max = 120)
    private String carrierName;

    @Size(max = 120)
    private String trackingNumber;

    @Size(max = 500)
    private String trackingUrl;

    @Size(max = 1000)
    private String deliveryNotes;

    @Size(max = 1000)
    private String publicNotes;

    private LocalDateTime estimatedDeliveryAt;
    private String status;
}
