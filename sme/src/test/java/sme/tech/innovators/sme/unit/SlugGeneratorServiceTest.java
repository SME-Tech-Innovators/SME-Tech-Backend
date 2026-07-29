package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
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

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(BusinessRepository.class);
        WorkspaceRepository workspaceRepository = Mockito.mock(WorkspaceRepository.class);
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
}
