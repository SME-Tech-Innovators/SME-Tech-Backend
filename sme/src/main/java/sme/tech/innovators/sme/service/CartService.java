package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.response.CartDto;
import sme.tech.innovators.sme.dto.response.CartItemDto;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.*;
import sme.tech.innovators.sme.repository.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final WorkspaceRepository workspaceRepository;

    /**
     * Creates an empty active cart for the given store (resolved by public slug).
     */
    @Transactional
    public CartDto createCart(String storeSlug) {
        Workspace workspace = resolveWorkspace(storeSlug);
        String sessionId = UUID.randomUUID().toString();
        Cart cart = Cart.builder()
                .workspace(workspace)
                .customerSessionId(sessionId)
                .currency("ZAR")
                .build();
        cart = cartRepository.save(cart);
        log.info("Created cart {} for store {}", cart.getId(), storeSlug);
        return toCartDto(cart);
    }

    /**
     * Returns the cart with backend-calculated totals.
     */
    @Transactional(readOnly = true)
    public CartDto getCart(String storeSlug, String cartId) {
        Workspace workspace = resolveWorkspace(storeSlug);
        Cart cart = resolveActiveCart(UUID.fromString(cartId), workspace.getId());
        return toCartDto(cart);
    }

    /**
     * Adds a product to the cart. Snapshots the price from the backend — never trusts frontend price.
     */
    @Transactional
    public CartDto addItem(String storeSlug, String cartId, String productId, int quantity) {
        if (quantity < 1) {
            throw new InvalidQuantityException("Quantity must be at least 1");
        }
        Workspace workspace = resolveWorkspace(storeSlug);
        Cart cart = resolveActiveCart(UUID.fromString(cartId), workspace.getId());

        Product product = productRepository.findByWorkspaceIdAndIdAndStatus(
                workspace.getId(), UUID.fromString(productId), ProductStatus.ACTIVE)
                .orElseThrow(() -> new ProductNotAvailableException(
                        "Product is not available: " + productId));

        CartItem item = CartItem.builder()
                .cart(cart)
                .product(product)
                .quantity(quantity)
                .unitPriceAmount(product.getPriceAmount())
                .currency(product.getCurrency())
                .build();
        cartItemRepository.save(item);

        cart.getItems().add(item);
        cartRepository.save(cart);

        return toCartDto(cart);
    }

    /**
     * Updates the quantity of an existing cart item.
     */
    @Transactional
    public CartDto updateItem(String storeSlug, String cartId, String itemId, int quantity) {
        if (quantity < 1) {
            throw new InvalidQuantityException("Quantity must be at least 1");
        }
        Workspace workspace = resolveWorkspace(storeSlug);
        Cart cart = resolveActiveCart(UUID.fromString(cartId), workspace.getId());
        CartItem item = cartItemRepository.findByIdAndCartId(
                UUID.fromString(itemId), cart.getId())
                .orElseThrow(() -> new CartItemNotFoundException("Cart item not found: " + itemId));

        item.setQuantity(quantity);
        cartItemRepository.save(item);

        return toCartDto(reloadCart(cart.getId()));
    }

    /**
     * Removes an item from the cart.
     */
    @Transactional
    public CartDto removeItem(String storeSlug, String cartId, String itemId) {
        Workspace workspace = resolveWorkspace(storeSlug);
        Cart cart = resolveActiveCart(UUID.fromString(cartId), workspace.getId());
        CartItem item = cartItemRepository.findByIdAndCartId(
                UUID.fromString(itemId), cart.getId())
                .orElseThrow(() -> new CartItemNotFoundException("Cart item not found: " + itemId));

        cartItemRepository.delete(item);
        return toCartDto(reloadCart(cart.getId()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Workspace resolveWorkspace(String storeSlug) {
        return workspaceRepository.findByPublicSlugIgnoreCase(storeSlug)
                .orElseThrow(() -> new StoreNotFoundException("Store not found: " + storeSlug));
    }

    private Cart resolveActiveCart(UUID cartId, UUID workspaceId) {
        return cartRepository.findByIdAndWorkspaceIdAndStatus(cartId, workspaceId, CartStatus.ACTIVE)
                .orElseThrow(() -> new CartNotFoundException("Cart not found or no longer active: " + cartId));
    }

    private Cart reloadCart(UUID cartId) {
        return cartRepository.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found: " + cartId));
    }

    private CartDto toCartDto(Cart cart) {
        List<CartItemDto> itemDtos = cart.getItems().stream()
                .map(this::toCartItemDto)
                .collect(Collectors.toList());

        int subtotal = itemDtos.stream()
                .mapToInt(CartItemDto::getLineTotalAmount)
                .sum();

        return CartDto.builder()
                .id(cart.getId().toString())
                .workspaceId(cart.getWorkspace().getId().toString())
                .status(cart.getStatus().name().toLowerCase())
                .currency(cart.getCurrency())
                .items(itemDtos)
                .subtotalAmount(subtotal)
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }

    private CartItemDto toCartItemDto(CartItem item) {
        Product product = item.getProduct();
        String imageUrl = product.getMainImage() != null
                && product.getMainImage().getStatus() == MediaStatus.READY
                ? product.getMainImage().getUrl()
                : null;

        int lineTotal = item.getUnitPriceAmount() * item.getQuantity();
        return CartItemDto.builder()
                .id(item.getId().toString())
                .productId(product.getId().toString())
                .productTitle(product.getTitle())
                .productSlug(product.getSlug())
                .productImageUrl(imageUrl)
                .quantity(item.getQuantity())
                .unitPriceAmount(item.getUnitPriceAmount())
                .lineTotalAmount(lineTotal)
                .currency(item.getCurrency())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
