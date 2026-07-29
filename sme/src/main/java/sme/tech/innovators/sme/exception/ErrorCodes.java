package sme.tech.innovators.sme.exception;

public final class ErrorCodes {

    private ErrorCodes() {}

    public static final String EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String INVALID_PASSWORD = "INVALID_PASSWORD";
    public static final String SLUG_GENERATION_FAILED = "SLUG_GENERATION_FAILED";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String TOKEN_REVOKED = "TOKEN_REVOKED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String ACCOUNT_ALREADY_DELETED = "ACCOUNT_ALREADY_DELETED";
    public static final String ACCOUNT_ALREADY_VERIFIED = "ACCOUNT_ALREADY_VERIFIED";

    // Storefront / workspace
    public static final String WORKSPACE_NOT_FOUND = "WORKSPACE_NOT_FOUND";
    public static final String STOREFRONT_NOT_FOUND = "STOREFRONT_NOT_FOUND";
    public static final String TEMPLATE_NOT_FOUND = "TEMPLATE_NOT_FOUND";
    public static final String TEMPLATE_DISABLED = "TEMPLATE_DISABLED";
    public static final String INVALID_STOREFRONT_CONFIG = "INVALID_STOREFRONT_CONFIG";

    // Publish / Go Live
    public static final String PUBLISH_CONFIRMATION_REQUIRED = "PUBLISH_CONFIRMATION_REQUIRED";
    public static final String STOREFRONT_DRAFT_NOT_FOUND = "STOREFRONT_DRAFT_NOT_FOUND";
    public static final String PUBLISHED_STOREFRONT_NOT_FOUND = "PUBLISHED_STOREFRONT_NOT_FOUND";
    public static final String INVALID_PUBLISH_CONFIG = "INVALID_PUBLISH_CONFIG";
    public static final String PUBLIC_SLUG_UNAVAILABLE = "PUBLIC_SLUG_UNAVAILABLE";
    public static final String PUBLISH_FAILED = "PUBLISH_FAILED";

    // Product catalog
    public static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
    public static final String PRODUCT_SKU_EXISTS = "PRODUCT_SKU_EXISTS";
    public static final String PRODUCT_SLUG_EXISTS = "PRODUCT_SLUG_EXISTS";
    public static final String INVALID_PRODUCT_STATUS = "INVALID_PRODUCT_STATUS";
    public static final String INVALID_PRODUCT_PRICE = "INVALID_PRODUCT_PRICE";
    public static final String CATEGORY_NOT_FOUND = "CATEGORY_NOT_FOUND";
    public static final String INVALID_PRODUCT_DATA = "INVALID_PRODUCT_DATA";

    // Media
    public static final String MEDIA_NOT_FOUND = "MEDIA_NOT_FOUND";
    public static final String INVALID_MEDIA_TYPE = "INVALID_MEDIA_TYPE";
    public static final String MEDIA_TOO_LARGE = "MEDIA_TOO_LARGE";
    public static final String UPLOAD_URL_FAILED = "UPLOAD_URL_FAILED";
    public static final String MEDIA_NOT_READY = "MEDIA_NOT_READY";
    public static final String MEDIA_ALREADY_DELETED = "MEDIA_ALREADY_DELETED";

    // Cart & Checkout
    public static final String CART_NOT_FOUND = "CART_NOT_FOUND";
    public static final String CART_ITEM_NOT_FOUND = "CART_ITEM_NOT_FOUND";
    public static final String CART_EMPTY = "CART_EMPTY";
    public static final String PRODUCT_NOT_AVAILABLE = "PRODUCT_NOT_AVAILABLE";
    public static final String INVALID_QUANTITY = "INVALID_QUANTITY";
    public static final String CHECKOUT_VALIDATION_ERROR = "CHECKOUT_VALIDATION_ERROR";
    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";

    // Public storefront
    public static final String STORE_NOT_FOUND = "STORE_NOT_FOUND";
    public static final String STORE_NOT_AVAILABLE = "STORE_NOT_AVAILABLE";
    public static final String PUBLIC_STOREFRONT_NOT_PUBLISHED = "PUBLIC_STOREFRONT_NOT_PUBLISHED";
    public static final String PUBLIC_PRODUCT_NOT_FOUND = "PUBLIC_PRODUCT_NOT_FOUND";
    public static final String PUBLIC_PAGE_NOT_FOUND = "PUBLIC_PAGE_NOT_FOUND";
}
