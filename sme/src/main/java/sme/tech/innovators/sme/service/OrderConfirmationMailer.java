package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sme.tech.innovators.sme.entity.Order;
import sme.tech.innovators.sme.entity.OrderItem;
import sme.tech.innovators.sme.entity.Workspace;
import sme.tech.innovators.sme.repository.OrderRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sends order confirmation email at most once after payment success.
 * Invoked from webhook / verify after marking the order paid; never throws to callers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderConfirmationMailer {

    private final OrderRepository orderRepository;
    private final EmailService emailService;

    /** Schedule email after the current transaction commits (or send immediately if none). */
    public void scheduleAfterPayment(UUID orderId) {
        if (orderId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        sendIfNeeded(orderId);
                    } catch (Exception e) {
                        log.error("Order confirmation email scheduling failed for order={}: {}",
                                orderId, e.getMessage());
                    }
                }
            });
        } else {
            try {
                sendIfNeeded(orderId);
            } catch (Exception e) {
                log.error("Order confirmation email failed for order={}: {}", orderId, e.getMessage());
            }
        }
    }

    @Transactional
    public void sendIfNeeded(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Skipping confirmation email — order not found: {}", orderId);
            return;
        }

        String email = order.getCustomerEmail();
        if (email == null || email.isBlank()) {
            log.info("Skipping confirmation email — no customer email on order={}", orderId);
            return;
        }

        if (order.isConfirmationEmailSent()) {
            log.info("Skipping confirmation email — already sent for order={}", orderId);
            return;
        }

        // Claim before async send so duplicate webhook/verify cannot double-send
        order.setConfirmationEmailSent(true);
        orderRepository.save(order);

        Workspace workspace = order.getWorkspace();
        String storeName = workspace != null && workspace.getName() != null
                ? workspace.getName()
                : "Store";
        String storeSlug = workspace != null ? workspace.getPublicSlug() : null;

        List<EmailService.OrderLine> lines = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                lines.add(new EmailService.OrderLine(
                        item.getTitle(),
                        item.getQuantity(),
                        item.getTotalAmount() != null ? item.getTotalAmount() : 0,
                        item.getCurrency() != null ? item.getCurrency() : order.getCurrency()
                ));
            }
        }

        emailService.sendOrderConfirmationEmail(
                email.trim(),
                order.getCustomerName(),
                storeName,
                storeSlug,
                order.getOrderNumber(),
                lines,
                order.getTotalAmount(),
                order.getCurrency()
        );
    }
}
