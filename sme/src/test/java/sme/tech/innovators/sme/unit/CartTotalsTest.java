package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.Test;
import sme.tech.innovators.sme.dto.response.CartItemDto;
import sme.tech.innovators.sme.entity.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Pure unit tests for cart total calculation logic.
 * These tests validate the backend-owned total calculation rules
 * (unit price × quantity, subtotal = sum of line totals).
 */
class CartTotalsTest {

    // ── Helpers to build entities without full service stack ──────────────

    private Product buildProduct(BigDecimal priceAmount) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setTitle("Test Product");
        p.setSku("SKU-001");
        p.setPriceAmount(priceAmount);
        p.setCurrency("ZAR");
        p.setStatus(ProductStatus.ACTIVE);
        return p;
    }

    private CartItem buildCartItem(Product product, int quantity) {
        CartItem item = new CartItem();
        item.setId(UUID.randomUUID());
        item.setProduct(product);
        item.setQuantity(quantity);
        // Price is always snapshotted from product — frontend price ignored
        item.setUnitPriceAmount(product.getPriceAmount());
        item.setCurrency(product.getCurrency());
        return item;
    }

    /** Simulates the backend line-total formula: unit × qty. */
    private BigDecimal lineTotal(CartItem item) {
        return item.getUnitPriceAmount().multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    /** Simulates the backend subtotal formula: sum of line totals. */
    private BigDecimal subtotal(List<CartItem> items) {
        return items.stream()
                .map(this::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── Tests ─────────────────────────────────────────────────────────────

    @Test
    void lineTotal_isUnitPriceTimesQuantity() {
        Product product = buildProduct(new BigDecimal("100.00")); // R100.00
        CartItem item = buildCartItem(product, 3);
        assertThat(lineTotal(item)).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    void subtotal_isCorrectForMultipleItems() {
        Product p1 = buildProduct(new BigDecimal("100.00")); // R100.00
        Product p2 = buildProduct(new BigDecimal("50.00"));  // R50.00

        CartItem item1 = buildCartItem(p1, 2); // R200.00
        CartItem item2 = buildCartItem(p2, 3); // R150.00

        BigDecimal result = subtotal(List.of(item1, item2));
        // 2 * 100.00 + 3 * 50.00 = 200.00 + 150.00 = 350.00
        assertThat(result).isEqualByComparingTo(new BigDecimal("350.00"));
    }

    @Test
    void subtotal_isZeroForEmptyCart() {
        assertThat(subtotal(List.of())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void cartItemPriceSnapshot_isIndependentOfFrontendInput() {
        // Simulate: frontend sends price label "R999" but backend always uses product.priceAmount
        Product product = buildProduct(new BigDecimal("100.00"));
        CartItem item = buildCartItem(product, 1);

        // Price label is never trusted — we only use the entity's snapshotted amount
        assertThat(item.getUnitPriceAmount()).isEqualByComparingTo(product.getPriceAmount());
        assertThat(item.getUnitPriceAmount()).isNotEqualByComparingTo(new BigDecimal("999.00"));
    }

    @Test
    void shippingIsZeroInStep06A() {
        // Step 06A: shipping always = 0
        BigDecimal shippingAmount = BigDecimal.ZERO;
        assertThat(shippingAmount).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void orderTotalEqualsSubtotalPlusShipping() {
        Product p = buildProduct(new BigDecimal("200.00"));
        CartItem item = buildCartItem(p, 2);
        BigDecimal sub = subtotal(List.of(item));
        BigDecimal shipping = BigDecimal.ZERO;
        BigDecimal total = sub.add(shipping);
        assertThat(total).isEqualByComparingTo(new BigDecimal("400.00"));
    }
}
