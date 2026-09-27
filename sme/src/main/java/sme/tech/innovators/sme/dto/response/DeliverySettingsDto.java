package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Current delivery settings for a workspace — returned to the merchant dashboard.
 */
@Data
@Builder
public class DeliverySettingsDto {

    private String workspaceId;

    // ── Provider flags ────────────────────────────────────────────────────

    /** Whether Uber Direct has been enabled by the merchant. */
    private boolean uberDirectEnabled;

    /**
     * Whether Uber Direct is actually usable right now.
     * {@code true} only when the merchant has enabled it AND the platform
     * credentials are configured.
     */
    private boolean uberDirectAvailable;

    // ── Pickup address ────────────────────────────────────────────────────

    private String pickupAddressLine1;
    private String pickupAddressLine2;
    private String pickupCity;
    private String pickupProvince;
    private String pickupPostalCode;
    private String pickupCountry;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private String pickupContactName;
    private String pickupContactPhone;

    private LocalDateTime updatedAt;
}
