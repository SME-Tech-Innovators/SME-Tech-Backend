package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;
import sme.tech.innovators.sme.entity.WorkspaceStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UnpublishResultDto {
    private UUID workspaceId;
    private WorkspaceStatus status;
    private LocalDateTime lastPublishedAt;
}
