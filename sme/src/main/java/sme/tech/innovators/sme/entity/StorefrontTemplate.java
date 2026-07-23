package sme.tech.innovators.sme.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "storefront_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorefrontTemplate {

    @Id
    @Column(length = 100)
    private String id; // e.g. "classic-boutique" — not auto-generated

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 1000)
    private String description;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "available"; // available | coming_soon | disabled

    @Column(length = 500)
    private String previewImageUrl;

    @Builder.Default
    @Column(nullable = false)
    private Integer latestVersion = 1;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = "available";
        }
        if (this.latestVersion == null) {
            this.latestVersion = 1;
        }
    }

    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}