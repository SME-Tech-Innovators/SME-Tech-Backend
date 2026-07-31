package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.request.CreateProductRequest;
import sme.tech.innovators.sme.dto.request.UpdateProductRequest;
import sme.tech.innovators.sme.dto.response.PageResponse;
import sme.tech.innovators.sme.dto.response.ProductDto;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.*;
import sme.tech.innovators.sme.repository.CategoryRepository;
import sme.tech.innovators.sme.repository.ProductImageRepository;
import sme.tech.innovators.sme.repository.ProductRepository;
import sme.tech.innovators.sme.repository.WorkspaceRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;
    private final WorkspaceRepository workspaceRepository;
    private final CategoryService categoryService;
    private final MediaService mediaService;
    private final SlugGeneratorService slugGeneratorService;
    private final ProductNormalizationHelper normalizationHelper;
    private final OutOfStockMailer outOfStockMailer;

    @Transactional(readOnly = true)
    public PageResponse<ProductDto> listProducts(UUID workspaceId,
                                                  UUID userId,
                                                  String status,
                                                  UUID categoryId,
                                                  String search,
                                                  Boolean onSale,
                                                  Boolean inStock,
                                                  String sort,
                                                  int page,
                                                  int limit) {
        loadOwnedWorkspace(workspaceId, userId);

        int safePage = Math.max(page, 0);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        ProductStatus statusFilter = parseStatusOrNull(status);

        Page<Product> result = productRepository.search(
                workspaceId,
                statusFilter,
                categoryId,
                search,
                onSale,
                inStock,
                buildMerchantPageable(safePage, safeLimit, sort)
        );

        List<ProductDto> items = result.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return PageResponse.<ProductDto>builder()
                .items(items)
                .page(safePage)
                .limit(safeLimit)
                .totalItems(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional
    public ProductDto createProduct(UUID workspaceId, UUID userId, CreateProductRequest request) {
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);

        String title = requireNonBlank(request.getTitle(), "title");
        String sku = requireNonBlank(request.getSku(), "sku").trim();
        normalizationHelper.validatePriceAmount(request.getPriceAmount());
        normalizationHelper.validateCompareAtPriceAmount(
                request.getCompareAtPriceAmount(), request.getPriceAmount());
        String currency = normalizationHelper.normalizeCurrency(request.getCurrency());
        normalizationHelper.validateOptionalUrl(request.getImageUrl(), "imageUrl");
        normalizationHelper.validateGalleryUrls(request.getGalleryUrls());

        if (productRepository.existsByWorkspaceIdAndSkuIgnoreCase(workspaceId, sku)) {
            throw new ProductSkuExistsException("SKU already exists in this workspace: " + sku);
        }

        String slug = resolveUniqueProductSlug(workspaceId, request.getSlug(), title, null);
        ProductStatus status = request.getStatus() != null ? request.getStatus() : ProductStatus.DRAFT;
        Category category = resolveCategory(workspace, request.getCategoryId(), request.getCategoryName());

        if (status == ProductStatus.ACTIVE) {
            validateActiveRequirements(title, sku, request.getPriceAmount(), currency,
                    request.getQuantityAvailable());
        }

        MediaAsset mainImage = null;
        if (request.getMainImageId() != null) {
            mainImage = mediaService.requireReadyMedia(workspaceId, request.getMainImageId());
        }

        int quantityAvailable = requireQuantityAvailable(request.getQuantityAvailable());

        Product product = Product.builder()
                .workspace(workspace)
                .category(category)
                .title(title.trim())
                .slug(slug)
                .sku(sku)
                .description(request.getSummary())
                .priceAmount(request.getPriceAmount())
                .compareAtPriceAmount(request.getCompareAtPriceAmount())
                .currency(currency)
                .quantityAvailable(quantityAvailable)
                .status(status)
                .mainImage(mainImage)
                .imageUrl(mainImage != null ? mainImage.getUrl() : blankToNull(request.getImageUrl()))
                .galleryUrls(request.getGalleryUrls())
                .configurationLabel(request.getConfigurationLabel())
                .warrantyNote(request.getWarrantyNote())
                .shippingNote(request.getShippingNote())
                .metadata(request.getMetadata())
                .build();

        product = productRepository.save(product);

        if (request.getGalleryMediaIds() != null && !request.getGalleryMediaIds().isEmpty()) {
            replaceGalleryMedia(product, workspaceId, request.getGalleryMediaIds());
        }

        log.info("Created product={} in workspace={}", product.getId(), workspaceId);
        return toDto(product);
    }

    @Transactional(readOnly = true)
    public ProductDto getProduct(UUID workspaceId, UUID productId, UUID userId) {
        loadOwnedWorkspace(workspaceId, userId);
        return toDto(loadOwnedProduct(workspaceId, productId));
    }

    @Transactional
    public ProductDto updateProduct(UUID workspaceId,
                                     UUID productId,
                                     UUID userId,
                                     UpdateProductRequest request) {
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);
        Product product = loadOwnedProduct(workspaceId, productId);

        if (request.getTitle() != null) {
            product.setTitle(requireNonBlank(request.getTitle(), "title").trim());
        }

        if (request.getSku() != null) {
            String sku = requireNonBlank(request.getSku(), "sku").trim();
            if (productRepository.existsByWorkspaceIdAndSkuIgnoreCaseAndIdNot(workspaceId, sku, productId)) {
                throw new ProductSkuExistsException("SKU already exists in this workspace: " + sku);
            }
            product.setSku(sku);
        }

        if (request.getSlug() != null) {
            String slug = resolveUniqueProductSlug(workspaceId, request.getSlug(), product.getTitle(), productId);
            product.setSlug(slug);
        }

        if (request.getPriceAmount() != null) {
            normalizationHelper.validatePriceAmount(request.getPriceAmount());
            product.setPriceAmount(request.getPriceAmount());
        }

        boolean soldOutViaPatch = false;
        if (request.getQuantityAvailable() != null) {
            int previousQty = product.getQuantityAvailable() != null ? product.getQuantityAvailable() : 0;
            int newQty = requireQuantityAvailable(request.getQuantityAvailable());
            product.setQuantityAvailable(newQty);
            if (newQty > 0) {
                product.setOutOfStockNotifiedAt(null);
            } else if (previousQty > 0) {
                soldOutViaPatch = true;
            }
        }

        if (Boolean.TRUE.equals(request.getClearCompareAtPrice())) {
            product.setCompareAtPriceAmount(null);
        } else if (request.getCompareAtPriceAmount() != null) {
            product.setCompareAtPriceAmount(request.getCompareAtPriceAmount());
        }

        // Re-validate compare-at against the effective selling price after any price changes.
        normalizationHelper.validateCompareAtPriceAmount(
                product.getCompareAtPriceAmount(), product.getPriceAmount());

        if (request.getCurrency() != null) {
            product.setCurrency(normalizationHelper.normalizeCurrency(request.getCurrency()));
        }

        if (Boolean.TRUE.equals(request.getClearCategory())) {
            product.setCategory(null);
        } else if (request.getCategoryId() != null || (request.getCategoryName() != null && !request.getCategoryName().isBlank())) {
            product.setCategory(resolveCategory(workspace, request.getCategoryId(), request.getCategoryName()));
        }

        if (request.getStatus() != null) {
            product.setStatus(request.getStatus());
        }

        if (Boolean.TRUE.equals(request.getClearMainImage())) {
            product.setMainImage(null);
        } else if (request.getMainImageId() != null) {
            MediaAsset mainImage = mediaService.requireReadyMedia(workspaceId, request.getMainImageId());
            product.setMainImage(mainImage);
            product.setImageUrl(mainImage.getUrl());
        }

        if (request.getImageUrl() != null && request.getMainImageId() == null && !Boolean.TRUE.equals(request.getClearMainImage())) {
            normalizationHelper.validateOptionalUrl(request.getImageUrl(), "imageUrl");
            product.setImageUrl(blankToNull(request.getImageUrl()));
        }

        if (request.getSummary() != null) {
            product.setDescription(request.getSummary());
        }

        if (request.getGalleryUrls() != null) {
            normalizationHelper.validateGalleryUrls(request.getGalleryUrls());
            product.setGalleryUrls(request.getGalleryUrls());
        }

        if (request.getGalleryMediaIds() != null) {
            replaceGalleryMedia(product, workspaceId, request.getGalleryMediaIds());
        }

        if (request.getConfigurationLabel() != null) {
            product.setConfigurationLabel(request.getConfigurationLabel());
        }
        if (request.getWarrantyNote() != null) {
            product.setWarrantyNote(request.getWarrantyNote());
        }
        if (request.getShippingNote() != null) {
            product.setShippingNote(request.getShippingNote());
        }
        if (request.getMetadata() != null) {
            product.setMetadata(request.getMetadata());
        }

        if (product.getStatus() == ProductStatus.ACTIVE) {
            validateActiveRequirements(
                    product.getTitle(),
                    product.getSku(),
                    product.getPriceAmount(),
                    product.getCurrency(),
                    product.getQuantityAvailable()
            );
        }

        product = productRepository.save(product);
        if (soldOutViaPatch) {
            outOfStockMailer.notifyIfSoldOut(product.getId());
        }
        return toDto(product);
    }

    @Transactional
    public ProductDto archiveProduct(UUID workspaceId, UUID productId, UUID userId) {
        loadOwnedWorkspace(workspaceId, userId);
        Product product = loadOwnedProduct(workspaceId, productId);
        product.setStatus(ProductStatus.ARCHIVED);
        return toDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto publishProduct(UUID workspaceId, UUID productId, UUID userId) {
        loadOwnedWorkspace(workspaceId, userId);
        Product product = loadOwnedProduct(workspaceId, productId);
        validateActiveRequirements(
                product.getTitle(),
                product.getSku(),
                product.getPriceAmount(),
                product.getCurrency(),
                product.getQuantityAvailable()
        );
        product.setStatus(ProductStatus.ACTIVE);
        return toDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto draftProduct(UUID workspaceId, UUID productId, UUID userId) {
        loadOwnedWorkspace(workspaceId, userId);
        Product product = loadOwnedProduct(workspaceId, productId);
        product.setStatus(ProductStatus.DRAFT);
        return toDto(productRepository.save(product));
    }

    private void replaceGalleryMedia(Product product, UUID workspaceId, List<UUID> galleryMediaIds) {
        productImageRepository.deleteByProductId(product.getId());
        List<String> urls = new ArrayList<>();
        int order = 0;
        for (UUID mediaId : galleryMediaIds) {
            MediaAsset media = mediaService.requireReadyMedia(workspaceId, mediaId);
            ProductImage image = ProductImage.builder()
                    .product(product)
                    .media(media)
                    .imageUrl(media.getUrl())
                    .sortOrder(order++)
                    .build();
            productImageRepository.save(image);
            urls.add(media.getUrl());
        }
        product.setGalleryUrls(urls);
        productRepository.save(product);
    }

    private Category resolveCategory(Workspace workspace, UUID categoryId, String categoryName) {
        if (categoryId != null) {
            return categoryRepository.findByIdAndWorkspaceId(categoryId, workspace.getId())
                    .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + categoryId));
        }
        if (categoryName != null && !categoryName.isBlank()) {
            return categoryService.findOrCreateByName(workspace, categoryName);
        }
        return null;
    }

    private String resolveUniqueProductSlug(UUID workspaceId,
                                             String requestedSlug,
                                             String title,
                                             UUID excludeId) {
        String base = slugGeneratorService.sanitizeSlug(
                requestedSlug != null && !requestedSlug.isBlank() ? requestedSlug : title);
        if (base.isBlank()) {
            throw new InvalidProductDataException("Unable to generate product slug from title");
        }

        String candidate = base;
        for (int i = 0; i < 50; i++) {
            boolean exists = excludeId == null
                    ? productRepository.existsByWorkspaceIdAndSlug(workspaceId, candidate)
                    : productRepository.existsByWorkspaceIdAndSlugAndIdNot(workspaceId, candidate, excludeId);
            if (!exists) {
                return candidate;
            }
            if (i == 0 && requestedSlug != null && !requestedSlug.isBlank()) {
                throw new ProductSlugExistsException("Product slug already exists: " + requestedSlug);
            }
            candidate = base + "-" + (i + 1);
        }
        throw new ProductSlugExistsException("Unable to generate a unique product slug");
    }

    private void validateActiveRequirements(String title,
                                             String sku,
                                             Integer priceAmount,
                                             String currency,
                                             Integer quantityAvailable) {
        if (title == null || title.isBlank()) {
            throw new InvalidProductDataException("Active products require a title");
        }
        if (sku == null || sku.isBlank()) {
            throw new InvalidProductDataException("Active products require a SKU");
        }
        normalizationHelper.validatePriceAmount(priceAmount);
        normalizationHelper.normalizeCurrency(currency);
        requireQuantityAvailable(quantityAvailable);
    }

    private int requireQuantityAvailable(Integer quantityAvailable) {
        if (quantityAvailable == null) {
            throw new InvalidProductDataException("quantityAvailable is required");
        }
        if (quantityAvailable < 0) {
            throw new InvalidProductDataException("quantityAvailable must be >= 0");
        }
        return quantityAvailable;
    }

    private ProductStatus parseStatusOrNull(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ProductStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidProductStatusException(
                    "Invalid product status '" + status + "'. Allowed: draft, active, archived");
        }
    }

    /** Merchant sort: newest | updated | price_asc | price_desc (default updated). */
    Pageable buildMerchantPageable(int page, int limit, String sort) {
        return PageRequest.of(page, limit, resolveMerchantSort(sort));
    }

    /** Public sort: newest | price_asc | price_desc (default updated for backwards compatibility). */
    Pageable buildPublicPageable(int page, int limit, String sort) {
        return PageRequest.of(page, limit, resolvePublicSort(sort));
    }

    private Sort resolveMerchantSort(String sort) {
        String key = sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "priceAmount");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "priceAmount");
            case "updated", "" -> Sort.by(Sort.Direction.DESC, "updatedAt");
            default -> throw new InvalidProductDataException(
                    "Invalid sort '" + sort + "'. Allowed: newest, updated, price_asc, price_desc");
        };
    }

    private Sort resolvePublicSort(String sort) {
        String key = sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "priceAmount");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "priceAmount");
            case "" -> Sort.by(Sort.Direction.DESC, "updatedAt");
            default -> throw new InvalidProductDataException(
                    "Invalid sort '" + sort + "'. Allowed: newest, price_asc, price_desc");
        };
    }

    private String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidProductDataException(field + " is required");
        }
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Workspace loadOwnedWorkspace(UUID workspaceId, UUID userId) {
        return workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Workspace not found or you do not have access to it"));
    }

    private Product loadOwnedProduct(UUID workspaceId, UUID productId) {
        return productRepository.findByIdAndWorkspaceId(productId, workspaceId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));
    }

    private ProductDto toDto(Product product) {
        List<ProductImage> gallery = productImageRepository.findByProductIdOrderBySortOrderAsc(product.getId());
        List<UUID> galleryMediaIds = gallery.stream()
                .map(img -> img.getMedia().getId())
                .collect(Collectors.toList());
        List<String> galleryUrls = !gallery.isEmpty()
                ? gallery.stream().map(img -> img.getMedia().getUrl()).collect(Collectors.toList())
                : product.getGalleryUrls();

        String imageUrl = product.getMainImage() != null
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
}
