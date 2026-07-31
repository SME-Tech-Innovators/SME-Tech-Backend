package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorDetail error;
    private String timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .error(null)
                .timestamp(Instant.now().toString())
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .data(null)
                .error(ErrorDetail.builder().code(code).message(message).build())
                .timestamp(Instant.now().toString())
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message, Integer availableQuantity) {
        return ApiResponse.<T>builder()
                .success(false)
                .data(null)
                .error(ErrorDetail.builder()
                        .code(code)
                        .message(message)
                        .availableQuantity(availableQuantity)
                        .build())
                .timestamp(Instant.now().toString())
                .build();
    }

    @Data
    @Builder
    public static class ErrorDetail {
        private String code;
        private String message;
        /** Optional — e.g. remaining stock for INSUFFICIENT_STOCK. */
        private Integer availableQuantity;
    }
}
