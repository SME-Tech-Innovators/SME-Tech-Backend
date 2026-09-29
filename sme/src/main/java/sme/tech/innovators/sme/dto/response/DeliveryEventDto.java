package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DeliveryEventDto {
    private String id;
    private String status;
    private String note;
    private String source;
    private LocalDateTime createdAt;
}
