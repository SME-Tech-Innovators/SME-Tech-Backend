package sme.tech.innovators.sme.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sme.tech.innovators.sme.dto.response.*;
import sme.tech.innovators.sme.entity.Business;
import sme.tech.innovators.sme.exception.StoreNotFoundException;
import sme.tech.innovators.sme.repository.BusinessRepository;
import sme.tech.innovators.sme.service.PublicStorefrontService;

@Tag(name = "Public Store", description = "Public customer-facing storefront and product APIs — no authentication required")
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicStoreController {

    private final BusinessRepository businessRepository;
    private final PublicStorefrontService publicStorefrontService;

    @Operation(summary = "Get business by slug (legacy)",
               description = "Returns public business information. Prefer /public/storefronts/{storeSlug} for live storefronts.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Business found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Business not found")
    })
    @GetMapping("/store/{slug}")
    public ResponseEntity<ApiResponse<PublicBusinessDto>> getStore(@PathVariable String slug) {
        Business business = businessRepository.findBySlugAndIsDeletedFalse(slug)
                .orElseThrow(() -> new StoreNotFoundException("Business not found for slug: " + slug));

        PublicBusinessDto dto = PublicBusinessDto.builder()
                .name(business.getName())
                .slug(business.getSlug())
                .description(business.getDescription())
                .publicLink(business.getPublicLink())
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(summary = "Get published storefront by public slug")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Live storefront returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Store unpublished or suspended"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Store not found or not published")
    })
    @GetMapping("/storefronts/{storeSlug}")
    public ResponseEntity<ApiResponse<PublicStorefrontDto>> getPublicStorefront(@PathVariable String storeSlug) {
        return ResponseEntity.ok(ApiResponse.success(publicStorefrontService.getPublicStorefront(storeSlug)));
    }

    @Operation(summary = "List active products for a live storefront")
    @GetMapping("/storefronts/{storeSlug}/products")
    public ResponseEntity<ApiResponse<PageResponse<ProductDto>>> getPublicProducts(
            @PathVariable String storeSlug,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean onSale,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                publicStorefrontService.getPublicProducts(
                        storeSlug, category, search, onSale, sort, page, limit)));
    }

    @Operation(summary = "Get one active product by slug")
    @GetMapping("/storefronts/{storeSlug}/products/{productSlug}")
    public ResponseEntity<ApiResponse<ProductDto>> getPublicProduct(
            @PathVariable String storeSlug,
            @PathVariable String productSlug) {
        return ResponseEntity.ok(ApiResponse.success(
                publicStorefrontService.getPublicProduct(storeSlug, productSlug)));
    }

    @Operation(summary = "Get a custom page from the published storefront snapshot")
    @GetMapping("/storefronts/{storeSlug}/pages/{pageSlug}")
    public ResponseEntity<ApiResponse<PublicPageDto>> getPublicPage(
            @PathVariable String storeSlug,
            @PathVariable String pageSlug) {
        return ResponseEntity.ok(ApiResponse.success(
                publicStorefrontService.getPublicPage(storeSlug, pageSlug)));
    }
}
