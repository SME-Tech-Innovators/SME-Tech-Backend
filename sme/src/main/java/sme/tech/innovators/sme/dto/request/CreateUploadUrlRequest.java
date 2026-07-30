package sme.tech.innovators.sme.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateUploadUrlRequest {

    @NotBlank(message = "filename is required")
    private String filename;

    @NotBlank(message = "mimeType is required")
    private String mimeType;

    @NotNull(message = "sizeBytes is required")
    @Min(value = 1, message = "sizeBytes must be > 0")
    private Long sizeBytes;
}
