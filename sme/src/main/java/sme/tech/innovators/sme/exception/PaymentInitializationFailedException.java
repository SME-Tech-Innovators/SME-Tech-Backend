package sme.tech.innovators.sme.exception;

public class PaymentInitializationFailedException extends RuntimeException {
    public PaymentInitializationFailedException(String message) {
        super(message);
    }
}
