package sme.tech.innovators.sme.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sme.tech.innovators.sme.entity.WorkspaceDeliverySettings;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceDeliverySettingsRepository extends JpaRepository<WorkspaceDeliverySettings, UUID> {

    Optional<WorkspaceDeliverySettings> findByWorkspaceId(UUID workspaceId);

    boolean existsByWorkspaceId(UUID workspaceId);
}
