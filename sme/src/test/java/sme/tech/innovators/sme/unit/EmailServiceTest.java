package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;
import sme.tech.innovators.sme.service.AuditService;
import sme.tech.innovators.sme.service.EmailService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EmailService}.
 *
 * No Spring context is loaded — SesClient and AuditService are mocked with
 * Mockito, and @Value fields are injected via ReflectionTestUtils.
 *
 * Validates: Requirements 4.6, 4.7, 4.8, 4.9
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private SesClient sesClient;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        // Inject @Value fields that would normally be bound by Spring
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@example.com");
        ReflectionTestUtils.setField(emailService, "senderName", "SME Operations");
    }

    // -------------------------------------------------------------------------
    // sendVerificationEmail — correct verification link in plain-text body
    // -------------------------------------------------------------------------

    /**
     * Validates: Requirement 4.6
     *
     * Calls sendVerificationEmail and asserts that the captured SendEmailRequest
     * plain-text body contains the expected verification link.
     */
    @Test
    void sendVerificationEmail_buildsRequestWithCorrectVerificationLink() {
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);

        emailService.sendVerificationEmail("test@example.com", "John", "abc123");

        verify(sesClient).sendEmail(captor.capture());
        SendEmailRequest captured = captor.getValue();

        String textBody = captured.message().body().text().data();
        String expectedLink = "http://localhost:8080/verify?token=abc123";

        assertTrue(textBody.contains(expectedLink),
                "Plain-text body should contain the verification link: " + expectedLink
                        + "\nActual body: " + textBody);
    }

    // -------------------------------------------------------------------------
    // sendWelcomeEmail — correct public link in plain-text body
    // -------------------------------------------------------------------------

    /**
     * Validates: Requirement 4.7
     *
     * Calls sendWelcomeEmail and asserts that the captured SendEmailRequest
     * plain-text body contains the supplied public link.
     */
    @Test
    void sendWelcomeEmail_buildsRequestWithCorrectPublicLink() {
        String publicLink = "https://sme-operations.netlify.app/store/my-biz";
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);

        emailService.sendWelcomeEmail("test@example.com", "John", publicLink);

        verify(sesClient).sendEmail(captor.capture());
        SendEmailRequest captured = captor.getValue();

        String textBody = captured.message().body().text().data();

        assertTrue(textBody.contains(publicLink),
                "Plain-text body should contain the public link: " + publicLink
                        + "\nActual body: " + textBody);
    }

    // -------------------------------------------------------------------------
    // sendWithRetry — no exception propagated when SES always throws
    // -------------------------------------------------------------------------

    /**
     * Validates: Requirement 4.8
     *
     * Configures SesClient to always throw SesException and asserts that
     * sendVerificationEmail does not propagate any exception to the caller.
     */
    @Test
    void sendWithRetry_doesNotPropagateException_whenSesAlwaysThrows() {
        doThrow(SesException.builder().message("SES error").build())
                .when(sesClient).sendEmail(any(SendEmailRequest.class));

        assertDoesNotThrow(
                () -> emailService.sendVerificationEmail("test@example.com", "John", "abc123"),
                "EmailService must swallow SES exceptions and not propagate them to the caller"
        );
    }

    // -------------------------------------------------------------------------
    // sendWithRetry — auditService called exactly 3 times when SES always throws
    // -------------------------------------------------------------------------

    /**
     * Validates: Requirements 4.8, 4.9
     *
     * Configures SesClient to always throw SesException and asserts that
     * auditService.logSecurityEvent is invoked exactly 3 times (once per retry
     * attempt).
     */
    @Test
    void sendWithRetry_callsAuditService_exactlyThreeTimes_whenSesAlwaysThrows() {
        doThrow(SesException.builder().message("SES error").build())
                .when(sesClient).sendEmail(any(SendEmailRequest.class));

        emailService.sendVerificationEmail("test@example.com", "John", "abc123");

        verify(auditService, times(3))
                .logSecurityEvent(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendOrderConfirmationEmail_includesOrderNumberAndTotal() {
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);

        emailService.sendOrderConfirmationEmail(
                "ada@example.com",
                "Ada",
                "Bridge Labs",
                "bridge-labs",
                "ORD-20260731-12345",
                java.util.List.of(new EmailService.OrderLine("Tee", 2, 5000, "ZAR")),
                5000,
                "ZAR");

        verify(sesClient).sendEmail(captor.capture());
        SendEmailRequest captured = captor.getValue();
        assertTrue(captured.message().subject().data().contains("ORD-20260731-12345"));
        String text = captured.message().body().text().data();
        assertTrue(text.contains("ZAR 50.00"));
        assertTrue(text.contains("Tee × 2"));
        assertTrue(text.contains("/s/bridge-labs"));
        assertTrue(text.contains("Payment status: paid"));
    }

    @Test
    void sendOutOfStockEmail_includesProductAndInventoryLink() {
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        UUID workspaceId = UUID.randomUUID();

        emailService.sendOutOfStockEmail(
                "merchant@example.com",
                "Mona",
                "Classic Tee",
                "TEE-1",
                "Something Good",
                workspaceId);

        verify(sesClient).sendEmail(captor.capture());
        SendEmailRequest captured = captor.getValue();
        assertTrue(captured.message().subject().data().contains("Out of stock: Classic Tee"));
        String text = captured.message().body().text().data();
        assertTrue(text.contains("SKU: TEE-1"));
        assertTrue(text.contains("quantity available: 0"));
        assertTrue(text.contains("/dashboard/" + workspaceId + "?section=products"));
    }
}
