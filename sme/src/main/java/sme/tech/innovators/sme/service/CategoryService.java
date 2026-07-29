package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.request.CreateCategoryRequest;
import sme.tech.innovators.sme.dto.request.UpdateCategoryRequest;
import sme.tech.innovators.sme.dto.response.CategoryDto;
import sme.tech.innovators.sme.entity.Category;
import sme.tech.innovators.sme.entity.Workspace;
import sme.tech.innovators.sme.exception.CategoryNotFoundException;
import sme.tech.innovators.sme.exception.InvalidProductDataException;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.CategoryRepository;
import sme.tech.innovators.sme.repository.WorkspaceRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final WorkspaceRepository workspaceRepository;
    private final SlugGeneratorService slugGeneratorService;

    @Transactional(readOnly = true)
    public List<CategoryDto> listCategories(UUID workspaceId, UUID userId) {
        loadOwnedWorkspace(workspaceId, userId);
        return categoryRepository.findByWorkspaceIdOrderByNameAsc(workspaceId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoryDto createCategory(UUID workspaceId, UUID userId, CreateCategoryRequest request) {
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);
        String name = request.getName().trim();
        if (name.isBlank()) {
            throw new InvalidProductDataException("name is required");
        }

        String slug = resolveUniqueSlug(workspaceId, request.getSlug(), name);
        Category category = Category.builder()
                .workspace(workspace)
                .name(name)
                .slug(slug)
                .build();
        category = categoryRepository.save(category);
        log.info("Created category={} for workspace={}", category.getId(), workspaceId);
        return toDto(category);
    }

    @Transactional
    public CategoryDto updateCategory(UUID workspaceId,
                                      UUID categoryId,
                                      UUID userId,
                                      UpdateCategoryRequest request) {
        loadOwnedWorkspace(workspaceId, userId);
        Category category = categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + categoryId));

        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.isBlank()) {
                throw new InvalidProductDataException("name cannot be blank");
            }
            category.setName(name);
        }

        if (request.getSlug() != null) {
            String slug = slugGeneratorService.sanitizeSlug(request.getSlug());
            if (slug.isBlank()) {
                throw new InvalidProductDataException("slug is invalid");
            }
            if (categoryRepository.existsByWorkspaceIdAndSlug(workspaceId, slug)
                    && !slug.equals(category.getSlug())) {
                throw new InvalidProductDataException("Category slug already exists in this workspace");
            }
            category.setSlug(slug);
        }

        category = categoryRepository.save(category);
        return toDto(category);
    }

    @Transactional
    public Category findOrCreateByName(Workspace workspace, String categoryName) {
        return categoryRepository.findByWorkspaceIdAndNameIgnoreCase(workspace.getId(), categoryName.trim())
                .orElseGet(() -> {
                    String slug = resolveUniqueSlug(workspace.getId(), null, categoryName);
                    Category created = Category.builder()
                            .workspace(workspace)
                            .name(categoryName.trim())
                            .slug(slug)
                            .build();
                    return categoryRepository.save(created);
                });
    }

    private String resolveUniqueSlug(UUID workspaceId, String requestedSlug, String name) {
        String base = slugGeneratorService.sanitizeSlug(
                requestedSlug != null && !requestedSlug.isBlank() ? requestedSlug : name);
        if (base.isBlank()) {
            throw new InvalidProductDataException("Unable to generate category slug");
        }
        if (!categoryRepository.existsByWorkspaceIdAndSlug(workspaceId, base)) {
            return base;
        }
        for (int i = 1; i <= 20; i++) {
            String candidate = base + "-" + i;
            if (!categoryRepository.existsByWorkspaceIdAndSlug(workspaceId, candidate)) {
                return candidate;
            }
        }
        throw new InvalidProductDataException("Unable to generate unique category slug");
    }

    private Workspace loadOwnedWorkspace(UUID workspaceId, UUID userId) {
        return workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Workspace not found or you do not have access to it"));
    }

    CategoryDto toDto(Category category) {
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .build();
    }
}
