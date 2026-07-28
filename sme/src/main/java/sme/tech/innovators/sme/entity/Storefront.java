package sme.tech.innovators.sme.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Holds the draft storefront configuration for a workspace.
 * One workspace has exactly one storefront record (1:1).
 */
@Entity
@Table(name = "storefronts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Storefront {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false, unique = true)
    private Workspace workspace;

    @Column(name = "template_id", nullable = false, length = 100)
    private String templateId;

    @Builder.Default
    @Column(name = "template_version", nullable = false)
    private Integer templateVersion = 1;

    /** The current editable storefront config (draft). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_config")
    private Map<String, Object> draftConfig;

    /** Schema version of the draft config to support future migrations. */
    @Builder.Default
    @Column(name = "draft_config_version", nullable = false)
    private Integer draftConfigVersion = 1;

    /** Points to the last published snapshot. Null until first publish. */
    @Column(name = "published_snapshot_id")
    private UUID publishedSnapshotId;

    @Column(name = "last_published_at")
    private LocalDateTime lastPublishedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.templateVersion == null) this.templateVersion = 1;
        if (this.draftConfigVersion == null) this.draftConfigVersion = 1;
    }

    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
