package sme.tech.innovators.sme.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import software.amazon.awssdk.services.ses.SesClient;
import org.springframework.test.context.ActiveProfiles;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.repository.VerificationTokenRepository;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RegistrationE2ETest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @MockBean private SesClient sesClient;

    @Test
    void registerReturns201WithApiResponseEnvelope() {
        Map<String, Object> request = Map.of(
            "user", Map.of("email", "e2e-reg@example.com", "password", "SecurePass1!", "fullName", "E2E User"),
            "business", Map.of("name", "E2E Business")
        );

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/auth/register", request, Map.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().get("success"));
        assertNotNull(response.getBody().get("data"));
        assertNull(response.getBody().get("error"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void verifyTokenReturns302AndUpdatesAccountStatus() {
        Map<String, Object> request = Map.of(
            "business", Map.of(
                "email", "e2e-verify@example.com",
                "password", "SecurePass1!",
                "fullName", "Verify E2E",
                "businessName", "Verify E2E Business"
            )
        );
        restTemplate.postForEntity("/api/v1/auth/register", request, Map.class);

        var user = userRepository.findByEmailAndIsDeletedFalse("e2e-verify@example.com").orElseThrow();
        var token = verificationTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(user.getId()))
                .findFirst().orElseThrow();

        // Disable redirect following so we can assert the 302 directly
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setOutputStreaming(false);
        restTemplate.getRestTemplate().setRequestFactory(factory);

        ResponseEntity<Void> verifyResponse = restTemplate.getForEntity(
                "/api/v1/auth/verify?token=" + token.getToken(), Void.class);

        assertEquals(HttpStatus.FOUND, verifyResponse.getStatusCode());
        assertNotNull(verifyResponse.getHeaders().getLocation(),
                "Location header must be present on 302 redirect");

        var updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(sme.tech.innovators.sme.entity.AccountStatus.VERIFIED, updatedUser.getAccountStatus());
    }

    @Test
    void allResponsesHaveApiResponseEnvelopeFields() {
        Map<String, Object> request = Map.of(
            "user", Map.of("email", "e2e-envelope@example.com", "password", "SecurePass1!", "fullName", "Envelope User"),
            "business", Map.of("name", "Envelope Business")
        );

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/auth/register", request, Map.class);

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("success"));
        assertTrue(body.containsKey("data"));
        assertTrue(body.containsKey("error"));
        assertTrue(body.containsKey("timestamp"));
    }
}
