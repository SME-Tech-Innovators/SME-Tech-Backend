package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sme.tech.innovators.sme.dto.response.OrderConfirmationDto;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.InvalidOrderStatusTransitionException;
import sme.tech.innovators.sme.repository.OrderRepository;
import sme.tech.innovators.sme.repository.WorkspaceRepository;
import sme.tech.innovators.sme.service.CheckoutService;
import sme.tech.innovators.sme.service.InventoryService;
import sme.tech.innovators.sme.service.MerchantOrderService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantOrderServiceTest {

    @Mock WorkspaceRepository workspaceRepository;
    @Mock OrderRepository orderRepository;
    @Mock CheckoutService checkoutService;
    @Mock InventoryService inventoryService;

    private MerchantOrderService service;
    private UUID workspaceId;
    private UUID userId;
    private Workspace workspace;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new MerchantOrderService(workspaceRepository, orderRepository, checkoutService, inventoryService);
        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        workspace = Workspace.builder().id(workspaceId).name("Store").build();
        order = Order.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .orderNumber("ORD-1")
                .customerName("Ada")
                .customerEmail("ada@example.com")
                .customerPhone("+2700")
                .subtotalAmount(1000)
                .shippingAmount(0)
                .totalAmount(1000)
                .currency("ZAR")
                .status(OrderStatus.PAID)
                .paymentStatus(PaymentStatus.PAID)
                .items(List.of())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        lenient().when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId))
                .thenReturn(Optional.of(workspace));
        lenient().when(checkoutService.toConfirmationDto(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return OrderConfirmationDto.builder()
                    .id(o.getId().toString())
                    .orderNumber(o.getOrderNumber())
                    .status(o.getStatus().name().toLowerCase())
                    .paymentStatus(o.getPaymentStatus().name().toLowerCase())
                    .build();
        });
    }

    @Test
    void paidCanMoveToProcessing() {
        when(orderRepository.findByIdAndWorkspaceId(order.getId(), workspaceId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderConfirmationDto dto = service.updateOrderStatus(
                workspaceId, userId, order.getId(), "processing");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(dto.getStatus()).isEqualTo("processing");
        verify(inventoryService, never()).restockForCancelledOrder(any());
    }

    @Test
    void cancelAfterPaidRestocks() {
        when(orderRepository.findByIdAndWorkspaceId(order.getId(), workspaceId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateOrderStatus(workspaceId, userId, order.getId(), "cancelled");

        verify(inventoryService).restockForCancelledOrder(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void processingCanMoveToFulfilled() {
        order.setStatus(OrderStatus.PROCESSING);
        when(orderRepository.findByIdAndWorkspaceId(order.getId(), workspaceId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderConfirmationDto dto = service.updateOrderStatus(
                workspaceId, userId, order.getId(), "fulfilled");

        assertThat(dto.getStatus()).isEqualTo("fulfilled");
    }

    @Test
    void paidCannotMoveDirectlyToFulfilled() {
        when(orderRepository.findByIdAndWorkspaceId(order.getId(), workspaceId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateOrderStatus(
                workspaceId, userId, order.getId(), "fulfilled"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void pendingPaymentCannotBeUpdated() {
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findByIdAndWorkspaceId(order.getId(), workspaceId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateOrderStatus(
                workspaceId, userId, order.getId(), "processing"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    @Test
    void invalidStatusValueRejected() {
        when(orderRepository.findByIdAndWorkspaceId(order.getId(), workspaceId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateOrderStatus(
                workspaceId, userId, order.getId(), "shipped"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    @Test
    void allowedTransitionMatrix() {
        assertThat(MerchantOrderService.isAllowedTransition(OrderStatus.PAID, OrderStatus.PROCESSING)).isTrue();
        assertThat(MerchantOrderService.isAllowedTransition(OrderStatus.PAID, OrderStatus.CANCELLED)).isTrue();
        assertThat(MerchantOrderService.isAllowedTransition(OrderStatus.PROCESSING, OrderStatus.FULFILLED)).isTrue();
        assertThat(MerchantOrderService.isAllowedTransition(OrderStatus.PROCESSING, OrderStatus.CANCELLED)).isTrue();
        assertThat(MerchantOrderService.isAllowedTransition(OrderStatus.PAID, OrderStatus.FULFILLED)).isFalse();
        assertThat(MerchantOrderService.isAllowedTransition(OrderStatus.FULFILLED, OrderStatus.CANCELLED)).isFalse();
    }
}
