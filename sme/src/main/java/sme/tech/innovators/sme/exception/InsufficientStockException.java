package sme.tech.innovators.sme.exception;

public class InsufficientStockException extends RuntimeException {

    private final Integer availableQuantity;

    public InsufficientStockException(String message) {
        this(message, null);
    }

    public InsufficientStockException(String message, Integer availableQuantity) {
        super(message);
        this.availableQuantity = availableQuantity;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }
}
