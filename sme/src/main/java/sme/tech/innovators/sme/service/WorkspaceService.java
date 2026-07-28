package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.request.ResetStorefrontDraftRequest;
import sme.tech.innovators.sme.dto.request.UpdateStorefrontDraftRequest;
import sme.tech.innovators.sme.dto.response.StorefrontDraftDto;
import sme.tech.innovators.sme.dto.response.WorkspaceDto;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.*;
import sme.tech.innovators.sme.repository.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private static final String CLASSIC_BOUTIQUE_ID = "classic-boutique";

    private final WorkspaceRepository workspaceRepository;
    private final StorefrontRepository storefrontRepository;
    private final StorefrontTemplateRepository templateRepository;
    private final StorefrontTemplateVersionRepository templateVersionRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final StorefrontConfigValidator configValidator;

    // -------------------------------------------------------------------------
    // Workspace APIs
    // -------------------------------------------------------------------------

    /**
     * Returns all workspaces owned by the authenticated user.
     * Auto-creates a workspace for the user's business if one doesn't exist yet.
     */
    @Transactional
    public List<WorkspaceDto> getWorkspacesForUser(UUID userId) {
        Business business = loadBusinessForUser(userId);
        ensureWorkspaceExists(business);
        return workspaceRepository.findAllByBusiness_Owner_Id(userId)
                .stream()
                .map(this::toWorkspaceDto)
                .collect(Collectors.toList());
    }

    /**
     * Returns a single workspace by ID, enforcing that the authenticated user owns it.
     */
    @Transactional(readOnly = true)
    public WorkspaceDto getWorkspace(UUID workspaceId, UUID userId) {
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);
        return toWorkspaceDto(workspace);
    }

    // -------------------------------------------------------------------------
    // Storefront Draft APIs
    // -------------------------------------------------------------------------

    /**
     * Returns the storefront draft for the workspace.
     * Auto-creates the storefront from classic-boutique if it doesn't exist yet.
     */
    @Transactional
    public StorefrontDraftDto getStorefrontDraft(UUID workspaceId, UUID userId) {
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);
        Storefront storefront = ensureStorefrontExists(workspace);
        return toStorefrontDraftDto(storefront);
    }

    /**
     * Replaces the full draft config, validating it against the template before saving.
     */
    @Transactional
    public StorefrontDraftDto updateStorefrontDraft(UUID workspaceId,
                                                     UUID userId,
                                                     UpdateStorefrontDraftRequest request) {
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);
        Storefront storefront = ensureStorefrontExists(workspace);

        // Validate template + version
        StorefrontTemplateVersion templateVersion = loadAndValidateTemplateVersion(
                request.getTemplateId(), request.getTemplateVersion());

        // Validate the config itself
        configValidator.validate(
                request.getConfig(),
                templateVersion.getSupportedSections(),
                templateVersion.getSupportedThemes()
        );

        storefront.setTemplateId(request.getTemplateId());
        storefront.setTemplateVersion(request.getTemplateVersion());
        storefront.setDraftConfig(request.getConfig());
        storefront.setDraftConfigVersion(request.getConfigVersion());
        storefrontRepository.save(storefront);

        log.info("Storefront draft updated for workspace={}", workspaceId);
        return toStorefrontDraftDto(storefront);
    }

    /**
     * Resets the draft config back to the template's default config.
     */
    @Transactional
    public StorefrontDraftDto resetStorefrontDraft(UUID workspaceId,
                                                    UUID userId,
                                                    ResetStorefrontDraftRequest request) {
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);
        Storefront storefront = ensureStorefrontExists(workspace);

        StorefrontTemplateVersion templateVersion = loadAndValidateTemplateVersion(
                request.getTemplateId(), request.getTemplateVersion());

        storefront.setTemplateId(request.getTemplateId());
        storefront.setTemplateVersion(request.getTemplateVersion());
        storefront.setDraftConfig(templateVersion.getDefaultConfig());
        storefront.setDraftConfigVersion(1);
        storefrontRepository.save(storefront);

        log.info("Storefront draft reset to template={} v{} for workspace={}",
                request.getTemplateId(), request.getTemplateVersion(), workspaceId);
        return toStorefrontDraftDto(storefront);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Loads the business owned by the given user, throwing WorkspaceNotFoundException
     * if the user has no active business.
     */
    private Business loadBusinessForUser(UUID userId) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new WorkspaceNotFoundException("User not found"));
        return businessRepository.findByOwnerAndIsDeletedFalse(user)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "No active business found for this account"));
    }

    /**
     * Loads a workspace ensuring the authenticated user owns it.
     */
    private Workspace loadOwnedWorkspace(UUID workspaceId, UUID userId) {
        return workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Workspace not found or you do not have access to it"));
    }

    /**
     * Auto-creates a workspace for the business if one doesn't exist yet.
     */
    private Workspace ensureWorkspaceExists(Business business) {
        return workspaceRepository.findByBusiness(business)
                .orElseGet(() -> {
                    log.info("Auto-creating workspace for business={}", business.getId());
                    Workspace workspace = Workspace.builder()
                            .business(business)
                            .name(business.getName())
                            .status(WorkspaceStatus.DRAFT)
                            .build();
                    return workspaceRepository.save(workspace);
                });
    }

    /**
     * Auto-creates a storefront from classic-boutique if one doesn't exist yet.
     */
    private Storefront ensureStorefrontExists(Workspace workspace) {
        return storefrontRepository.findByWorkspace(workspace)
                .orElseGet(() -> {
                    log.info("Auto-creating storefront for workspace={}", workspace.getId());
                    StorefrontTemplateVersion defaultVersion =
                            templateVersionRepository.findByTemplateIdAndVersion(CLASSIC_BOUTIQUE_ID, 1)
                                    .orElseThrow(() -> new StorefrontNotFoundException(
                                            "Default template 'classic-boutique' v1 not found. " +
                                            "Please ensure seed data has run."));

                    Storefront storefront = Storefront.builder()
                            .workspace(workspace)
                            .templateId(CLASSIC_BOUTIQUE_ID)
                            .templateVersion(1)
                            .draftConfig(defaultVersion.getDefaultConfig())
                            .draftConfigVersion(1)
                            .build();
                    return storefrontRepository.save(storefront);
                });
    }

    /**
     * Loads and validates a template + version, throwing meaningful errors
     * if the template doesn't exist, is disabled, or the version doesn't exist.
     */
    private StorefrontTemplateVersion loadAndValidateTemplateVersion(String templateId,
                                                                      Integer version) {
        StorefrontTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new TemplateNotFoundException(
                        "Template '" + templateId + "' does not exist"));

        if (template.getStatus() == StorefrontTemplateStatus.DISABLED) {
            throw new TemplateDisabledException(
                    "Template '" + templateId + "' is disabled and cannot be used");
        }

        return templateVersionRepository.findByTemplateAndVersion(template, version)
                .orElseThrow(() -> new TemplateNotFoundException(
                        "Template '" + templateId + "' version " + version + " does not exist"));
    }

    // -------------------------------------------------------------------------
    // Mappers
    // -------------------------------------------------------------------------

    private WorkspaceDto toWorkspaceDto(Workspace workspace) {
        return WorkspaceDto.builder()
                .id(workspace.getId())
                .businessId(workspace.getBusiness().getId())
                .name(workspace.getName())
                .publicSlug(workspace.getPublicSlug())
                .status(workspace.getStatus())
                .createdAt(workspace.getCreatedAt())
                .updatedAt(workspace.getUpdatedAt())
                .build();
    }

    private StorefrontDraftDto toStorefrontDraftDto(Storefront storefront) {
        return StorefrontDraftDto.builder()
                .workspaceId(storefront.getWorkspace().getId())
                .storefrontId(storefront.getId())
                .templateId(storefront.getTemplateId())
                .templateVersion(storefront.getTemplateVersion())
                .configVersion(storefront.getDraftConfigVersion())
                .config(storefront.getDraftConfig())
                .updatedAt(storefront.getUpdatedAt())
                .build();
    }
}
