package sme.tech.innovators.sme.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sme.tech.innovators.sme.dto.response.ApiResponse;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WorkspaceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleWorkspaceNotFound(WorkspaceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCodes.WORKSPACE_NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(StorefrontNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorefrontNotFound(StorefrontNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCodes.STOREFRONT_NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(TemplateNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleTemplateNotFound(TemplateNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCodes.TEMPLATE_NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(TemplateDisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleTemplateDisabled(TemplateDisabledException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ErrorCodes.TEMPLATE_DISABLED, ex.getMessage()));
    }

    @ExceptionHandler(InvalidStorefrontConfigException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidStorefrontConfig(InvalidStorefrontConfigException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCodes.INVALID_STOREFRONT_CONFIG, ex.getMessage()));
    }

    @ExceptionHandler(PublishConfirmationRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handlePublishConfirmationRequired(PublishConfirmationRequiredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCodes.PUBLISH_CONFIRMATION_REQUIRED, ex.getMessage()));
    }

    @ExceptionHandler(StorefrontDraftNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorefrontDraftNotFound(StorefrontDraftNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCodes.STOREFRONT_DRAFT_NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(PublishedStorefrontNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePublishedStorefrontNotFound(PublishedStorefrontNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCodes.PUBLISHED_STOREFRONT_NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(InvalidPublishConfigException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidPublishConfig(InvalidPublishConfigException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCodes.INVALID_PUBLISH_CONFIG, ex.getMessage()));
    }

    @ExceptionHandler(PublicSlugUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handlePublicSlugUnavailable(PublicSlugUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCodes.PUBLIC_SLUG_UNAVAILABLE, ex.getMessage()));
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleProductNotFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCodes.PRODUCT_NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(ProductSkuExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleProductSkuExists(ProductSkuExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCodes.PRODUCT_SKU_EXISTS, ex.getMessage()));
    }

    @ExceptionHandler(ProductSlugExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleProductSlugExists(ProductSlugExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCodes.PRODUCT_SLUG_EXISTS, ex.getMessage()));
    }

    @ExceptionHandler(InvalidProductStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidProductStatus(InvalidProductStatusException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCodes.INVALID_PRODUCT_STATUS, ex.getMessage()));
    }

    @ExceptionHandler(InvalidProductPriceException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidProductPrice(InvalidProductPriceException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCodes.INVALID_PRODUCT_PRICE, ex.getMessage()));
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCategoryNotFound(CategoryNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCodes.CATEGORY_NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(InvalidProductDataException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidProductData(InvalidProductDataException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCodes.INVALID_PRODUCT_DATA, ex.getMessage()));
    }

    @ExceptionHandler(MediaNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaNotFound(MediaNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCodes.MEDIA_NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(InvalidMediaTypeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidMediaType(InvalidMediaTypeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCodes.INVALID_MEDIA_TYPE, ex.getMessage()));
    }

    @ExceptionHandler(MediaTooLargeException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTooLarge(MediaTooLargeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCodes.MEDIA_TOO_LARGE, ex.getMessage()));
    }

    @ExceptionHandler(UploadUrlFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadUrlFailed(UploadUrlFailedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(ErrorCodes.UPLOAD_URL_FAILED, ex.getMessage()));
    }

    @ExceptionHandler(MediaNotReadyException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaNotReady(MediaNotReadyException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCodes.MEDIA_NOT_READY, ex.getMessage()));
    }

    @ExceptionHandler(MediaAlreadyDeletedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaAlreadyDeleted(MediaAlreadyDeletedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCodes.MEDIA_ALREADY_DELETED, ex.getMessage()));
    }

    @ExceptionHandler(StoreNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleStoreNotFound(StoreNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCodes.STORE_NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(StoreNotAvailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleStoreNotAvailable(StoreNotAvailableException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCodes.STORE_NOT_AVAILABLE, ex.getMessage()));
    }

    @ExceptionHandler(PublicStorefrontNotPublishedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePublicStorefrontNotPublished(PublicStorefrontNotPublishedException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCodes.PUBLIC_STOREFRONT_NOT_PUBLISHED, ex.getMessage()));
    }

    @ExceptionHandler(PublicProductNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePublicProductNotFound(PublicProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCodes.PUBLIC_PRODUCT_NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(PublicPageNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePublicPageNotFound(PublicPageNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCodes.PUBLIC_PAGE_NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCodes.EMAIL_ALREADY_EXISTS, ex.getMessage()));
    }

    @ExceptionHandler(AlreadyVerifiedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAlreadyVerified(AlreadyVerifiedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCodes.ACCOUNT_ALREADY_VERIFIED, ex.getMessage()));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidToken(InvalidTokenException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCodes.INVALID_TOKEN, ex.getMessage()));
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenExpired(TokenExpiredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCodes.TOKEN_EXPIRED, ex.getMessage()));
    }

    @ExceptionHandler(TokenRevokedException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenRevoked(TokenRevokedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCodes.TOKEN_REVOKED, ex.getMessage()));
    }

    @ExceptionHandler(AccountNotVerifiedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountNotVerified(AccountNotVerifiedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCodes.ACCESS_DENIED, ex.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(ErrorCodes.RATE_LIMIT_EXCEEDED, ex.getMessage()));
    }

    @ExceptionHandler(BusinessAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessAccessDenied(BusinessAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCodes.ACCESS_DENIED, ex.getMessage()));
    }

    @ExceptionHandler(PasswordValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handlePasswordValidation(PasswordValidationException ex) {
        String message = String.join("; ", ex.getErrors());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCodes.INVALID_PASSWORD, message));
    }

    @ExceptionHandler(InvalidSlugException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidSlug(InvalidSlugException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCodes.VALIDATION_FAILED, ex.getMessage()));
    }

    @ExceptionHandler(SlugGenerationException.class)
    public ResponseEntity<ApiResponse<Void>> handleSlugGeneration(SlugGenerationException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCodes.SLUG_GENERATION_FAILED, ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCodes.INVALID_CREDENTIALS, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCodes.VALIDATION_FAILED, message));
    }

    @ExceptionHandler(CartNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCartNotFound(CartNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCodes.CART_NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(CartItemNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCartItemNotFound(CartItemNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCodes.CART_ITEM_NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(CartEmptyException.class)
    public ResponseEntity<ApiResponse<Void>> handleCartEmpty(CartEmptyException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCodes.CART_EMPTY, ex.getMessage()));
    }

    @ExceptionHandler(ProductNotAvailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleProductNotAvailable(ProductNotAvailableException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ErrorCodes.PRODUCT_NOT_AVAILABLE, ex.getMessage()));
    }

    @ExceptionHandler(InvalidQuantityException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidQuantity(InvalidQuantityException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCodes.INVALID_QUANTITY, ex.getMessage()));
    }

    @ExceptionHandler(CheckoutValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleCheckoutValidation(CheckoutValidationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ErrorCodes.CHECKOUT_VALIDATION_ERROR, ex.getMessage()));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCodes.ORDER_NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCodes.INTERNAL_ERROR, "An unexpected error occurred"));
    }
}
