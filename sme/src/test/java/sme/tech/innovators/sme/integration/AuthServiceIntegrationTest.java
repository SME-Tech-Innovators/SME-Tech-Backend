package sme.tech.innovators.sme.integration;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import sme.tech.innovators.sme.config.TestAwsS3Config;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.request.LoginRequest;
import sme.tech.innovators.sme.dto.response.AuthResponse;
import sme.tech.innovators.sme.entity.AccountStatus;
import sme.tech.innovators.sme.entity.RefreshToken;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.entity.UserRole;
import sme.tech.innovators.sme.exception.AccountNotVerifiedException;
import sme.tech.innovators.sme.exception.TokenRevokedException;
import sme.tech.innovators.sme.repository.RefreshTokenRepository;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.AuthService;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "app.slug.reserved-keywords=admin,api,app,auth,dashboard,login,logout,register,signup,store,support,www"
})
@ActiveProfiles("test")
@Transactional
@Import(TestAwsS3Config.class)
class AuthServiceIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockBean private SesClient sesClient;
    @MockBean private S3Client s3Client;
    @MockBean private S3Presigner s3Presigner;

    private User verifiedUser;
    private HttpServletRequest mockHttpRequest;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest httpReq = new MockHttpServletRequest();
        httpReq.setRemoteAddr("127.0.0.1");
        mockHttpRequest = httpReq;
        verifiedUser = new User();
        verifiedUser.setEmail("auth@example.com");
        verifiedUser.setPassword(passwordEncoder.encode("SecurePass1!"));
        verifiedUser.setFullName("Auth User");
        verifiedUser.setAccountStatus(AccountStatus.VERIFIED);
        verifiedUser.setRole(UserRole.OWNER);
        verifiedUser = userRepository.save(verifiedUser);
    }

    @Test
    void loginWithValidCredentialsReturnsBothTokens() {
        LoginRequest req = new LoginRequest();
        req.setEmail("auth@example.com");
        req.setPassword("SecurePass1!");

        AuthResponse response = authService.login(req, mockHttpRequest);
        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals(900L, response.getExpiresIn());
    }

    @Test
    void loginWithUnverifiedAccountThrows() {
        verifiedUser.setAccountStatus(AccountStatus.PENDING_VERIFICATION);
        userRepository.save(verifiedUser);

        LoginRequest req = new LoginRequest();
        req.setEmail("auth@example.com");
        req.setPassword("SecurePass1!");

        assertThrows(AccountNotVerifiedException.class, () -> authService.login(req, mockHttpRequest));
    }

    @Test
    void loginWithWrongPasswordThrows() {
        LoginRequest req = new LoginRequest();
        req.setEmail("auth@example.com");
        req.setPassword("WrongPass1!");

        assertThrows(BadCredentialsException.class, () -> authService.login(req, mockHttpRequest));
    }

    @Test
    void refreshWithValidTokenReturnsNewAccessToken() {
        LoginRequest req = new LoginRequest();
        req.setEmail("auth@example.com");
        req.setPassword("SecurePass1!");
        AuthResponse loginResponse = authService.login(req, mockHttpRequest);

        AuthResponse refreshResponse = authService.refresh(loginResponse.getRefreshToken());
        assertNotNull(refreshResponse.getAccessToken());
    }

    @Test
    void refreshWithRevokedTokenThrows() {
        RefreshToken token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(verifiedUser.getId())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(true)
                .build();
        refreshTokenRepository.save(token);

        assertThrows(TokenRevokedException.class, () -> authService.refresh(token.getToken()));
    }

    @Test
    void logoutMarksTokenRevoked() {
        LoginRequest req = new LoginRequest();
        req.setEmail("auth@example.com");
        req.setPassword("SecurePass1!");
        AuthResponse loginResponse = authService.login(req, mockHttpRequest);

        authService.logout(loginResponse.getRefreshToken());

        RefreshToken token = refreshTokenRepository.findByToken(loginResponse.getRefreshToken()).orElseThrow();
        assertTrue(token.isRevoked());
    }
}
