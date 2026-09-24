package sme.tech.innovators.sme.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestOrderCancellation(@NotBlank @Size(max = 500) String reason) {}
