package sme.tech.innovators.sme.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-workspace delivery configuration.
 *
 * <p>Stores which delivery providers the merchant has enabled and the
 * pickup address that will be used as the collection point for all
 * deliveries originating from this workspace.
 *
 * <p>Currently supports Uber Direct. Additional delivery providers can be
 * added as additional boolean columns here.
 */
@Entity
@Table(name = "workspace_delivery_settings",
       uniqueConstraints = @UniqueConstraint(columnNames = "workspace_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceDeliverySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Workspace workspace;

    // ── Uber Direct ──────────────────────────────────────────────────────────

    /**
     * Whether the merchant has turned on Uber Direct for this workspace.
     * Only effective when the platform-level {@code app.uber-direct.enabled}
     * is also {@code true}.
     */
    @Column(name = "uber_direct_enabled", nullable = false)
    @Builder.Default
    private boolean uberDirectEnabled = false;

    // ── Pickup / collection address (shared across providers) ─────────────

    /** Street address line 1, e.g. "12 Main Street". */
    @Column(name = "pickup_address_line1", length = 255)
    private String pickupAddressLine1;

    /** Optional street address line 2, e.g. "Unit 4". */
    @Column(name = "pickup_address_line2", length = 255)
    private String pickupAddressLine2;

    /** City, e.g. "Cape Town". */
    @Column(name = "pickup_city", length = 100)
    private String pickupCity;

    /** Province / state, e.g. "Western Cape". */
    @Column(name = "pickup_province", length = 100)
    private String pickupProvince;

    /** Postal code, e.g. "8001". */
    @Column(name = "pickup_postal_code", length = 20)
    private String pickupPostalCode;

    /** ISO 3166-1 alpha-2 country code, e.g. "ZA". */
    @Column(name = "pickup_country", length = 10)
    @Builder.Default
    private String pickupCountry = "ZA";

    /**
     * WGS-84 latitude of the pickup location.
     * Required by Uber Direct for accurate routing.
     */
    @Column(name = "pickup_latitude")
    private Double pickupLatitude;

    /**
     * WGS-84 longitude of the pickup location.
     * Required by Uber Direct for accurate routing.
     */
    @Column(name = "pickup_longitude")
    private Double pickupLongitude;

    /**
     * Contact name shown to the Uber courier at pickup.
     * Defaults to the business owner's name if not set.
     */
    @Column(name = "pickup_contact_name", length = 255)
    private String pickupContactName;

    /** Phone number of the pickup contact in E.164 format, e.g. "+27821234567". */
    @Column(name = "pickup_contact_phone", length = 30)
    private String pickupContactPhone;

    // ── Audit ─────────────────────────────────────────────────────────────

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
