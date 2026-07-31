package sme.tech.innovators.sme.entity;

/**
 * Status of a payments row (provider-side lifecycle).
 * Distinct from {@link PaymentStatus} on orders.
 */
public enum PaymentRecordStatus {
    INITIALIZED,
    PAID,
    FAILED,
    ABANDONED
}
