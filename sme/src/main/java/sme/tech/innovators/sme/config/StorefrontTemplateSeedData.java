package sme.tech.innovators.sme.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Seeds storefront templates on startup and refreshes version metadata
 * (supported sections, default config, vibe) so catalog/picker stay aligned
 * without a manual DB migration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorefrontTemplateSeedData implements ApplicationRunner {

    private final StorefrontTemplateRepository templateRepository;
    private final StorefrontTemplateVersionRepository templateVersionRepository;
    private final ObjectMapper objectMapper;

    private static final String CLASSIC_BOUTIQUE_ID = "classic-boutique";
    private static final String MINIMAL_CATALOGUE_ID = "minimal-catalogue";

    /** Canonical section allowlist for classic-boutique (order matches frontend library). */
    static final List<String> CLASSIC_BOUTIQUE_SUPPORTED_SECTIONS = List.of(
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
            "contactCta"
    );

    private static final List<String> CLASSIC_BOUTIQUE_THEMES = List.of("blue", "red");
    private static final String CLASSIC_BOUTIQUE_VIBE = "Editorial retail";

    private static final List<String> MINIMAL_CATALOGUE_SECTIONS = List.of(
            "hero",
            "featuredProducts",
            "newsletter",
            "contactCta"
    );
    private static final List<String> MINIMAL_CATALOGUE_THEMES = List.of("blue");
    private static final String MINIMAL_CATALOGUE_VIBE = "Clean product focus";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureClassicBoutique();
        ensureMinimalCatalogueComingSoon();
    }

    private void ensureClassicBoutique() {
        StorefrontTemplate template = templateRepository.findById(CLASSIC_BOUTIQUE_ID)
                .orElseGet(() -> {
                    log.info("Seeding storefront template: {}", CLASSIC_BOUTIQUE_ID);
                    return templateRepository.saveAndFlush(StorefrontTemplate.builder()
                            .id(CLASSIC_BOUTIQUE_ID)
                            .name("Classic Boutique")
                            .description("Editorial homepage with hero, products, promos, and value props.")
                            .vibe(CLASSIC_BOUTIQUE_VIBE)
                            .status(StorefrontTemplateStatus.AVAILABLE)
                            .latestVersion(1)
                            .build());
                });

        if (!CLASSIC_BOUTIQUE_VIBE.equals(template.getVibe())
                || template.getStatus() != StorefrontTemplateStatus.AVAILABLE
                || !"Editorial homepage with hero, products, promos, and value props."
                        .equals(template.getDescription())) {
            template.setVibe(CLASSIC_BOUTIQUE_VIBE);
            template.setStatus(StorefrontTemplateStatus.AVAILABLE);
            template.setDescription("Editorial homepage with hero, products, promos, and value props.");
            template.setName("Classic Boutique");
            templateRepository.save(template);
        }

        StorefrontTemplateVersion version = templateVersionRepository
                .findByTemplateIdAndVersion(CLASSIC_BOUTIQUE_ID, 1)
                .orElseGet(() -> templateVersionRepository.save(StorefrontTemplateVersion.builder()
                        .template(template)
                        .version(1)
                        .supportedThemes(CLASSIC_BOUTIQUE_THEMES)
                        .supportedSections(CLASSIC_BOUTIQUE_SUPPORTED_SECTIONS)
                        .defaultConfig(buildClassicBoutiqueDefaultConfig())
                        .build()));

        Map<String, Object> desiredConfig = buildClassicBoutiqueDefaultConfig();
        boolean dirty = false;
        if (!CLASSIC_BOUTIQUE_SUPPORTED_SECTIONS.equals(version.getSupportedSections())) {
            version.setSupportedSections(CLASSIC_BOUTIQUE_SUPPORTED_SECTIONS);
            dirty = true;
        }
        if (!CLASSIC_BOUTIQUE_THEMES.equals(version.getSupportedThemes())) {
            version.setSupportedThemes(CLASSIC_BOUTIQUE_THEMES);
            dirty = true;
        }
        if (!Objects.equals(version.getDefaultConfig(), desiredConfig)) {
            version.setDefaultConfig(desiredConfig);
            dirty = true;
        }
        if (dirty) {
            templateVersionRepository.save(version);
            log.info("Refreshed storefront template '{}' v1 seed metadata", CLASSIC_BOUTIQUE_ID);
        }
    }

    private void ensureMinimalCatalogueComingSoon() {
        if (templateRepository.existsById(MINIMAL_CATALOGUE_ID)) {
            return;
        }

        log.info("Seeding storefront template (coming soon): {}", MINIMAL_CATALOGUE_ID);
        StorefrontTemplate template = templateRepository.saveAndFlush(StorefrontTemplate.builder()
                .id(MINIMAL_CATALOGUE_ID)
                .name("Minimal Catalogue")
                .description("A focused product-first layout with fewer homepage sections.")
                .vibe(MINIMAL_CATALOGUE_VIBE)
                .status(StorefrontTemplateStatus.COMING_SOON)
                .latestVersion(1)
                .build());

        templateVersionRepository.save(StorefrontTemplateVersion.builder()
                .template(template)
                .version(1)
                .supportedThemes(MINIMAL_CATALOGUE_THEMES)
                .supportedSections(MINIMAL_CATALOGUE_SECTIONS)
                .defaultConfig(buildMinimalCatalogueDefaultConfig())
                .build());
    }

    private Map<String, Object> buildClassicBoutiqueDefaultConfig() {
        String json = """
                {
                  "configVersion": 1,
                  "themeId": "blue",
                  "shopName": "My Store",
                  "sections": [
                    {
                      "id": "hero-1",
                      "type": "hero",
                      "visible": true,
                      "content": {
                        "headline": "Welcome to Our Store",
                        "subheadline": "Discover our amazing collection",
                        "ctaText": "Shop Now",
                        "ctaLink": "/products"
                      }
                    },
                    {
                      "id": "featured-1",
                      "type": "featuredProducts",
                      "visible": true,
                      "content": {
                        "title": "Featured Products",
                        "products": []
                      }
                    },
                    {
                      "id": "promo-1",
                      "type": "promoBanner",
                      "visible": true,
                      "content": {
                        "message": "Free shipping on orders over R500!",
                        "backgroundColor": "#1d4ed8"
                      }
                    },
                    {
                      "id": "features-1",
                      "type": "features",
                      "visible": true,
                      "content": {
                        "title": "Why Shop With Us",
                        "items": [
                          { "icon": "truck", "title": "Fast Delivery", "description": "Get your order within 3-5 business days" },
                          { "icon": "shield", "title": "Secure Payment", "description": "Your payment information is always safe" },
                          { "icon": "refresh", "title": "Easy Returns", "description": "30-day hassle-free return policy" }
                        ]
                      }
                    },
                    {
                      "id": "contact-1",
                      "type": "contactCta",
                      "visible": true,
                      "content": {
                        "headline": "Get In Touch",
                        "subheadline": "We'd love to hear from you",
                        "email": "",
                        "phone": ""
                      }
                    }
                  ],
                  "pages": []
                }
                """;
        return parseConfig(json);
    }

    private Map<String, Object> buildMinimalCatalogueDefaultConfig() {
        String json = """
                {
                  "configVersion": 1,
                  "themeId": "blue",
                  "shopName": "My Store",
                  "sections": [
                    {
                      "id": "hero-1",
                      "type": "hero",
                      "visible": true,
                      "content": {
                        "headline": "Shop the collection",
                        "subheadline": "Simple, focused product discovery",
                        "ctaText": "Browse",
                        "ctaLink": "/products"
                      }
                    },
                    {
                      "id": "featured-1",
                      "type": "featuredProducts",
                      "visible": true,
                      "content": {
                        "title": "Products",
                        "products": []
                      }
                    },
                    {
                      "id": "contact-1",
                      "type": "contactCta",
                      "visible": true,
                      "content": {
                        "headline": "Questions?",
                        "subheadline": "We're here to help",
                        "email": "",
                        "phone": ""
                      }
                    }
                  ],
                  "pages": []
                }
                """;
        return parseConfig(json);
    }

    private Map<String, Object> parseConfig(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to parse default storefront config — returning empty map", e);
            return Map.of("configVersion", 1);
        }
    }
}
