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
            "contactCta"
    );

    /** @deprecated use {@link #BUILTIN_SUPPORTED_SECTIONS} */
    static final List<String> CLASSIC_BOUTIQUE_SUPPORTED_SECTIONS = BUILTIN_SUPPORTED_SECTIONS;

    private static final List<String> BUILTIN_THEMES = List.of(
            "blue", "red", "ink", "forest", "teal", "stone");
    private static final String CLASSIC_BOUTIQUE_VIBE = "Editorial retail";
    private static final String CLASSIC_BOUTIQUE_DESCRIPTION =
            "Editorial homepage with hero, products, promos, and value props.";

    private static final String MINIMAL_CATALOGUE_VIBE = "Clean catalogue";
    private static final String MINIMAL_CATALOGUE_DESCRIPTION =
            "Product-first layout for multi-category retail — clear prices, stock, and easy checkout.";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureClassicBoutique();
        ensureMinimalCatalogue();
    }

    private void ensureClassicBoutique() {
        StorefrontTemplate template = templateRepository.findById(CLASSIC_BOUTIQUE_ID)
                .orElseGet(() -> {
                    log.info("Seeding storefront template: {}", CLASSIC_BOUTIQUE_ID);
                    return templateRepository.saveAndFlush(StorefrontTemplate.builder()
                            .id(CLASSIC_BOUTIQUE_ID)
                            .name("Classic Boutique")
                            .description(CLASSIC_BOUTIQUE_DESCRIPTION)
                            .vibe(CLASSIC_BOUTIQUE_VIBE)
                            .status(StorefrontTemplateStatus.AVAILABLE)
                            .latestVersion(1)
                            .build());
                });

        if (!CLASSIC_BOUTIQUE_VIBE.equals(template.getVibe())
                || template.getStatus() != StorefrontTemplateStatus.AVAILABLE
                || !CLASSIC_BOUTIQUE_DESCRIPTION.equals(template.getDescription())) {
            template.setVibe(CLASSIC_BOUTIQUE_VIBE);
            template.setStatus(StorefrontTemplateStatus.AVAILABLE);
            template.setDescription(CLASSIC_BOUTIQUE_DESCRIPTION);
            template.setName("Classic Boutique");
            templateRepository.save(template);
        }

        refreshVersion(
                template,
                CLASSIC_BOUTIQUE_ID,
                BUILTIN_THEMES,
                BUILTIN_SUPPORTED_SECTIONS,
                buildClassicBoutiqueDefaultConfig()
        );
    }

    private void ensureMinimalCatalogue() {
        StorefrontTemplate template = templateRepository.findById(MINIMAL_CATALOGUE_ID)
                .orElseGet(() -> {
                    log.info("Seeding storefront template: {}", MINIMAL_CATALOGUE_ID);
                    return templateRepository.saveAndFlush(StorefrontTemplate.builder()
                            .id(MINIMAL_CATALOGUE_ID)
                            .name("Minimal Catalogue")
                            .description(MINIMAL_CATALOGUE_DESCRIPTION)
                            .vibe(MINIMAL_CATALOGUE_VIBE)
                            .status(StorefrontTemplateStatus.AVAILABLE)
                            .latestVersion(1)
                            .build());
                });

        if (!MINIMAL_CATALOGUE_VIBE.equals(template.getVibe())
                || template.getStatus() != StorefrontTemplateStatus.AVAILABLE
                || !MINIMAL_CATALOGUE_DESCRIPTION.equals(template.getDescription())
                || !"Minimal Catalogue".equals(template.getName())) {
            template.setVibe(MINIMAL_CATALOGUE_VIBE);
            template.setStatus(StorefrontTemplateStatus.AVAILABLE);
            template.setDescription(MINIMAL_CATALOGUE_DESCRIPTION);
            template.setName("Minimal Catalogue");
            if (template.getLatestVersion() == null || template.getLatestVersion() < 1) {
                template.setLatestVersion(1);
            }
            templateRepository.save(template);
            log.info("Promoted storefront template '{}' to AVAILABLE", MINIMAL_CATALOGUE_ID);
        }

        refreshVersion(
                template,
                MINIMAL_CATALOGUE_ID,
                BUILTIN_THEMES,
                BUILTIN_SUPPORTED_SECTIONS,
                buildMinimalCatalogueDefaultConfig()
        );
    }

    private void refreshVersion(StorefrontTemplate template,
                                String templateId,
                                List<String> themes,
                                List<String> sections,
                                Map<String, Object> desiredConfig) {
        StorefrontTemplateVersion version = templateVersionRepository
                .findByTemplateIdAndVersion(templateId, 1)
                .orElseGet(() -> templateVersionRepository.save(StorefrontTemplateVersion.builder()
                        .template(template)
                        .version(1)
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
            log.info("Refreshed storefront template '{}' v1 seed metadata", templateId);
        }
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

    /**
     * Neutral multi-category seed — no fashion/seasonal language.
     * Home bias: hero → featured → shop by category → values.
     */
    private Map<String, Object> buildMinimalCatalogueDefaultConfig() {
        String json = """
                {
                  "configVersion": 1,
                  "themeId": "blue",
                  "shopName": "My Store",
                  "tagline": "Clear prices. Easy online orders.",
                  "sections": [
                    {
                      "id": "hero-1",
                      "type": "hero",
                      "visible": true,
                      "content": {
                        "headline": "Browse the catalogue",
                        "subheadline": "Find what you need, check stock, and check out securely.",
                        "ctaText": "Shop now",
                        "ctaLink": "/products"
                      }
                    },
                    {
                      "id": "featured-1",
                      "type": "featuredProducts",
                      "visible": true,
                      "content": {
                        "title": "Popular products",
                        "products": []
                      }
                    },
                    {
                      "id": "categories-1",
                      "type": "shopByCategory",
                      "visible": true,
                      "content": {
                        "title": "Shop by category",
                        "categories": []
                      }
                    },
                    {
                      "id": "features-1",
                      "type": "features",
                      "visible": true,
                      "content": {
                        "title": "Why order with us",
                        "items": [
                          { "icon": "truck", "title": "Reliable shipping", "description": "Trackable delivery on every order" },
                          { "icon": "shield", "title": "Secure payment", "description": "Checkout protected with trusted payments" },
                          { "icon": "headset", "title": "Helpful support", "description": "Questions about stock or orders? We're here" }
                        ]
                      }
                    },
                    {
                      "id": "contact-1",
                      "type": "contactCta",
                      "visible": true,
                      "content": {
                        "headline": "Need help?",
                        "subheadline": "Contact us about products, stock, or your order",
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
