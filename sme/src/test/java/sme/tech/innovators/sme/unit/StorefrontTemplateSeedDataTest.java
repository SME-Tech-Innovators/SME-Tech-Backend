package sme.tech.innovators.sme.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import sme.tech.innovators.sme.config.StorefrontTemplateSeedData;
import sme.tech.innovators.sme.config.StorefrontTemplateSeedLoader;
import sme.tech.innovators.sme.entity.StorefrontTemplate;
import sme.tech.innovators.sme.entity.StorefrontTemplateStatus;
import sme.tech.innovators.sme.entity.StorefrontTemplateVersion;
import sme.tech.innovators.sme.repository.StorefrontTemplateRepository;
import sme.tech.innovators.sme.repository.StorefrontTemplateVersionRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorefrontTemplateSeedDataTest {

    @Mock StorefrontTemplateRepository templateRepository;
    @Mock StorefrontTemplateVersionRepository templateVersionRepository;

    private StorefrontTemplateSeedData seedData;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        StorefrontTemplateSeedLoader seedLoader = new StorefrontTemplateSeedLoader(objectMapper);
        seedData = new StorefrontTemplateSeedData(
                templateRepository, templateVersionRepository, seedLoader);
    }

    @Test
    void promotesExistingMinimalCatalogueFromComingSoon() {
        StorefrontTemplate classic = StorefrontTemplate.builder()
                .id("classic-boutique")
                .name("Classic Boutique")
                .description("Editorial homepage with hero, products, promos, and value props.")
                .vibe("Editorial retail")
                .status(StorefrontTemplateStatus.AVAILABLE)
                .latestVersion(1)
                .build();
        StorefrontTemplate minimal = StorefrontTemplate.builder()
                .id("minimal-catalogue")
                .name("Minimal Catalogue")
                .description("old")
                .vibe("Clean product focus")
                .status(StorefrontTemplateStatus.COMING_SOON)
                .latestVersion(1)
                .build();
        StorefrontTemplate artisan = StorefrontTemplate.builder()
                .id("artisan-atelier")
                .name("Artisan Atelier")
                .description("old")
                .vibe("Handmade studio")
                .status(StorefrontTemplateStatus.AVAILABLE)
                .latestVersion(1)
                .build();
        StorefrontTemplateVersion classicVersion = StorefrontTemplateVersion.builder()
                .template(classic)
                .version(1)
                .supportedThemes(List.of("blue", "red"))
                .supportedSections(StorefrontTemplateSeedData.BUILTIN_SUPPORTED_SECTIONS)
                .defaultConfig(Map.of("configVersion", 1))
                .build();
        StorefrontTemplateVersion minimalVersion = StorefrontTemplateVersion.builder()
                .template(minimal)
                .version(1)
                .supportedThemes(List.of("blue"))
                .supportedSections(List.of("hero"))
                .defaultConfig(Map.of("configVersion", 1))
                .build();
        StorefrontTemplateVersion artisanVersion = StorefrontTemplateVersion.builder()
                .template(artisan)
                .version(1)
                .supportedThemes(List.of("blue"))
                .supportedSections(List.of("hero"))
                .defaultConfig(Map.of("configVersion", 1))
                .build();

        when(templateRepository.findById("classic-boutique")).thenReturn(Optional.of(classic));
        when(templateRepository.findById("minimal-catalogue")).thenReturn(Optional.of(minimal));
        when(templateRepository.findById("artisan-atelier")).thenReturn(Optional.of(artisan));
        when(templateVersionRepository.findByTemplateIdAndVersion("classic-boutique", 1))
                .thenReturn(Optional.of(classicVersion));
        when(templateVersionRepository.findByTemplateIdAndVersion("minimal-catalogue", 1))
                .thenReturn(Optional.of(minimalVersion));
        when(templateVersionRepository.findByTemplateIdAndVersion("artisan-atelier", 1))
                .thenReturn(Optional.of(artisanVersion));
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(templateVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        seedData.run(new DefaultApplicationArguments());

        assertThat(minimal.getStatus()).isEqualTo(StorefrontTemplateStatus.AVAILABLE);
        assertThat(minimal.getVibe()).isEqualTo("Clean catalogue");
        verify(templateRepository).save(minimal);

        ArgumentCaptor<StorefrontTemplateVersion> versionCaptor =
                ArgumentCaptor.forClass(StorefrontTemplateVersion.class);
        verify(templateVersionRepository, atLeastOnce()).save(versionCaptor.capture());
        StorefrontTemplateVersion savedMinimal = versionCaptor.getAllValues().stream()
                .filter(v -> v.getTemplate() != null && "minimal-catalogue".equals(v.getTemplate().getId())
                        || v == minimalVersion)
                .findFirst()
                .orElse(minimalVersion);
        assertThat(savedMinimal.getSupportedThemes())
                .containsExactly("blue", "red", "ink", "forest", "teal", "stone");
        assertThat(savedMinimal.getSupportedSections())
                .isEqualTo(StorefrontTemplateSeedData.BUILTIN_SUPPORTED_SECTIONS);
        assertThat(savedMinimal.getDefaultConfig().get("tagline"))
                .isEqualTo("Clear prices. Easy online orders.");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections =
                (List<Map<String, Object>>) savedMinimal.getDefaultConfig().get("sections");
        assertThat(sections).extracting(s -> s.get("type"))
                .containsExactly("hero", "featuredProducts", "shopByCategory", "features", "contactCta");
    }
}
