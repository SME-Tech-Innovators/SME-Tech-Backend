package sme.tech.innovators.sme.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.entity.StorefrontTemplate;
import sme.tech.innovators.sme.entity.StorefrontTemplateStatus;
import sme.tech.innovators.sme.entity.StorefrontTemplateVersion;
import sme.tech.innovators.sme.repository.StorefrontTemplateRepository;
import sme.tech.innovators.sme.repository.StorefrontTemplateVersionRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Seeds storefront templates on startup from {@code storefront-templates/*.json}
 * and refreshes version metadata so catalog/picker stay aligned without manual DB work.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorefrontTemplateSeedData implements ApplicationRunner {

    private final StorefrontTemplateRepository templateRepository;
    private final StorefrontTemplateVersionRepository templateVersionRepository;
    private final StorefrontTemplateSeedLoader seedLoader;

    /**
     * Shared section allowlist (same section types for all built-in templates;
     * chrome + default seed differ).
     */
    public static final List<String> BUILTIN_SUPPORTED_SECTIONS = List.of(
            "hero",
            "featuredProducts",
            "newArrivals",
            "shopByCategory",
            "sale",
            "promoBanner",
            "textImage",
            "features",
            "testimonials",
            "instagramGallery",
            "newsletter",
            "faq",
            "contact",
            "contactCta"
    );

    /** @deprecated use {@link #BUILTIN_SUPPORTED_SECTIONS} */
    static final List<String> CLASSIC_BOUTIQUE_SUPPORTED_SECTIONS = BUILTIN_SUPPORTED_SECTIONS;

    public static final List<String> BUILTIN_THEMES = List.of(
            "blue", "red", "ink", "forest", "teal", "stone");

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<StorefrontTemplateSeedDefinition> definitions = seedLoader.loadAll();
        if (definitions.isEmpty()) {
            log.warn("No storefront template seeds found under storefront-templates/");
            return;
        }
        log.info("Seeding {} storefront template(s) from JSON resources", definitions.size());
        for (StorefrontTemplateSeedDefinition definition : definitions) {
            ensureTemplate(definition);
        }
    }

    private void ensureTemplate(StorefrontTemplateSeedDefinition definition) {
        String templateId = definition.getId();
        StorefrontTemplateStatus desiredStatus = definition.getStatus() != null
                ? definition.getStatus()
                : StorefrontTemplateStatus.AVAILABLE;
        int desiredVersion = definition.getLatestVersion() != null && definition.getLatestVersion() > 0
                ? definition.getLatestVersion()
                : 1;

        StorefrontTemplate template = templateRepository.findById(templateId)
                .orElseGet(() -> {
                    log.info("Seeding storefront template: {}", templateId);
                    return templateRepository.saveAndFlush(StorefrontTemplate.builder()
                            .id(templateId)
                            .name(definition.getName())
                            .description(definition.getDescription())
                            .vibe(definition.getVibe())
                            .status(desiredStatus)
                            .previewImageUrl(definition.getPreviewImageUrl())
                            .latestVersion(desiredVersion)
                            .build());
                });

        if (needsTemplateUpdate(template, definition, desiredStatus, desiredVersion)) {
            template.setName(definition.getName());
            template.setDescription(definition.getDescription());
            template.setVibe(definition.getVibe());
            template.setStatus(desiredStatus);
            template.setPreviewImageUrl(definition.getPreviewImageUrl());
            if (template.getLatestVersion() == null || template.getLatestVersion() < desiredVersion) {
                template.setLatestVersion(desiredVersion);
            }
            templateRepository.save(template);
            log.info("Updated storefront template metadata: {}", templateId);
        }

        List<String> themes = definition.getSupportedThemes() != null
                ? definition.getSupportedThemes()
                : BUILTIN_THEMES;
        List<String> sections = definition.getSupportedSections() != null
                ? definition.getSupportedSections()
                : BUILTIN_SUPPORTED_SECTIONS;

        refreshVersion(template, templateId, desiredVersion, themes, sections, definition.getDefaultConfig());
    }

    private static boolean needsTemplateUpdate(StorefrontTemplate template,
                                               StorefrontTemplateSeedDefinition definition,
                                               StorefrontTemplateStatus desiredStatus,
                                               int desiredVersion) {
        return !Objects.equals(definition.getName(), template.getName())
                || !Objects.equals(definition.getDescription(), template.getDescription())
                || !Objects.equals(definition.getVibe(), template.getVibe())
                || template.getStatus() != desiredStatus
                || !Objects.equals(definition.getPreviewImageUrl(), template.getPreviewImageUrl())
                || template.getLatestVersion() == null
                || template.getLatestVersion() < desiredVersion;
    }

    private void refreshVersion(StorefrontTemplate template,
                                String templateId,
                                int versionNumber,
                                List<String> themes,
                                List<String> sections,
                                Map<String, Object> desiredConfig) {
        StorefrontTemplateVersion version = templateVersionRepository
                .findByTemplateIdAndVersion(templateId, versionNumber)
                .orElseGet(() -> templateVersionRepository.save(StorefrontTemplateVersion.builder()
                        .template(template)
                        .version(versionNumber)
                        .supportedThemes(themes)
                        .supportedSections(sections)
                        .defaultConfig(desiredConfig)
                        .build()));

        boolean dirty = false;
        if (!sections.equals(version.getSupportedSections())) {
            version.setSupportedSections(sections);
            dirty = true;
        }
        if (!themes.equals(version.getSupportedThemes())) {
            version.setSupportedThemes(themes);
            dirty = true;
        }
        if (!Objects.equals(version.getDefaultConfig(), desiredConfig)) {
            version.setDefaultConfig(desiredConfig);
            dirty = true;
        }
        if (dirty) {
            templateVersionRepository.save(version);
            log.info("Refreshed storefront template '{}' v{} seed metadata", templateId, versionNumber);
        }
    }
}
