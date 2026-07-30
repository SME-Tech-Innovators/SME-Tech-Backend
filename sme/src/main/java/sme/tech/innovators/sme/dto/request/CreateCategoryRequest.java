package sme.tech.innovators.sme.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCategoryRequest {

    @NotBlank(message = "name is required")
    private String name;

    private String slug;
}
