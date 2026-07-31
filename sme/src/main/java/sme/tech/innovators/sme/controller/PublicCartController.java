package sme.tech.innovators.sme.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sme.tech.innovators.sme.dto.request.AddCartItemRequest;
import sme.tech.innovators.sme.dto.request.CheckoutRequest;
import sme.tech.innovators.sme.dto.request.InitializePaymentRequest;
import sme.tech.innovators.sme.dto.request.OrderLookupRequest;
import sme.tech.innovators.sme.dto.request.UpdateCartItemRequest;
import sme.tech.innovators.sme.dto.response.ApiResponse;
import sme.tech.innovators.sme.dto.response.CartDto;
import sme.tech.innovators.sme.dto.response.OrderConfirmationDto;
import sme.tech.innovators.sme.dto.response.PaymentInitDto;
import sme.tech.innovators.sme.service.CartService;
import sme.tech.innovators.sme.service.CheckoutService;
import sme.tech.innovators.sme.service.PaymentService;
import sme.tech.innovators.sme.service.RateLimitService;

@RestController
@RequestMapping("/api/v1/public/storefronts/{storeSlug}")
@RequiredArgsConstructor
public class PublicCartController {

    private final CartService cartService;
    private final CheckoutService checkoutService;
    private final PaymentService paymentService;
    private final RateLimitService rateLimitService;

    // ── Cart endpoints ─────────────────────────────────────────────────────

    @PostMapping("/carts")
    public ResponseEntity<ApiResponse<CartDto>> createCart(@PathVariable String storeSlug) {
        CartDto cart = cartService.createCart(storeSlug);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(cart));
    }

    @GetMapping("/carts/{cartId}")
    public ResponseEntity<ApiResponse<CartDto>> getCart(
            @PathVariable String storeSlug,
            @PathVariable String cartId) {
        CartDto cart = cartService.getCart(storeSlug, cartId);
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @PostMapping("/carts/{cartId}/items")
    public ResponseEntity<ApiResponse<CartDto>> addItem(
            @PathVariable String storeSlug,
            @PathVariable String cartId,
            @Valid @RequestBody AddCartItemRequest request) {
        CartDto cart = cartService.addItem(storeSlug, cartId, request.getProductId(), request.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(cart));
    }

    @PatchMapping("/carts/{cartId}/items/{itemId}")
    public ResponseEntity<ApiResponse<CartDto>> updateItem(
            @PathVariable String storeSlug,
            @PathVariable String cartId,
            @PathVariable String itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        CartDto cart = cartService.updateItem(storeSlug, cartId, itemId, request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @DeleteMapping("/carts/{cartId}/items/{itemId}")
    public ResponseEntity<ApiResponse<CartDto>> removeItem(
            @PathVariable String storeSlug,
            @PathVariable String cartId,
            @PathVariable String itemId) {
        CartDto cart = cartService.removeItem(storeSlug, cartId, itemId);
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    // ── Checkout endpoints ─────────────────────────────────────────────────

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<OrderConfirmationDto>> checkout(
            @PathVariable String storeSlug,
            @Valid @RequestBody CheckoutRequest request) {
        OrderConfirmationDto confirmation = checkoutService.checkout(storeSlug, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(confirmation));
    }

    @PostMapping("/checkout/{orderId}/pay")
    public ResponseEntity<ApiResponse<PaymentInitDto>> pay(
            @PathVariable String storeSlug,
            @PathVariable String orderId,
            @RequestBody(required = false) InitializePaymentRequest request) {
        PaymentInitDto init = paymentService.initializePayment(
                storeSlug, orderId, request != null ? request : new InitializePaymentRequest());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(init));
    }

    @GetMapping("/orders/{orderId}/payment/verify")
    public ResponseEntity<ApiResponse<OrderConfirmationDto>> verifyPayment(
            @PathVariable String storeSlug,
            @PathVariable String orderId,
            @RequestParam(defaultValue = "false") boolean forceInventoryHeal) {
        OrderConfirmationDto confirmation =
                paymentService.verifyPayment(storeSlug, orderId, forceInventoryHeal);
        return ResponseEntity.ok(ApiResponse.success(confirmation));
    }

    @PostMapping("/orders/lookup")
    public ResponseEntity<ApiResponse<OrderConfirmationDto>> lookupOrder(
            @PathVariable String storeSlug,
            @Valid @RequestBody OrderLookupRequest request,
            HttpServletRequest httpRequest) {
        rateLimitService.checkAndIncrementOrderLookup(clientIp(httpRequest));
        OrderConfirmationDto confirmation = checkoutService.lookupOrder(
                storeSlug, request.getOrderNumber(), request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(confirmation));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<OrderConfirmationDto>> getOrderConfirmation(
            @PathVariable String storeSlug,
            @PathVariable String orderId) {
        OrderConfirmationDto confirmation = checkoutService.getOrderConfirmation(storeSlug, orderId);
        return ResponseEntity.ok(ApiResponse.success(confirmation));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
