package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SeoDto {
    private String title;
    private String description;
    private String imageUrl;
}
