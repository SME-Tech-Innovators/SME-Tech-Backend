package sme.tech.innovators.sme.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sme.tech.innovators.sme.entity.Storefront;
import sme.tech.innovators.sme.entity.Workspace;

import java.util.Optional;
import java.util.UUID;

public interface StorefrontRepository extends JpaRepository<Storefront, UUID> {
    Optional<Storefront> findByWorkspace(Workspace workspace);
    Optional<Storefront> findByWorkspaceId(UUID workspaceId);
}
