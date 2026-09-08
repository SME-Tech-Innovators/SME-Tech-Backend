package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.request.CheckoutRequest;
import sme.tech.innovators.sme.dto.response.OrderConfirmationDto;
import sme.tech.innovators.sme.dto.response.OrderItemDto;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.*;
import sme.tech.innovators.sme.repository.CartRepository;
import sme.tech.innovators.sme.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    public static final String GENERIC_LOOKUP_MISS =
            "We couldn't find an order with those details.";

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final PublicStoreResolver publicStoreResolver;

    @Transactional
    public OrderConfirmationDto checkout(String storeSlug, CheckoutRequest request) {
        Workspace workspace = publicStoreResolver.requireLiveWorkspace(storeSlug);

        UUID cartId;
        try {
            cartId = UUID.fromString(request.getCartId());
        } catch (IllegalArgumentException e) {
            throw new CheckoutValidationException("Invalid cartId format");
        }

        Cart cart = cartRepository.findByIdAndWorkspaceIdAndStatus(cartId, workspace.getId(), CartStatus.ACTIVE)
                .orElseThrow(() -> new CartNotFoundException("Cart not found or already used: " + cartId));

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new CartEmptyException("Cannot checkout with an empty cart");
        }

        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            if (product == null || product.getStatus() != ProductStatus.ACTIVE) {
                throw new ProductNotAvailableException(
                        "Product is not available for checkout: "
                                + (product != null ? product.getId() : cartItem.getId()));
            }

            int available = product.getQuantityAvailable() != null ? product.getQuantityAvailable() : 0;
            if (cartItem.getQuantity() > available) {
                throw new InsufficientStockException(
                        "Not enough stock for this product.", available);
            }

            BigDecimal lineTotal = cartItem.getUnitPriceAmount()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            orderItems.add(OrderItem.builder()
                    .product(product)
                    .title(product.getTitle())
                    .sku(product.getSku())
                    .quantity(cartItem.getQuantity())
                    .unitPriceAmount(cartItem.getUnitPriceAmount())
                    .totalAmount(lineTotal)
                    .currency(cartItem.getCurrency())
                    .build());
        }

        BigDecimal subtotal = orderItems.stream()
                .map(OrderItem::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shippingAmount = BigDecimal.ZERO;
        BigDecimal total = subtotal.add(shippingAmount);

        Order order = Order.builder()
                .workspace(workspace)
                .cart(cart)
                .orderNumber(generateOrderNumber())
                .customerName(request.getCustomer().getName())
                .customerEmail(request.getCustomer().getEmail())
                .customerPhone(request.getCustomer().getPhone())
                .shippingAddress(buildAddressMap(request.getShippingAddress()))
                .subtotalAmount(subtotal)
                .shippingAmount(shippingAmount)
                .totalAmount(total)
                .currency(cart.getCurrency())
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.UNPAID)
                .build();

        orderItems.forEach(item -> item.setOrder(order));
        order.setItems(orderItems);

        Order saved = orderRepository.save(order);

        cart.setStatus(CartStatus.CONVERTED);
        cartRepository.save(cart);

        log.info("Order {} created for store {}", saved.getOrderNumber(), storeSlug);
        return toConfirmationDto(saved);
    }

    @Transactional(readOnly = true)
    public OrderConfirmationDto getOrderConfirmation(String storeSlug, String orderId) {
        Workspace workspace = publicStoreResolver.requireLiveWorkspace(storeSlug);

        UUID oid;
        try {
            oid = UUID.fromString(orderId);
        } catch (IllegalArgumentException e) {
            throw new OrderNotFoundException("Order not found: " + orderId);
        }

        Order order = orderRepository.findByIdAndWorkspaceId(oid, workspace.getId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        return toConfirmationDto(order);
    }

    /**
     * Public order track lookup by order number + customer email.
     * Misses always use a generic message (no enumeration of which field failed).
     */
    @Transactional(readOnly = true)
    public OrderConfirmationDto lookupOrder(String storeSlug, String orderNumber, String email) {
        Workspace workspace = publicStoreResolver.requireLiveWorkspace(storeSlug);

        String normalizedNumber = orderNumber == null ? "" : orderNumber.trim();
        String normalizedEmail = email == null ? "" : email.trim();
        if (normalizedNumber.isEmpty() || normalizedEmail.isEmpty()) {
            throw new OrderNotFoundException(GENERIC_LOOKUP_MISS);
        }

        Order order = orderRepository
                .findByWorkspaceIdAndOrderNumberIgnoreCaseAndCustomerEmailIgnoreCase(
                        workspace.getId(), normalizedNumber, normalizedEmail)
                .orElseThrow(() -> new OrderNotFoundException(GENERIC_LOOKUP_MISS));

        return toConfirmationDto(order);
    }

    private String generateOrderNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int suffix = ThreadLocalRandom.current().nextInt(10000, 99999);
        String candidate = "ORD-" + date + "-" + suffix;
        if (orderRepository.existsByOrderNumber(candidate)) {
            candidate = "ORD-" + date + "-" + ThreadLocalRandom.current().nextInt(10000, 99999);
        }
        return candidate;
    }

    private Map<String, Object> buildAddressMap(CheckoutRequest.ShippingAddress addr) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("line1", addr.getLine1());
        map.put("line2", addr.getLine2() != null ? addr.getLine2() : "");
        map.put("city", addr.getCity());
        map.put("province", addr.getProvince() != null ? addr.getProvince() : "");
        map.put("postalCode", addr.getPostalCode() != null ? addr.getPostalCode() : "");
        map.put("country", addr.getCountry());
        return map;
    }

    public OrderConfirmationDto toConfirmationDto(Order order) {
        List<OrderItemDto> items = order.getItems().stream()
                .map(item -> OrderItemDto.builder()
                        .id(item.getId().toString())
                        .orderId(order.getId().toString())
                        .productId(item.getProduct() != null ? item.getProduct().getId().toString() : null)
                        .title(item.getTitle())
                        .sku(item.getSku())
                        .quantity(item.getQuantity())
                        .unitPriceAmount(item.getUnitPriceAmount())
                        .totalAmount(item.getTotalAmount())
                        .currency(item.getCurrency())
                        .build())
                .collect(Collectors.toList());

        return OrderConfirmationDto.builder()
                .id(order.getId().toString())
                .workspaceId(order.getWorkspace().getId().toString())
                .cartId(order.getCart() != null ? order.getCart().getId().toString() : null)
                .orderNumber(order.getOrderNumber())
                .customerName(order.getCustomerName())
                .customerEmail(order.getCustomerEmail())
                .customerPhone(order.getCustomerPhone())
                .shippingAddress(order.getShippingAddress())
                .items(items)
                .subtotalAmount(order.getSubtotalAmount())
                .shippingAmount(order.getShippingAmount())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .status(order.getStatus().name().toLowerCase())
                .paymentStatus(order.getPaymentStatus().name().toLowerCase())
                .inventoryDecremented(order.isInventoryDecremented())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
