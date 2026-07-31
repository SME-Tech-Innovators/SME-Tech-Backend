package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sme.tech.innovators.sme.entity.Business;
import sme.tech.innovators.sme.entity.Product;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.entity.Workspace;
import sme.tech.innovators.sme.repository.ProductRepository;

import java.util.UUID;

/**
 * Sends a one-shot merchant email when a product reaches quantity 0.
 * Callers must claim {@code products.out_of_stock_notified_at} first (or invoke
 * {@link #notifyIfSoldOut(UUID)} which claims atomically). Never throws to payment callers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutOfStockMailer {

    private final ProductRepository productRepository;
    private final EmailService emailService;

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
                return;
            }
            scheduleSend(productId);
        } catch (Exception e) {
            log.error("Out-of-stock notify claim failed for product={}: {}", productId, e.getMessage());
        }
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
                        sendClaimed(productId);
                    } catch (Exception e) {
                        log.error("Out-of-stock email scheduling failed for product={}: {}",
                                productId, e.getMessage());
                    }
                }
            });
        } else {
            try {
                sendClaimed(productId);
            } catch (Exception e) {
                log.error("Out-of-stock email failed for product={}: {}", productId, e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public void sendClaimed(UUID productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            log.warn("Skipping out-of-stock email — product not found: {}", productId);
            return;
        }

        Workspace workspace = product.getWorkspace();
        Business business = workspace != null ? workspace.getBusiness() : null;
        User owner = business != null ? business.getOwner() : null;
        String toEmail = owner != null ? owner.getEmail() : null;
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipping out-of-stock email — no merchant email for product={}", productId);
            return;
        }

        String storeName = workspace != null && workspace.getName() != null && !workspace.getName().isBlank()
                ? workspace.getName()
                : (business != null && business.getName() != null ? business.getName() : "your store");
        UUID workspaceId = workspace != null ? workspace.getId() : null;

        emailService.sendOutOfStockEmail(
                toEmail.trim(),
                owner.getFullName(),
                product.getTitle(),
                product.getSku(),
                storeName,
                workspaceId
        );
    }
}
