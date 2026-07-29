package sme.tech.innovators.sme.exception;

public class UploadUrlFailedException extends RuntimeException {
    public UploadUrlFailedException(String message) { super(message); }
    public UploadUrlFailedException(String message, Throwable cause) { super(message, cause); }
}
