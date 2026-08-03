package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import sme.tech.innovators.sme.exception.PublicSlugUnavailableException;
import sme.tech.innovators.sme.exception.SlugGenerationException;
import sme.tech.innovators.sme.repository.BusinessRepository;
import sme.tech.innovators.sme.repository.WorkspaceRepository;
import sme.tech.innovators.sme.service.SlugGeneratorService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SlugGeneratorServiceTest {

    private SlugGeneratorService service;
    private BusinessRepository repo;
    private WorkspaceRepository workspaceRepository;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(BusinessRepository.class);
        workspaceRepository = Mockito.mock(WorkspaceRepository.class);
        service = new SlugGeneratorService(repo, workspaceRepository);
        ReflectionTestUtils.setField(service, "maxRetries", 5);
        ReflectionTestUtils.setField(service, "minLength", 3);
        ReflectionTestUtils.setField(service, "maxLength", 50);
        ReflectionTestUtils.setField(service, "reservedKeywords", List.of(
            "admin", "api", "app", "auth", "dashboard", "login", "logout",
            "register", "signup", "store", "support", "www", "mail",
            "static", "assets", "public", "private", "internal", "system", "root"
        ));
    }

    @Test
    void emptyInputAfterSanitizationThrows() {
        when(repo.existsBySlugAndIsDeletedFalse(anyString())).thenReturn(false);
        assertThrows(SlugGenerationException.class, () -> service.generateUniqueSlug("!!!"));
    }

    @Test
    void inputWithOnlySpecialCharsThrows() {
        when(repo.existsBySlugAndIsDeletedFalse(anyString())).thenReturn(false);
        assertThrows(SlugGenerationException.class, () -> service.generateUniqueSlug("@#$%"));
    }

    @Test
    void reservedKeywordGetsSuffix() {
        when(repo.existsBySlugAndIsDeletedFalse(anyString())).thenReturn(false);
        String slug = service.generateUniqueSlug("admin");
        assertFalse(slug.equals("admin"), "Reserved keyword should get a suffix");
        assertTrue(slug.startsWith("admin"), "Slug should start with reserved keyword");
    }

    @Test
    void slugTruncatedAt50Chars() {
        when(repo.existsBySlugAndIsDeletedFalse(anyString())).thenReturn(false);
        String longName = "a".repeat(100);
        String slug = service.sanitizeSlug(longName);
        assertTrue(slug.length() <= 50, "Slug should be truncated to 50 chars");
    }

    @Test
    void sameInputProducesSameBaseSlug() {
        String slug1 = service.sanitizeSlug("Acme Coffee Shop");
        String slug2 = service.sanitizeSlug("Acme Coffee Shop");
        assertEquals(slug1, slug2, "Same input should produce same base slug");
    }

    @Test
    void numericSuffixIncrements() {
        // First call returns true (slug exists), second returns false
        when(repo.existsBySlugAndIsDeletedFalse("acme")).thenReturn(true);
        when(repo.existsBySlugAndIsDeletedFalse("acme-1")).thenReturn(false);
        String slug = service.generateUniqueSlug("acme");
        assertEquals("acme-1", slug);
    }

    // -------------------------------------------------------------------------
    // Public workspace slug (Step 02 publish)
    // -------------------------------------------------------------------------

    @Test
    void publicSlug_fromBusinessName() {
        when(workspaceRepository.existsByPublicSlug("nkandu-fashion")).thenReturn(false);
        String slug = service.generateUniquePublicSlug("Nkandu Fashion");
        assertEquals("nkandu-fashion", slug);
    }

    @Test
    void publicSlug_appendsHexSuffixOnConflict() {
        when(workspaceRepository.existsByPublicSlug("nkandu-fashion")).thenReturn(true);
        when(workspaceRepository.existsByPublicSlug(anyString())).thenAnswer(inv -> {
            String candidate = inv.getArgument(0);
            return "nkandu-fashion".equals(candidate);
        });

        String slug = service.generateUniquePublicSlug("Nkandu Fashion");
        assertTrue(slug.startsWith("nkandu-fashion-"));
        assertEquals(4, slug.substring("nkandu-fashion-".length()).length());
    }

    @Test
    void publicSlug_reservedKeywordGetsStoreSuffix() {
        when(workspaceRepository.existsByPublicSlug("store-store")).thenReturn(false);
        String slug = service.generateUniquePublicSlug("store");
        assertEquals("store-store", slug);
    }

    @Test
    void publicSlug_tooShortThrowsUnavailable() {
        assertThrows(PublicSlugUnavailableException.class,
                () -> service.generateUniquePublicSlug("ab"));
    }

    @Test
    void publicSlug_exhaustedRetriesThrowsUnavailable() {
        ReflectionTestUtils.setField(service, "maxRetries", 2);
        when(workspaceRepository.existsByPublicSlug(anyString())).thenReturn(true);

        assertThrows(PublicSlugUnavailableException.class,
                () -> service.generateUniquePublicSlug("nkandu-fashion"));
    }
}
