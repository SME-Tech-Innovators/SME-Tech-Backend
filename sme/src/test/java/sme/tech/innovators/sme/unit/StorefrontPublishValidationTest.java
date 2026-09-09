package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sme.tech.innovators.sme.exception.InvalidPublishConfigException;
import sme.tech.innovators.sme.service.StorefrontConfigValidator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StorefrontPublishValidationTest {

    private StorefrontConfigValidator validator;
    private final List<String> sections = List.of(
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
    private final List<String> themes = List.of(
            "blue", "red", "ink", "forest", "teal", "stone");

    @BeforeEach
    void setUp() {
        validator = new StorefrontConfigValidator();
    }

    @Test
    void publishRequiresShopName() {
        Map<String, Object> config = validConfig();
        config.remove("shopName");
        assertThrows(InvalidPublishConfigException.class,
                () -> validator.validateForPublish(config, sections, themes));
    }

    @Test
    void publishRequiresThemeId() {
        Map<String, Object> config = validConfig();
        config.remove("themeId");
        assertThrows(InvalidPublishConfigException.class,
                () -> validator.validateForPublish(config, sections, themes));
    }

    @Test
    void validPublishConfigPasses() {
        assertDoesNotThrow(() -> validator.validateForPublish(validConfig(), sections, themes));
    }

    @Test
    void publishRejectsNullConfig() {
        assertThrows(InvalidPublishConfigException.class,
                () -> validator.validateForPublish(null, sections, themes));
    }

    @Test
    void publishRejectsEmptyConfig() {
        assertThrows(InvalidPublishConfigException.class,
                () -> validator.validateForPublish(Map.of(), sections, themes));
    }

    @Test
    void publishRejectsBlankShopName() {
        Map<String, Object> config = validConfig();
        config.put("shopName", "   ");
        assertThrows(InvalidPublishConfigException.class,
                () -> validator.validateForPublish(config, sections, themes));
    }

    @Test
    void publishRejectsUnsupportedTheme() {
        Map<String, Object> config = validConfig();
        config.put("themeId", "neon-pink");
        assertThrows(InvalidPublishConfigException.class,
                () -> validator.validateForPublish(config, sections, themes));
    }

    @Test
    void publishRejectsUnsupportedSectionType() {
        Map<String, Object> config = validConfig();
        config.put("sections", List.of(Map.of("id", "x-1", "type", "unknownWidget")));
        assertThrows(InvalidPublishConfigException.class,
                () -> validator.validateForPublish(config, sections, themes));
    }

    @Test
    void publishRejectsDuplicatePageSlugs() {
        Map<String, Object> config = validConfig();
        config.put("pages", List.of(
                Map.of("slug", "about", "title", "About"),
                Map.of("slug", "about", "title", "About again")
        ));
        assertThrows(InvalidPublishConfigException.class,
                () -> validator.validateForPublish(config, sections, themes));
    }

    @Test
    void publishRejectsMissingConfigVersion() {
        Map<String, Object> config = validConfig();
        config.remove("configVersion");
        assertThrows(InvalidPublishConfigException.class,
                () -> validator.validateForPublish(config, sections, themes));
    }

    @Test
    void publishRejectsSectionMissingId() {
        Map<String, Object> config = validConfig();
        config.put("sections", List.of(Map.of("type", "hero")));
        assertThrows(InvalidPublishConfigException.class,
                () -> validator.validateForPublish(config, sections, themes));
    }

    @Test
    void publishAllowsPlaceholderFeaturedProducts() {
        Map<String, Object> config = validConfig();
        config.put("sections", List.of(
                Map.of("id", "hero-1", "type", "hero"),
                Map.of(
                        "id", "fp-1",
                        "type", "featuredProducts",
                        "title", "Featured",
                        "products", List.of(Map.of("id", "placeholder-1", "name", "Sample"))
                )
        ));
        assertDoesNotThrow(() -> validator.validateForPublish(config, sections, themes));
    }

    @Test
    void publishAllowsUniquePageSlugs() {
        Map<String, Object> config = validConfig();
        config.put("pages", List.of(
                Map.of("slug", "about", "title", "About"),
                Map.of("slug", "contact", "title", "Contact")
        ));
        assertDoesNotThrow(() -> validator.validateForPublish(config, sections, themes));
    }

    @Test
    void acceptsNewSectionTypes() {
        Map<String, Object> config = validConfig();
        config.put("sections", List.of(
                Map.of("id", "hero-1", "type", "hero"),
                Map.of("id", "arrivals-1", "type", "newArrivals",
                        "title", "New arrivals",
                        "viewAll", Map.of("label", "View all", "href", "@shop")),
                Map.of("id", "cats-1", "type", "shopByCategory",
                        "title", "Shop by category",
                        "viewAll", Map.of("label", "View all", "href", "@shop"),
                        "categories", List.of()),
                Map.of("id", "sale-1", "type", "sale",
                        "eyebrow", "Sale",
                        "title", "Limited-time offers",
                        "description", "Save on selected pieces while stocks last.",
                        "buttonLabel", "Shop the sale",
                        "href", "@shop",
                        "imageUrl", "",
                        "products", List.of()),
                Map.of("id", "quotes-1", "type", "testimonials",
                        "title", "What customers say",
                        "items", List.of()),
                Map.of("id", "ig-1", "type", "instagramGallery",
                        "title", "On Instagram",
                        "handle", "@mystore",
                        "images", List.of()),
                Map.of("id", "news-1", "type", "newsletter",
                        "title", "Stay in the loop",
                        "body", "Get drops first.",
                        "placeholder", "you@email.com",
                        "buttonLabel", "Subscribe",
                        "successMessage", "Thanks!")
        ));
        assertDoesNotThrow(() -> validator.validate(config, sections, themes));
        assertDoesNotThrow(() -> validator.validateForPublish(config, sections, themes));
    }

    private Map<String, Object> validConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("configVersion", 1);
        config.put("themeId", "blue");
        config.put("shopName", "My Store");
        config.put("sections", List.of(Map.of("id", "hero-1", "type", "hero")));
        config.put("pages", List.of());
        return config;
    }
}
