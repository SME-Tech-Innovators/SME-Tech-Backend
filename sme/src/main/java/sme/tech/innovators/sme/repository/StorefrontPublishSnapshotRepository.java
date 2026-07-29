package sme.tech.innovators.sme.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sme.tech.innovators.sme.entity.StorefrontPublishSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StorefrontPublishSnapshotRepository extends JpaRepository<StorefrontPublishSnapshot, UUID> {

    List<StorefrontPublishSnapshot> findByWorkspaceIdOrderByPublishedAtDesc(UUID workspaceId);

    Optional<StorefrontPublishSnapshot> findByIdAndWorkspaceId(UUID snapshotId, UUID workspaceId);
}
