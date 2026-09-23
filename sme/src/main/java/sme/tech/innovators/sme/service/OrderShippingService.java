package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.response.OrderShippingStatusDto;
import sme.tech.innovators.sme.entity.Order;
import sme.tech.innovators.sme.entity.OrderShipment;
import sme.tech.innovators.sme.entity.ShipmentStatus;
import sme.tech.innovators.sme.exception.OrderNotFoundException;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.config.BobGoConfig;
import sme.tech.innovators.sme.integration.bobgo.BobGoClient;
import sme.tech.innovators.sme.integration.bobgo.BobGoTrackingUrls;
import sme.tech.innovators.sme.repository.OrderRepository;
import sme.tech.innovators.sme.repository.OrderShipmentRepository;
import sme.tech.innovators.sme.repository.WorkspaceRepository;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderShippingService {

    private final WorkspaceRepository workspaceRepository;
    private final OrderRepository orderRepository;
    private final OrderShipmentRepository orderShipmentRepository;
    private final BobGoClient bobGoClient;
    private final BobGoConfig bobGoConfig;
    private final PublicStoreResolver publicStoreResolver;
    private final BobGoShipmentStatusService shipmentStatusService;

    @Transactional(readOnly = true)
    public OrderShippingStatusDto getMerchantShipping(UUID workspaceId, UUID userId, UUID orderId) {
        loadOwnedWorkspace(workspaceId, userId);
        return buildStatus(loadOrder(workspaceId, orderId), true);
    }

    @Transactional(readOnly = true)
    public OrderShippingStatusDto getPublicShipping(String storeSlug, UUID orderId) {
        var workspace = publicStoreResolver.requireLiveWorkspace(storeSlug);
        Order order = orderRepository.findByIdAndWorkspaceId(orderId, workspace.getId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        return buildStatus(order, false);
    }

    public OrderShippingStatusDto refreshTracking(UUID workspaceId, UUID userId, UUID orderId) {
        loadOwnedWorkspace(workspaceId, userId);
        loadOrder(workspaceId, orderId);
        OrderShipment shipment = orderShipmentRepository.findByOrderId(orderId).orElse(null);
        if (shipment != null && "bobgo".equalsIgnoreCase(shipment.getProvider())
                && shipment.getTrackingReference() != null) {
            Map<String, Object> tracking = bobGoClient.getTracking(shipment.getTrackingReference());
            shipmentStatusService.applyTrackingSnapshot(workspaceId, orderId, tracking);
        }
        return getMerchantShipping(workspaceId, userId, orderId);
    }

    private Order loadOrder(UUID workspaceId, UUID orderId) {
        return orderRepository.findByIdAndWorkspaceId(orderId, workspaceId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
    }

    private OrderShippingStatusDto buildStatus(Order order, boolean includeInternalIds) {
        Optional<OrderShipment> shipmentOpt = orderShipmentRepository.findByOrderId(order.getId());
        if (shipmentOpt.isEmpty()) {
            String provider = order.getShippingProvider();
            if (provider == null && order.getShippingAmount() != null
                    && order.getShippingAmount().signum() == 0) {
                return OrderShippingStatusDto.builder()
                        .provider("none")
                        .status("not_required")
                        .statusLabel("No shipment")
                        .build();
            }
            return OrderShippingStatusDto.builder()
                    .provider(provider)
                    .status("pending")
                    .statusLabel("Awaiting shipment")
                    .build();
        }

        OrderShipment shipment = shipmentOpt.get();
        String trackingRef = shipment.getTrackingReference();
        String trackingUrl = null;
        if ("bobgo".equalsIgnoreCase(shipment.getProvider()) && trackingRef != null) {
            trackingUrl = BobGoTrackingUrls.publicTrackingUrl(trackingRef, bobGoConfig.isSandbox());
        }
        OrderShippingStatusDto.OrderShippingStatusDtoBuilder builder = OrderShippingStatusDto.builder()
                .canCancel(includeInternalIds && "bobgo".equalsIgnoreCase(shipment.getProvider())
                        && shipment.getStatus() == ShipmentStatus.CREATED && trackingRef != null
                        && order.getStatus() != sme.tech.innovators.sme.entity.OrderStatus.FULFILLED
                        && order.getStatus() != sme.tech.innovators.sme.entity.OrderStatus.CANCELLED)
                .provider(shipment.getProvider())
                .status(shipment.getStatus().name().toLowerCase(Locale.ROOT))
                .statusLabel(statusLabel(shipment.getStatus()))
                .trackingReference(trackingRef)
                .trackingUrl(trackingUrl)
                .shippingOptionLabel(order.getShippingMethod())
                .lastError(shipment.getLastError());
        if (includeInternalIds) {
            builder.bobgoShipmentId(shipment.getBobgoShipmentId());
        }
        return builder.build();
    }

    private static String statusLabel(ShipmentStatus status) {
        return switch (status) {
            case PENDING -> "Pending";
            case CREATED -> "Label created";
            case IN_TRANSIT -> "In transit";
            case DELIVERED -> "Delivered";
            case CANCEL_REQUESTED -> "Cancellation requested — awaiting Bob Go";
            case CANCELLATION_UNKNOWN -> "Cancellation requires verification with Bob Go";
            case CANCELLED -> "Cancelled by Bob Go";
            case FAILED -> "Failed";
        };
    }

    private void loadOwnedWorkspace(UUID workspaceId, UUID userId) {
        workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Workspace not found or you do not have access to it"));
    }
}
