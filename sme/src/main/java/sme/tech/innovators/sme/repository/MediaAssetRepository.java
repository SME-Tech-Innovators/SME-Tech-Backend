package sme.tech.innovators.sme.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sme.tech.innovators.sme.entity.MediaAsset;
import sme.tech.innovators.sme.entity.MediaStatus;

import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    Optional<MediaAsset> findByIdAndWorkspaceId(UUID mediaId, UUID workspaceId);

    @Query("""
            SELECT m FROM MediaAsset m
            WHERE m.workspace.id = :workspaceId
              AND m.status <> sme.tech.innovators.sme.entity.MediaStatus.DELETED
              AND (:imageOnly = false OR LOWER(m.mimeType) LIKE 'image/%')
            ORDER BY m.createdAt DESC
            """)
    Page<MediaAsset> findLibrary(
            @Param("workspaceId") UUID workspaceId,
            @Param("imageOnly") boolean imageOnly,
            Pageable pageable);

    Optional<MediaAsset> findByIdAndWorkspaceIdAndStatus(UUID mediaId, UUID workspaceId, MediaStatus status);
}
