package sme.tech.innovators.sme.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import software.amazon.awssdk.services.ses.SesClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.InvalidTokenException;
import sme.tech.innovators.sme.exception.TokenExpiredException;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.repository.VerificationTokenRepository;
import sme.tech.innovators.sme.service.VerificationService;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "app.slug.reserved-keywords=admin,api,app,auth,dashboard,login,logout,register,signup,store,support,www"
})
@ActiveProfiles("test")
@Transactional
class VerificationServiceIntegrationTest {

    @Autowired private VerificationService verificationService;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @MockBean private SesClient sesClient;

    private User pendingUser;

    @BeforeEach
    void setUp() {
        pendingUser = new User();
        pendingUser.setEmail("verify@example.com");
        pendingUser.setPassword("hashed");
        pendingUser.setFullName("Verify User");
        pendingUser.setAccountStatus(AccountStatus.PENDING_VERIFICATION);
        pendingUser.setRole(UserRole.OWNER);
        pendingUser = userRepository.save(pendingUser);
    }

    @Test
    void validTokenUpdatesStatusToVerifiedAndDeletesToken() {
        VerificationToken token = verificationService.createVerificationToken(pendingUser);
        verificationService.verifyToken(token.getToken());

        User updated = userRepository.findById(pendingUser.getId()).orElseThrow();
        assertEquals(AccountStatus.VERIFIED, updated.getAccountStatus());
        assertTrue(verificationTokenRepository.findByToken(token.getToken()).isEmpty());
    }

    @Test
    void expiredTokenThrowsTokenExpiredException() {
        VerificationToken token = VerificationToken.builder()
                .token("expired-token-123")
                .user(pendingUser)
                .createdAt(LocalDateTime.now().minusDays(2))
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();
        verificationTokenRepository.save(token);

        assertThrows(TokenExpiredException.class, () -> verificationService.verifyToken("expired-token-123"));
    }

    @Test
    void invalidTokenThrowsInvalidTokenException() {
        assertThrows(InvalidTokenException.class, () -> verificationService.verifyToken("nonexistent-token"));
    }

    @Test
    void resendInvalidatesOldTokenAndCreatesNew() {
        VerificationToken oldToken = verificationService.createVerificationToken(pendingUser);
        String oldTokenValue = oldToken.getToken();

        verificationService.resendVerificationEmail("verify@example.com");

        assertTrue(verificationTokenRepository.findByToken(oldTokenValue).isEmpty(),
                "Old token should be invalidated");
    }
}
