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

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(productRepository, orderRepository);
    }

    @Test
    void decrementOnceAndIdempotent() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setQuantityAvailable(5);
        OrderItem item = OrderItem.builder().product(product).quantity(2).build();
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .inventoryDecremented(false)
                .items(List.of(item))
                .build();

        when(productRepository.decrementStockIfAvailable(product.getId(), 2)).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.decrementForPaidOrder(order);
        assertThat(order.isInventoryDecremented()).isTrue();

        inventoryService.decrementForPaidOrder(order);
        verify(productRepository, times(1)).decrementStockIfAvailable(product.getId(), 2);
    }

    @Test
    void decrementFailsWhenStockTooLow() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setQuantityAvailable(1);
        OrderItem item = OrderItem.builder().product(product).quantity(2).build();
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .inventoryDecremented(false)
                .items(List.of(item))
                .build();

        when(productRepository.decrementStockIfAvailable(product.getId(), 2)).thenReturn(0);

        assertThatThrownBy(() -> inventoryService.decrementForPaidOrder(order))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(order.isInventoryDecremented()).isFalse();
        verify(orderRepository, never()).save(any());
    }

    @Test
    void restockOnlyWhenDecremented() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        OrderItem item = OrderItem.builder().product(product).quantity(3).build();
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .inventoryDecremented(true)
                .items(List.of(item))
                .build();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.restockForCancelledOrder(order);

        verify(productRepository).incrementStock(eq(product.getId()), eq(3));
        assertThat(order.isInventoryDecremented()).isFalse();

        inventoryService.restockForCancelledOrder(order);
        verify(productRepository, times(1)).incrementStock(any(), anyInt());
    }
}
