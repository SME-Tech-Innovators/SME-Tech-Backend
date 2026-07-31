package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.entity.Order;
import sme.tech.innovators.sme.entity.OrderItem;
import sme.tech.innovators.sme.exception.InsufficientStockException;
import sme.tech.innovators.sme.repository.OrderRepository;
import sme.tech.innovators.sme.repository.ProductRepository;

/**
 * Hard-stock decrement / restock for paid orders. Idempotent via {@code order.inventoryDecremented}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public void decrementForPaidOrder(Order order) {
        if (order == null) {
            return;
        }
        if (order.isInventoryDecremented()) {
            log.info("Skipping stock decrement — already applied for order={}", order.getId());
            return;
        }
        if (order.getItems() == null || order.getItems().isEmpty()) {
            log.error("Cannot decrement stock — order {} has no line items", order.getId());
            throw new InsufficientStockException(
                    "Order has no line items to decrement stock for.", null);
        }

        int decrementedLines = 0;
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                log.warn("Skipping order item without product/qty on order={}", order.getId());
                continue;
            }
            int updated = productRepository.decrementStockIfAvailable(
                    item.getProduct().getId(), item.getQuantity());
            if (updated == 0) {
                log.error("Stock decrement failed for order={} product={} qty={}",
                        order.getId(), item.getProduct().getId(), item.getQuantity());
                throw new InsufficientStockException(
                        "Not enough stock for this product.",
                        item.getProduct().getQuantityAvailable());
            }
            decrementedLines++;
        }

        if (decrementedLines == 0) {
            log.error("Cannot decrement stock — no order items linked to products for order={}",
                    order.getId());
            throw new InsufficientStockException(
                    "Order items are missing product links; stock was not changed.", null);
        }

        order.setInventoryDecremented(true);
        orderRepository.save(order);
        log.info("Decremented stock for paid order={} lines={}", order.getId(), decrementedLines);
    }

    @Transactional
    public void restockForCancelledOrder(Order order) {
        if (order == null || !order.isInventoryDecremented()) {
            return;
        }
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                    continue;
                }
                productRepository.incrementStock(item.getProduct().getId(), item.getQuantity());
            }
        }
        order.setInventoryDecremented(false);
        orderRepository.save(order);
        log.info("Restocked inventory for cancelled order={}", order.getId());
    }
}
