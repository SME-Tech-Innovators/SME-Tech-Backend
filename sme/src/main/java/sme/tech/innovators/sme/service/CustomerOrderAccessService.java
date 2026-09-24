package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sme.tech.innovators.sme.entity.Order;
import sme.tech.innovators.sme.exception.OrderNotFoundException;
import sme.tech.innovators.sme.repository.OrderRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerOrderAccessService {
    private final PublicStoreResolver stores;
    private final OrderRepository orders;
    private final EmailService emails;

    @Transactional
    public void sendAccessLink(String slug, String number, String email) {
        var workspace = stores.requireLiveWorkspace(slug);
        var match = orders.findByWorkspaceIdAndOrderNumberIgnoreCaseAndCustomerEmailIgnoreCase(
                workspace.getId(), number.trim(), email.trim());
        if (match.isEmpty()) throw new OrderNotFoundException(
                "Incorrect order number or email. Check the details in your order confirmation.");
        var order = orders.lockForReturn(match.get().getId(), workspace.getId()).orElseThrow();
        var now = LocalDateTime.now();
        if (order.getCustomerAccessSentAt() != null
                && order.getCustomerAccessSentAt().isAfter(now.minusMinutes(1))) return;
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        order.setCustomerAccessTokenHash(hash(token));
        order.setCustomerAccessExpiresAt(now.plusHours(24));
        order.setCustomerAccessSentAt(now);
        orders.save(order);
        String recipient = order.getCustomerEmail();
        String storeSlug = workspace.getPublicSlug();
        UUID orderId = order.getId();
        Runnable send = () -> emails.sendOrderAccessEmail(recipient, storeSlug, orderId, token);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else send.run();
    }

    @Transactional(readOnly = true)
    public Order authorize(String slug, UUID orderId, String token) {
        var workspace = stores.requireLiveWorkspace(slug);
        var order = orders.findByIdAndWorkspaceId(orderId, workspace.getId()).orElseThrow(this::invalid);
        if (token == null || token.length() != 43 || order.getCustomerAccessTokenHash() == null
                || order.getCustomerAccessExpiresAt() == null
                || !order.getCustomerAccessExpiresAt().isAfter(LocalDateTime.now())
                || !MessageDigest.isEqual(hash(token).getBytes(StandardCharsets.UTF_8),
                        order.getCustomerAccessTokenHash().getBytes(StandardCharsets.UTF_8))) throw invalid();
        return order;
    }

    private OrderNotFoundException invalid() {
        return new OrderNotFoundException("This order link is invalid or expired. Request a new link.");
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
