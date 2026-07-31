package sme.tech.innovators.sme.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A workspace represents a merchant's store context, linked 1:1 to a Business.
 * One business = one workspace in the current model.
 */
@Entity
@Table(name = "workspaces")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false, unique = true)
    private Business business;

    @Column(nullable = false, length = 255)
    private String name;

    /** Public-facing slug used for the live storefront URL. */
    @Column(unique = true, length = 100)
    private String publicSlug;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 50)
    private WorkspaceStatus status = WorkspaceStatus.DRAFT;

    @Column(name = "seo_title", length = 255)
    private String seoTitle;

    @Column(name = "seo_description", length = 1000)
    private String seoDescription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seo_image_id")
    private MediaAsset seoImage;

    // ── Paystack payout / subaccount (platform-managed; never store merchant secrets) ──

    @Column(name = "paystack_subaccount_code", length = 100)
    private String paystackSubaccountCode;

    @Column(name = "payout_business_name", length = 255)
    private String payoutBusinessName;

    @Column(name = "payout_bank_code", length = 20)
    private String payoutBankCode;

    @Column(name = "payout_account_number", length = 50)
    private String payoutAccountNumber;

    @Column(name = "payout_account_name", length = 255)
    private String payoutAccountName;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "paystack_subaccount_status", nullable = false, length = 30)
    private PaystackSubaccountStatus paystackSubaccountStatus = PaystackSubaccountStatus.NOT_CONNECTED;

    /** Optional per-workspace override of platform fee percent. Null → env default. */
    @Column(name = "platform_fee_percent")
    private Integer platformFeePercent;

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
            this.status = WorkspaceStatus.DRAFT;
        }
        if (this.paystackSubaccountStatus == null) {
            this.paystackSubaccountStatus = PaystackSubaccountStatus.NOT_CONNECTED;
        }
    }

    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
