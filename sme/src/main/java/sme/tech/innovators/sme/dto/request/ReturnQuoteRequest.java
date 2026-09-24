package sme.tech.innovators.sme.dto.request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record ReturnQuoteRequest(
    @NotBlank @Size(max=1000) String reason,
    @NotBlank @Size(max=255) String merchantContactName,
    @NotBlank @Email @Size(max=255) String merchantContactEmail,
    @NotBlank @Size(max=50) String merchantContactPhone,
    @NotEmpty @Size(max=50) List<@Valid Parcel> parcels) {
    public record Parcel(@NotBlank @Size(max=255) String description,
        @NotNull @DecimalMin("0.01") BigDecimal lengthCm,
        @NotNull @DecimalMin("0.01") BigDecimal widthCm,
        @NotNull @DecimalMin("0.01") BigDecimal heightCm,
        @NotNull @DecimalMin("0.01") BigDecimal weightKg) {}
}
