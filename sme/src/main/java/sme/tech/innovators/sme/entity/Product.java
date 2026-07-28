package sme.tech.innovators.sme.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
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

    @ManyToOne
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category; // nullable

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 255)
    private String slug; // unique per workspace

    @Column(nullable = false, length = 100)
    private String sku; // unique per workspace

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer priceAmount; // minor units, e.g. cents

    @Column(nullable = false, length = 10)
    private String currency; // e.g. "ZAR"

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "draft"; // draft | active | archived

    @Column(length = 500)
    private String imageUrl; // temporary until media table integration

    @Column(length = 255)
    private String configurationLabel; // PDP field

    @Column(length = 500)
    private String warrantyNote; // PDP field

    @Column(length = 500)
    private String shippingNote; // PDP field

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata; // PDP highlights/specs/sidebar content

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
            this.status = "draft";
        }
    }

    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
