package sme.tech.innovators.sme.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Customer-facing request to get an Uber Direct delivery quote
 * for a specific store's checkout.
 *
 * <p>The pickup address comes from the workspace delivery settings;
 * only the dropoff address is supplied by the customer.
 */
@Data
public class UberDirectQuoteRequest {

    // ── Dropoff (customer delivery address) ──────────────────────────────

    @NotBlank(message = "dropoffAddressLine1 is required")
    private String dropoffAddressLine1;

    private String dropoffAddressLine2;

    @NotBlank(message = "dropoffCity is required")
    private String dropoffCity;

    private String dropoffProvince;

    private String dropoffPostalCode;

    @NotBlank(message = "dropoffCountry is required")
    private String dropoffCountry;

    /** Optional WGS-84 latitude for more accurate routing. */
    private Double dropoffLatitude;

    /** Optional WGS-84 longitude for more accurate routing. */
    private Double dropoffLongitude;

    // ── Parcel dimensions for pricing ─────────────────────────────────────

    /**
     * Total manifest value in ZAR cents (used by Uber Direct for the
     * {@code manifest_total_value} field).  Defaults to 0 if omitted.
     */
    @NotNull(message = "manifestTotalValueCents is required")
    private Integer manifestTotalValueCents;
}
