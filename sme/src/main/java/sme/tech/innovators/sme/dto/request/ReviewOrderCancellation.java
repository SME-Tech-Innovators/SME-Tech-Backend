package sme.tech.innovators.sme.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewOrderCancellation(
        @NotBlank @Pattern(regexp = "approve|reject") String decision,
        @Size(max = 500) String note) {}
