package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.response.OrderConfirmationDto;
import sme.tech.innovators.sme.entity.Order;
import sme.tech.innovators.sme.entity.OrderStatus;
import sme.tech.innovators.sme.entity.Workspace;
import sme.tech.innovators.sme.exception.InvalidOrderStatusTransitionException;
import sme.tech.innovators.sme.exception.OrderNotFoundException;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.OrderRepository;
import sme.tech.innovators.sme.repository.WorkspaceRepository;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantOrderService {

    private final WorkspaceRepository workspaceRepository;
    private final OrderRepository orderRepository;
    private final CheckoutService checkoutService;
    private final InventoryService inventoryService;

    @Transactional(readOnly = true)
    public List<OrderConfirmationDto> listOrders(UUID workspaceId, UUID userId) {
        loadOwnedWorkspace(workspaceId, userId);
        return orderRepository.findAllByWorkspace_IdOrderByCreatedAtDesc(workspaceId)
                .stream()
                .map(checkoutService::toConfirmationDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderConfirmationDto getOrder(UUID workspaceId, UUID userId, UUID orderId) {
        loadOwnedWorkspace(workspaceId, userId);
        Order order = orderRepository.findByIdAndWorkspaceId(orderId, workspaceId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        return checkoutService.toConfirmationDto(order);
    }

    @Transactional
    public OrderConfirmationDto updateOrderStatus(UUID workspaceId,
                                                   UUID userId,
                                                   UUID orderId,
                                                   String statusRaw) {
        loadOwnedWorkspace(workspaceId, userId);
        Order order = orderRepository.findByIdAndWorkspaceId(orderId, workspaceId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        OrderStatus target = parseMerchantStatus(statusRaw);
        OrderStatus current = order.getStatus();
        if (!isAllowedTransition(current, target)) {
            throw new InvalidOrderStatusTransitionException(
                    "Cannot change order status from " + current.name().toLowerCase(Locale.ROOT)
                            + " to " + target.name().toLowerCase(Locale.ROOT));
        }

        if (target == OrderStatus.CANCELLED) {
            inventoryService.restockForCancelledOrder(order);
        }

        order.setStatus(target);
        orderRepository.save(order);
        log.info("Merchant updated order={} status {} -> {}", orderId, current, target);
        return checkoutService.toConfirmationDto(order);
    }

    public static OrderStatus parseMerchantStatus(String statusRaw) {
        if (statusRaw == null || statusRaw.isBlank()) {
            throw new InvalidOrderStatusTransitionException(
                    "status must be one of: processing, fulfilled, cancelled");
        }
        String normalized = statusRaw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "processing" -> OrderStatus.PROCESSING;
            case "fulfilled" -> OrderStatus.FULFILLED;
            case "cancelled", "canceled" -> OrderStatus.CANCELLED;
            default -> throw new InvalidOrderStatusTransitionException(
                    "status must be one of: processing, fulfilled, cancelled");
        };
    }

    public static boolean isAllowedTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case PAID -> to == OrderStatus.PROCESSING || to == OrderStatus.CANCELLED;
            case PROCESSING -> to == OrderStatus.FULFILLED || to == OrderStatus.CANCELLED;
            default -> false;
        };
    }

    private Workspace loadOwnedWorkspace(UUID workspaceId, UUID userId) {
        return workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Workspace not found or you do not have access to it"));
    }
}
