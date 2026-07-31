package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.entity.Order;
import sme.tech.innovators.sme.exception.InsufficientStockException;
import sme.tech.innovators.sme.repository.OrderRepository;
import sme.tech.innovators.sme.repository.ProductRepository;

import java.util.List;
import java.util.UUID;

/**
 * Hard-stock decrement / restock for paid orders. Idempotent via {@code order.inventoryDecremented}.
 * Stock lines are read from {@code order_items} via native SQL so LAZY associations cannot skip work.
 * When a line reaches quantity 0, schedules a one-shot merchant out-of-stock email.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OutOfStockMailer outOfStockMailer;

    @Transactional
    public void decrementForPaidOrder(Order order) {
        decrementForPaidOrder(order, false);
    }

    /**
     * @param force when true, clears {@code inventoryDecremented} first so a paid order that was
     *              incorrectly flagged (stock never moved) can be healed once.
     */
    @Transactional
    public void decrementForPaidOrder(Order order, boolean force) {
        if (order == null) {
            return;
        }
        if (force && order.isInventoryDecremented()) {
            log.warn("Force inventory heal — clearing inventoryDecremented for order={}", order.getId());
            order.setInventoryDecremented(false);
            orderRepository.saveAndFlush(order);
        }
        if (order.isInventoryDecremented()) {
            log.info("Skipping stock decrement — already applied for order={}", order.getId());
            return;
        }

        List<Object[]> lines = productRepository.findStockLinesForOrder(order.getId());
        if (lines == null || lines.isEmpty()) {
            log.error("Cannot decrement stock — order {} has no product_id lines", order.getId());
            throw new InsufficientStockException(
                    "Order items are missing product links; stock was not changed.", null);
        }

        for (Object[] row : lines) {
            UUID productId = toUuid(row[0]);
            int qty = ((Number) row[1]).intValue();
            int updated = productRepository.decrementStockIfAvailable(productId, qty);
            if (updated == 0) {
                log.error("Stock decrement failed for order={} product={} qty={}",
                        order.getId(), productId, qty);
                throw new InsufficientStockException(
                        "Not enough stock for this product.", null);
            }
            outOfStockMailer.notifyIfSoldOut(productId);
        }

        order.setInventoryDecremented(true);
        orderRepository.save(order);
        log.info("Decremented stock for paid order={} lines={}", order.getId(), lines.size());
    }

    private static UUID toUuid(Object raw) {
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(String.valueOf(raw));
    }

    @Transactional
    public void restockForCancelledOrder(Order order) {
        if (order == null || !order.isInventoryDecremented()) {
            return;
        }
        List<Object[]> lines = productRepository.findStockLinesForOrder(order.getId());
        if (lines != null) {
            for (Object[] row : lines) {
                UUID productId = toUuid(row[0]);
                int qty = ((Number) row[1]).intValue();
                productRepository.incrementStock(productId, qty);
            }
        }
        order.setInventoryDecremented(false);
        orderRepository.save(order);
        log.info("Restocked inventory for cancelled order={}", order.getId());
    }
}
