package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.request.CreateDeliveryRequest;
import sme.tech.innovators.sme.dto.request.UpdateDeliveryRequest;
import sme.tech.innovators.sme.dto.response.DeliveryDto;
import sme.tech.innovators.sme.dto.response.DeliveryEventDto;
import sme.tech.innovators.sme.dto.response.DeliveryPublicDto;
import sme.tech.innovators.sme.entity.Delivery;
import sme.tech.innovators.sme.entity.DeliveryEvent;
import sme.tech.innovators.sme.entity.DeliveryStatus;
import sme.tech.innovators.sme.entity.Order;
import sme.tech.innovators.sme.entity.Workspace;
import sme.tech.innovators.sme.exception.InvalidOrderStatusTransitionException;
import sme.tech.innovators.sme.exception.OrderNotFoundException;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.DeliveryEventRepository;
import sme.tech.innovators.sme.repository.DeliveryRepository;
import sme.tech.innovators.sme.repository.OrderRepository;
import sme.tech.innovators.sme.repository.WorkspaceRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private static final String PUBLIC_LOOKUP_MISS = "We couldn't find an order with those details.";

    private final WorkspaceRepository workspaceRepository;
    private final OrderRepository orderRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryEventRepository deliveryEventRepository;
    private final PublicStoreResolver publicStoreResolver;

    @Transactional
    public DeliveryDto create(UUID workspaceId, UUID userId, UUID orderId, CreateDeliveryRequest request) {
        loadOwnedWorkspace(workspaceId, userId);
        Order order = loadOrder(workspaceId, orderId);
        if (deliveryRepository.existsByOrderId(orderId)) {
            throw new InvalidOrderStatusTransitionException("Delivery already exists for this order");
        }

        DeliveryStatus status = parseStatus(request.getStatus(), DeliveryStatus.PENDING);
        Delivery delivery = Delivery.builder()
                .order(order)
                .status(status)
                .carrierName(trimToNull(request.getCarrierName()))
                .trackingNumber(trimToNull(request.getTrackingNumber()))
                .trackingUrl(trimToNull(request.getTrackingUrl()))
                .deliveryNotes(trimToNull(request.getDeliveryNotes()))
                .publicNotes(trimToNull(request.getPublicNotes()))
                .estimatedDeliveryAt(request.getEstimatedDeliveryAt())
                .build();
        applyStatusTimestamp(delivery, status);
        delivery = deliveryRepository.save(delivery);
        recordEvent(delivery, status, request.getDeliveryNotes(), "merchant");
        return toDto(delivery);
    }

    @Transactional
    public DeliveryDto update(UUID workspaceId, UUID userId, UUID orderId, UpdateDeliveryRequest request) {
        loadOwnedWorkspace(workspaceId, userId);
        Order order = loadOrder(workspaceId, orderId);
        Delivery delivery = deliveryRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new OrderNotFoundException("Delivery not found for order: " + orderId));

        DeliveryStatus current = delivery.getStatus();
        DeliveryStatus target = request.getStatus() == null || request.getStatus().isBlank()
                ? current
                : parseStatus(request.getStatus(), current);
        if (target != current && !isAllowedTransition(current, target)) {
            throw new InvalidOrderStatusTransitionException(
                    "Cannot change delivery status from " + lower(current) + " to " + lower(target));
        }

        boolean changed = target != current;
        changed |= updateIfChanged(delivery.getCarrierName(), request.getCarrierName(), delivery::setCarrierName);
        changed |= updateIfChanged(delivery.getTrackingNumber(), request.getTrackingNumber(), delivery::setTrackingNumber);
        changed |= updateIfChanged(delivery.getTrackingUrl(), request.getTrackingUrl(), delivery::setTrackingUrl);
        changed |= updateIfChanged(delivery.getDeliveryNotes(), request.getDeliveryNotes(), delivery::setDeliveryNotes);
        changed |= updateIfChanged(delivery.getPublicNotes(), request.getPublicNotes(), delivery::setPublicNotes);
        changed |= updateIfChanged(delivery.getEstimatedDeliveryAt(), request.getEstimatedDeliveryAt(), delivery::setEstimatedDeliveryAt);

        if (target != current) {
            delivery.setStatus(target);
            applyStatusTimestamp(delivery, target);
        }
        if (changed) {
            deliveryRepository.save(delivery);
            recordEvent(delivery, target, request.getDeliveryNotes(), "merchant");
        }
        return toDto(delivery);
    }

    @Transactional(readOnly = true)
    public DeliveryDto get(UUID workspaceId, UUID userId, UUID orderId) {
        loadOwnedWorkspace(workspaceId, userId);
        loadOrder(workspaceId, orderId);
        return deliveryRepository.findByOrderId(orderId)
                .map(this::toDto)
                .orElseThrow(() -> new OrderNotFoundException("Delivery not found for order: " + orderId));
    }

    @Transactional(readOnly = true)
    public DeliveryPublicDto getPublic(String storeSlug, UUID orderId, String orderNumber, String email) {
        Workspace workspace = publicStoreResolver.requireLiveWorkspace(storeSlug);
        String normalizedNumber = orderNumber == null ? "" : orderNumber.trim();
        String normalizedEmail = email == null ? "" : email.trim();
        if (normalizedNumber.isEmpty() || normalizedEmail.isEmpty()) {
            throw new OrderNotFoundException(PUBLIC_LOOKUP_MISS);
        }

        Order order = orderRepository
                .findByWorkspaceIdAndOrderNumberIgnoreCaseAndCustomerEmailIgnoreCase(
                        workspace.getId(), normalizedNumber, normalizedEmail)
                .filter(candidate -> candidate.getId().equals(orderId))
                .orElseThrow(() -> new OrderNotFoundException(PUBLIC_LOOKUP_MISS));

        return deliveryRepository.findByOrderId(order.getId())
                .map(this::toPublicDto)
                .orElseThrow(() -> new OrderNotFoundException("Delivery not found for order: " + orderId));
    }

    public static boolean isAllowedTransition(DeliveryStatus from, DeliveryStatus to) {
        if (from == null || to == null || from == to) return false;
        return switch (from) {
            case PENDING -> to == DeliveryStatus.ASSIGNED || to == DeliveryStatus.CANCELLED;
            case ASSIGNED -> to == DeliveryStatus.DISPATCHED || to == DeliveryStatus.CANCELLED;
            case DISPATCHED -> to == DeliveryStatus.IN_TRANSIT
                    || to == DeliveryStatus.FAILED || to == DeliveryStatus.CANCELLED;
            case IN_TRANSIT -> to == DeliveryStatus.OUT_FOR_DELIVERY
                    || to == DeliveryStatus.DELIVERED || to == DeliveryStatus.FAILED;
            case OUT_FOR_DELIVERY -> to == DeliveryStatus.DELIVERED || to == DeliveryStatus.FAILED;
            case FAILED -> to == DeliveryStatus.ASSIGNED
                    || to == DeliveryStatus.DISPATCHED || to == DeliveryStatus.CANCELLED;
            case RETURNED -> to == DeliveryStatus.ASSIGNED || to == DeliveryStatus.DISPATCHED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    public static DeliveryStatus parseStatus(String raw, DeliveryStatus defaultStatus) {
        if (raw == null || raw.isBlank()) return defaultStatus;
        try {
            return DeliveryStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            throw new InvalidOrderStatusTransitionException(
                    "delivery status must be one of: pending, assigned, dispatched, in_transit, "
                            + "out_for_delivery, delivered, failed, returned, cancelled");
        }
    }

    private boolean updateIfChanged(String current, String incoming, java.util.function.Consumer<String> setter) {
        if (incoming == null) return false;
        String normalized = trimToNull(incoming);
        if (Objects.equals(current, normalized)) return false;
        setter.accept(normalized);
        return true;
    }

    private boolean updateIfChanged(LocalDateTime current, LocalDateTime incoming,
                                    java.util.function.Consumer<LocalDateTime> setter) {
        if (incoming == null || Objects.equals(current, incoming)) return false;
        setter.accept(incoming);
        return true;
    }

    private void applyStatusTimestamp(Delivery delivery, DeliveryStatus status) {
        if (status == DeliveryStatus.DISPATCHED && delivery.getDispatchedAt() == null) {
            delivery.setDispatchedAt(LocalDateTime.now());
        }
        if (status == DeliveryStatus.DELIVERED && delivery.getDeliveredAt() == null) {
            delivery.setDeliveredAt(LocalDateTime.now());
        }
        if (status == DeliveryStatus.CANCELLED) delivery.setActive(false);
    }

    private void recordEvent(Delivery delivery, DeliveryStatus status, String note, String source) {
        deliveryEventRepository.save(DeliveryEvent.builder()
                .delivery(delivery)
                .status(status)
                .note(trimToNull(note))
                .source(source)
                .build());
    }

    private DeliveryDto toDto(Delivery delivery) {
        List<DeliveryEventDto> events = deliveryEventRepository
                .findAllByDeliveryIdOrderByCreatedAtAsc(delivery.getId())
                .stream()
                .map(event -> DeliveryEventDto.builder()
                        .id(event.getId().toString())
                        .status(lower(event.getStatus()))
                        .note(event.getNote())
                        .source(event.getSource())
                        .createdAt(event.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return DeliveryDto.builder()
                .id(delivery.getId().toString())
                .orderId(delivery.getOrder().getId().toString())
                .status(lower(delivery.getStatus()))
                .carrierName(delivery.getCarrierName())
                .trackingNumber(delivery.getTrackingNumber())
                .parcelTrackingNumber(delivery.getParcelTrackingNumber())
                .provider(delivery.getProvider())
                .externalShipmentId(delivery.getExternalShipmentId())
                .serviceLevelCode(delivery.getServiceLevelCode())
                .serviceLevelName(delivery.getServiceLevelName())
                .quotedAmount(delivery.getQuotedAmount())
                .trackingUrl(delivery.getTrackingUrl())
                .deliveryNotes(delivery.getDeliveryNotes())
                .publicNotes(delivery.getPublicNotes())
                .estimatedDeliveryAt(delivery.getEstimatedDeliveryAt())
                .dispatchedAt(delivery.getDispatchedAt())
                .deliveredAt(delivery.getDeliveredAt())
                .active(delivery.isActive())
                .createdAt(delivery.getCreatedAt())
                .updatedAt(delivery.getUpdatedAt())
                .events(events)
                .build();
    }

    public DeliveryPublicDto toPublicDto(Delivery delivery) {
        return DeliveryPublicDto.builder()
                .id(delivery.getId().toString())
                .status(lower(delivery.getStatus()))
                .carrierName(delivery.getCarrierName())
                .trackingNumber(delivery.getTrackingNumber())
                .trackingUrl(delivery.getTrackingUrl())
                .publicNotes(delivery.getPublicNotes())
                .estimatedDeliveryAt(delivery.getEstimatedDeliveryAt())
                .dispatchedAt(delivery.getDispatchedAt())
                .deliveredAt(delivery.getDeliveredAt())
                .build();
    }

    private Order loadOrder(UUID workspaceId, UUID orderId) {
        return orderRepository.findByIdAndWorkspaceId(orderId, workspaceId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
    }

    private Workspace loadOwnedWorkspace(UUID workspaceId, UUID userId) {
        return workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Workspace not found or you do not have access to it"));
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String lower(DeliveryStatus status) {
        return status.name().toLowerCase(Locale.ROOT);
    }
}
