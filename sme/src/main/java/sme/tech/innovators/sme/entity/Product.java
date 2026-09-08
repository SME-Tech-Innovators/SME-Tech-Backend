package sme.tech.innovators.sme.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "products",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"workspace_id", "slug"}),
           @UniqueConstraint(columnNames = {"workspace_id", "sku"})
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Price in major currency units (e.g. 300.00 = R300.00). */
    @Column(name = "price_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceAmount;

    /**
     * Optional compare-at (was) price in major currency units.
     * When set and strictly greater than {@link #priceAmount}, the product is on sale.
     */
    @Column(name = "compare_at_price_amount", precision = 19, scale = 2)
    private BigDecimal compareAtPriceAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    /** Units left to sell. Always tracked; ≥ 0. */
    @Column(name = "quantity_available", nullable = false)
    @Builder.Default
    private Integer quantityAvailable = 999;

    /**
     * Set when a merchant out-of-stock email is sent for the current sold-out episode.
     * Cleared when {@link #quantityAvailable} goes back above 0 so the next sell-out can notify again.
     */
    @Column(name = "out_of_stock_notified_at")
    private LocalDateTime outOfStockNotifiedAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_image_id")
    private MediaAsset mainImage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gallery_urls")
    private List<String> galleryUrls;

    @Column(name = "configuration_label", length = 255)
    private String configurationLabel;

    @Column(name = "warranty_note", length = 1000)
    private String warrantyNote;

    @Column(name = "shipping_note", length = 1000)
    private String shippingNote;

    /** PDP highlights/specs/sidebar content. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private Map<String, Object> metadata;

    @Column(name = "seo_title", length = 255)
    private String seoTitle;

    @Column(name = "seo_description", length = 1000)
    private String seoDescription;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = ProductStatus.DRAFT;
        }
        if (this.currency == null) {
            this.currency = "ZAR";
        }
        if (this.quantityAvailable == null) {
            this.quantityAvailable = 999;
        }
    }

    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
