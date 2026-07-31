package sme.tech.innovators.sme.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.request.PublishStorefrontRequest;
import sme.tech.innovators.sme.dto.request.ResetStorefrontDraftRequest;
import sme.tech.innovators.sme.dto.request.UpdateStorefrontDraftRequest;
import sme.tech.innovators.sme.dto.response.*;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.*;
import sme.tech.innovators.sme.repository.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final StorefrontPublishSnapshotRepository publishSnapshotRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final StorefrontConfigValidator configValidator;
    private final SlugGeneratorService slugGeneratorService;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<WorkspaceDto> getWorkspacesForUser(UUID userId) {
        Business business = loadBusinessForUser(userId);
        ensureWorkspaceExists(business);
        return workspaceRepository.findAllByBusiness_Owner_Id(userId)
                .stream()
                .map(this::toWorkspaceDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WorkspaceDto getWorkspace(UUID workspaceId, UUID userId) {
        return toWorkspaceDto(loadOwnedWorkspace(workspaceId, userId));
    }

    @Transactional
    public StorefrontDraftDto getStorefrontDraft(UUID workspaceId, UUID userId) {
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);
        return toStorefrontDraftDto(ensureStorefrontExists(workspace));
    }

    @Transactional
    public StorefrontDraftDto updateStorefrontDraft(UUID workspaceId,
                                                     UUID userId,
                                                     UpdateStorefrontDraftRequest request) {
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);
        Storefront storefront = ensureStorefrontExists(workspace);

        StorefrontTemplateVersion templateVersion = loadAndValidateTemplateVersionForApply(
                request.getTemplateId(), request.getTemplateVersion());

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

    @Transactional
    public StorefrontDraftDto resetStorefrontDraft(UUID workspaceId,
                                                    UUID userId,
                                                    ResetStorefrontDraftRequest request) {
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);
        Storefront storefront = ensureStorefrontExists(workspace);

        StorefrontTemplateVersion templateVersion = loadAndValidateTemplateVersionForApply(
                request.getTemplateId(), request.getTemplateVersion());

        storefront.setTemplateId(request.getTemplateId());
        storefront.setTemplateVersion(request.getTemplateVersion());
        storefront.setDraftConfig(deepCopyConfig(templateVersion.getDefaultConfig()));
        storefront.setDraftConfigVersion(1);
        if (storefront.getTemplateSetupCompletedAt() == null) {
            storefront.setTemplateSetupCompletedAt(LocalDateTime.now());
        }
        storefrontRepository.save(storefront);

        log.info("Storefront draft reset to template={} v{} for workspace={}",
                request.getTemplateId(), request.getTemplateVersion(), workspaceId);
        return toStorefrontDraftDto(storefront);
    }

    @Transactional
    @CacheEvict(value = "publicStorefront", allEntries = true)
    public PublishResultDto publishStorefront(UUID workspaceId,
                                               UUID userId,
                                               PublishStorefrontRequest request) {
        if (request.getConfirm() == null || !request.getConfirm()) {
            throw new PublishConfirmationRequiredException("Publishing requires confirm=true");
        }

        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new WorkspaceNotFoundException("User not found"));
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);
        Storefront storefront = storefrontRepository.findByWorkspace(workspace)
                .orElseThrow(() -> new StorefrontNotFoundException(
                        "Storefront not found for workspace " + workspaceId));

        if (storefront.getDraftConfig() == null || storefront.getDraftConfig().isEmpty()) {
            throw new StorefrontDraftNotFoundException(
                    "Draft config is missing. Save a draft before publishing.");
        }

        StorefrontTemplateVersion templateVersion = loadAndValidateTemplateVersion(
                storefront.getTemplateId(), storefront.getTemplateVersion());

        if (templateVersion.getTemplate().getStatus() != StorefrontTemplateStatus.AVAILABLE) {
            throw new TemplateDisabledException(
                    "Template '" + storefront.getTemplateId() + "' is not available for publishing");
        }

        configValidator.validateForPublish(
                storefront.getDraftConfig(),
                templateVersion.getSupportedSections(),
                templateVersion.getSupportedThemes()
        );

        ensurePublicSlug(workspace);

        Map<String, Object> snapshotConfig = deepCopyConfig(storefront.getDraftConfig());
        LocalDateTime publishedAt = LocalDateTime.now();

        StorefrontPublishSnapshot snapshot = StorefrontPublishSnapshot.builder()
                .storefront(storefront)
                .workspace(workspace)
                .templateId(storefront.getTemplateId())
                .templateVersion(storefront.getTemplateVersion())
                .config(snapshotConfig)
                .configVersion(storefront.getDraftConfigVersion())
                .publishedBy(user)
                .publishedAt(publishedAt)
                .notes(request.getNotes())
                .build();
        snapshot = publishSnapshotRepository.saveAndFlush(snapshot);

        storefront.setPublishedSnapshotId(snapshot.getId());
        storefront.setLastPublishedAt(publishedAt);
        storefrontRepository.saveAndFlush(storefront);

        workspace.setStatus(WorkspaceStatus.LIVE);
        workspaceRepository.saveAndFlush(workspace);

        log.info("Published storefront snapshot={} for workspace={}", snapshot.getId(), workspaceId);

        return PublishResultDto.builder()
                .workspaceId(workspace.getId())
                .storefrontId(storefront.getId())
                .publishedSnapshotId(snapshot.getId())
                .status(workspace.getStatus())
                .publishedAt(publishedAt)
                .build();
    }

    @Transactional(readOnly = true)
    public PublishedStorefrontDto getPublishedStorefront(UUID workspaceId, UUID userId) {
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);
        Storefront storefront = storefrontRepository.findByWorkspace(workspace)
                .orElseThrow(() -> new StorefrontNotFoundException(
                        "Storefront not found for workspace " + workspaceId));

        if (storefront.getPublishedSnapshotId() == null) {
            throw new PublishedStorefrontNotFoundException(
                    "No published storefront found for this workspace");
        }

        StorefrontPublishSnapshot snapshot = publishSnapshotRepository
                .findById(storefront.getPublishedSnapshotId())
                .orElseThrow(() -> new PublishedStorefrontNotFoundException(
                        "Published snapshot not found"));

        return toPublishedStorefrontDto(workspace, storefront, snapshot);
    }

    @Transactional(readOnly = true)
    public List<PublishHistoryItemDto> getPublishHistory(UUID workspaceId, UUID userId) {
        loadOwnedWorkspace(workspaceId, userId);
        return publishSnapshotRepository.findByWorkspaceIdOrderByPublishedAtDesc(workspaceId)
                .stream()
                .map(this::toPublishHistoryItemDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PublishedStorefrontDto getPublishSnapshot(UUID workspaceId,
                                                      UUID snapshotId,
                                                      UUID userId) {
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);
        Storefront storefront = storefrontRepository.findByWorkspace(workspace)
                .orElseThrow(() -> new StorefrontNotFoundException(
                        "Storefront not found for workspace " + workspaceId));

        StorefrontPublishSnapshot snapshot = publishSnapshotRepository
                .findByIdAndWorkspaceId(snapshotId, workspaceId)
                .orElseThrow(() -> new PublishedStorefrontNotFoundException(
                        "Publish snapshot not found: " + snapshotId));

        return toPublishedStorefrontDto(workspace, storefront, snapshot);
    }

    @Transactional
    @CacheEvict(value = "publicStorefront", allEntries = true)
    public UnpublishResultDto unpublishStorefront(UUID workspaceId, UUID userId) {
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);
        Storefront storefront = storefrontRepository.findByWorkspace(workspace)
                .orElseThrow(() -> new StorefrontNotFoundException(
                        "Storefront not found for workspace " + workspaceId));

        if (storefront.getPublishedSnapshotId() == null) {
            throw new PublishedStorefrontNotFoundException(
                    "Cannot unpublish: no published snapshot exists");
        }

        workspace.setStatus(WorkspaceStatus.UNPUBLISHED);
        workspaceRepository.save(workspace);

        log.info("Unpublished storefront for workspace={}", workspaceId);

        return UnpublishResultDto.builder()
                .workspaceId(workspace.getId())
                .status(workspace.getStatus())
                .lastPublishedAt(storefront.getLastPublishedAt())
                .build();
    }

    private void ensurePublicSlug(Workspace workspace) {
        if (workspace.getPublicSlug() != null && !workspace.getPublicSlug().isBlank()) {
            return;
        }
        String sourceName = workspace.getName() != null && !workspace.getName().isBlank()
                ? workspace.getName()
                : workspace.getBusiness().getName();
        String slug = slugGeneratorService.generateUniquePublicSlug(sourceName);
        workspace.setPublicSlug(slug);
        workspaceRepository.save(workspace);
        log.info("Assigned publicSlug={} to workspace={}", slug, workspace.getId());
    }

    private Map<String, Object> deepCopyConfig(Map<String, Object> config) {
        return objectMapper.convertValue(config, new TypeReference<HashMap<String, Object>>() {});
    }

    private Business loadBusinessForUser(UUID userId) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new WorkspaceNotFoundException("User not found"));
        return businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "No active business found for this account"));
    }

    private Workspace loadOwnedWorkspace(UUID workspaceId, UUID userId) {
        return workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Workspace not found or you do not have access to it"));
    }

    private Workspace ensureWorkspaceExists(Business business) {
        return workspaceRepository.findFirstByBusinessOrderByCreatedAtAsc(business)
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
                            .draftConfig(deepCopyConfig(defaultVersion.getDefaultConfig()))
                            .draftConfigVersion(1)
                            .build();
                    return storefrontRepository.save(storefront);
                });
    }

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

    /** Reset/apply only allows AVAILABLE templates (coming_soon stays catalog-only). */
    private StorefrontTemplateVersion loadAndValidateTemplateVersionForApply(String templateId,
                                                                              Integer version) {
        StorefrontTemplateVersion templateVersion = loadAndValidateTemplateVersion(templateId, version);
        if (templateVersion.getTemplate().getStatus() != StorefrontTemplateStatus.AVAILABLE) {
            throw new TemplateDisabledException(
                    "Template '" + templateId + "' is not available to apply yet");
        }
        return templateVersion;
    }

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
                .templateSetupCompletedAt(storefront.getTemplateSetupCompletedAt())
                .updatedAt(storefront.getUpdatedAt())
                .build();
    }

    private PublishedStorefrontDto toPublishedStorefrontDto(Workspace workspace,
                                                             Storefront storefront,
                                                             StorefrontPublishSnapshot snapshot) {
        return PublishedStorefrontDto.builder()
                .workspaceId(workspace.getId())
                .storefrontId(storefront.getId())
                .publishedSnapshotId(snapshot.getId())
                .templateId(snapshot.getTemplateId())
                .templateVersion(snapshot.getTemplateVersion())
                .configVersion(snapshot.getConfigVersion())
                .config(snapshot.getConfig())
                .status(workspace.getStatus())
                .publicSlug(workspace.getPublicSlug())
                .publishedAt(snapshot.getPublishedAt())
                .notes(snapshot.getNotes())
                .build();
    }

    private PublishHistoryItemDto toPublishHistoryItemDto(StorefrontPublishSnapshot snapshot) {
        return PublishHistoryItemDto.builder()
                .snapshotId(snapshot.getId())
                .templateId(snapshot.getTemplateId())
                .templateVersion(snapshot.getTemplateVersion())
                .configVersion(snapshot.getConfigVersion())
                .publishedByUserId(snapshot.getPublishedBy().getId())
                .publishedAt(snapshot.getPublishedAt())
                .notes(snapshot.getNotes())
                .build();
    }
}
