package sme.tech.innovators.sme.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "product_images",
       uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "media_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id", nullable = false)
    private MediaAsset media;

    /** Denormalized public URL for gallery display (kept in sync with media.url). */
    @Column(name = "image_url", nullable = false, length = 2000)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.sortOrder == null) {
            this.sortOrder = 0;
        }
        if ((this.imageUrl == null || this.imageUrl.isBlank()) && this.media != null) {
            this.imageUrl = this.media.getUrl();
        }
    }
}
