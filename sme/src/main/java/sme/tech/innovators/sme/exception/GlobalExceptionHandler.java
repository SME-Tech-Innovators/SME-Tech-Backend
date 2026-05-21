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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCodes.INTERNAL_ERROR, "An unexpected error occurred"));
    }
}
