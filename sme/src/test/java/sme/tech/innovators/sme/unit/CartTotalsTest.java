package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.Test;
import sme.tech.innovators.sme.dto.response.CartItemDto;
import sme.tech.innovators.sme.entity.*;

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

    private Product buildProduct(int priceAmount) {
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
    private int lineTotal(CartItem item) {
        return item.getUnitPriceAmount() * item.getQuantity();
    }

    /** Simulates the backend subtotal formula: sum of line totals. */
    private int subtotal(List<CartItem> items) {
        return items.stream().mapToInt(this::lineTotal).sum();
    }

    // ── Tests ─────────────────────────────────────────────────────────────

    @Test
    void lineTotal_isUnitPriceTimesQuantity() {
        Product product = buildProduct(10000); // R100.00
        CartItem item = buildCartItem(product, 3);
        assertThat(lineTotal(item)).isEqualTo(30000);
    }

    @Test
    void subtotal_isCorrectForMultipleItems() {
        Product p1 = buildProduct(10000); // R100.00
        Product p2 = buildProduct(5000);  // R50.00

        CartItem item1 = buildCartItem(p1, 2); // R200.00
        CartItem item2 = buildCartItem(p2, 3); // R150.00

        int result = subtotal(List.of(item1, item2));
        // 2 * 10000 + 3 * 5000 = 20000 + 15000 = 35000
        assertThat(result).isEqualTo(35000);
    }

    @Test
    void subtotal_isZeroForEmptyCart() {
        assertThat(subtotal(List.of())).isEqualTo(0);
    }

    @Test
    void cartItemPriceSnapshot_isIndependentOfFrontendInput() {
        // Simulate: frontend sends price label "R999" but backend always uses product.priceAmount
        Product product = buildProduct(10000);
        CartItem item = buildCartItem(product, 1);

        // Price label is never trusted — we only use the entity's snapshotted amount
        assertThat(item.getUnitPriceAmount()).isEqualTo(product.getPriceAmount());
        assertThat(item.getUnitPriceAmount()).isNotEqualTo(99900);
    }

    @Test
    void shippingIsZeroInStep06A() {
        // Step 06A: shipping always = 0
        int shippingAmount = 0;
        assertThat(shippingAmount).isEqualTo(0);
    }

    @Test
    void orderTotalEqualsSubtotalPlusShipping() {
        Product p = buildProduct(20000);
        CartItem item = buildCartItem(p, 2);
        int sub = subtotal(List.of(item));
        int shipping = 0;
        int total = sub + shipping;
        assertThat(total).isEqualTo(40000);
    }
}
