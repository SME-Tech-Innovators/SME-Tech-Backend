package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.response.*;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.*;
import sme.tech.innovators.sme.repository.ProductImageRepository;
import sme.tech.innovators.sme.repository.ProductRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicStorefrontService {

    private final PublicStoreResolver publicStoreResolver;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryService categoryService;
    private final ProductNormalizationHelper normalizationHelper;
    private final ProductService productService;

    @Transactional(readOnly = true)
    @Cacheable(value = "publicStorefront", key = "#storeSlug.toLowerCase()")
    public PublicStorefrontDto getPublicStorefront(String storeSlug) {
        PublicStoreResolver.LiveStore live = publicStoreResolver.resolveLiveStore(storeSlug);
        StorefrontPublishSnapshot snapshot = live.snapshot();
        Workspace workspace = live.workspace();

        return PublicStorefrontDto.builder()
                .workspaceId(workspace.getId())
                .storeSlug(workspace.getPublicSlug())
                .storeName(workspace.getName())
                .status(workspace.getStatus())
                .templateId(snapshot.getTemplateId())
                .templateVersion(snapshot.getTemplateVersion())
                .configVersion(snapshot.getConfigVersion())
                .config(snapshot.getConfig())
                .publishedAt(snapshot.getPublishedAt())
                .seo(buildStoreSeo(workspace, snapshot.getConfig()))
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductDto> getPublicProducts(String storeSlug,
                                                       String category,
                                                       String search,
                                                       Boolean onSale,
                                                       String sort,
                                                       int page,
                                                       int limit) {
        PublicStoreResolver.LiveStore live = publicStoreResolver.resolveLiveStore(storeSlug);
        int safePage = Math.max(page, 0);
        int safeLimit = Math.min(Math.max(limit, 1), 100);

        Page<Product> result = productRepository.searchPublic(
                live.workspace().getId(),
                blankToNull(category),
                blankToNull(search),
                onSale,
                productService.buildPublicPageable(safePage, safeLimit, sort)
        );

        List<ProductDto> items = result.getContent().stream()
                .map(this::toPublicProductDto)
                .collect(Collectors.toList());

        return PageResponse.<ProductDto>builder()
                .items(items)
                .page(safePage)
                .limit(safeLimit)
                .totalItems(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public ProductDto getPublicProduct(String storeSlug, String productSlug) {
        PublicStoreResolver.LiveStore live = publicStoreResolver.resolveLiveStore(storeSlug);
        Product product = productRepository
                .findByWorkspaceIdAndSlugAndStatus(live.workspace().getId(), productSlug, ProductStatus.ACTIVE)
                .orElseThrow(() -> new PublicProductNotFoundException(
                        "Product not found: " + productSlug));
        return toPublicProductDto(product);
    }

    @Transactional(readOnly = true)
    public PublicPageDto getPublicPage(String storeSlug, String pageSlug) {
        PublicStoreResolver.LiveStore live = publicStoreResolver.resolveLiveStore(storeSlug);
        Map<String, Object> config = live.snapshot().getConfig();
        if (config == null) {
            throw new PublicPageNotFoundException("Page not found: " + pageSlug);
        }

        Object pagesObj = config.get("pages");
        if (!(pagesObj instanceof List<?> pages)) {
            throw new PublicPageNotFoundException("Page not found: " + pageSlug);
        }

        for (Object pageObj : pages) {
            if (!(pageObj instanceof Map<?, ?> pageMap)) {
                continue;
            }
            Object slug = pageMap.get("slug");
            if (slug != null && pageSlug.equalsIgnoreCase(slug.toString())) {
                String title = pageMap.get("title") != null
                        ? pageMap.get("title").toString()
                        : pageSlug;
                @SuppressWarnings("unchecked")
                Map<String, Object> pageData = (Map<String, Object>) pageMap;
                return PublicPageDto.builder()
                        .slug(pageSlug)
                        .title(title)
                        .page(pageData)
                        .build();
            }
        }

        throw new PublicPageNotFoundException("Page not found: " + pageSlug);
    }

    private SeoDto buildStoreSeo(Workspace workspace, Map<String, Object> config) {
        String title = workspace.getSeoTitle() != null && !workspace.getSeoTitle().isBlank()
                ? workspace.getSeoTitle()
                : workspace.getName();
        String description = workspace.getSeoDescription() != null && !workspace.getSeoDescription().isBlank()
                ? workspace.getSeoDescription()
                : "Shop products from " + workspace.getName();

        String imageUrl = null;
        if (workspace.getSeoImage() != null
                && workspace.getSeoImage().getStatus() == MediaStatus.READY) {
            imageUrl = workspace.getSeoImage().getUrl();
        } else {
            imageUrl = extractHeroImageUrl(config);
        }

        return SeoDto.builder()
                .title(title)
                .description(description)
                .imageUrl(imageUrl)
                .build();
    }

    @SuppressWarnings("unchecked")
    private String extractHeroImageUrl(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        Object direct = config.get("heroBackgroundImageUrl");
        if (direct != null && !direct.toString().isBlank()) {
            return direct.toString();
        }
        Object sectionsObj = config.get("sections");
        if (!(sectionsObj instanceof List<?> sections)) {
            return null;
        }
        for (Object sectionObj : sections) {
            if (!(sectionObj instanceof Map<?, ?> section)) {
                continue;
            }
            if (!"hero".equals(String.valueOf(section.get("type")))) {
                continue;
            }
            Object contentObj = section.get("content");
            if (contentObj instanceof Map<?, ?> content) {
                Object imageUrl = content.get("imageUrl");
                if (imageUrl == null) {
                    imageUrl = content.get("backgroundImageUrl");
                }
                if (imageUrl != null && !imageUrl.toString().isBlank()) {
                    return imageUrl.toString();
                }
            }
            Object imageUrl = section.get("imageUrl");
            if (imageUrl != null && !imageUrl.toString().isBlank()) {
                return imageUrl.toString();
            }
        }
        return null;
    }

    private ProductDto toPublicProductDto(Product product) {
        List<ProductImage> gallery = productImageRepository.findByProductIdOrderBySortOrderAsc(product.getId());
        List<UUID> galleryMediaIds = gallery.stream()
                .map(img -> img.getMedia().getId())
                .collect(Collectors.toList());
        List<String> galleryUrls = !gallery.isEmpty()
                ? gallery.stream().map(img -> img.getMedia().getUrl()).collect(Collectors.toList())
                : product.getGalleryUrls();

        String imageUrl = product.getMainImage() != null
                && product.getMainImage().getStatus() == MediaStatus.READY
                ? product.getMainImage().getUrl()
                : product.getImageUrl();

        boolean onSale = normalizationHelper.isOnSale(
                product.getCompareAtPriceAmount(), product.getPriceAmount());

        return ProductDto.builder()
                .id(product.getId())
                .workspaceId(product.getWorkspace().getId())
                .title(product.getTitle())
                .slug(product.getSlug())
                .sku(product.getSku())
                .priceAmount(product.getPriceAmount())
                .compareAtPriceAmount(product.getCompareAtPriceAmount())
                .currency(product.getCurrency())
                .priceLabel(normalizationHelper.formatPriceLabel(product.getPriceAmount(), product.getCurrency()))
                .compareAtPriceLabel(onSale
                        ? normalizationHelper.formatPriceLabel(
                                product.getCompareAtPriceAmount(), product.getCurrency())
                        : null)
                .onSale(onSale)
                .quantityAvailable(product.getQuantityAvailable() != null ? product.getQuantityAvailable() : 0)
                .inStock(product.getQuantityAvailable() != null && product.getQuantityAvailable() > 0)
                .category(product.getCategory() != null ? categoryService.toDto(product.getCategory()) : null)
                .status(product.getStatus())
                .mainImageId(product.getMainImage() != null ? product.getMainImage().getId() : null)
                .imageUrl(imageUrl)
                .summary(product.getDescription())
                .galleryMediaIds(galleryMediaIds.isEmpty() ? null : galleryMediaIds)
                .galleryUrls(galleryUrls)
                .configurationLabel(product.getConfigurationLabel())
                .warrantyNote(product.getWarrantyNote())
                .shippingNote(product.getShippingNote())
                .metadata(product.getMetadata())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
