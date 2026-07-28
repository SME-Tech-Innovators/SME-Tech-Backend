package sme.tech.innovators.sme.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An immutable version snapshot of a StorefrontTemplate.
 * Stores the default config, supported section types, and supported theme IDs.
 */
@Entity
@Table(name = "storefront_template_versions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"template_id", "version"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorefrontTemplateVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private StorefrontTemplate template;

    @Column(nullable = false)
    private Integer version;

    /** Default storefront config JSON for this template version. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_config")
    private Map<String, Object> defaultConfig;

    /** Allowed section type strings, e.g. ["hero","featuredProducts"]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_sections")
    private List<String> supportedSections;

    /** Allowed theme ID strings, e.g. ["blue","red"]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_themes")
    private List<String> supportedThemes;

    /** Optional JSON schema for config validation metadata. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_schema")
    private Map<String, Object> configSchema;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.version == null) {
            this.version = 1;
        }
    }
}
