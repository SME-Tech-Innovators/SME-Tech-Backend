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
}
