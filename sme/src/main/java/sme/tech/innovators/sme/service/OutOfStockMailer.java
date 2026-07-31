package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import sme.tech.innovators.sme.entity.Business;
import sme.tech.innovators.sme.entity.Product;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.entity.Workspace;
import sme.tech.innovators.sme.repository.ProductRepository;

import java.util.UUID;

/**
 * Sends a one-shot merchant email when a product reaches quantity 0.
 * Claims {@code products.out_of_stock_notified_at} atomically; releases the claim if send fails
 * so a later restock→0 (or retry) can notify again. Never throws to payment callers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutOfStockMailer {

    private final ProductRepository productRepository;
    private final EmailService emailService;
    private final PlatformTransactionManager transactionManager;

    /**
     * After stock may have hit 0: claim the sold-out episode and email the workspace owner.
     * Safe to call after every successful decrement / merchant quantity PATCH to 0.
     */
    public void notifyIfSoldOut(UUID productId) {
        if (productId == null) {
            return;
        }
        try {
            int claimed = productRepository.claimOutOfStockNotification(productId);
            if (claimed == 0) {
                log.info("Out-of-stock email not claimed for product={} (qty not 0 or already notified)",
                        productId);
                return;
            }
            log.info("Claimed out-of-stock email for product={}", productId);
            scheduleSend(productId);
        } catch (Exception e) {
            log.error("Out-of-stock notify claim failed for product={}: {}", productId, e.getMessage());
        }
    }

    /**
     * Clears any prior claim and attempts send again (product must already be at qty 0).
     * For merchant retry / support after SES failure.
     */
    public void forceNotifyIfSoldOut(UUID productId) {
        if (productId == null) {
            return;
        }
        releaseClaimQuietly(productId);
        notifyIfSoldOut(productId);
    }

    /** Schedule email after the current transaction commits (or send immediately if none). */
    public void scheduleSend(UUID productId) {
        if (productId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        // afterCommit is outside the original TX — open a new one for load + send.
                        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                                sendClaimed(productId));
                    } catch (Exception e) {
                        log.error("Out-of-stock email scheduling failed for product={}: {}",
                                productId, e.getMessage());
                        releaseClaimQuietly(productId);
                    }
                }
            });
        } else {
            try {
                sendClaimed(productId);
            } catch (Exception e) {
                log.error("Out-of-stock email failed for product={}: {}", productId, e.getMessage());
                releaseClaimQuietly(productId);
            }
        }
    }

    @Transactional
    public void sendClaimed(UUID productId) {
        Product product = productRepository.findByIdWithWorkspaceOwner(productId).orElse(null);
        if (product == null) {
            log.warn("Skipping out-of-stock email — product not found: {}", productId);
            releaseClaimQuietly(productId);
            return;
        }

        Workspace workspace = product.getWorkspace();
        Business business = workspace != null ? workspace.getBusiness() : null;
        User owner = business != null ? business.getOwner() : null;
        String toEmail = owner != null ? owner.getEmail() : null;
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipping out-of-stock email — no merchant email for product={}", productId);
            releaseClaimQuietly(productId);
            return;
        }

        String storeName = workspace != null && workspace.getName() != null && !workspace.getName().isBlank()
                ? workspace.getName()
                : (business != null && business.getName() != null ? business.getName() : "your store");
        UUID workspaceId = workspace != null ? workspace.getId() : null;

        boolean sent = emailService.sendOutOfStockEmailSync(
                toEmail.trim(),
                owner.getFullName(),
                product.getTitle(),
                product.getSku(),
                storeName,
                workspaceId
        );
        if (!sent) {
            log.error("Out-of-stock email failed for product={} to={} — releasing claim for retry",
                    productId, toEmail);
            releaseClaimQuietly(productId);
        }
    }

    private void releaseClaimQuietly(UUID productId) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    productRepository.clearOutOfStockNotification(productId));
        } catch (Exception e) {
            log.warn("Could not release out-of-stock claim for product={}: {}", productId, e.getMessage());
        }
    }
}
