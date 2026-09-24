package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import sme.tech.innovators.sme.dto.request.ReturnQuoteRequest;
import sme.tech.innovators.sme.dto.response.*;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.*;
import sme.tech.innovators.sme.integration.bobgo.*;
import sme.tech.innovators.sme.integration.paystack.PaystackClient;
import sme.tech.innovators.sme.repository.*;
import java.time.LocalDateTime;
import java.util.*;

/** Full-order returns. Provider mutations run only after the durable claim has committed. */
@Service
@RequiredArgsConstructor
public class OrderReturnService {
    private final WorkspaceRepository workspaces;
    private final OrderRepository orders;
    private final OrderReturnRepository returns;
    private final PaymentRepository payments;
    private final WorkspaceShippingSettingsRepository shippingSettings;
    private final BobGoClient bobgo;
    private final BobGoPayloadBuilder payloadBuilder;
    private final BobGoRateMapper rateMapper;
    private final PaystackClient paystack;
    private final TransactionTemplate transactions;

    public OrderReturnDto quote(UUID workspaceId, UUID userId, UUID orderId, ReturnQuoteRequest request) {
        return transactions.execute(tx -> {
            Order order = ownedOrder(workspaceId, userId, orderId);
            require(order.getStatus() == OrderStatus.FULFILLED && order.getPaymentStatus() == PaymentStatus.PAID,
                    "Only fulfilled, paid orders can be returned");
            require("bobgo".equalsIgnoreCase(order.getShippingProvider()), "Order was not shipped with Bob Go");
            OrderReturn ret = returns.findById(orderId).orElseGet(OrderReturn::new);
            require("QUOTED".equals(ret.getShipmentStatus()), "Return shipment has already been requested");
            WorkspaceShippingSettings settings = shippingSettings.findById(workspaceId)
                    .orElseThrow(() -> invalid("Shipping settings not found"));
            require(settings.getCollectionAddress() != null && order.getShippingAddress() != null,
                    "Collection and return delivery addresses are required");
            require(order.getCustomerEmail() != null && !order.getCustomerEmail().isBlank(),
                    "Customer email is required for the return");
            List<Map<String, Object>> parcels = request.parcels().stream().map(p -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("description", p.description());
                row.put("submitted_length_cm", p.lengthCm());
                row.put("submitted_width_cm", p.widthCm());
                row.put("submitted_height_cm", p.heightCm());
                row.put("submitted_weight_kg", p.weightKg());
                return row;
            }).toList();
            // Original deliveries are standalone shipments, not Bob Go orders. Book a reverse shipment.
            Map<String, Object> body = payloadBuilder.buildRatesPayload(order.getShippingAddress(),
                    settings.getCollectionAddress(), parcels, order.getSubtotalAmount(),
                    order.getCustomerName(), order.getCustomerEmail(), order.getCustomerPhone(),
                    request.merchantContactName(), request.merchantContactEmail(), request.merchantContactPhone());
            Map<String, Object> rates = bobgo.postRates(body);
            require(!rateMapper.parseRatesResponse(rates, order.getCurrency()).isEmpty(), "No return courier rates available");
            ret.setOrderId(orderId);
            ret.setReason(request.reason());
            ret.setRates(rates);
            ret.setQuotedAt(LocalDateTime.now());
            ret.setShipmentPayload(payloadBuilder.applyShipmentContacts(body,
                    order.getCustomerName(), order.getCustomerEmail(), order.getCustomerPhone(),
                    request.merchantContactName(), request.merchantContactEmail(), request.merchantContactPhone()));
            returns.save(ret);
            return dto(ret, order);
        });
    }

    public OrderReturnDto get(UUID workspaceId, UUID userId, UUID orderId) {
        return transactions.execute(tx -> dto(load(orderId, ownedOrder(workspaceId, userId, orderId)),
                orders.findById(orderId).orElseThrow()));
    }

    public OrderReturnDto createShipment(UUID workspaceId, UUID userId, UUID orderId, String optionId) {
        Map<String, Object> body = transactions.execute(tx -> {
            Order order = ownedOrder(workspaceId, userId, orderId);
            OrderReturn ret = load(orderId, order);
            if (!"QUOTED".equals(ret.getShipmentStatus())) return null;
            require(order.getStatus() == OrderStatus.FULFILLED && order.getPaymentStatus() == PaymentStatus.PAID,
                    "Order is no longer eligible for return");
            require(ret.getQuotedAt().isAfter(LocalDateTime.now().minusMinutes(15)), "Return quote expired; request new rates");
            var rate = rateMapper.parseRatesResponse(ret.getRates(), order.getCurrency()).stream()
                    .filter(r -> r.optionId().equals(optionId)).findFirst()
                    .orElseThrow(() -> invalid("Select an option from this return quote"));
            // Preserve the shipment contact fields; the generic builder strips them.
            Map<String, Object> payload = new LinkedHashMap<>(ret.getShipmentPayload());
            payload.put("provider_slug", rate.shipmentMeta().get("provider_slug"));
            payload.put("service_level_code", rate.shipmentMeta().get("service_level_code"));
            payload.put("custom_order_number", "RETURN-" + order.getOrderNumber());
            ret.setShipmentStatus("SUBMITTING");
            returns.saveAndFlush(ret);
            return payload;
        });
        if (body == null) return get(workspaceId, userId, orderId);
        try {
            Map<String, Object> response = bobgo.postShipments(body);
            String tracking = text(response, "tracking_reference");
            String id = text(response, "id");
            if (id == null) id = text(response, "shipment_id");
            require(tracking != null && id != null, "Bob Go response missing shipment identifiers");
            final String shipmentId = id;
            transactions.executeWithoutResult(tx -> {
                ownedOrder(workspaceId, userId, orderId);
                OrderReturn ret = returns.findById(orderId).orElseThrow();
                ret.setTrackingReference(tracking);
                ret.setShipmentId(shipmentId);
                ret.setShipmentStatus("CREATED");
                returns.save(ret);
            });
        } catch (RuntimeException ex) {
            markUnknown(workspaceId, userId, orderId, false);
        }
        return get(workspaceId, userId, orderId);
    }

    public OrderReturnDto receive(UUID workspaceId, UUID userId, UUID orderId) {
        return transactions.execute(tx -> {
            Order order = ownedOrder(workspaceId, userId, orderId);
            OrderReturn ret = load(orderId, order);
            require(Set.of("CREATED", "RECEIVED").contains(ret.getShipmentStatus()), "Return shipment must exist before receipt");
            if (ret.getReceivedAt() == null) ret.setReceivedAt(LocalDateTime.now());
            ret.setShipmentStatus("RECEIVED");
            returns.save(ret);
            return dto(ret, order);
        });
    }

    public OrderReturnDto refund(UUID workspaceId, UUID userId, UUID orderId) {
        RefundCommand command = transactions.execute(tx -> {
            Order order = ownedOrder(workspaceId, userId, orderId);
            OrderReturn ret = load(orderId, order);
            require(ret.getReceivedAt() != null, "Confirm receipt before requesting a refund");
            if (!"NOT_REQUESTED".equals(ret.getRefundStatus())) return null;
            require(order.getPaymentStatus() == PaymentStatus.PAID, "Order is not paid");
            List<Payment> paid = payments.findByOrderIdAndStatus(orderId, PaymentRecordStatus.PAID);
            require(paid.size() == 1, "Exactly one successful payment is required; reconcile payments first");
            Payment payment = paid.getFirst();
            require("paystack".equals(payment.getProvider()) && payment.getAmount().compareTo(order.getTotalAmount()) == 0
                    && payment.getCurrency().equals(order.getCurrency()), "Payment does not match the order");
            int amount = payment.getAmount().movePointRight(2).intValueExact();
            require(amount > 0, "Refund amount must be positive");
            ret.setPaymentId(payment.getId());
            ret.setRefundStatus("SUBMITTING");
            returns.saveAndFlush(ret);
            return new RefundCommand(payment.getProviderReference(), amount, payment.getCurrency(), ret.getReason());
        });
        if (command == null) return get(workspaceId, userId, orderId);
        try {
            Map<String, Object> response = paystack.createRefund(command.reference(), command.amount(), command.currency(), command.reason());
            require(text(response, "id") != null, "Paystack response missing refund identifier");
            applyRefund(workspaceId, userId, orderId, response);
        } catch (RuntimeException ex) {
            markUnknown(workspaceId, userId, orderId, true);
        }
        return get(workspaceId, userId, orderId);
    }

    public OrderReturnDto refreshRefund(UUID workspaceId, UUID userId, UUID orderId) {
        String id = transactions.execute(tx -> {
            OrderReturn ret = load(orderId, ownedOrder(workspaceId, userId, orderId));
            require(ret.getRefundId() != null, "No refund ID available; reconcile the provider result before retrying");
            return ret.getRefundId();
        });
        applyRefund(workspaceId, userId, orderId, paystack.fetchRefund(id));
        return get(workspaceId, userId, orderId);
    }

    private void applyRefund(UUID workspaceId, UUID userId, UUID orderId, Map<String, Object> response) {
        transactions.executeWithoutResult(tx -> {
            Order order = ownedOrder(workspaceId, userId, orderId);
            OrderReturn ret = load(orderId, order);
            String id = text(response, "id");
            require(id != null && (ret.getRefundId() == null || ret.getRefundId().equals(id)), "Refund identifier mismatch");
            Payment payment = payments.findById(ret.getPaymentId()).orElseThrow();
            require(new java.math.BigDecimal(String.valueOf(response.get("amount"))).compareTo(payment.getAmount().movePointRight(2)) == 0
                    && payment.getCurrency().equals(response.get("currency")), "Refund amount or currency mismatch");
            ret.setRefundId(id);
            String status = text(response, "status");
            require(status != null, "Missing refund status");
            if (!"PROCESSED".equals(ret.getRefundStatus())) ret.setRefundStatus(status.toUpperCase(Locale.ROOT).replace('-', '_'));
            if ("PROCESSED".equals(ret.getRefundStatus())) order.setPaymentStatus(PaymentStatus.REFUNDED);
            returns.save(ret);
            orders.save(order);
        });
    }

    private void markUnknown(UUID workspaceId, UUID userId, UUID orderId, boolean refund) {
        transactions.executeWithoutResult(tx -> {
            OrderReturn ret = load(orderId, ownedOrder(workspaceId, userId, orderId));
            if (refund && "SUBMITTING".equals(ret.getRefundStatus())) ret.setRefundStatus("UNKNOWN");
            if (!refund && "SUBMITTING".equals(ret.getShipmentStatus())) ret.setShipmentStatus("UNKNOWN");
            returns.save(ret);
        });
    }

    private Order ownedOrder(UUID workspaceId, UUID userId, UUID orderId) {
        workspaces.findByIdAndBusiness_Owner_Id(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found or access denied"));
        return orders.lockForReturn(orderId, workspaceId).orElseThrow(() -> new OrderNotFoundException("Order not found"));
    }
    private OrderReturn load(UUID orderId, Order order) {
        return returns.findById(orderId).orElseThrow(() -> invalid("Return not found; request a return quote first"));
    }
    private OrderReturnDto dto(OrderReturn ret, Order order) {
        var options = rateMapper.parseRatesResponse(ret.getRates(), order.getCurrency()).stream()
                .map(r -> ShippingQuoteDto.ShippingOptionDto.builder().id(r.optionId()).provider("bobgo")
                        .label(r.label()).amount(r.amountMinor()).currency(r.currency()).estimatedDays(r.estimatedDays()).build()).toList();
        return new OrderReturnDto(ret.getOrderId(), ret.getShipmentStatus(), ret.getRefundStatus(),
                ret.getTrackingReference(), ret.getShipmentId(), ret.getRefundId(), ret.getReceivedAt(), options);
    }
    private static String text(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
    private static void require(boolean condition, String message) { if (!condition) throw invalid(message); }
    private static InvalidOrderStatusTransitionException invalid(String message) { return new InvalidOrderStatusTransitionException(message); }
    private record RefundCommand(String reference, int amount, String currency, String reason) {}
}
