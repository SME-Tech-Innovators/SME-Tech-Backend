package sme.tech.innovators.sme.dto.request;

import lombok.Data;

@Data
public class UpdateCategoryRequest {
    private String name;
    private String slug;
}
