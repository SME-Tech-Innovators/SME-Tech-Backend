package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;
import sme.tech.innovators.sme.entity.WorkspaceStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PublishResultDto {
    private UUID workspaceId;
    private UUID storefrontId;
    private UUID publishedSnapshotId;
    private WorkspaceStatus status;
    private LocalDateTime publishedAt;
}
