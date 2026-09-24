package sme.tech.innovators.sme.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sme.tech.innovators.sme.entity.WorkspaceShippingSettings;

import java.util.UUID;

public interface WorkspaceShippingSettingsRepository extends JpaRepository<WorkspaceShippingSettings, UUID> {
}
