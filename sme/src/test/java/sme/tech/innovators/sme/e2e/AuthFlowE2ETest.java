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
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.JwtService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.slug.reserved-keywords=admin,api,app,auth,dashboard,login,logout,register,signup,store,support,www"})
@ActiveProfiles("test")
@Import(TestAwsS3Config.class)
class AuthFlowE2ETest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @MockBean private SesClient sesClient;
    @MockBean private S3Client s3Client;
    @MockBean private S3Presigner s3Presigner;

    @BeforeEach
    void setUp() {
        if (userRepository.findByEmailAndIsDeletedFalse("flow@example.com").isEmpty()) {
            User user = new User();
            user.setEmail("flow@example.com");
            user.setPassword(passwordEncoder.encode("SecurePass1!"));
            user.setFullName("Flow User");
            user.setAccountStatus(AccountStatus.VERIFIED);
            user.setRole(UserRole.OWNER);
            userRepository.save(user);
        }
    }

    @Test
    void loginReturns200WithAccessAndRefreshTokens() {
        Map<String, String> loginReq = Map.of("email", "flow@example.com", "password", "SecurePass1!");
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/auth/login", loginReq, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("accessToken"));
        assertNotNull(data.get("refreshToken"));
    }

    @Test
    void refreshReturnsNewAccessToken() {
        Map<String, String> loginReq = Map.of("email", "flow@example.com", "password", "SecurePass1!");
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity("/api/v1/auth/login", loginReq, Map.class);
        Map<String, Object> loginData = (Map<String, Object>) loginResponse.getBody().get("data");
        String refreshToken = (String) loginData.get("refreshToken");

        Map<String, String> refreshReq = Map.of("refreshToken", refreshToken);
        ResponseEntity<Map> refreshResponse = restTemplate.postForEntity("/api/v1/auth/refresh", refreshReq, Map.class);

        assertEquals(HttpStatus.OK, refreshResponse.getStatusCode());
        Map<String, Object> refreshData = (Map<String, Object>) refreshResponse.getBody().get("data");
        assertNotNull(refreshData.get("accessToken"));
    }

    @Test
    void logoutRevokesToken() {
        Map<String, String> loginReq = Map.of("email", "flow@example.com", "password", "SecurePass1!");
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity("/api/v1/auth/login", loginReq, Map.class);
        Map<String, Object> loginData = (Map<String, Object>) loginResponse.getBody().get("data");
        String refreshToken = (String) loginData.get("refreshToken");

        Map<String, String> logoutReq = Map.of("refreshToken", refreshToken);
        ResponseEntity<Map> logoutResponse = restTemplate.postForEntity("/api/v1/auth/logout", logoutReq, Map.class);
        assertEquals(HttpStatus.OK, logoutResponse.getStatusCode());

        // Subsequent refresh with revoked token should return 401
        Map<String, String> refreshReq = Map.of("refreshToken", refreshToken);
        ResponseEntity<Map> refreshResponse = restTemplate.postForEntity("/api/v1/auth/refresh", refreshReq, Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, refreshResponse.getStatusCode());
    }

    @Test
    void protectedEndpointWithValidJwtReturnsNot401() {
        // Login to get a valid access token
        Map<String, String> loginReq = Map.of("email", "flow@example.com", "password", "SecurePass1!");
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity("/api/v1/auth/login", loginReq, Map.class);
        Map<String, Object> loginData = (Map<String, Object>) loginResponse.getBody().get("data");
        String accessToken = (String) loginData.get("accessToken");

        // Access a protected endpoint with valid JWT — should NOT return 401
        // 404 means the endpoint doesn't exist but auth passed
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/protected-ping", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertNotEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void protectedEndpointWithExpiredJwtReturns401() {
        // A well-formed JWT with an expired timestamp and invalid signature
        // Spring Security will reject it as unauthorized
        String expiredToken = "eyJhbGciOiJIUzI1NiJ9." +
                "eyJ1c2VySWQiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDEiLCJlbWFpbCI6InRlc3RAZXhhbXBsZS5jb20iLCJyb2xlIjoiT1dORVIiLCJpYXQiOjE2MDAwMDAwMDAsImV4cCI6MTYwMDAwMDkwMH0." +
                "invalid-signature";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(expiredToken);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/protected-ping", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
