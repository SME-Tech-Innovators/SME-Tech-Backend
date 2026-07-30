package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.PublicStorefrontNotPublishedException;
import sme.tech.innovators.sme.exception.StoreNotAvailableException;
import sme.tech.innovators.sme.exception.StoreNotFoundException;
import sme.tech.innovators.sme.repository.*;
import sme.tech.innovators.sme.service.CategoryService;
import sme.tech.innovators.sme.service.ProductNormalizationHelper;
import sme.tech.innovators.sme.service.ProductService;
import sme.tech.innovators.sme.service.PublicStorefrontService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicStorefrontServiceTest {

    @Mock WorkspaceRepository workspaceRepository;
    @Mock StorefrontRepository storefrontRepository;
    @Mock StorefrontPublishSnapshotRepository publishSnapshotRepository;
    @Mock ProductRepository productRepository;
    @Mock ProductImageRepository productImageRepository;
    @Mock CategoryService categoryService;
    @Mock ProductNormalizationHelper normalizationHelper;
    @Mock ProductService productService;

    private PublicStorefrontService service;

    @BeforeEach
    void setUp() {
        service = new PublicStorefrontService(
                workspaceRepository,
                storefrontRepository,
                publishSnapshotRepository,
                productRepository,
                productImageRepository,
                categoryService,
                normalizationHelper,
                productService
        );
    }

    @Test
    void missingSlugThrowsStoreNotFound() {
        when(workspaceRepository.findByPublicSlugIgnoreCase("missing")).thenReturn(Optional.empty());
        assertThrows(StoreNotFoundException.class, () -> service.getPublicStorefront("missing"));
    }

    @Test
    void unpublishedThrowsStoreNotAvailable() {
        Workspace workspace = workspace("shop", WorkspaceStatus.UNPUBLISHED);
        when(workspaceRepository.findByPublicSlugIgnoreCase("shop")).thenReturn(Optional.of(workspace));
        assertThrows(StoreNotAvailableException.class, () -> service.getPublicStorefront("shop"));
    }

    @Test
    void draftThrowsNotPublished() {
        Workspace workspace = workspace("shop", WorkspaceStatus.DRAFT);
        when(workspaceRepository.findByPublicSlugIgnoreCase("shop")).thenReturn(Optional.of(workspace));
        assertThrows(PublicStorefrontNotPublishedException.class, () -> service.getPublicStorefront("shop"));
    }

    @Test
    void liveReturnsPublishedConfigNotDraft() {
        Workspace workspace = workspace("shop", WorkspaceStatus.LIVE);
        UUID snapshotId = UUID.randomUUID();

        Storefront storefront = Storefront.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .templateId("classic-boutique")
                .templateVersion(1)
                .draftConfig(Map.of("shopName", "DRAFT SHOULD NOT APPEAR"))
                .draftConfigVersion(1)
                .publishedSnapshotId(snapshotId)
                .build();

        Map<String, Object> publishedConfig = new HashMap<>();
        publishedConfig.put("shopName", "Live Shop");
        publishedConfig.put("configVersion", 5);

        StorefrontPublishSnapshot snapshot = StorefrontPublishSnapshot.builder()
                .id(snapshotId)
                .workspace(workspace)
                .storefront(storefront)
                .templateId("classic-boutique")
                .templateVersion(1)
                .configVersion(5)
                .config(publishedConfig)
                .build();

        when(workspaceRepository.findByPublicSlugIgnoreCase("shop")).thenReturn(Optional.of(workspace));
        when(storefrontRepository.findByWorkspace(workspace)).thenReturn(Optional.of(storefront));
        when(publishSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));

        var dto = service.getPublicStorefront("shop");
        assertEquals("Live Shop", dto.getConfig().get("shopName"));
        assertNotEquals("DRAFT SHOULD NOT APPEAR", dto.getConfig().get("shopName"));
        assertEquals(WorkspaceStatus.LIVE, dto.getStatus());
    }

    private Workspace workspace(String slug, WorkspaceStatus status) {
        return Workspace.builder()
                .id(UUID.randomUUID())
                .name("My Store")
                .publicSlug(slug)
                .status(status)
                .build();
    }
}
