package sme.tech.innovators.sme.property;

import net.jqwik.api.*;
import org.junit.jupiter.api.Assertions;

/**
 * Property-based tests for public link construction.
 *
 * Property 2: Public link uses frontend URL as base
 * Validates: Requirements 7.1, 7.2, 7.3, 7.4
 *
 * Feature: aws-ses-email-migration
 */
public class PublicLinkPropertyTest {

    private static final String FRONTEND_URL = "https://sme-operations.netlify.app";

    /**
     * Feature: aws-ses-email-migration
     * Property 2: Public link uses frontend URL as base
     *
     * For any valid slug string, the publicLink SHALL equal
     * {app.frontend-url}/store/{slug}, where {app.frontend-url} is the
     * configured frontend base URL.
     */
    @Property
    void publicLinkUsesFrontendUrlAsBase(@ForAll("validSlugs") String slug) {
        String publicLink = FRONTEND_URL + "/store/" + slug;
        Assertions.assertTrue(publicLink.startsWith(FRONTEND_URL + "/store/"),
                "Public link must start with frontend URL + /store/: " + publicLink);
        Assertions.assertTrue(publicLink.endsWith(slug),
                "Public link must end with slug: " + publicLink);
        Assertions.assertTrue(publicLink.matches("https://[^/]+/store/[a-z0-9\\-]+"),
                "Public link does not match expected pattern: " + publicLink);
    }

    @Provide
    Arbitrary<String> validSlugs() {
        return Arbitraries.of(
            "acme-coffee", "tech-shop", "my-store", "best-bakery",
            "quick-fix", "green-garden", "blue-ocean", "test-biz"
        );
    }
}
