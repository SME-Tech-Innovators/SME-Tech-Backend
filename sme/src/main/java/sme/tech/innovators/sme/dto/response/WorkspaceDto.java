package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;
import sme.tech.innovators.sme.entity.WorkspaceStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class WorkspaceDto {
    private UUID id;
    private UUID businessId;
    private String name;
    private String publicSlug;
    private WorkspaceStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
