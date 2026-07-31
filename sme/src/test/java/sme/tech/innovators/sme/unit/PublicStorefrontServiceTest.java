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
import sme.tech.innovators.sme.repository.ProductImageRepository;
import sme.tech.innovators.sme.repository.ProductRepository;
import sme.tech.innovators.sme.service.CategoryService;
import sme.tech.innovators.sme.service.ProductNormalizationHelper;
import sme.tech.innovators.sme.service.ProductService;
import sme.tech.innovators.sme.service.PublicStoreResolver;
import sme.tech.innovators.sme.service.PublicStorefrontService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicStorefrontServiceTest {

    @Mock PublicStoreResolver publicStoreResolver;
    @Mock ProductRepository productRepository;
    @Mock ProductImageRepository productImageRepository;
    @Mock CategoryService categoryService;
    @Mock ProductNormalizationHelper normalizationHelper;
    @Mock ProductService productService;

    private PublicStorefrontService service;

    @BeforeEach
    void setUp() {
        service = new PublicStorefrontService(
                publicStoreResolver,
                productRepository,
                productImageRepository,
                categoryService,
                normalizationHelper,
                productService
        );
    }

    @Test
    void missingSlugThrowsStoreNotFound() {
        when(publicStoreResolver.resolveLiveStore("missing"))
                .thenThrow(new StoreNotFoundException("Store not found: missing"));
        assertThrows(StoreNotFoundException.class, () -> service.getPublicStorefront("missing"));
    }

    @Test
    void unpublishedThrowsStoreNotAvailable() {
        when(publicStoreResolver.resolveLiveStore("shop"))
                .thenThrow(new StoreNotAvailableException("Store is not available: shop"));
        assertThrows(StoreNotAvailableException.class, () -> service.getPublicStorefront("shop"));
    }

    @Test
    void draftThrowsNotPublished() {
        when(publicStoreResolver.resolveLiveStore("shop"))
                .thenThrow(new PublicStorefrontNotPublishedException("Storefront is not published: shop"));
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

        when(publicStoreResolver.resolveLiveStore("shop"))
                .thenReturn(new PublicStoreResolver.LiveStore(workspace, storefront, snapshot));

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
