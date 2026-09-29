package sme.tech.innovators.sme.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "deliveries", uniqueConstraints = @UniqueConstraint(columnNames = "order_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(length = 120)
    private String carrierName;

    @Column(length = 120)
    private String trackingNumber;

    @Column(length = 120)
    private String parcelTrackingNumber;

    @Column(length = 40)
    private String provider;

    @Column(length = 80)
    private String externalShipmentId;

    @Column(length = 30)
    private String serviceLevelCode;

    @Column(length = 120)
    private String serviceLevelName;

    private java.math.BigDecimal quotedAmount;

    @Column(length = 500)
    private String trackingUrl;

    @Column(length = 1000)
    private String deliveryNotes;

    @Column(name = "public_notes", length = 1000)
    private String publicNotes;

    private LocalDateTime estimatedDeliveryAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime deliveredAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<DeliveryEvent> events = new ArrayList<>();

    @PrePersist
    protected void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = DeliveryStatus.PENDING;
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
