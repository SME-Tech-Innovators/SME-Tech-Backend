package sme.tech.innovators.sme.exception;

public class InvalidBankAccountException extends RuntimeException {
    public InvalidBankAccountException(String message) {
        super(message);
    }
}
