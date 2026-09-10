package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sme.tech.innovators.sme.dto.response.StorefrontTemplateDto;
import sme.tech.innovators.sme.dto.response.StorefrontTemplateVersionDto;
import sme.tech.innovators.sme.entity.StorefrontTemplate;
import sme.tech.innovators.sme.entity.StorefrontTemplateStatus;
import sme.tech.innovators.sme.entity.StorefrontTemplateVersion;
import sme.tech.innovators.sme.exception.TemplateNotFoundException;
import sme.tech.innovators.sme.repository.StorefrontTemplateRepository;
import sme.tech.innovators.sme.repository.StorefrontTemplateVersionRepository;
import sme.tech.innovators.sme.service.StorefrontTemplateService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorefrontTemplateServiceTest {

    @Mock
    private StorefrontTemplateRepository templateRepository;

    @Mock
    private StorefrontTemplateVersionRepository templateVersionRepository;

    @InjectMocks
    private StorefrontTemplateService storefrontTemplateService;

    @Test
    void listTemplatesReturnsAvailableAndComingSoonWithThemes() {
        StorefrontTemplate classic = StorefrontTemplate.builder()
                .id("classic-boutique")
                .name("Classic Boutique")
                .description("Editorial homepage")
                .vibe("Editorial retail")
                .status(StorefrontTemplateStatus.AVAILABLE)
                .latestVersion(1)
                .previewImageUrl("https://cdn.example/classic.jpg")
                .build();
        StorefrontTemplate minimal = StorefrontTemplate.builder()
                .id("minimal-catalogue")
                .name("Minimal Catalogue")
                .description("Product-first")
                .vibe("Clean catalogue")
                .status(StorefrontTemplateStatus.AVAILABLE)
                .latestVersion(1)
                .build();

        when(templateRepository.findAllByStatusIn(anyList()))
                .thenReturn(List.of(classic, minimal));
        when(templateVersionRepository.findByTemplateIdAndVersion("classic-boutique", 1))
                .thenReturn(Optional.of(StorefrontTemplateVersion.builder()
                        .supportedThemes(List.of("blue", "red"))
                        .build()));
        when(templateVersionRepository.findByTemplateIdAndVersion("minimal-catalogue", 1))
                .thenReturn(Optional.of(StorefrontTemplateVersion.builder()
                        .supportedThemes(List.of("blue", "red"))
                        .build()));

        List<StorefrontTemplateDto> result = storefrontTemplateService.listTemplates();

        assertEquals(2, result.size());
        StorefrontTemplateDto first = result.get(0);
        assertEquals("classic-boutique", first.getId());
        assertEquals("available", first.getStatus());
        assertEquals("Editorial retail", first.getVibe());
        assertEquals(List.of("blue", "red"), first.getSupportedThemeIds());
        assertEquals("available", result.get(1).getStatus());
        assertEquals("minimal-catalogue", result.get(1).getId());
        assertEquals(List.of("blue", "red"), result.get(1).getSupportedThemeIds());
    }

    @Test
    void getTemplateVersionReturnsDefaultConfigAndMetadata() {
        StorefrontTemplate template = StorefrontTemplate.builder()
                .id("artisan-atelier")
                .name("Artisan Atelier")
                .status(StorefrontTemplateStatus.AVAILABLE)
                .latestVersion(1)
                .build();
        Map<String, Object> defaultConfig = Map.of(
                "configVersion", 5,
                "themeId", "stone",
                "shopName", "Artisan Atelier");
        StorefrontTemplateVersion version = StorefrontTemplateVersion.builder()
                .template(template)
                .version(1)
                .defaultConfig(defaultConfig)
                .supportedSections(List.of("hero", "features"))
                .supportedThemes(List.of("stone", "blue"))
                .build();

        when(templateRepository.findById("artisan-atelier")).thenReturn(Optional.of(template));
        when(templateVersionRepository.findByTemplateIdAndVersion("artisan-atelier", 1))
                .thenReturn(Optional.of(version));

        StorefrontTemplateVersionDto result =
                storefrontTemplateService.getTemplateVersion("artisan-atelier", 1);

        assertEquals("artisan-atelier", result.getTemplateId());
        assertEquals(1, result.getVersion());
        assertEquals(defaultConfig, result.getDefaultConfig());
        assertEquals(List.of("hero", "features"), result.getSupportedSections());
        assertEquals(List.of("stone", "blue"), result.getSupportedThemes());
    }

    @Test
    void getTemplateVersionThrowsWhenVersionMissing() {
        StorefrontTemplate template = StorefrontTemplate.builder()
                .id("classic-boutique")
                .status(StorefrontTemplateStatus.AVAILABLE)
                .latestVersion(1)
                .build();

        when(templateRepository.findById("classic-boutique")).thenReturn(Optional.of(template));
        when(templateVersionRepository.findByTemplateIdAndVersion("classic-boutique", 99))
                .thenReturn(Optional.empty());

        assertThrows(TemplateNotFoundException.class,
                () -> storefrontTemplateService.getTemplateVersion("classic-boutique", 99));
    }
}
