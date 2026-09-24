package sme.tech.innovators.sme.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "workspace_shipping_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceShippingSettings {

    @Id
    @Column(name = "workspace_id")
    private UUID workspaceId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String provider = "bobgo";

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "collection_address")
    private Map<String, Object> collectionAddress;

    @Column(name = "fallback_flat_rate_amount", precision = 19, scale = 2)
    private BigDecimal fallbackFlatRateAmount;

    @Builder.Default
    @Column(name = "fallback_flat_rate_currency", length = 3)
    private String fallbackFlatRateCurrency = "ZAR";

    @Builder.Default
    @Column(name = "allow_pickup", nullable = false)
    private boolean allowPickup = false;

    @Column(name = "pickup_label", length = 255)
    private String pickupLabel;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.provider == null) {
            this.provider = "bobgo";
        }
        if (this.fallbackFlatRateCurrency == null) {
            this.fallbackFlatRateCurrency = "ZAR";
        }
    }

    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
