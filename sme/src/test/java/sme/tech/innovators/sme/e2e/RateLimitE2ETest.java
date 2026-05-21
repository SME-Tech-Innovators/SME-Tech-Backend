package sme.tech.innovators.sme.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import software.amazon.awssdk.services.ses.SesClient;
import org.springframework.test.context.ActiveProfiles;
import sme.tech.innovators.sme.service.RateLimitService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.slug.reserved-keywords=admin,api,app,auth,dashboard,login,logout,register,signup,store,support,www"})
@ActiveProfiles("test")
class RateLimitE2ETest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private RateLimitService rateLimitService;
    @MockBean private SesClient sesClient;

    @Test
    void correlationIdHeaderPresentInAllResponses() {
        Map<String, Object> request = Map.of(
            "user", Map.of("email", "corr@example.com", "password", "SecurePass1!", "fullName", "Corr User"),
            "business", Map.of("name", "Corr Business")
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/register", request, Map.class);

        assertNotNull(response.getHeaders().getFirst("X-Correlation-ID"),
                "X-Correlation-ID header should be present in response");
    }

    @Test
    void sixthRegistrationAttemptFromSameIpReturns429() {
        // Use a unique IP that won't be used by other tests
        String testIp = "10.99.99.99";

        // Pre-populate the IP counter to the limit (5) by simulating 5 failed attempts
        // This bypasses the need to make actual HTTP requests that might reset the counter
        for (int i = 0; i < 5; i++) {
            rateLimitService.incrementAttempt(testIp, null);
        }

        // Now the next request from this IP should be rate limited
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", testIp);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> request = Map.of(
            "user", Map.of("email", "rl-blocked-direct@example.com", "password", "SecurePass1!", "fullName", "Blocked User"),
            "business", Map.of("name", "Blocked Business")
        );
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/register", new HttpEntity<>(request, headers), Map.class);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertNotNull(error);
        assertEquals("RATE_LIMIT_EXCEEDED", error.get("code"));
    }
}
