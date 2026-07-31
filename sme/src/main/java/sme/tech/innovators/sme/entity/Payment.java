package sme.tech.innovators.sme.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "payments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"provider_reference"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String provider = "paystack";

    @Column(name = "provider_reference", nullable = false, length = 100)
    private String providerReference;

    @Column(name = "provider_access_code", length = 100)
    private String providerAccessCode;

    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private PaymentRecordStatus status = PaymentRecordStatus.INITIALIZED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response")
    private Map<String, Object> rawResponse;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.provider == null) this.provider = "paystack";
        if (this.status == null) this.status = PaymentRecordStatus.INITIALIZED;
    }

    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
