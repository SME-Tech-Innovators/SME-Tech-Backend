package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    public record OrderLine(String title, int quantity, int lineTotalAmount, String currency) {}

    private final SesClient sesClient;
    private final AuditService auditService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.email.from}")
    private String fromEmail;

    @Value("${app.email.sender-name:SME Operations}")
    private String senderName;

    @Async("emailTaskExecutor")
    public void sendVerificationEmail(String toEmail, String fullName, String token) {
        String normalizedFrontendUrl = normalizeFrontendUrl();
        String verificationLink = normalizedFrontendUrl + "/verify?token=" + token;
        String subject = "Verify your email address";
        String textBody = "Hi " + fullName + ",\n\nPlease verify your email by clicking the link below:\n\n"
                + verificationLink + "\n\nThis link expires in 24 hours.\n\nThank you!";
        String htmlBody = "<p>Hi " + fullName + ",</p>"
                + "<p>Please verify your email by clicking the link below:</p>"
                + "<p><a href=\"" + verificationLink + "\">Verify Email</a></p>"
                + "<p>This link expires in 24 hours.</p><p>Thank you!</p>";
        sendWithRetry(toEmail, subject, textBody, htmlBody, "VERIFICATION_EMAIL");
    }

    @Async("emailTaskExecutor")
    public void sendWelcomeEmail(String toEmail, String fullName, String publicLink) {
        String subject = "Welcome to SME Operations!";
        String textBody = "Hi " + fullName + ",\n\nYour business is now live!\n\nPublic link: " + publicLink
                + "\n\nThank you for joining us!";
        String htmlBody = "<p>Hi " + fullName + ",</p>"
                + "<p>Your business is now live!</p>"
                + "<p>Public link: <a href=\"" + publicLink + "\">" + publicLink + "</a></p>"
                + "<p>Thank you for joining us!</p>";
        sendWithRetry(toEmail, subject, textBody, htmlBody, "WELCOME_EMAIL");
    }

    @Async("emailTaskExecutor")
    public void sendOrderConfirmationEmail(String toEmail,
                                           String customerName,
                                           String storeName,
                                           String storeSlug,
                                           String orderNumber,
                                           List<OrderLine> lines,
                                           int totalAmount,
                                           String currency) {
        String name = customerName != null && !customerName.isBlank() ? customerName : "there";
        String store = storeName != null && !storeName.isBlank() ? storeName : "the store";
        String subject = "Order confirmed — " + orderNumber + " · " + store;

        StringBuilder itemsText = new StringBuilder();
        StringBuilder itemsHtml = new StringBuilder("<ul>");
        if (lines != null) {
            for (OrderLine line : lines) {
                String lineTotal = formatMoney(line.lineTotalAmount(), line.currency());
                itemsText.append("- ")
                        .append(line.title())
                        .append(" × ")
                        .append(line.quantity())
                        .append(" — ")
                        .append(lineTotal)
                        .append("\n");
                itemsHtml.append("<li>")
                        .append(escapeHtml(line.title()))
                        .append(" × ")
                        .append(line.quantity())
                        .append(" — ")
                        .append(escapeHtml(lineTotal))
                        .append("</li>");
            }
        }
        itemsHtml.append("</ul>");

        String storeLink = null;
        if (storeSlug != null && !storeSlug.isBlank()) {
            storeLink = normalizeFrontendUrl() + "/s/" + storeSlug;
        }

        String totalFormatted = formatMoney(totalAmount, currency);
        String textBody = "Hi " + name + ",\n\n"
                + "Thank you for your order from " + store + ".\n\n"
                + "Order number: " + orderNumber + "\n"
                + "Payment status: paid\n\n"
                + "Items:\n" + itemsText
                + "\nTotal: " + totalFormatted + "\n"
                + (storeLink != null ? "\nVisit the store: " + storeLink + "\n" : "")
                + "\nThank you!";

        String htmlBody = "<p>Hi " + escapeHtml(name) + ",</p>"
                + "<p>Thank you for your order from <strong>" + escapeHtml(store) + "</strong>.</p>"
                + "<p>Order number: <strong>" + escapeHtml(orderNumber) + "</strong><br/>"
                + "Payment status: <strong>paid</strong></p>"
                + "<p>Items:</p>" + itemsHtml
                + "<p>Total: <strong>" + escapeHtml(totalFormatted) + "</strong></p>"
                + (storeLink != null
                ? "<p><a href=\"" + storeLink + "\">Back to store</a></p>"
                : "")
                + "<p>Thank you!</p>";

        sendWithRetry(toEmail, subject, textBody, htmlBody, "ORDER_CONFIRMATION_EMAIL");
    }

    private String normalizeFrontendUrl() {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return "https://sme-operations.netlify.app";
        }
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    static String formatMoney(int amountMinor, String currency) {
        String code = currency == null || currency.isBlank() ? "ZAR" : currency.toUpperCase(Locale.ROOT);
        int abs = Math.abs(amountMinor);
        String major = String.format(Locale.ROOT, "%d.%02d", abs / 100, abs % 100);
        if (amountMinor < 0) {
            major = "-" + major;
        }
        return code + " " + major;
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private void sendWithRetry(String toEmail, String subject,
                               String textBody, String htmlBody, String emailType) {
        int maxRetries = 3;
        long[] backoffMs = {1000, 2000, 4000};
        String fromAddress = senderName + " <" + fromEmail + ">";

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                SendEmailRequest request = SendEmailRequest.builder()
                        .destination(Destination.builder().toAddresses(toEmail).build())
                        .message(Message.builder()
                                .subject(Content.builder().data(subject).charset("UTF-8").build())
                                .body(Body.builder()
                                        .text(Content.builder().data(textBody).charset("UTF-8").build())
                                        .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                        .build())
                                .build())
                        .source(fromAddress)
                        .build();

                sesClient.sendEmail(request);
                log.info("Email [{}] sent successfully to {} on attempt {}",
                        emailType, toEmail, attempt + 1);
                return;
            } catch (Exception e) {
                log.warn("Email [{}] send attempt {}/{} failed for {}: {}",
                        emailType, attempt + 1, maxRetries, toEmail, e.getMessage());
                auditService.logSecurityEvent(emailType + "_FAILURE", "system", toEmail,
                        "Attempt " + (attempt + 1) + ": " + e.getMessage());
                if (attempt < maxRetries - 1) {
                    try {
                        Thread.sleep(backoffMs[attempt]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        log.error("All {} email send attempts failed for {} [type={}]",
                maxRetries, toEmail, emailType);
    }
}
