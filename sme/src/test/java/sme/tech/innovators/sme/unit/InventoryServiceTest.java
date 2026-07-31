package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.InsufficientStockException;
import sme.tech.innovators.sme.repository.OrderRepository;
import sme.tech.innovators.sme.repository.ProductRepository;
import sme.tech.innovators.sme.service.InventoryService;
import sme.tech.innovators.sme.service.OutOfStockMailer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock ProductRepository productRepository;
    @Mock OrderRepository orderRepository;
    @Mock OutOfStockMailer outOfStockMailer;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(productRepository, orderRepository, outOfStockMailer);
    }

    @Test
    void decrementOnceAndIdempotent() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .inventoryDecremented(false)
                .build();

        when(productRepository.findStockLinesForOrder(orderId))
                .thenReturn(List.<Object[]>of(new Object[]{productId, 2}));
        when(productRepository.decrementStockIfAvailable(productId, 2)).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.decrementForPaidOrder(order);
        assertThat(order.isInventoryDecremented()).isTrue();
        verify(outOfStockMailer).notifyIfSoldOut(productId);

        inventoryService.decrementForPaidOrder(order);
        verify(productRepository, times(1)).decrementStockIfAvailable(productId, 2);
        verify(outOfStockMailer, times(1)).notifyIfSoldOut(productId);
    }

    @Test
    void decrementFailsWhenStockTooLow() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .inventoryDecremented(false)
                .build();

        when(productRepository.findStockLinesForOrder(orderId))
                .thenReturn(List.<Object[]>of(new Object[]{productId, 2}));
        when(productRepository.decrementStockIfAvailable(productId, 2)).thenReturn(0);

        assertThatThrownBy(() -> inventoryService.decrementForPaidOrder(order))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(order.isInventoryDecremented()).isFalse();
        verify(orderRepository, never()).save(any());
        verify(outOfStockMailer, never()).notifyIfSoldOut(any());
    }

    @Test
    void forceClearsStaleFlagThenDecrements() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .inventoryDecremented(true)
                .build();

        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.findStockLinesForOrder(orderId))
                .thenReturn(List.<Object[]>of(new Object[]{productId, 1}));
        when(productRepository.decrementStockIfAvailable(productId, 1)).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.decrementForPaidOrder(order, true);

        assertThat(order.isInventoryDecremented()).isTrue();
        verify(productRepository).decrementStockIfAvailable(productId, 1);
        verify(outOfStockMailer).notifyIfSoldOut(productId);
    }

    @Test
    void restockOnlyWhenDecremented() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .inventoryDecremented(true)
                .build();
        when(productRepository.findStockLinesForOrder(orderId))
                .thenReturn(List.<Object[]>of(new Object[]{productId, 3}));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.restockForCancelledOrder(order);

        verify(productRepository).incrementStock(eq(productId), eq(3));
        assertThat(order.isInventoryDecremented()).isFalse();

        inventoryService.restockForCancelledOrder(order);
        verify(productRepository, times(1)).incrementStock(any(), anyInt());
    }
}
