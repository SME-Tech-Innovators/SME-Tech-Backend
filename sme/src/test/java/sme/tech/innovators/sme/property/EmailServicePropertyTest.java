package sme.tech.innovators.sme.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;
import sme.tech.innovators.sme.service.AuditService;
import sme.tech.innovators.sme.service.EmailService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for {@link EmailService} using jqwik 1.8.2.
 *
 * No Spring context is loaded. EmailService is instantiated directly with
 * Mockito mocks; @Value fields are injected via ReflectionTestUtils.
 *
 * Properties covered:
 *   Property 1 - Verification link embeds base URL and token (Requirements 5.1, 5.2)
 *   Property 3 - SendEmailRequest is correctly constructed (Requirements 4.1-4.4, 4.10)
 *   Property 4 - Retry exhaustion triggers audit without propagation (Requirements 4.6-4.9)
 *   Property 5 - Retry count matches failure count (Requirement 4.6)
 */
public class EmailServicePropertyTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String FROM_EMAIL = "noreply@example.com";
    private static final String SENDER_NAME = "SME Operations";

    private EmailService buildEmailService(SesClient sesClient, AuditService auditService) {
        EmailService service = new EmailService(sesClient, auditService);
        ReflectionTestUtils.setField(service, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(service, "fromEmail", FROM_EMAIL);
        ReflectionTestUtils.setField(service, "senderName", SENDER_NAME);
        return service;
    }

    @Provide
    Arbitrary<String> validEmails() {
        return Arbitraries.of(
                "alice@example.com", "bob@test.org", "carol@domain.net",
                "dave@company.io", "eve@mail.co", "frank@service.com",
                "grace@startup.dev", "henry@corp.biz"
        );
    }

    // -------------------------------------------------------------------------
    // Property 1: Verification link embeds base URL and token
    // Feature: aws-ses-email-migration
    // Validates: Requirements 5.1, 5.2
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void verificationLinkContainsBaseUrlAndToken(
            @ForAll @StringLength(min = 1, max = 100) String token,
            @ForAll("validEmails") String toEmail,
            @ForAll @StringLength(min = 1, max = 50) String fullName) {

        SesClient sesClient = Mockito.mock(SesClient.class);
        AuditService auditService = Mockito.mock(AuditService.class);
        EmailService emailService = buildEmailService(sesClient, auditService);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);

        emailService.sendVerificationEmail(toEmail, fullName, token);

        verify(sesClient).sendEmail(captor.capture());
        String textBody = captor.getValue().message().body().text().data();
        String expectedLink = BASE_URL + "/api/v1/auth/verify?token=" + token;

        assertTrue(textBody.contains(expectedLink),
                "Plain-text body must contain verification link.\nExpected: " + expectedLink
                        + "\nActual body: " + textBody);
    }

    // -------------------------------------------------------------------------
    // Property 3: SendEmailRequest is correctly constructed for all email types
    // Feature: aws-ses-email-migration
    // Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.10
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void sendEmailRequestHasCorrectStructure(
            @ForAll("validEmails") String toEmail,
            @ForAll @StringLength(min = 1, max = 50) String fullName,
            @ForAll @StringLength(min = 1, max = 100) String token) {

        SesClient sesClient = Mockito.mock(SesClient.class);
        AuditService auditService = Mockito.mock(AuditService.class);
        EmailService emailService = buildEmailService(sesClient, auditService);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);

        emailService.sendVerificationEmail(toEmail, fullName, token);

        verify(sesClient).sendEmail(captor.capture());
        SendEmailRequest req = captor.getValue();

        assertTrue(req.destination().toAddresses().contains(toEmail),
                "destination.toAddresses must contain: " + toEmail);

        String source = req.source();
        assertTrue(source.matches(".+ <.+@.+>"),
                "source must match display-name format but was: " + source);

        assertFalse(req.message().subject().data().isBlank(), "subject must not be blank");
        assertFalse(req.message().body().text().data().isBlank(), "plain-text body must not be blank");
        assertFalse(req.message().body().html().data().isBlank(), "HTML body must not be blank");
    }

    // -------------------------------------------------------------------------
    // Property 4: Retry exhaustion triggers audit and error log without propagation
    // Feature: aws-ses-email-migration
    // Validates: Requirements 4.6, 4.7, 4.8, 4.9
    // -------------------------------------------------------------------------

    @Property(tries = 10)
    void retryExhaustionAuditsAndDoesNotPropagate(
            @ForAll("validEmails") String toEmail,
            @ForAll @StringLength(min = 1, max = 30) String fullName) {

        SesClient sesClient = Mockito.mock(SesClient.class);
        AuditService auditService = Mockito.mock(AuditService.class);
        EmailService emailService = buildEmailService(sesClient, auditService);

        doThrow(SesException.builder().message("SES error").build())
                .when(sesClient).sendEmail(any(SendEmailRequest.class));

        assertDoesNotThrow(
                () -> emailService.sendVerificationEmail(toEmail, fullName, "test-token"),
                "EmailService must not propagate SES exceptions to the caller"
        );

        verify(auditService, times(3))
                .logSecurityEvent(anyString(), anyString(), anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // Property 5: Retry count matches failure count
    // Feature: aws-ses-email-migration
    // Validates: Requirement 4.6
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    void retryCountMatchesFailureCount(@ForAll @IntRange(min = 0, max = 2) int failureCount) {
        SesClient sesClient = Mockito.mock(SesClient.class);
        AuditService auditService = Mockito.mock(AuditService.class);
        EmailService emailService = buildEmailService(sesClient, auditService);

        int[] callCount = {0};
        doAnswer(invocation -> {
            callCount[0]++;
            if (callCount[0] <= failureCount) {
                throw SesException.builder().message("SES error attempt " + callCount[0]).build();
            }
            return null;
        }).when(sesClient).sendEmail(any(SendEmailRequest.class));

        emailService.sendVerificationEmail("test@example.com", "Test User", "token-abc");

        verify(sesClient, times(failureCount + 1)).sendEmail(any(SendEmailRequest.class));
    }
}
