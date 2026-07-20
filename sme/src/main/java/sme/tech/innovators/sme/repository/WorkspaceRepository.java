package sme.tech.innovators.sme.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sme.tech.innovators.sme.entity.Business;
import sme.tech.innovators.sme.entity.Workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    Optional<Workspace> findByBusiness(Business business);
    Optional<Workspace> findByIdAndBusiness_Owner_Id(UUID workspaceId, UUID ownerId);
    List<Workspace> findAllByBusiness_Owner_Id(UUID ownerId);
    boolean existsByPublicSlug(String publicSlug);
}
