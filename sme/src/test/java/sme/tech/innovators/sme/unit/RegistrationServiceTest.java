package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import sme.tech.innovators.sme.dto.request.BusinessRegistrationRequest;
import sme.tech.innovators.sme.dto.request.RegistrationRequest;
import sme.tech.innovators.sme.dto.response.RegistrationResponse;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.entity.VerificationToken;
import sme.tech.innovators.sme.repository.BusinessRepository;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.AuditService;
import sme.tech.innovators.sme.service.EmailService;
import sme.tech.innovators.sme.service.RateLimitService;
import sme.tech.innovators.sme.service.RegistrationService;
import sme.tech.innovators.sme.service.SlugGeneratorService;
import sme.tech.innovators.sme.service.VerificationService;
import sme.tech.innovators.sme.validator.PasswordValidator;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RegistrationService} — public link construction.
 *
 * Validates that the publicLink in the RegistrationResponse uses
 * app.frontend-url as its base (Requirements 7.1, 7.3).
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final String FRONTEND_URL = "https://sme-operations.netlify.app";
    private static final String SLUG = "my-biz";

    @Mock
    private UserRepository userRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordValidator passwordValidator;

    @Mock
    private SlugGeneratorService slugGeneratorService;

    @Mock
    private VerificationService verificationService;

    @Mock
    private EmailService emailService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        // Inject the @Value field that would normally be bound by Spring
        ReflectionTestUtils.setField(registrationService, "frontendUrl", FRONTEND_URL);

        // Build a mock User with a UUID so the service can proceed past saveAndFlush
        User mockUser = User.builder()
                .email("owner@example.com")
                .password("encoded-password")
                .fullName("Test Owner")
                .build();
        ReflectionTestUtils.setField(mockUser, "id", UUID.randomUUID());

        // Build a mock VerificationToken
        VerificationToken mockToken = VerificationToken.builder()
                .token("mock-verification-token")
                .user(mockUser)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        // Wire up all mocks needed for registerUserAndBusiness to complete
        when(userRepository.existsByEmailAndIsDeletedFalse(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(mockUser);
        when(slugGeneratorService.generateUniqueSlug(anyString())).thenReturn(SLUG);
        when(businessRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationService.createVerificationToken(any(User.class))).thenReturn(mockToken);
        doNothing().when(passwordValidator).validate(anyString());
        doNothing().when(emailService).sendVerificationEmail(anyString(), anyString(), anyString());
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString());
        doNothing().when(rateLimitService).incrementAttempt(anyString(), anyString());
        doNothing().when(rateLimitService).resetAttempts(anyString(), anyString());
        doNothing().when(auditService).logRegistrationAttempt(anyString(), anyString());
        doNothing().when(auditService).logRegistrationSuccess(any(), any(), anyString(), anyString());
    }

    private RegistrationRequest buildRequest() {
        BusinessRegistrationRequest biz = new BusinessRegistrationRequest();
        biz.setEmail("owner@example.com");
        biz.setPassword("Password1!");
        biz.setFullName("Test Owner");
        biz.setBusinessName("My Biz");
        biz.setDescription("A test business");

        RegistrationRequest request = new RegistrationRequest();
        request.setBusiness(biz);
        return request;
    }

    // -------------------------------------------------------------------------
    // publicLink starts with frontendUrl + "/store/"
    // -------------------------------------------------------------------------

    /**
     * Validates: Requirement 7.1, 7.3
     *
     * The publicLink in the returned RegistrationResponse must start with
     * the configured frontend URL followed by "/store/".
     */
    @Test
    void register_publicLinkStartsWithFrontendUrl() {
        RegistrationResponse response = registrationService.registerUserAndBusiness(
                buildRequest(), "127.0.0.1");

        assertTrue(
                response.getPublicLink().startsWith(FRONTEND_URL + "/store/"),
                "publicLink should start with '" + FRONTEND_URL + "/store/' but was: "
                        + response.getPublicLink()
        );
    }

    // -------------------------------------------------------------------------
    // publicLink ends with the generated slug
    // -------------------------------------------------------------------------

    /**
     * Validates: Requirement 7.1, 7.3
     *
     * The publicLink in the returned RegistrationResponse must end with
     * the slug returned by SlugGeneratorService.
     */
    @Test
    void register_publicLinkEndsWithSlug() {
        RegistrationResponse response = registrationService.registerUserAndBusiness(
                buildRequest(), "127.0.0.1");

        assertTrue(
                response.getPublicLink().endsWith(SLUG),
                "publicLink should end with slug '" + SLUG + "' but was: "
                        + response.getPublicLink()
        );
    }
}
