package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class PublicPageDto {
    private String slug;
    private String title;
    private Map<String, Object> page;
}
