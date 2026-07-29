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
import sme.tech.innovators.sme.repository.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final WorkspaceRepository workspaceRepository;

    @Transactional
    public OrderConfirmationDto checkout(String storeSlug, CheckoutRequest request) {
        Workspace workspace = workspaceRepository.findByPublicSlugIgnoreCase(storeSlug)
                .orElseThrow(() -> new StoreNotFoundException("Store not found: " + storeSlug));

        UUID cartId;
        try {
            cartId = UUID.fromString(request.getCartId());
        } catch (IllegalArgumentException e) {
            throw new CheckoutValidationException("Invalid cartId format");
        }

        Cart cart = cartRepository.findByIdAndWorkspaceIdAndStatus(cartId, workspace.getId(), CartStatus.ACTIVE)
                .orElseThrow(() -> new CartNotFoundException("Cart not found or already used: " + cartId));

        if (cart.getItems().isEmpty()) {
            throw new CartEmptyException("Cannot checkout with an empty cart");
        }

        // Build order items from cart — snapshot all prices from backend
        List<OrderItem> orderItems = cart.getItems().stream().map(cartItem -> {
            Product product = cartItem.getProduct();
            int lineTotal = cartItem.getUnitPriceAmount() * cartItem.getQuantity();
            return OrderItem.builder()
                    .product(product)
                    .title(product.getTitle())
                    .sku(product.getSku())
                    .quantity(cartItem.getQuantity())
                    .unitPriceAmount(cartItem.getUnitPriceAmount())
                    .totalAmount(lineTotal)
                    .currency(cartItem.getCurrency())
                    .build();
        }).collect(Collectors.toList());

        int subtotal = orderItems.stream().mapToInt(OrderItem::getTotalAmount).sum();
        int shippingAmount = 0;  // Step 06A: no shipping charge
        int total = subtotal + shippingAmount;

        Map<String, Object> addressMap = buildAddressMap(request.getShippingAddress());

        Order order = Order.builder()
                .workspace(workspace)
                .cart(cart)
                .orderNumber(generateOrderNumber())
                .customerName(request.getCustomer().getName())
                .customerEmail(request.getCustomer().getEmail())
                .customerPhone(request.getCustomer().getPhone())
                .shippingAddress(addressMap)
                .subtotalAmount(subtotal)
                .shippingAmount(shippingAmount)
                .totalAmount(total)
                .currency(cart.getCurrency())
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.UNPAID)
                .build();

        // Link items to order before saving
        orderItems.forEach(item -> item.setOrder(order));
        order.setItems(orderItems);

        Order saved = orderRepository.save(order);

        // Mark cart as converted
        cart.setStatus(CartStatus.CONVERTED);
        cartRepository.save(cart);

        log.info("Order {} created for store {}", saved.getOrderNumber(), storeSlug);
        return toConfirmationDto(saved);
    }

    @Transactional(readOnly = true)
    public OrderConfirmationDto getOrderConfirmation(String storeSlug, String orderId) {
        Workspace workspace = workspaceRepository.findByPublicSlugIgnoreCase(storeSlug)
                .orElseThrow(() -> new StoreNotFoundException("Store not found: " + storeSlug));

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

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String generateOrderNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int suffix = ThreadLocalRandom.current().nextInt(10000, 99999);
        String candidate = "ORD-" + date + "-" + suffix;
        // Retry once on collision (extremely unlikely)
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

    private OrderConfirmationDto toConfirmationDto(Order order) {
        List<OrderItemDto> items = order.getItems().stream()
                .map(item -> OrderItemDto.builder()
                        .id(item.getId().toString())
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
                .createdAt(order.getCreatedAt())
                .build();
    }
}
