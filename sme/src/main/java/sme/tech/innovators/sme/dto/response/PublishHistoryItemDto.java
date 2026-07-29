package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PublishHistoryItemDto {
    private UUID snapshotId;
    private String templateId;
    private Integer templateVersion;
    private Integer configVersion;
    private UUID publishedByUserId;
    private LocalDateTime publishedAt;
    private String notes;
}
