package sme.tech.innovators.sme.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Merchant request to update their workspace delivery settings,
 * including which providers are enabled and the pickup address
 * that couriers will collect from.
 */
@Data
public class UpdateDeliverySettingsRequest {

    // ── Provider toggles ─────────────────────────────────────────────────

    /**
     * Set to {@code true} to activate Uber Direct for this workspace.
     * The platform-level {@code app.uber-direct.enabled} flag must also be
     * {@code true}; otherwise the request is accepted but deliveries will
     * not be bookable at checkout.
     */
    @NotNull(message = "uberDirectEnabled is required")
    private Boolean uberDirectEnabled;

    // ── Pickup address ───────────────────────────────────────────────────

    @NotBlank(message = "pickupAddressLine1 is required")
    @Size(max = 255)
    private String pickupAddressLine1;

    @Size(max = 255)
    private String pickupAddressLine2;

    @NotBlank(message = "pickupCity is required")
    @Size(max = 100)
    private String pickupCity;

    @Size(max = 100)
    private String pickupProvince;

    @Size(max = 20)
    private String pickupPostalCode;

    @NotBlank(message = "pickupCountry is required")
    @Size(max = 10)
    private String pickupCountry;

    /**
     * WGS-84 latitude (-90 to +90).
     * Strongly recommended — Uber Direct uses it for accurate routing.
     */
    private Double pickupLatitude;

    /**
     * WGS-84 longitude (-180 to +180).
     */
    private Double pickupLongitude;

    @Size(max = 255)
    private String pickupContactName;

    /** E.164 format, e.g. "+27821234567". */
    @Size(max = 30)
    private String pickupContactPhone;
}
