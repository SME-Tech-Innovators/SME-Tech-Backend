package sme.tech.innovators.sme.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

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

    @OneToOne
    @JoinColumn(name = "workspace_id", nullable = false, unique = true)
    private Workspace workspace;

    @Column(name = "template_id", nullable = false, length = 100)
    private String templateId; // e.g. "classic-boutique"

    @Builder.Default
    @Column(nullable = false)
    private Integer templateVersion = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String draftConfig;

    @Builder.Default
    @Column(nullable = false)
    private Integer draftConfigVersion = 1;

    @Column
    private UUID publishedSnapshotId; // proper FK added in Step 02

    @Column
    private LocalDateTime lastPublishedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.templateVersion == null) {
            this.templateVersion = 1;
        }
        if (this.draftConfigVersion == null) {
            this.draftConfigVersion = 1;
        }
    }

    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
