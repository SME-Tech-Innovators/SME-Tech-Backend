package sme.tech.innovators.sme.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;
import sme.tech.innovators.sme.repository.BusinessRepository;
import sme.tech.innovators.sme.repository.WorkspaceRepository;
import sme.tech.innovators.sme.service.SlugGeneratorService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

public class SlugGenerationPropertyTest {

    private SlugGeneratorService createService() {
        BusinessRepository repo = Mockito.mock(BusinessRepository.class);
        WorkspaceRepository workspaceRepository = Mockito.mock(WorkspaceRepository.class);
        Mockito.when(repo.existsBySlugAndIsDeletedFalse(Mockito.anyString())).thenReturn(false);
        SlugGeneratorService service = new SlugGeneratorService(repo, workspaceRepository);
        ReflectionTestUtils.setField(service, "maxRetries", 5);
        ReflectionTestUtils.setField(service, "minLength", 3);
        ReflectionTestUtils.setField(service, "maxLength", 50);
        ReflectionTestUtils.setField(service, "reservedKeywords", List.of(
            "admin", "api", "app", "auth", "dashboard", "login", "logout",
            "register", "signup", "store", "support", "www", "mail",
            "static", "assets", "public", "private", "internal", "system", "root"
        ));
        return service;
    }

    @Property
    void sanitizedSlugIsLowercaseAndValidChars(@ForAll @StringLength(min = 3, max = 100) @AlphaChars String name) {
        SlugGeneratorService service = createService();
        String slug = service.sanitizeSlug(name);
        Assertions.assertTrue(slug.matches("[a-z0-9\\-]*"), "Slug contains invalid chars: " + slug);
    }

    @Property
    void sanitizeIsIdempotent(@ForAll @StringLength(min = 3, max = 100) @AlphaChars String name) {
        SlugGeneratorService service = createService();
        String once = service.sanitizeSlug(name);
        String twice = service.sanitizeSlug(once);
        Assertions.assertEquals(once, twice, "sanitizeSlug is not idempotent");
    }

    @Property
    void generatedSlugLengthIsWithinBounds(@ForAll("validBusinessNames") String name) {
        SlugGeneratorService service = createService();
        String slug = service.sanitizeSlug(name);
        if (!slug.isEmpty()) {
            Assertions.assertTrue(slug.length() <= 50, "Slug too long: " + slug.length());
        }
    }

    @Provide
    Arbitrary<String> validBusinessNames() {
        return Arbitraries.of(
            "Acme Coffee Shop", "Tech Innovators", "My Business", "Hello World Store",
            "Best Bakery Ever", "Quick Fix Auto", "Green Garden", "Blue Ocean Cafe"
        );
    }
}
