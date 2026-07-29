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

/**
 * Seeds the classic-boutique storefront template on application startup if not already present.
 * Also refreshes supported_sections on existing versions so allowlist expansions ship without a
 * manual DB migration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorefrontTemplateSeedData implements ApplicationRunner {

    private final StorefrontTemplateRepository templateRepository;
    private final StorefrontTemplateVersionRepository templateVersionRepository;
    private final ObjectMapper objectMapper;

    private static final String CLASSIC_BOUTIQUE_ID = "classic-boutique";

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

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (templateRepository.existsById(CLASSIC_BOUTIQUE_ID)) {
            refreshSupportedSections();
            return;
        }

        log.info("Seeding storefront template: {}", CLASSIC_BOUTIQUE_ID);

        StorefrontTemplate template = StorefrontTemplate.builder()
                .id(CLASSIC_BOUTIQUE_ID)
                .name("Classic Boutique")
                .description("A polished storefront with hero content, featured products, promos, and value props.")
                .status(StorefrontTemplateStatus.AVAILABLE)
                .latestVersion(1)
                .build();
        templateRepository.saveAndFlush(template);

        List<String> supportedThemes = List.of("blue", "red");

        Map<String, Object> defaultConfig = buildDefaultConfig();

        StorefrontTemplateVersion version = StorefrontTemplateVersion.builder()
                .template(template)
                .version(1)
                .supportedThemes(supportedThemes)
                .supportedSections(CLASSIC_BOUTIQUE_SUPPORTED_SECTIONS)
                .defaultConfig(defaultConfig)
                .build();
        templateVersionRepository.save(version);

        log.info("Seeded storefront template '{}' v1 successfully", CLASSIC_BOUTIQUE_ID);
    }

    /** Idempotent allowlist sync for already-seeded classic-boutique versions. */
    private void refreshSupportedSections() {
        templateVersionRepository.findByTemplateIdAndVersion(CLASSIC_BOUTIQUE_ID, 1)
                .ifPresent(version -> {
                    List<String> current = version.getSupportedSections();
                    if (CLASSIC_BOUTIQUE_SUPPORTED_SECTIONS.equals(current)) {
                        log.debug("StorefrontTemplate '{}' supported_sections already up to date",
                                CLASSIC_BOUTIQUE_ID);
                        return;
                    }
                    version.setSupportedSections(CLASSIC_BOUTIQUE_SUPPORTED_SECTIONS);
                    templateVersionRepository.save(version);
                    log.info("Updated storefront template '{}' v1 supported_sections: {}",
                            CLASSIC_BOUTIQUE_ID, CLASSIC_BOUTIQUE_SUPPORTED_SECTIONS);
                });
    }

    /**
     * Builds the default storefront config for classic-boutique v1.
     * This mirrors the frontend default-storefront.json structure.
     */
    private Map<String, Object> buildDefaultConfig() {
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
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to parse default storefront config — returning empty map", e);
            return Map.of("configVersion", 1);
        }
    }
}
