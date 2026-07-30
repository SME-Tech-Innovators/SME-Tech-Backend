package sme.tech.innovators.sme.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable publish record of a storefront draft at a point in time.
 * Rows are never updated after creation.
 */
@Entity
@Table(name = "storefront_publish_snapshots")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorefrontPublishSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storefront_id", nullable = false)
    private Storefront storefront;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "template_id", nullable = false, length = 100)
    private String templateId;

    @Column(name = "template_version", nullable = false)
    private Integer templateVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", nullable = false)
    private Map<String, Object> config;

    @Column(name = "config_version", nullable = false)
    private Integer configVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by_user_id", nullable = false)
    private User publishedBy;

    @Column(name = "published_at", nullable = false, updatable = false)
    private LocalDateTime publishedAt;

    @Column(length = 500)
    private String notes;

    @PrePersist
    protected void prePersist() {
        if (this.publishedAt == null) {
            this.publishedAt = LocalDateTime.now();
        }
    }
}
