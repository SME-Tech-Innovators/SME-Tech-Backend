package sme.tech.innovators.sme.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sme.tech.innovators.sme.dto.request.CreateCategoryRequest;
import sme.tech.innovators.sme.dto.request.CreateProductRequest;
import sme.tech.innovators.sme.dto.request.UpdateCategoryRequest;
import sme.tech.innovators.sme.dto.request.UpdateProductRequest;
import sme.tech.innovators.sme.dto.response.ApiResponse;
import sme.tech.innovators.sme.dto.response.CategoryDto;
import sme.tech.innovators.sme.dto.response.PageResponse;
import sme.tech.innovators.sme.dto.response.ProductDto;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.CategoryService;
import sme.tech.innovators.sme.service.ProductService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Products", description = "Merchant product catalog and category management")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final UserRepository userRepository;

    @Operation(summary = "List products")
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<PageResponse<ProductDto>>> listProducts(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean onSale,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                productService.listProducts(
                        workspaceId, resolveUserId(auth), status, categoryId, search, onSale, inStock, sort, page, limit)));
    }

    @Operation(summary = "Create product")
    @PostMapping("/products")
    public ResponseEntity<ApiResponse<ProductDto>> createProduct(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateProductRequest request,
            Authentication auth) {
        ProductDto created = productService.createProduct(workspaceId, resolveUserId(auth), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @Operation(summary = "Get product")
    @GetMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<ProductDto>> getProduct(
            @PathVariable UUID workspaceId,
            @PathVariable UUID productId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                productService.getProduct(workspaceId, productId, resolveUserId(auth))));
    }

    @Operation(summary = "Update product (partial)")
    @PatchMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<ProductDto>> updateProduct(
            @PathVariable UUID workspaceId,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                productService.updateProduct(workspaceId, productId, resolveUserId(auth), request)));
    }

    @Operation(summary = "Archive product")
    @PostMapping("/products/{productId}/archive")
    public ResponseEntity<ApiResponse<ProductDto>> archiveProduct(
            @PathVariable UUID workspaceId,
            @PathVariable UUID productId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                productService.archiveProduct(workspaceId, productId, resolveUserId(auth))));
    }

    @Operation(summary = "Publish product (set active)")
    @PostMapping("/products/{productId}/publish")
    public ResponseEntity<ApiResponse<ProductDto>> publishProduct(
            @PathVariable UUID workspaceId,
            @PathVariable UUID productId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                productService.publishProduct(workspaceId, productId, resolveUserId(auth))));
    }

    @Operation(summary = "Return product to draft")
    @PostMapping("/products/{productId}/draft")
    public ResponseEntity<ApiResponse<ProductDto>> draftProduct(
            @PathVariable UUID workspaceId,
            @PathVariable UUID productId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                productService.draftProduct(workspaceId, productId, resolveUserId(auth))));
    }

    @Operation(summary = "List categories")
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> listCategories(
            @PathVariable UUID workspaceId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                categoryService.listCategories(workspaceId, resolveUserId(auth))));
    }

    @Operation(summary = "Create category")
    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<CategoryDto>> createCategory(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateCategoryRequest request,
            Authentication auth) {
        CategoryDto created = categoryService.createCategory(workspaceId, resolveUserId(auth), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @Operation(summary = "Update category")
    @PatchMapping("/categories/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryDto>> updateCategory(
            @PathVariable UUID workspaceId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                categoryService.updateCategory(workspaceId, categoryId, resolveUserId(auth), request)));
    }

    private UUID resolveUserId(Authentication auth) {
        String email = auth.getName();
        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new WorkspaceNotFoundException("Authenticated user not found: " + email));
        return user.getId();
    }
}
