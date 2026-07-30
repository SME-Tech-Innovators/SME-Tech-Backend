package sme.tech.innovators.sme.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a storefront design template.
 * Uses a String primary key (e.g. "classic-boutique") for human-readable IDs.
 */
@Entity
@Table(name = "storefront_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorefrontTemplate {

    @Id
    @Column(nullable = false, length = 100)
    private String id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 1000)
    private String description;

    /** Short marketing vibe label for the picker, e.g. "Editorial retail". */
    @Column(length = 255)
    private String vibe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StorefrontTemplateStatus status;

    @Column(length = 500)
    private String previewImageUrl;

    @Column(nullable = false)
    private Integer latestVersion;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.latestVersion == null) {
            this.latestVersion = 1;
        }
    }

    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
