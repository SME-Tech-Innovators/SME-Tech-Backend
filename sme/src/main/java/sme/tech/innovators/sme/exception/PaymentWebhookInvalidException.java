package sme.tech.innovators.sme.exception;

public class PaymentWebhookInvalidException extends RuntimeException {
    public PaymentWebhookInvalidException(String message) {
        super(message);
    }
}
