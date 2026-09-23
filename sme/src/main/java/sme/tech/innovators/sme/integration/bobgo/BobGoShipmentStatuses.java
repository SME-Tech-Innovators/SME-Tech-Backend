package sme.tech.innovators.sme.integration.bobgo;

import sme.tech.innovators.sme.entity.*;
import java.util.Locale;
import java.util.Map;

/** Exact provider states: e.g. out-for-delivery is NOT delivered. Unknown events preserve state. */
public final class BobGoShipmentStatuses {
    private BobGoShipmentStatuses() {}

    public static ShipmentStatus parse(Map<String, Object> payload) {
        if (payload == null) return null;
        for (String wrapper : new String[]{"data", "fulfillment"}) {
            if (payload.get(wrapper) instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked") var map = (Map<String, Object>) nested;
                return parse(map);
            }
        }
        Object raw = payload.get("status");
        if (raw == null) raw = payload.get("fulfillment_status");
        if (raw == null) raw = payload.get("state");
        if (raw == null) return null;
        return switch (raw.toString().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "created", "submitted", "pending-collection", "collection-assigned", "collection-pending", "ready-for-collection" -> ShipmentStatus.CREATED;
            case "collected", "in-transit", "out-for-delivery", "dispatched", "delivery-attempted", "delivery-failed" -> ShipmentStatus.IN_TRANSIT;
            case "delivered" -> ShipmentStatus.DELIVERED;
            case "cancelled", "canceled" -> ShipmentStatus.CANCELLED;
            default -> null;
        };
    }

    public static void apply(OrderShipment shipment, ShipmentStatus next) {
        apply(shipment, next, false);
    }

    /** A direct tracking snapshot can correct nonterminal progress; webhooks may arrive out of order. */
    public static void applyTrackingSnapshot(OrderShipment shipment, ShipmentStatus next) {
        apply(shipment, next, true);
    }

    private static void apply(OrderShipment shipment, ShipmentStatus next, boolean snapshot) {
        if (next == null) return;
        ShipmentStatus current = shipment.getStatus();
        if (current == ShipmentStatus.DELIVERED || current == ShipmentStatus.CANCELLED) return;
        if (!snapshot && current == ShipmentStatus.IN_TRANSIT && next == ShipmentStatus.CREATED) return;
        if ((current == ShipmentStatus.CANCEL_REQUESTED || current == ShipmentStatus.CANCELLATION_UNKNOWN)
                && next == ShipmentStatus.CREATED) return;
        shipment.setStatus(next);
        shipment.setLastError(null);
    }

    public static void syncOrder(OrderShipment shipment) {
        Order order = shipment.getOrder();
        if (order == null || !"bobgo".equalsIgnoreCase(shipment.getProvider())) return;
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.FULFILLED) return;
        if (order.getPaymentStatus() != PaymentStatus.PAID && order.getPaymentStatus() != PaymentStatus.REFUNDED) return;
        switch (shipment.getStatus()) {
            case CREATED, IN_TRANSIT -> order.setStatus(OrderStatus.PROCESSING);
            case DELIVERED -> order.setStatus(OrderStatus.FULFILLED);
            case CANCELLED -> order.setStatus(OrderStatus.CANCELLED);
            default -> { }
        }
        // Shipment cancellation confirms neither a payment refund nor physical stock receipt.
    }
}
