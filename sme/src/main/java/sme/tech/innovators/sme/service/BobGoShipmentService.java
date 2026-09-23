package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.ShipmentAlreadyExistsException;
import sme.tech.innovators.sme.exception.ShipmentCreateFailedException;
import sme.tech.innovators.sme.integration.bobgo.BobGoClient;
import sme.tech.innovators.sme.integration.bobgo.BobGoPayloadBuilder;
import sme.tech.innovators.sme.repository.OrderRepository;
import sme.tech.innovators.sme.repository.OrderShipmentRepository;
import sme.tech.innovators.sme.repository.WorkspaceShippingSettingsRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BobGoShipmentService {

    private final OrderRepository orderRepository;
    private final OrderShipmentRepository orderShipmentRepository;
    private final WorkspaceShippingSettingsRepository shippingSettingsRepository;
    private final BobGoClient bobGoClient;
    private final BobGoPayloadBuilder payloadBuilder;

    @Transactional
    public void createShipmentIfNeeded(UUID orderId) {
        Order order = orderRepository.findByIdWithItemsAndProducts(orderId).orElse(null);
        if (order == null || order.getPaymentStatus() != PaymentStatus.PAID) {
            return;
        }
        if (!"bobgo".equalsIgnoreCase(stringVal(order.getShippingProvider()))) {
            return;
        }
        OrderShipment pending = orderShipmentRepository.findByOrderId(orderId).orElse(null);
        if (pending != null && pending.getStatus() != ShipmentStatus.FAILED) {
            return;
        }
        if (pending == null) {
            pending = OrderShipment.builder()
                    .order(order)
                    .workspaceId(order.getWorkspace().getId())
                    .provider("bobgo")
                    .shippingOptionId(extractOptionId(order))
                    .status(ShipmentStatus.PENDING)
                    .build();
            orderShipmentRepository.save(pending);
        } else {
            pending.setStatus(ShipmentStatus.PENDING);
            pending.setLastError(null);
            orderShipmentRepository.save(pending);
        }

        try {
            Map<String, Object> response = bobGoClient.postShipments(buildShipmentBody(order));
            pending.setBobgoShipmentId(firstNonBlank(
                    stringVal(response.get("shipment_id")),
                    stringVal(response.get("id"))));
            pending.setTrackingReference(firstNonBlank(
                    stringVal(response.get("tracking_reference")),
                    stringVal(response.get("tracking_number")),
                    stringVal(response.get("awb"))));
            pending.setStatus(ShipmentStatus.CREATED);
            pending.setRawResponse(new LinkedHashMap<>(response));
            pending.setLastError(null);
            orderShipmentRepository.save(pending);

            if (order.getStatus() == OrderStatus.PAID) {
                order.setStatus(OrderStatus.PROCESSING);
                orderRepository.save(order);
            }
            log.info("Bob Go shipment created for order={} ref={}", orderId, pending.getTrackingReference());
        } catch (RuntimeException ex) {
            pending.setStatus(ShipmentStatus.FAILED);
            pending.setLastError(ex.getMessage());
            orderShipmentRepository.save(pending);
            log.warn("Bob Go shipment failed for order={}: {}", orderId, ex.getMessage());
        }
    }

    @Transactional
    public OrderShipment createShipmentForMerchant(UUID workspaceId, UUID orderId) {
        Order order = orderRepository.findByIdAndWorkspaceId(orderId, workspaceId)
                .orElseThrow(() -> new ShipmentCreateFailedException("Order not found"));
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new ShipmentCreateFailedException("Order must be paid before creating a shipment");
        }
        orderShipmentRepository.findByOrderId(orderId).ifPresent(existing -> {
            if (existing.getStatus() != ShipmentStatus.FAILED) {
                throw new ShipmentAlreadyExistsException("Shipment already exists for this order");
            }
        });
        createShipmentIfNeeded(orderId);
        return orderShipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ShipmentCreateFailedException("Shipment was not created"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildShipmentBody(Order order) {
        Map<String, Object> selection = order.getShippingSelection();
        Map<String, Object> meta = null;
        Map<String, Object> ratesPayload = null;
        if (selection != null) {
            if (selection.get("bobgoShipmentMeta") instanceof Map<?, ?> m) {
                meta = (Map<String, Object>) m;
            }
            if (selection.get("bobgoRatesPayload") instanceof Map<?, ?> r) {
                ratesPayload = (Map<String, Object>) r;
            }
        }

        WorkspaceShippingSettings settings = shippingSettingsRepository
                .findById(order.getWorkspace().getId())
                .orElse(null);

        String storeName = firstNonBlank(order.getWorkspace().getName(), "Store");
        String collectionEmail = "shipping@store.local";
        String collectionPhone = "+27800000000";

        if (ratesPayload == null) {
            if (settings == null) {
                throw new ShipmentCreateFailedException("Shipping settings not found");
            }
            ratesPayload = payloadBuilder.buildRatesPayload(
                    settings.getCollectionAddress(),
                    order.getShippingAddress(),
                    payloadBuilder.parcelsFromOrder(order),
                    order.getSubtotalAmount(),
                    storeName,
                    collectionEmail,
                    collectionPhone,
                    order.getCustomerName(),
                    order.getCustomerEmail(),
                    order.getCustomerPhone());
        }

        ratesPayload = payloadBuilder.applyShipmentContacts(
                ratesPayload,
                storeName,
                collectionEmail,
                collectionPhone,
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getCustomerPhone());

        Map<String, Object> shipmentBody = payloadBuilder.buildShipmentPayload(ratesPayload, meta);
        return payloadBuilder.applyShipmentContacts(
                shipmentBody,
                storeName,
                collectionEmail,
                collectionPhone,
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getCustomerPhone());
    }

    private static String extractOptionId(Order order) {
        if (order.getShippingSelection() != null && order.getShippingSelection().get("optionId") != null) {
            return order.getShippingSelection().get("optionId").toString();
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }
}
