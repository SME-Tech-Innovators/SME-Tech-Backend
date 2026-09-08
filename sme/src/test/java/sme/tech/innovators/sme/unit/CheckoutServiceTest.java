package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sme.tech.innovators.sme.dto.request.CheckoutRequest;
import sme.tech.innovators.sme.dto.response.OrderConfirmationDto;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.CartEmptyException;
import sme.tech.innovators.sme.exception.CartNotFoundException;
import sme.tech.innovators.sme.exception.InsufficientStockException;
import sme.tech.innovators.sme.exception.OrderNotFoundException;
import sme.tech.innovators.sme.repository.CartRepository;
import sme.tech.innovators.sme.repository.OrderRepository;
import sme.tech.innovators.sme.service.CheckoutService;
import sme.tech.innovators.sme.service.PublicStoreResolver;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock CartRepository cartRepository;
    @Mock OrderRepository orderRepository;
    @Mock PublicStoreResolver publicStoreResolver;

    private CheckoutService checkoutService;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        checkoutService = new CheckoutService(cartRepository, orderRepository, publicStoreResolver);
        workspace = Workspace.builder()
                .id(UUID.randomUUID())
                .name("Bridge Labs")
                .publicSlug("bridge-labs")
                .status(WorkspaceStatus.LIVE)
                .build();
        when(publicStoreResolver.requireLiveWorkspace("bridge-labs")).thenReturn(workspace);
    }

    @Test
    void checkoutRejectsEmptyCart() {
        Cart cart = cartWithItems(List.of());
        when(cartRepository.findByIdAndWorkspaceIdAndStatus(cart.getId(), workspace.getId(), CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> checkoutService.checkout("bridge-labs", checkoutRequest(cart.getId())))
                .isInstanceOf(CartEmptyException.class);
    }

    @Test
    void checkoutCreatesPendingPaymentUnpaidOrderWithSnapshots() {
        Product product = product(new BigDecimal("150.00"), "Classic Tee", "SKU-TEE");
        CartItem item = cartItem(product, 2, new BigDecimal("150.00"));
        Cart cart = cartWithItems(List.of(item));

        when(cartRepository.findByIdAndWorkspaceIdAndStatus(cart.getId(), workspace.getId(), CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(orderRepository.existsByOrderNumber(any())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setId(UUID.randomUUID());
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());
            order.getItems().forEach(oi -> oi.setId(UUID.randomUUID()));
            return order;
        });

        OrderConfirmationDto dto = checkoutService.checkout("bridge-labs", checkoutRequest(cart.getId()));

        // Later product edits must not rewrite already-created order snapshots
        product.setPriceAmount(new BigDecimal("999.99"));
        product.setTitle("CHANGED TITLE");
        product.setSku("CHANGED-SKU");

        assertThat(dto.getStatus()).isEqualTo("pending_payment");
        assertThat(dto.getPaymentStatus()).isEqualTo("unpaid");
        assertThat(dto.getShippingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getSubtotalAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(dto.getTotalAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(dto.getWorkspaceId()).isEqualTo(workspace.getId().toString());
        assertThat(dto.getCartId()).isEqualTo(cart.getId().toString());
        assertThat(dto.getItems()).hasSize(1);
        assertThat(dto.getItems().get(0).getTitle()).isEqualTo("Classic Tee");
        assertThat(dto.getItems().get(0).getSku()).isEqualTo("SKU-TEE");
        assertThat(dto.getItems().get(0).getUnitPriceAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(dto.getItems().get(0).getTotalAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(dto.getItems().get(0).getOrderId()).isEqualTo(dto.getId());
        assertThat(cart.getStatus()).isEqualTo(CartStatus.CONVERTED);
        verify(cartRepository).save(cart);
    }

    @Test
    void checkoutRejectsInsufficientStock() {
        Product product = product(new BigDecimal("150.00"), "Classic Tee", "SKU-TEE");
        product.setQuantityAvailable(1);
        Cart cart = cartWithItems(List.of(cartItem(product, 2, new BigDecimal("150.00"))));
        when(cartRepository.findByIdAndWorkspaceIdAndStatus(cart.getId(), workspace.getId(), CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> checkoutService.checkout("bridge-labs", checkoutRequest(cart.getId())))
                .isInstanceOf(InsufficientStockException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void checkoutWrongStoreCartThrowsNotFound() {
        UUID cartId = UUID.randomUUID();
        when(cartRepository.findByIdAndWorkspaceIdAndStatus(cartId, workspace.getId(), CartStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkoutService.checkout("bridge-labs", checkoutRequest(cartId)))
                .isInstanceOf(CartNotFoundException.class);
    }

    @Test
    void getOrderWrongStoreThrowsNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdAndWorkspaceId(orderId, workspace.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkoutService.getOrderConfirmation("bridge-labs", orderId.toString()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void lookupOrderMatchesCaseInsensitiveTrimmedFields() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .orderNumber("ORD-1001")
                .customerName("Ada")
                .customerEmail("ada@example.com")
                .customerPhone("+2700")
                .subtotalAmount(new BigDecimal("10.00"))
                .shippingAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("10.00"))
                .currency("ZAR")
                .status(OrderStatus.PROCESSING)
                .paymentStatus(PaymentStatus.PAID)
                .items(List.of())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(orderRepository.findByWorkspaceIdAndOrderNumberIgnoreCaseAndCustomerEmailIgnoreCase(
                eq(workspace.getId()), eq("ord-1001"), eq("Ada@Example.com")))
                .thenReturn(Optional.of(order));

        OrderConfirmationDto dto = checkoutService.lookupOrder(
                "bridge-labs", "  ord-1001  ", "  Ada@Example.com  ");

        assertThat(dto.getOrderNumber()).isEqualTo("ORD-1001");
        assertThat(dto.getStatus()).isEqualTo("processing");
        assertThat(dto.getPaymentStatus()).isEqualTo("paid");
    }

    @Test
    void lookupOrderMissUsesGenericMessage() {
        when(orderRepository.findByWorkspaceIdAndOrderNumberIgnoreCaseAndCustomerEmailIgnoreCase(
                any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkoutService.lookupOrder("bridge-labs", "ORD-X", "x@y.com"))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage(CheckoutService.GENERIC_LOOKUP_MISS);
    }

    @Test
    void orderTotalEqualsSubtotalWhenShippingZero() {
        Product product = product(new BigDecimal("200.00"), "Hoodie", "SKU-H");
        Cart cart = cartWithItems(List.of(cartItem(product, 2, new BigDecimal("200.00"))));
        when(cartRepository.findByIdAndWorkspaceIdAndStatus(cart.getId(), workspace.getId(), CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(orderRepository.existsByOrderNumber(any())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setId(UUID.randomUUID());
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());
            order.getItems().forEach(oi -> oi.setId(UUID.randomUUID()));
            return order;
        });

        OrderConfirmationDto dto = checkoutService.checkout("bridge-labs", checkoutRequest(cart.getId()));
        assertThat(dto.getTotalAmount()).isEqualByComparingTo(dto.getSubtotalAmount().add(dto.getShippingAmount()));
        assertThat(dto.getTotalAmount()).isEqualByComparingTo(new BigDecimal("400.00"));
    }

    private CheckoutRequest checkoutRequest(UUID cartId) {
        CheckoutRequest req = new CheckoutRequest();
        req.setCartId(cartId.toString());
        CheckoutRequest.CustomerInfo customer = new CheckoutRequest.CustomerInfo();
        customer.setName("Ada Lovelace");
        customer.setEmail("ada@example.com");
        customer.setPhone("+27000000000");
        req.setCustomer(customer);
        CheckoutRequest.ShippingAddress address = new CheckoutRequest.ShippingAddress();
        address.setLine1("123 Main");
        address.setCity("Cape Town");
        address.setProvince("Western Cape");
        address.setPostalCode("8001");
        address.setCountry("ZA");
        req.setShippingAddress(address);
        return req;
    }

    private Cart cartWithItems(List<CartItem> items) {
        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .customerSessionId("session")
                .status(CartStatus.ACTIVE)
                .currency("ZAR")
                .items(new ArrayList<>(items))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        items.forEach(i -> i.setCart(cart));
        return cart;
    }

    private CartItem cartItem(Product product, int qty, BigDecimal unitPrice) {
        return CartItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .quantity(qty)
                .unitPriceAmount(unitPrice)
                .currency("ZAR")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Product product(BigDecimal price, String title, String sku) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setTitle(title);
        p.setSku(sku);
        p.setPriceAmount(price);
        p.setCurrency("ZAR");
        p.setQuantityAvailable(999);
        p.setStatus(ProductStatus.ACTIVE);
        return p;
    }
}
