package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.response.OrderConfirmationDto;
import sme.tech.innovators.sme.entity.Order;
import sme.tech.innovators.sme.entity.Workspace;
import sme.tech.innovators.sme.exception.OrderNotFoundException;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.OrderRepository;
import sme.tech.innovators.sme.repository.WorkspaceRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantOrderService {

    private final WorkspaceRepository workspaceRepository;
    private final OrderRepository orderRepository;
    private final CheckoutService checkoutService;

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

    private Workspace loadOwnedWorkspace(UUID workspaceId, UUID userId) {
        return workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Workspace not found or you do not have access to it"));
    }
}
