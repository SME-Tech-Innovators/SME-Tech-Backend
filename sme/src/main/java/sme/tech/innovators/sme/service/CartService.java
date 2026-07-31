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
        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity() + quantity);
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

        int subtotal = itemDtos.stream()
                .mapToInt(CartItemDto::getLineTotalAmount)
                .sum();

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

        int lineTotal = item.getUnitPriceAmount() * item.getQuantity();
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
