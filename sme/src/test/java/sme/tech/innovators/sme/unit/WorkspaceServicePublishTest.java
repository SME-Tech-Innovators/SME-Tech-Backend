package sme.tech.innovators.sme.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sme.tech.innovators.sme.dto.request.PublishStorefrontRequest;
import sme.tech.innovators.sme.dto.response.PublishHistoryItemDto;
import sme.tech.innovators.sme.dto.response.PublishResultDto;
import sme.tech.innovators.sme.dto.response.PublishedStorefrontDto;
import sme.tech.innovators.sme.dto.response.UnpublishResultDto;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.*;
import sme.tech.innovators.sme.repository.*;
import sme.tech.innovators.sme.service.SlugGeneratorService;
import sme.tech.innovators.sme.service.StorefrontConfigValidator;
import sme.tech.innovators.sme.service.WorkspaceService;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceServicePublishTest {

    @Mock WorkspaceRepository workspaceRepository;
    @Mock StorefrontRepository storefrontRepository;
    @Mock StorefrontTemplateRepository templateRepository;
    @Mock StorefrontTemplateVersionRepository templateVersionRepository;
    @Mock StorefrontPublishSnapshotRepository publishSnapshotRepository;
    @Mock BusinessRepository businessRepository;
    @Mock UserRepository userRepository;
    @Mock StorefrontConfigValidator configValidator;
    @Mock SlugGeneratorService slugGeneratorService;

    private WorkspaceService workspaceService;

    private UUID userId;
    private UUID workspaceId;
    private UUID storefrontId;
    private User user;
    private Business business;
    private Workspace workspace;
    private Storefront storefront;
    private StorefrontTemplate template;
    private StorefrontTemplateVersion templateVersion;

    @BeforeEach
    void setUp() {
        workspaceService = new WorkspaceService(
                workspaceRepository,
                storefrontRepository,
                templateRepository,
                templateVersionRepository,
                publishSnapshotRepository,
                businessRepository,
                userRepository,
                configValidator,
                slugGeneratorService,
                new ObjectMapper()
        );

        userId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        storefrontId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .email("owner@example.com")
                .password("$2a$12$hashed")
                .fullName("Jane Doe")
                .accountStatus(AccountStatus.VERIFIED)
                .role(UserRole.OWNER)
                .build();

        business = Business.builder()
                .id(UUID.randomUUID())
                .name("Nkandu Fashion")
                .slug("nkandu-fashion")
                .publicLink("https://example.com/store/nkandu-fashion")
                .owner(user)
                .build();

        workspace = Workspace.builder()
                .id(workspaceId)
                .business(business)
                .name("Nkandu Fashion")
                .publicSlug("nkandu-fashion")
                .status(WorkspaceStatus.DRAFT)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        storefront = Storefront.builder()
                .id(storefrontId)
                .workspace(workspace)
                .templateId("classic-boutique")
                .templateVersion(1)
                .draftConfig(validDraftConfig())
                .draftConfigVersion(1)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        template = StorefrontTemplate.builder()
                .id("classic-boutique")
                .name("Classic Boutique")
                .description("A classic boutique template")
                .status(StorefrontTemplateStatus.AVAILABLE)
                .latestVersion(1)
                .build();

        templateVersion = StorefrontTemplateVersion.builder()
                .id(UUID.randomUUID())
                .template(template)
                .version(1)
                .supportedSections(List.of("hero", "featuredProducts"))
                .supportedThemes(List.of("blue", "red"))
                .defaultConfig(validDraftConfig())
                .build();
    }

    // -------------------------------------------------------------------------
    // POST publish — happy path
    // -------------------------------------------------------------------------

    @Test
    void publish_createsImmutableSnapshot_marksWorkspaceLive_andUpdatesStorefrontPointers() {
        stubOwnedWorkspaceWithStorefront();
        stubAvailableTemplate();
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));

        UUID snapshotId = UUID.randomUUID();
        when(publishSnapshotRepository.saveAndFlush(any(StorefrontPublishSnapshot.class)))
                .thenAnswer(inv -> {
                    StorefrontPublishSnapshot s = inv.getArgument(0);
                    s.setId(snapshotId);
                    return s;
                });
        when(storefrontRepository.saveAndFlush(any(Storefront.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workspaceRepository.saveAndFlush(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

        PublishStorefrontRequest request = publishRequest(true, "Initial launch");
        PublishResultDto result = workspaceService.publishStorefront(workspaceId, userId, request);

        assertThat(result.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(result.getStorefrontId()).isEqualTo(storefrontId);
        assertThat(result.getPublishedSnapshotId()).isEqualTo(snapshotId);
        assertThat(result.getStatus()).isEqualTo(WorkspaceStatus.LIVE);
        assertThat(result.getPublishedAt()).isNotNull();

        assertThat(storefront.getPublishedSnapshotId()).isEqualTo(snapshotId);
        assertThat(storefront.getLastPublishedAt()).isNotNull();
        assertThat(workspace.getStatus()).isEqualTo(WorkspaceStatus.LIVE);

        ArgumentCaptor<StorefrontPublishSnapshot> snapCaptor =
                ArgumentCaptor.forClass(StorefrontPublishSnapshot.class);
        verify(publishSnapshotRepository).saveAndFlush(snapCaptor.capture());
        StorefrontPublishSnapshot saved = snapCaptor.getValue();
        assertThat(saved.getTemplateId()).isEqualTo("classic-boutique");
        assertThat(saved.getTemplateVersion()).isEqualTo(1);
        assertThat(saved.getNotes()).isEqualTo("Initial launch");
        assertThat(saved.getPublishedBy()).isEqualTo(user);
        assertThat(saved.getConfig()).containsEntry("shopName", "My Store");
        // Snapshot must be a copy — mutating draft later must not affect saved config identity check
        assertThat(saved.getConfig()).isNotSameAs(storefront.getDraftConfig());

        verify(configValidator).validateForPublish(
                eq(storefront.getDraftConfig()),
                eq(templateVersion.getSupportedSections()),
                eq(templateVersion.getSupportedThemes())
        );
        verify(slugGeneratorService, never()).generateUniquePublicSlug(anyString());
    }

    @Test
    void publish_keepsExistingPublicSlug() {
        workspace.setPublicSlug("already-live");
        stubOwnedWorkspaceWithStorefront();
        stubAvailableTemplate();
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        stubSuccessfulSnapshotSave();

        workspaceService.publishStorefront(workspaceId, userId, publishRequest(true, null));

        assertThat(workspace.getPublicSlug()).isEqualTo("already-live");
        verify(slugGeneratorService, never()).generateUniquePublicSlug(anyString());
    }

    @Test
    void publish_generatesPublicSlug_whenMissing() {
        workspace.setPublicSlug(null);
        stubOwnedWorkspaceWithStorefront();
        stubAvailableTemplate();
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(slugGeneratorService.generateUniquePublicSlug("Nkandu Fashion"))
                .thenReturn("nkandu-fashion");
        when(workspaceRepository.save(workspace)).thenReturn(workspace);
        stubSuccessfulSnapshotSave();

        workspaceService.publishStorefront(workspaceId, userId, publishRequest(true, null));

        assertThat(workspace.getPublicSlug()).isEqualTo("nkandu-fashion");
        verify(slugGeneratorService).generateUniquePublicSlug("Nkandu Fashion");
        verify(workspaceRepository).save(workspace);
    }

    @Test
    void publish_generatesPublicSlugFromBusinessName_whenWorkspaceNameBlank() {
        workspace.setName("  ");
        workspace.setPublicSlug("");
        stubOwnedWorkspaceWithStorefront();
        stubAvailableTemplate();
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(slugGeneratorService.generateUniquePublicSlug("Nkandu Fashion"))
                .thenReturn("nkandu-fashion-8f3a");
        when(workspaceRepository.save(workspace)).thenReturn(workspace);
        stubSuccessfulSnapshotSave();

        workspaceService.publishStorefront(workspaceId, userId, publishRequest(true, null));

        assertThat(workspace.getPublicSlug()).isEqualTo("nkandu-fashion-8f3a");
        verify(slugGeneratorService).generateUniquePublicSlug("Nkandu Fashion");
    }

    @Test
    void publish_doesNotMutateDraftConfig() {
        Map<String, Object> originalDraft = new HashMap<>(validDraftConfig());
        storefront.setDraftConfig(originalDraft);
        stubOwnedWorkspaceWithStorefront();
        stubAvailableTemplate();
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        stubSuccessfulSnapshotSave();

        workspaceService.publishStorefront(workspaceId, userId, publishRequest(true, "notes"));

        assertThat(storefront.getDraftConfig()).isEqualTo(originalDraft);
        assertThat(storefront.getDraftConfig()).containsEntry("shopName", "My Store");
    }

    // -------------------------------------------------------------------------
    // POST publish — validation / error paths
    // -------------------------------------------------------------------------

    @Test
    void publish_rejectsWhenConfirmIsFalse() {
        PublishStorefrontRequest request = publishRequest(false, null);

        assertThatThrownBy(() -> workspaceService.publishStorefront(workspaceId, userId, request))
                .isInstanceOf(PublishConfirmationRequiredException.class);

        verifyNoInteractions(publishSnapshotRepository);
    }

    @Test
    void publish_rejectsWhenConfirmIsNull() {
        PublishStorefrontRequest request = new PublishStorefrontRequest();
        request.setConfirm(null);

        assertThatThrownBy(() -> workspaceService.publishStorefront(workspaceId, userId, request))
                .isInstanceOf(PublishConfirmationRequiredException.class);
    }

    @Test
    void publish_rejectsWhenUserDoesNotOwnWorkspace() {
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                workspaceService.publishStorefront(workspaceId, userId, publishRequest(true, null)))
                .isInstanceOf(WorkspaceNotFoundException.class);

        verifyNoInteractions(publishSnapshotRepository);
    }

    @Test
    void publish_rejectsWhenStorefrontMissing() {
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId))
                .thenReturn(Optional.of(workspace));
        when(storefrontRepository.findByWorkspace(workspace)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                workspaceService.publishStorefront(workspaceId, userId, publishRequest(true, null)))
                .isInstanceOf(StorefrontNotFoundException.class);
    }

    @Test
    void publish_rejectsWhenDraftConfigMissing() {
        storefront.setDraftConfig(null);
        stubOwnedWorkspaceWithStorefront();
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
                workspaceService.publishStorefront(workspaceId, userId, publishRequest(true, null)))
                .isInstanceOf(StorefrontDraftNotFoundException.class);
    }

    @Test
    void publish_rejectsWhenDraftConfigEmpty() {
        storefront.setDraftConfig(Map.of());
        stubOwnedWorkspaceWithStorefront();
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
                workspaceService.publishStorefront(workspaceId, userId, publishRequest(true, null)))
                .isInstanceOf(StorefrontDraftNotFoundException.class);
    }

    @Test
    void publish_rejectsWhenTemplateNotFound() {
        stubOwnedWorkspaceWithStorefront();
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(templateRepository.findById("classic-boutique")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                workspaceService.publishStorefront(workspaceId, userId, publishRequest(true, null)))
                .isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    void publish_rejectsWhenTemplateVersionMissing() {
        stubOwnedWorkspaceWithStorefront();
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(templateRepository.findById("classic-boutique")).thenReturn(Optional.of(template));
        when(templateVersionRepository.findByTemplateAndVersion(template, 1))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                workspaceService.publishStorefront(workspaceId, userId, publishRequest(true, null)))
                .isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    void publish_rejectsWhenTemplateDisabled() {
        template.setStatus(StorefrontTemplateStatus.DISABLED);
        stubOwnedWorkspaceWithStorefront();
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(templateRepository.findById("classic-boutique")).thenReturn(Optional.of(template));

        assertThatThrownBy(() ->
                workspaceService.publishStorefront(workspaceId, userId, publishRequest(true, null)))
                .isInstanceOf(TemplateDisabledException.class);
    }

    @Test
    void publish_rejectsWhenTemplateComingSoon() {
        template.setStatus(StorefrontTemplateStatus.COMING_SOON);
        stubOwnedWorkspaceWithStorefront();
        stubTemplateLookup();
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
                workspaceService.publishStorefront(workspaceId, userId, publishRequest(true, null)))
                .isInstanceOf(TemplateDisabledException.class);
    }

    @Test
    void publish_rejectsWhenConfigFailsPublishValidation() {
        stubOwnedWorkspaceWithStorefront();
        stubAvailableTemplate();
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        doThrow(new InvalidPublishConfigException("shopName is required before publishing"))
                .when(configValidator).validateForPublish(any(), any(), any());

        assertThatThrownBy(() ->
                workspaceService.publishStorefront(workspaceId, userId, publishRequest(true, null)))
                .isInstanceOf(InvalidPublishConfigException.class);

        verifyNoInteractions(publishSnapshotRepository);
    }

    @Test
    void publish_propagatesPublicSlugUnavailable() {
        workspace.setPublicSlug(null);
        stubOwnedWorkspaceWithStorefront();
        stubAvailableTemplate();
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(slugGeneratorService.generateUniquePublicSlug(anyString()))
                .thenThrow(new PublicSlugUnavailableException("Failed to generate a unique public slug"));

        assertThatThrownBy(() ->
                workspaceService.publishStorefront(workspaceId, userId, publishRequest(true, null)))
                .isInstanceOf(PublicSlugUnavailableException.class);

        verifyNoInteractions(publishSnapshotRepository);
    }

    // -------------------------------------------------------------------------
    // GET published
    // -------------------------------------------------------------------------

    @Test
    void getPublished_returnsLatestSnapshotWithFullConfig() {
        UUID snapshotId = UUID.randomUUID();
        storefront.setPublishedSnapshotId(snapshotId);
        workspace.setStatus(WorkspaceStatus.LIVE);

        StorefrontPublishSnapshot snapshot = StorefrontPublishSnapshot.builder()
                .id(snapshotId)
                .storefront(storefront)
                .workspace(workspace)
                .templateId("classic-boutique")
                .templateVersion(1)
                .config(validDraftConfig())
                .configVersion(1)
                .publishedBy(user)
                .publishedAt(LocalDateTime.of(2026, 5, 22, 8, 0))
                .notes("Initial launch")
                .build();

        when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId))
                .thenReturn(Optional.of(workspace));
        when(storefrontRepository.findByWorkspace(workspace)).thenReturn(Optional.of(storefront));
        when(publishSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));

        PublishedStorefrontDto dto = workspaceService.getPublishedStorefront(workspaceId, userId);

        assertThat(dto.getPublishedSnapshotId()).isEqualTo(snapshotId);
        assertThat(dto.getStatus()).isEqualTo(WorkspaceStatus.LIVE);
        assertThat(dto.getPublicSlug()).isEqualTo("nkandu-fashion");
        assertThat(dto.getConfig()).containsEntry("shopName", "My Store");
        assertThat(dto.getNotes()).isEqualTo("Initial launch");
    }

    @Test
    void getPublished_returns404_whenNeverPublished() {
        storefront.setPublishedSnapshotId(null);
        when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId))
                .thenReturn(Optional.of(workspace));
        when(storefrontRepository.findByWorkspace(workspace)).thenReturn(Optional.of(storefront));

        assertThatThrownBy(() -> workspaceService.getPublishedStorefront(workspaceId, userId))
                .isInstanceOf(PublishedStorefrontNotFoundException.class);
    }

    @Test
    void getPublished_returns404_whenSnapshotRowMissing() {
        UUID snapshotId = UUID.randomUUID();
        storefront.setPublishedSnapshotId(snapshotId);
        when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId))
                .thenReturn(Optional.of(workspace));
        when(storefrontRepository.findByWorkspace(workspace)).thenReturn(Optional.of(storefront));
        when(publishSnapshotRepository.findById(snapshotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.getPublishedStorefront(workspaceId, userId))
                .isInstanceOf(PublishedStorefrontNotFoundException.class);
    }

    @Test
    void getPublished_rejectsNonOwner() {
        when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.getPublishedStorefront(workspaceId, userId))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // GET publish history
    // -------------------------------------------------------------------------

    @Test
    void getPublishHistory_returnsMetadataNewestFirst_withoutFullConfig() {
        when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId))
                .thenReturn(Optional.of(workspace));

        StorefrontPublishSnapshot newer = historySnapshot(
                LocalDateTime.of(2026, 5, 22, 10, 0), "Second publish");
        StorefrontPublishSnapshot older = historySnapshot(
                LocalDateTime.of(2026, 5, 22, 8, 0), "Initial launch");

        when(publishSnapshotRepository.findByWorkspaceIdOrderByPublishedAtDesc(workspaceId))
                .thenReturn(List.of(newer, older));

        List<PublishHistoryItemDto> history = workspaceService.getPublishHistory(workspaceId, userId);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getNotes()).isEqualTo("Second publish");
        assertThat(history.get(1).getNotes()).isEqualTo("Initial launch");
        assertThat(history.get(0).getPublishedByUserId()).isEqualTo(userId);
        assertThat(history.get(0).getSnapshotId()).isNotNull();
        assertThat(history.get(0).getTemplateId()).isEqualTo("classic-boutique");
    }

    @Test
    void getPublishHistory_returnsEmptyList_whenNoSnapshots() {
        when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId))
                .thenReturn(Optional.of(workspace));
        when(publishSnapshotRepository.findByWorkspaceIdOrderByPublishedAtDesc(workspaceId))
                .thenReturn(List.of());

        assertThat(workspaceService.getPublishHistory(workspaceId, userId)).isEmpty();
    }

    @Test
    void getPublishHistory_rejectsNonOwner() {
        when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.getPublishHistory(workspaceId, userId))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // GET publish snapshot by id
    // -------------------------------------------------------------------------

    @Test
    void getPublishSnapshot_returnsFullConfigForOwnedSnapshot() {
        UUID snapshotId = UUID.randomUUID();
        StorefrontPublishSnapshot snapshot = StorefrontPublishSnapshot.builder()
                .id(snapshotId)
                .storefront(storefront)
                .workspace(workspace)
                .templateId("classic-boutique")
                .templateVersion(1)
                .config(validDraftConfig())
                .configVersion(1)
                .publishedBy(user)
                .publishedAt(LocalDateTime.now())
                .notes("v1")
                .build();

        when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId))
                .thenReturn(Optional.of(workspace));
        when(storefrontRepository.findByWorkspace(workspace)).thenReturn(Optional.of(storefront));
        when(publishSnapshotRepository.findByIdAndWorkspaceId(snapshotId, workspaceId))
                .thenReturn(Optional.of(snapshot));

        PublishedStorefrontDto dto =
                workspaceService.getPublishSnapshot(workspaceId, snapshotId, userId);

        assertThat(dto.getPublishedSnapshotId()).isEqualTo(snapshotId);
        assertThat(dto.getConfig()).containsEntry("themeId", "blue");
        assertThat(dto.getNotes()).isEqualTo("v1");
    }

    @Test
    void getPublishSnapshot_returns404_whenSnapshotNotInWorkspace() {
        UUID snapshotId = UUID.randomUUID();
        when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId))
                .thenReturn(Optional.of(workspace));
        when(storefrontRepository.findByWorkspace(workspace)).thenReturn(Optional.of(storefront));
        when(publishSnapshotRepository.findByIdAndWorkspaceId(snapshotId, workspaceId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                workspaceService.getPublishSnapshot(workspaceId, snapshotId, userId))
                .isInstanceOf(PublishedStorefrontNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // POST unpublish
    // -------------------------------------------------------------------------

    @Test
    void unpublish_setsStatusUnpublished_preservesDraftAndSnapshotPointers() {
        UUID snapshotId = UUID.randomUUID();
        LocalDateTime publishedAt = LocalDateTime.of(2026, 5, 22, 8, 0);
        storefront.setPublishedSnapshotId(snapshotId);
        storefront.setLastPublishedAt(publishedAt);
        storefront.setDraftConfig(validDraftConfig());
        workspace.setStatus(WorkspaceStatus.LIVE);

        when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId))
                .thenReturn(Optional.of(workspace));
        when(storefrontRepository.findByWorkspace(workspace)).thenReturn(Optional.of(storefront));
        when(workspaceRepository.save(workspace)).thenReturn(workspace);

        UnpublishResultDto result = workspaceService.unpublishStorefront(workspaceId, userId);

        assertThat(result.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(result.getStatus()).isEqualTo(WorkspaceStatus.UNPUBLISHED);
        assertThat(result.getLastPublishedAt()).isEqualTo(publishedAt);

        assertThat(workspace.getStatus()).isEqualTo(WorkspaceStatus.UNPUBLISHED);
        assertThat(storefront.getPublishedSnapshotId()).isEqualTo(snapshotId);
        assertThat(storefront.getLastPublishedAt()).isEqualTo(publishedAt);
        assertThat(storefront.getDraftConfig()).isNotNull();
        verify(publishSnapshotRepository, never()).delete(any());
        verify(publishSnapshotRepository, never()).deleteAll(any());
    }

    @Test
    void unpublish_rejectsWhenNeverPublished() {
        storefront.setPublishedSnapshotId(null);
        when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId))
                .thenReturn(Optional.of(workspace));
        when(storefrontRepository.findByWorkspace(workspace)).thenReturn(Optional.of(storefront));

        assertThatThrownBy(() -> workspaceService.unpublishStorefront(workspaceId, userId))
                .isInstanceOf(PublishedStorefrontNotFoundException.class);

        verify(workspaceRepository, never()).save(any());
    }

    @Test
    void unpublish_rejectsNonOwner() {
        when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.unpublishStorefront(workspaceId, userId))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void stubOwnedWorkspaceWithStorefront() {
        when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId))
                .thenReturn(Optional.of(workspace));
        when(storefrontRepository.findByWorkspace(workspace)).thenReturn(Optional.of(storefront));
    }

    private void stubTemplateLookup() {
        when(templateRepository.findById("classic-boutique")).thenReturn(Optional.of(template));
        when(templateVersionRepository.findByTemplateAndVersion(template, 1))
                .thenReturn(Optional.of(templateVersion));
    }

    private void stubAvailableTemplate() {
        template.setStatus(StorefrontTemplateStatus.AVAILABLE);
        stubTemplateLookup();
    }

    private void stubSuccessfulSnapshotSave() {
        when(publishSnapshotRepository.saveAndFlush(any(StorefrontPublishSnapshot.class)))
                .thenAnswer(inv -> {
                    StorefrontPublishSnapshot s = inv.getArgument(0);
                    s.setId(UUID.randomUUID());
                    return s;
                });
        when(storefrontRepository.saveAndFlush(any(Storefront.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workspaceRepository.saveAndFlush(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private StorefrontPublishSnapshot historySnapshot(LocalDateTime at, String notes) {
        return StorefrontPublishSnapshot.builder()
                .id(UUID.randomUUID())
                .storefront(storefront)
                .workspace(workspace)
                .templateId("classic-boutique")
                .templateVersion(1)
                .config(validDraftConfig())
                .configVersion(1)
                .publishedBy(user)
                .publishedAt(at)
                .notes(notes)
                .build();
    }

    private static PublishStorefrontRequest publishRequest(Boolean confirm, String notes) {
        PublishStorefrontRequest request = new PublishStorefrontRequest();
        request.setConfirm(confirm);
        request.setNotes(notes);
        return request;
    }

    private static Map<String, Object> validDraftConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("configVersion", 1);
        config.put("themeId", "blue");
        config.put("shopName", "My Store");
        config.put("sections", List.of(Map.of("id", "hero-1", "type", "hero")));
        config.put("pages", List.of());
        return config;
    }
}
