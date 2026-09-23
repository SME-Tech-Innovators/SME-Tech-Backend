package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.*;
import sme.tech.innovators.sme.integration.bobgo.BobGoClient;
import sme.tech.innovators.sme.repository.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BobGoCancellationService {
    private final WorkspaceRepository workspaces;
    private final OrderRepository orders;
    private final OrderShipmentRepository shipments;
    private final BobGoClient bobgo;
    private final BobGoShipmentStatusService statuses;
    private final TransactionTemplate transactions;

    public void cancel(UUID workspaceId, UUID userId, UUID orderId) {
        // Commit the claim before calling the provider, also blocking retries after process failure.
        String tracking = transactions.execute(tx -> {
            workspaces.findByIdAndBusiness_Owner_Id(workspaceId, userId)
                    .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found or access denied"));
            Order order = orders.lockForReturn(orderId, workspaceId)
                    .orElseThrow(() -> new OrderNotFoundException("Order not found"));
            OrderShipment shipment = shipments.findByOrderId(orderId)
                    .orElseThrow(() -> new InvalidOrderStatusTransitionException("No Bob Go shipment exists to cancel"));
            if (!"bobgo".equalsIgnoreCase(shipment.getProvider()))
                throw new InvalidOrderStatusTransitionException("This shipment is not managed by Bob Go");
            if (Set.of(ShipmentStatus.CANCEL_REQUESTED, ShipmentStatus.CANCELLATION_UNKNOWN, ShipmentStatus.CANCELLED).contains(shipment.getStatus())) return null;
            if (shipment.getStatus() != ShipmentStatus.CREATED || shipment.getTrackingReference() == null
                    || order.getStatus() == OrderStatus.FULFILLED || order.getStatus() == OrderStatus.CANCELLED)
                throw new InvalidOrderStatusTransitionException("Cancellation is only available before collection; contact Bob Go for assistance");
            shipment.setStatus(ShipmentStatus.CANCEL_REQUESTED);
            shipments.saveAndFlush(shipment);
            return shipment.getTrackingReference();
        });
        if (tracking == null) return;
        try {
            statuses.apply(workspaceId, orderId, bobgo.cancelShipment(tracking));
            // An accepted request alone is not proof of cancellation.
            statuses.applyTrackingSnapshot(workspaceId, orderId, bobgo.getTracking(tracking));
        } catch (RuntimeException ex) {
            transactions.executeWithoutResult(tx -> {
                orders.lockForReturn(orderId, workspaceId).orElseThrow();
                var shipment = shipments.findByOrderId(orderId).orElseThrow();
                if (shipment.getStatus() == ShipmentStatus.CANCEL_REQUESTED) {
                    shipment.setStatus(ShipmentStatus.CANCELLATION_UNKNOWN);
                    shipment.setLastError("Cancellation could not be confirmed. Refresh tracking or contact Bob Go before retrying.");
                    shipments.save(shipment);
                }
            });
        }
    }
}
