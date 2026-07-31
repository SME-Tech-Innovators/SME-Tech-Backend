package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.response.StorefrontTemplateDto;
import sme.tech.innovators.sme.entity.StorefrontTemplate;
import sme.tech.innovators.sme.entity.StorefrontTemplateStatus;
import sme.tech.innovators.sme.entity.StorefrontTemplateVersion;
import sme.tech.innovators.sme.repository.StorefrontTemplateRepository;
import sme.tech.innovators.sme.repository.StorefrontTemplateVersionRepository;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class StorefrontTemplateService {

    private final StorefrontTemplateRepository templateRepository;
    private final StorefrontTemplateVersionRepository templateVersionRepository;

    @Transactional(readOnly = true)
    public List<StorefrontTemplateDto> listTemplates() {
        return templateRepository
                .findAllByStatusIn(List.of(
                        StorefrontTemplateStatus.AVAILABLE,
                        StorefrontTemplateStatus.COMING_SOON))
                .stream()
                .map(this::toDto)
                .toList();
    }

    private StorefrontTemplateDto toDto(StorefrontTemplate template) {
        List<String> themeIds = templateVersionRepository
                .findByTemplateIdAndVersion(template.getId(), template.getLatestVersion())
                .map(StorefrontTemplateVersion::getSupportedThemes)
                .orElse(List.of());

        return StorefrontTemplateDto.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .vibe(template.getVibe())
                .status(toApiStatus(template.getStatus()))
                .latestVersion(template.getLatestVersion())
                .previewImageUrl(template.getPreviewImageUrl())
                .supportedThemeIds(themeIds)
                .build();
    }

    private static String toApiStatus(StorefrontTemplateStatus status) {
        return status.name().toLowerCase(Locale.ROOT);
    }
}
