package sme.tech.innovators.sme.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sme.tech.innovators.sme.entity.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    Optional<Category> findByIdAndWorkspaceId(UUID categoryId, UUID workspaceId);

    Optional<Category> findByWorkspaceIdAndSlug(UUID workspaceId, String slug);

    Optional<Category> findByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);

    boolean existsByWorkspaceIdAndSlug(UUID workspaceId, String slug);
}
