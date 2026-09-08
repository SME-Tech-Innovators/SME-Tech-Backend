package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.response.CartDto;
import sme.tech.innovators.sme.dto.response.CartItemDto;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.*;
import sme.tech.innovators.sme.repository.CartItemRepository;
import sme.tech.innovators.sme.repository.CartRepository;
import sme.tech.innovators.sme.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final PublicStoreResolver publicStoreResolver;

    @Transactional
    public CartDto createCart(String storeSlug) {
        Workspace workspace = publicStoreResolver.requireLiveWorkspace(storeSlug);
        Cart cart = Cart.builder()
                .workspace(workspace)
                .customerSessionId(UUID.randomUUID().toString())
                .currency("ZAR")
                .build();
        cart = cartRepository.save(cart);
        log.info("Created cart {} for store {}", cart.getId(), storeSlug);
        return toCartDto(cart);
    }

    @Transactional(readOnly = true)
    public CartDto getCart(String storeSlug, String cartId) {
        Workspace workspace = publicStoreResolver.requireLiveWorkspace(storeSlug);
        Cart cart = resolveActiveCart(parseUuid(cartId, "cart"), workspace.getId());
        return toCartDto(cart);
    }

    /**
     * Adds a product to the cart. Snapshots unit price from the product entity.
     * If the same product is already in the cart, increases quantity (keeps original price snapshot).
     */
    @Transactional
    public CartDto addItem(String storeSlug, String cartId, String productId, int quantity) {
        if (quantity < 1) {
            throw new InvalidQuantityException("Quantity must be at least 1");
        }
        Workspace workspace = publicStoreResolver.requireLiveWorkspace(storeSlug);
        Cart cart = resolveActiveCart(parseUuid(cartId, "cart"), workspace.getId());
        UUID productUuid = parseUuid(productId, "product");

        Product product = productRepository.findByWorkspaceIdAndIdAndStatus(
                        workspace.getId(), productUuid, ProductStatus.ACTIVE)
                .orElseThrow(() -> new ProductNotAvailableException(
                        "Product is not available: " + productId));

        Optional<CartItem> existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId());
        int resultingQty = existing.map(item -> item.getQuantity() + quantity).orElse(quantity);
        assertStockAvailable(product, resultingQty);

        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(resultingQty);
            cartItemRepository.save(item);
        } else {
            CartItem item = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(quantity)
                    .unitPriceAmount(product.getPriceAmount())
                    .currency(product.getCurrency())
                    .build();
            cartItemRepository.save(item);
            cart.getItems().add(item);
        }

        return toCartDto(reloadCart(cart.getId()));
    }

    @Transactional
    public CartDto updateItem(String storeSlug, String cartId, String itemId, int quantity) {
        if (quantity < 1) {
            throw new InvalidQuantityException("Quantity must be at least 1");
        }
        Workspace workspace = publicStoreResolver.requireLiveWorkspace(storeSlug);
        Cart cart = resolveActiveCart(parseUuid(cartId, "cart"), workspace.getId());
        CartItem item = cartItemRepository.findByIdAndCartId(
                        parseUuid(itemId, "item"), cart.getId())
                .orElseThrow(() -> new CartItemNotFoundException("Cart item not found: " + itemId));

        Product product = item.getProduct();
        if (product == null || product.getStatus() != ProductStatus.ACTIVE) {
            throw new ProductNotAvailableException("Product is not available for this cart item");
        }
        assertStockAvailable(product, quantity);

        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return toCartDto(reloadCart(cart.getId()));
    }

    @Transactional
    public CartDto removeItem(String storeSlug, String cartId, String itemId) {
        Workspace workspace = publicStoreResolver.requireLiveWorkspace(storeSlug);
        Cart cart = resolveActiveCart(parseUuid(cartId, "cart"), workspace.getId());
        CartItem item = cartItemRepository.findByIdAndCartId(
                        parseUuid(itemId, "item"), cart.getId())
                .orElseThrow(() -> new CartItemNotFoundException("Cart item not found: " + itemId));

        cartItemRepository.delete(item);
        cart.getItems().removeIf(i -> i.getId().equals(item.getId()));
        return toCartDto(reloadCart(cart.getId()));
    }

    private void assertStockAvailable(Product product, int requestedQty) {
        int available = product.getQuantityAvailable() != null ? product.getQuantityAvailable() : 0;
        if (requestedQty > available) {
            throw new InsufficientStockException(
                    "Not enough stock for this product.", available);
        }
    }

    private UUID parseUuid(String raw, String kind) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            if ("cart".equals(kind)) {
                throw new CartNotFoundException("Cart not found: " + raw);
            }
            if ("item".equals(kind)) {
                throw new CartItemNotFoundException("Cart item not found: " + raw);
            }
            throw new ProductNotAvailableException("Product is not available: " + raw);
        }
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

        BigDecimal subtotal = itemDtos.stream()
                .map(CartItemDto::getLineTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartDto.builder()
                .id(cart.getId().toString())
                .workspaceId(cart.getWorkspace().getId().toString())
                .customerSessionId(cart.getCustomerSessionId())
                .status(cart.getStatus().name().toLowerCase())
                .currency(cart.getCurrency())
                .items(itemDtos)
                .subtotalAmount(subtotal)
                .totalAmount(subtotal)
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

        BigDecimal lineTotal = item.getUnitPriceAmount()
                .multiply(BigDecimal.valueOf(item.getQuantity()));
        return CartItemDto.builder()
                .id(item.getId().toString())
                .cartId(item.getCart().getId().toString())
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
