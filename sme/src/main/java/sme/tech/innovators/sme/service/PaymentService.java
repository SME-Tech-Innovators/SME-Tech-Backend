package sme.tech.innovators.sme.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.request.InitializePaymentRequest;
import sme.tech.innovators.sme.dto.response.OrderConfirmationDto;
import sme.tech.innovators.sme.dto.response.PaymentInitDto;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.*;
import sme.tech.innovators.sme.integration.paystack.PaystackClient;
import sme.tech.innovators.sme.repository.OrderRepository;
import sme.tech.innovators.sme.repository.PaymentRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PublicStoreResolver publicStoreResolver;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaystackClient paystackClient;
    private final ObjectMapper objectMapper;
    private final OrderConfirmationMailer orderConfirmationMailer;
    private final CheckoutService checkoutService;
    private final InventoryService inventoryService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public PaymentInitDto initializePayment(String storeSlug,
                                             String orderId,
                                             InitializePaymentRequest request) {
        Workspace workspace = publicStoreResolver.requireLiveWorkspace(storeSlug);

        if (workspace.getPaystackSubaccountStatus() != PaystackSubaccountStatus.ACTIVE
                || workspace.getPaystackSubaccountCode() == null
                || workspace.getPaystackSubaccountCode().isBlank()) {
            throw new PaymentNotConfiguredException(
                    "This store has not connected payouts yet");
        }

        Order order = loadOrderForStore(workspace, orderId);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                || order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new PaymentInitializationFailedException(
                    "Order is not awaiting payment: " + orderId);
        }

        String reference = "ord_" + order.getId().toString().replace("-", "")
                + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        String email = order.getCustomerEmail();
        if (email == null || email.isBlank()) {
            email = "customer+" + order.getId() + "@payments.sme-operations.local";
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("orderId", order.getId().toString());
        metadata.put("workspaceId", workspace.getId().toString());
        metadata.put("storeSlug", storeSlug);
        metadata.put("orderNumber", order.getOrderNumber());

        String callbackUrl = resolveCallbackUrl(
                request != null ? request.getCallbackUrl() : null,
                storeSlug,
                order.getId().toString());

        Map<String, Object> initData = paystackClient.initializeTransaction(
                email,
                order.getTotalAmount(),
                order.getCurrency(),
                reference,
                callbackUrl,
                workspace.getPaystackSubaccountCode(),
                metadata
        );

        Payment payment = Payment.builder()
                .order(order)
                .workspace(workspace)
                .provider("paystack")
                .providerReference(reference)
                .providerAccessCode(stringVal(initData.get("access_code")))
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .status(PaymentRecordStatus.INITIALIZED)
                .rawResponse(initData)
                .build();
        paymentRepository.save(payment);

        order.setPaymentStatus(PaymentStatus.INITIALIZED);
        orderRepository.save(order);

        return PaymentInitDto.builder()
                .authorizationUrl(stringVal(initData.get("authorization_url")))
                .accessCode(stringVal(initData.get("access_code")))
                .reference(reference)
                .publicKey(paystackClient.getPublicKey())
                .orderId(order.getId().toString())
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .build();
    }

    /**
     * Verifies the latest Paystack transaction for an order (fallback when webhook is delayed).
     * Source of truth is Paystack verify / webhook — never trust browser redirect alone.
     */
    @Transactional
    public OrderConfirmationDto verifyPayment(String storeSlug, String orderId) {
        return verifyPayment(storeSlug, orderId, false);
    }

    @Transactional
    public OrderConfirmationDto verifyPayment(String storeSlug, String orderId, boolean forceInventoryHeal) {
        Workspace workspace = publicStoreResolver.requireLiveWorkspace(storeSlug);
        Order order = loadOrderForStore(workspace, orderId);

        if (order.getPaymentStatus() == PaymentStatus.PAID
                && order.getStatus() == OrderStatus.PAID) {
            // Webhook (often hitting another host) may have marked paid before stock ran.
            ensureStockDecremented(order.getId(), forceInventoryHeal);
            orderConfirmationMailer.scheduleAfterPayment(order.getId());
            return checkoutService.toConfirmationDto(
                    orderRepository.findByIdWithItemsAndProducts(order.getId()).orElse(order));
        }

        Payment payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                .orElseThrow(() -> new PaymentInitializationFailedException(
                        "No payment initialized for order: " + orderId));

        Map<String, Object> data = paystackClient.verifyTransaction(payment.getProviderReference());
        String status = stringVal(data.get("status"));
        if (!"success".equalsIgnoreCase(status)) {
            log.info("Paystack verify not success for order={} reference={} status={}",
                    orderId, payment.getProviderReference(), status);
            return checkoutService.toConfirmationDto(order);
        }

        applyPaid(payment, order, Map.of("event", "transaction.verify", "data", data));
        return checkoutService.toConfirmationDto(
                orderRepository.findByIdWithItemsAndProducts(order.getId()).orElse(order));
    }

    @Transactional
    public void handleWebhook(String signatureHeader, String rawBody) {
        verifySignature(signatureHeader, rawBody);

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(rawBody, new TypeReference<>() {});
        } catch (Exception e) {
            throw new PaymentWebhookInvalidException("Invalid webhook payload");
        }

        String event = stringVal(payload.get("event"));
        if (!"charge.success".equalsIgnoreCase(event)) {
            log.info("Ignoring Paystack webhook event={}", event);
            return;
        }

        Object dataObj = payload.get("data");
        if (!(dataObj instanceof Map<?, ?> dataMap)) {
            throw new PaymentWebhookInvalidException("Missing charge data");
        }
        Map<String, Object> data = objectMapper.convertValue(dataMap, new TypeReference<>() {});
        String reference = stringVal(data.get("reference"));
        if (reference == null || reference.isBlank()) {
            throw new PaymentWebhookInvalidException("Missing payment reference");
        }

        Payment payment = paymentRepository.findByProviderReference(reference)
                .orElseThrow(() -> new PaymentWebhookInvalidException(
                        "Unknown payment reference: " + reference));

        if (payment.getStatus() == PaymentRecordStatus.PAID) {
            log.info("Idempotent webhook for already-paid reference={}", reference);
            ensureStockDecremented(payment.getOrder().getId());
            orderConfirmationMailer.scheduleAfterPayment(payment.getOrder().getId());
            return;
        }

        applyPaid(payment, payment.getOrder(), payload);
    }

    /**
     * Heals paid orders that were marked paid before stock decrement ran.
     *
     * @param force when true, clears a stale {@code inventoryDecremented} flag first
     *              (use once for orders paid while inventory was broken).
     */
    private void ensureStockDecremented(UUID orderId, boolean force) {
        Order order = orderRepository.findByIdWithItemsAndProducts(orderId).orElse(null);
        if (order == null) {
            return;
        }
        if (!force && order.isInventoryDecremented()) {
            return;
        }
        log.info("Applying deferred stock decrement for paid order={} force={}", orderId, force);
        inventoryService.decrementForPaidOrder(order, force);
    }

    private void ensureStockDecremented(UUID orderId) {
        ensureStockDecremented(orderId, false);
    }

    private void applyPaid(Payment payment, Order order, Map<String, Object> rawPayload) {
        Order orderWithItems = orderRepository.findByIdWithItemsAndProducts(order.getId())
                .orElse(order);

        // Decrement stock before marking paid so a stock race rolls back the whole TX.
        inventoryService.decrementForPaidOrder(orderWithItems);

        payment.setStatus(PaymentRecordStatus.PAID);
        payment.setRawResponse(rawPayload);
        paymentRepository.save(payment);

        orderWithItems.setPaymentStatus(PaymentStatus.PAID);
        orderWithItems.setStatus(OrderStatus.PAID);
        orderRepository.save(orderWithItems);

        log.info("Marked order={} paid via Paystack reference={}",
                orderWithItems.getId(), payment.getProviderReference());
        orderConfirmationMailer.scheduleAfterPayment(orderWithItems.getId());
    }

    String resolveCallbackUrl(String requested, String storeSlug, String orderId) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        String base = frontendUrl;
        if (base == null || base.isBlank()) {
            base = "https://sme-operations.netlify.app";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/s/" + storeSlug + "/order/" + orderId;
    }

    private Order loadOrderForStore(Workspace workspace, String orderId) {
        UUID oid;
        try {
            oid = UUID.fromString(orderId);
        } catch (IllegalArgumentException e) {
            throw new OrderNotFoundException("Order not found: " + orderId);
        }
        return orderRepository.findByIdAndWorkspaceId(oid, workspace.getId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
    }

    private void verifySignature(String signatureHeader, String rawBody) {
        String secret = paystackClient.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new PaymentWebhookInvalidException("Webhook secret is not configured");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new PaymentWebhookInvalidException("Missing x-paystack-signature");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(digest);
            if (!computed.equalsIgnoreCase(signatureHeader.trim())) {
                throw new PaymentWebhookInvalidException("Invalid webhook signature");
            }
        } catch (PaymentWebhookInvalidException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PaymentWebhookInvalidException("Failed to verify webhook signature");
        }
    }

    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }
}
