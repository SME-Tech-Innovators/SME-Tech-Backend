package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Parsed Uber Direct quote returned to the customer at checkout.
 *
 * <p>The raw Uber Direct response contains amounts in the currency's
 * smallest unit (e.g. cents for ZAR).  We convert to a decimal
 * {@link BigDecimal} for consistency with the rest of the platform.
 */
@Data
@Builder
public class UberDirectQuoteDto {

    /** Uber's internal quote / delivery ID — pass this back when creating the delivery. */
    private String quoteId;

    /** Human-readable provider label shown in the checkout UI. */
    private String providerName;

    /** Display-friendly estimated delivery window, e.g. "30–45 min". */
    private String estimatedDeliveryTime;

    /** Fee amount in the store's currency (e.g. ZAR). */
    private BigDecimal fee;

    /** ISO 4217 currency code, e.g. "ZAR". */
    private String currency;

    /**
     * ISO-8601 timestamp after which this quote expires and a new one
     * must be requested.
     */
    private String expiresAt;

    /**
     * Whether this provider is currently available for the given
     * pickup → dropoff route.  {@code false} means no couriers are
     * available or the area is outside the service zone.
     */
    private boolean available;

    /** Human-readable reason when {@code available} is {@code false}. */
    private String unavailableReason;
}
