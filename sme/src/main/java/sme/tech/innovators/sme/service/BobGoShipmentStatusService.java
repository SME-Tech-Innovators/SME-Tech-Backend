package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.repository.*;
import sme.tech.innovators.sme.integration.bobgo.BobGoShipmentStatuses;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BobGoShipmentStatusService {
    private final OrderRepository orders;
    private final OrderShipmentRepository shipments;

    @Transactional
    public void apply(UUID workspaceId, UUID orderId, Map<String, Object> payload) {
        applyPayload(workspaceId, orderId, payload, false);
    }

    @Transactional
    public void applyTrackingSnapshot(UUID workspaceId, UUID orderId, Map<String, Object> payload) {
        applyPayload(workspaceId, orderId, payload, true);
    }

    private void applyPayload(UUID workspaceId, UUID orderId, Map<String, Object> payload, boolean snapshot) {
        var order = orders.lockForReturn(orderId, workspaceId).orElseThrow();
        var shipment = shipments.findByOrderId(orderId).orElseThrow();
        Map<?, ?> root = payload;
        if (payload != null && payload.get("data") instanceof Map<?, ?> data) root = data;
        else if (payload != null && payload.get("fulfillment") instanceof Map<?, ?> fulfillment) root = fulfillment;
        if (root != null) {
            if (shipment.getTrackingReference() == null) {
                Object tracking = root.get("tracking_reference");
                if (tracking != null && !tracking.toString().isBlank()) shipment.setTrackingReference(tracking.toString());
            }
            if (shipment.getBobgoShipmentId() == null) {
                Object shipmentId = root.get("shipment_id");
                if (shipmentId != null && !shipmentId.toString().isBlank()) shipment.setBobgoShipmentId(shipmentId.toString());
            }
        }
        if (snapshot) BobGoShipmentStatuses.applyTrackingSnapshot(shipment, BobGoShipmentStatuses.parse(payload));
        else BobGoShipmentStatuses.apply(shipment, BobGoShipmentStatuses.parse(payload));
        BobGoShipmentStatuses.syncOrder(shipment);
        var raw = new LinkedHashMap<String, Object>();
        if (shipment.getRawResponse() != null) raw.putAll(shipment.getRawResponse());
        if (payload != null) raw.put("lastProviderStatus", payload);
        shipment.setRawResponse(raw);
        shipments.save(shipment);
        orders.save(order);
    }
}
