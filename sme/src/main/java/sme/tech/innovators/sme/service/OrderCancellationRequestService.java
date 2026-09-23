package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import sme.tech.innovators.sme.dto.response.OrderCancellationDto;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.*;
import sme.tech.innovators.sme.repository.*;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderCancellationRequestService {
    private final CustomerOrderAccessService access;
    private final OrderRepository orders;
    private final OrderShipmentRepository shipments;
    private final WorkspaceRepository workspaces;
    private final BobGoCancellationService cancellation;
    private final InventoryService inventory;
    private final TransactionTemplate transactions;

    @Transactional(readOnly = true)
    public OrderCancellationDto customerStatus(String slug, UUID id, String token) {
        return describe(access.authorize(slug, id, token));
    }

    @Transactional
    public OrderCancellationDto request(String slug, UUID id, String token, String reason) {
        var authorized = access.authorize(slug, id, token);
        var order = orders.lockForReturn(id, authorized.getWorkspace().getId()).orElseThrow();
        // Revalidate after the lock in case an access link was rotated concurrently.
        access.authorize(slug, id, token);
        if (order.getCancellationRequestStatus() != null) return describe(order);
        requireEligible(order);
        order.setCancellationRequestStatus("REQUESTED");
        order.setCancellationRequestReason(reason.trim());
        order.setCancellationRequestedAt(LocalDateTime.now());
        orders.save(order);
        return describe(order);
    }

    @Transactional(readOnly = true)
    public OrderCancellationDto merchantStatus(UUID workspaceId, UUID userId, UUID id) {
        owner(workspaceId, userId);
        return describe(orders.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found")));
    }

    public OrderCancellationDto review(UUID workspaceId, UUID userId, UUID id, String decision, String note) {
        if (!"approve".equals(decision) && !"reject".equals(decision))
            throw new InvalidOrderStatusTransitionException("Decision must be approve or reject");
        boolean providerCall = Boolean.TRUE.equals(transactions.execute(tx -> {
            owner(workspaceId, userId);
            var order = orders.lockForReturn(id, workspaceId)
                    .orElseThrow(() -> new OrderNotFoundException("Order not found"));
            String target = "approve".equals(decision) ? "APPROVED" : "REJECTED";
            if (target.equals(order.getCancellationRequestStatus())) return false;
            if (!"REQUESTED".equals(order.getCancellationRequestStatus()))
                throw new InvalidOrderStatusTransitionException("No pending cancellation request");
            boolean bobgo = "bobgo".equalsIgnoreCase(order.getShippingProvider());
            if ("APPROVED".equals(target)) {
                requireEligible(order); // The carrier may have collected since the customer requested.
                if (!bobgo) {
                    inventory.restockForCancelledOrder(order);
                    order.setStatus(OrderStatus.CANCELLED);
                }
            }
            order.setCancellationRequestStatus(target);
            order.setCancellationReviewNote(note == null ? null : note.trim());
            order.setCancellationReviewedAt(LocalDateTime.now());
            orders.save(order);
            return "APPROVED".equals(target) && bobgo;
        }));
        if (providerCall) {
            try {
                cancellation.cancel(workspaceId, userId, id);
            } catch (RuntimeException ex) {
                // A failed claim made no provider call. Allow a fresh merchant review.
                transactions.executeWithoutResult(tx -> {
                    var order = orders.lockForReturn(id, workspaceId).orElseThrow();
                    order.setCancellationRequestStatus("REQUESTED");
                    order.setCancellationReviewedAt(null);
                    order.setCancellationReviewNote(null);
                    orders.save(order);
                });
                throw ex;
            }
        }
        return transactions.execute(tx -> merchantStatus(workspaceId, userId, id));
    }

    private void owner(UUID workspaceId, UUID userId) {
        workspaces.findByIdAndBusiness_Owner_Id(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found or access denied"));
    }

    private void requireEligible(Order order) {
        String reason = unavailable(order);
        if (reason != null) throw new InvalidOrderStatusTransitionException(reason);
    }

    private String unavailable(Order order) {
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.PROCESSING)
            return "Cancellation requests are available for paid orders before dispatch. Contact the store for help.";
        if (order.getPaymentStatus() != PaymentStatus.PAID)
            return "This payment is not eligible for a cancellation request. Contact the store.";
        var shipment = shipments.findByOrderId(order.getId()).orElse(null);
        if ("bobgo".equalsIgnoreCase(order.getShippingProvider())) {
            if (shipment == null || shipment.getStatus() != ShipmentStatus.CREATED
                    || shipment.getTrackingReference() == null)
                return "Cancellation is unavailable at this delivery stage. Contact the store for help or a return.";
        } else if (shipment != null && shipment.getStatus() != ShipmentStatus.CREATED
                && shipment.getStatus() != ShipmentStatus.PENDING) {
            return "This shipment can no longer be cancelled. Contact the store about a return.";
        }
        return null;
    }

    private OrderCancellationDto describe(Order order) {
        String unavailable = unavailable(order);
        String status = order.getCancellationRequestStatus();
        return new OrderCancellationDto(status == null ? "none" : status.toLowerCase(Locale.ROOT),
                order.getCancellationRequestReason(), order.getCancellationReviewNote(),
                order.getCancellationRequestedAt(), order.getCancellationReviewedAt(),
                status == null && unavailable == null, unavailable);
    }
}
