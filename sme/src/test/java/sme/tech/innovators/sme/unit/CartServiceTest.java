package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sme.tech.innovators.sme.dto.response.CartDto;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.CartNotFoundException;
import sme.tech.innovators.sme.exception.InsufficientStockException;
import sme.tech.innovators.sme.exception.InvalidQuantityException;
import sme.tech.innovators.sme.exception.ProductNotAvailableException;
import sme.tech.innovators.sme.repository.CartItemRepository;
import sme.tech.innovators.sme.repository.CartRepository;
import sme.tech.innovators.sme.repository.ProductRepository;
import sme.tech.innovators.sme.service.CartService;
import sme.tech.innovators.sme.service.PublicStoreResolver;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartRepository cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock ProductRepository productRepository;
    @Mock PublicStoreResolver publicStoreResolver;

    private CartService cartService;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, cartItemRepository, productRepository, publicStoreResolver);
        workspace = Workspace.builder()
                .id(UUID.randomUUID())
                .name("Bridge Labs")
                .publicSlug("bridge-labs")
                .status(WorkspaceStatus.LIVE)
                .build();
        lenient().when(publicStoreResolver.requireLiveWorkspace("bridge-labs")).thenReturn(workspace);
    }

    @Test
    void createCartReturnsSessionAndZeroTotals() {
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> {
            Cart c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setCreatedAt(LocalDateTime.now());
            c.setUpdatedAt(LocalDateTime.now());
            return c;
        });

        CartDto dto = cartService.createCart("bridge-labs");

        assertThat(dto.getWorkspaceId()).isEqualTo(workspace.getId().toString());
        assertThat(dto.getCustomerSessionId()).isNotBlank();
        assertThat(dto.getStatus()).isEqualTo("active");
        assertThat(dto.getSubtotalAmount()).isZero();
        assertThat(dto.getTotalAmount()).isZero();
        assertThat(dto.getItems()).isEmpty();
    }

    @Test
    void addItemRejectsInactiveProduct() {
        Cart cart = activeCart();
        stubActiveCart(cart);
        UUID productId = UUID.randomUUID();
        when(productRepository.findByWorkspaceIdAndIdAndStatus(workspace.getId(), productId, ProductStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem("bridge-labs", cart.getId().toString(), productId.toString(), 1))
                .isInstanceOf(ProductNotAvailableException.class);
    }

    @Test
    void addItemRejectsInvalidQuantity() {
        assertThatThrownBy(() -> cartService.addItem("bridge-labs", UUID.randomUUID().toString(), UUID.randomUUID().toString(), 0))
                .isInstanceOf(InvalidQuantityException.class);
    }

    @Test
    void addItemMergesSameProductAndKeepsPriceSnapshot() {
        Cart cart = activeCart();
        stubActiveCart(cart);

        Product product = product(10000);
        when(productRepository.findByWorkspaceIdAndIdAndStatus(workspace.getId(), product.getId(), ProductStatus.ACTIVE))
                .thenReturn(Optional.of(product));

        CartItem existing = CartItem.builder()
                .id(UUID.randomUUID())
                .cart(cart)
                .product(product)
                .quantity(1)
                .unitPriceAmount(10000)
                .currency("ZAR")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        cart.getItems().add(existing);

        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId()))
                .thenReturn(Optional.of(existing));
        when(cartItemRepository.save(existing)).thenReturn(existing);
        when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

        product.setPriceAmount(99999); // later product price change must not affect snapshot
        CartDto dto = cartService.addItem("bridge-labs", cart.getId().toString(), product.getId().toString(), 2);

        assertThat(existing.getQuantity()).isEqualTo(3);
        assertThat(existing.getUnitPriceAmount()).isEqualTo(10000);
        assertThat(dto.getSubtotalAmount()).isEqualTo(30000);
        assertThat(dto.getTotalAmount()).isEqualTo(30000);
        assertThat(dto.getItems().get(0).getCartId()).isEqualTo(cart.getId().toString());
    }

    @Test
    void addItemRejectsWhenQuantityExceedsStock() {
        Cart cart = activeCart();
        stubActiveCart(cart);
        Product product = product(10000);
        product.setQuantityAvailable(2);
        when(productRepository.findByWorkspaceIdAndIdAndStatus(
                workspace.getId(), product.getId(), ProductStatus.ACTIVE))
                .thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(
                "bridge-labs", cart.getId().toString(), product.getId().toString(), 3))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void wrongStoreOrMissingCartThrowsCartNotFound() {
        UUID cartId = UUID.randomUUID();
        when(cartRepository.findByIdAndWorkspaceIdAndStatus(cartId, workspace.getId(), CartStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.getCart("bridge-labs", cartId.toString()))
                .isInstanceOf(CartNotFoundException.class);
    }

    @Test
    void invalidCartIdFormatThrowsCartNotFound() {
        assertThatThrownBy(() -> cartService.getCart("bridge-labs", "not-a-uuid"))
                .isInstanceOf(CartNotFoundException.class);
    }

    private Cart activeCart() {
        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .customerSessionId("session-1")
                .status(CartStatus.ACTIVE)
                .currency("ZAR")
                .items(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return cart;
    }

    private void stubActiveCart(Cart cart) {
        when(cartRepository.findByIdAndWorkspaceIdAndStatus(cart.getId(), workspace.getId(), CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
    }

    private Product product(int price) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setTitle("Tee");
        p.setSlug("tee");
        p.setSku("SKU-1");
        p.setPriceAmount(price);
        p.setCurrency("ZAR");
        p.setQuantityAvailable(999);
        p.setStatus(ProductStatus.ACTIVE);
        return p;
    }
}
