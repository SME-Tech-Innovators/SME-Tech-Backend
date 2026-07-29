package sme.tech.innovators.sme.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import sme.tech.innovators.sme.config.TestAwsS3Config;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.repository.BusinessRepository;
import sme.tech.innovators.sme.repository.UserRepository;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.slug.reserved-keywords=admin,api,app,auth,dashboard,login,logout,register,signup,store,support,www"})
@ActiveProfiles("test")
@Import(TestAwsS3Config.class)
class PublicStoreE2ETest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockBean private SesClient sesClient;
    @MockBean private S3Client s3Client;
    @MockBean private S3Presigner s3Presigner;

    @BeforeEach
    void setUp() {
        if (businessRepository.findBySlugAndIsDeletedFalse("public-store-test").isEmpty()) {
            User owner = new User();
            owner.setEmail("store-owner@example.com");
            owner.setPassword(passwordEncoder.encode("SecurePass1!"));
            owner.setFullName("Store Owner");
            owner.setAccountStatus(AccountStatus.VERIFIED);
            owner.setRole(UserRole.OWNER);
            owner = userRepository.save(owner);

            Business business = new Business();
            business.setName("Public Store Test");
            business.setSlug("public-store-test");
            business.setPublicLink("http://localhost:8080/store/public-store-test");
            business.setDescription("A test store");
            business.setOwner(owner);
            businessRepository.save(business);
        }
    }

    @Test
    void getStoreReturns200WithPublicBusinessDto() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/public/store/public-store-test", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("name"));
        assertNotNull(data.get("slug"));
        assertNotNull(data.get("publicLink"));
        // Sensitive fields must NOT be present
        assertNull(data.get("id"));
        assertNull(data.get("ownerId"));
        assertNull(data.get("email"));
    }

    @Test
    void nonExistentSlugReturns404() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/public/store/nonexistent-slug-xyz", Map.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void publicEndpointRequiresNoAuthorizationHeader() {
        // No auth header — should still return 200
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/public/store/public-store-test", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
