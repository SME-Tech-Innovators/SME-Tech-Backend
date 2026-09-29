package sme.tech.innovators.sme.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import sme.tech.innovators.sme.dto.request.ShippingQuoteRequest;
import sme.tech.innovators.sme.dto.request.UberDirectQuoteRequest;
import sme.tech.innovators.sme.dto.request.UpdateCartItemRequest;
import sme.tech.innovators.sme.dto.response.ApiResponse;
import sme.tech.innovators.sme.dto.response.CartDto;
import sme.tech.innovators.sme.dto.response.CheckoutDeliveryOptionsDto;
import sme.tech.innovators.sme.dto.response.DeliveryPublicDto;
import sme.tech.innovators.sme.dto.response.OrderConfirmationDto;
import sme.tech.innovators.sme.dto.response.OrderShippingStatusDto;
import sme.tech.innovators.sme.dto.response.PaymentInitDto;
import sme.tech.innovators.sme.dto.response.ShippingQuoteDto;
import sme.tech.innovators.sme.service.CartService;
import sme.tech.innovators.sme.service.CheckoutService;
import sme.tech.innovators.sme.service.DeliveryService;
import sme.tech.innovators.sme.service.OrderShippingService;
import sme.tech.innovators.sme.service.PaymentService;
import sme.tech.innovators.sme.service.RateLimitService;
import sme.tech.innovators.sme.service.ShippingQuoteService;
import sme.tech.innovators.sme.service.UberDirectService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/storefronts/{storeSlug}")
@RequiredArgsConstructor
@Tag(name = "Public Store Cart & Checkout",
     description = "Customer-facing cart, checkout, payment, and delivery options")
public class PublicCartController {

    private final CartService cartService;
    private final CheckoutService checkoutService;
    private final PaymentService paymentService;
    private final RateLimitService rateLimitService;
    private final ShippingQuoteService shippingQuoteService;
    private final OrderShippingService orderShippingService;
    private final DeliveryService deliveryService;
    private final UberDirectService uberDirectService;

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

    // ── Shipping quote (Bob Go) ───────────────────────────────────────────

    @PostMapping("/shipping/quote")
    public ResponseEntity<ApiResponse<ShippingQuoteDto>> shippingQuote(
            @PathVariable String storeSlug,
            @Valid @RequestBody ShippingQuoteRequest request) {
        return ResponseEntity.ok(ApiResponse.success(shippingQuoteService.quote(storeSlug, request)));
    }

    // ── Uber Direct delivery options ──────────────────────────────────────

    @Operation(summary = "Get delivery options for checkout",
               description = "Returns available delivery providers and their fees/ETAs for the "
                             + "customer's dropoff address. Call this before checkout to show "
                             + "delivery options. Pass the returned quoteId in the payment/booking "
                             + "step for price consistency.")
    @PostMapping("/checkout/delivery-options")
    public ResponseEntity<ApiResponse<CheckoutDeliveryOptionsDto>> getDeliveryOptions(
            @PathVariable String storeSlug,
            @Valid @RequestBody UberDirectQuoteRequest request) {
        CheckoutDeliveryOptionsDto options = uberDirectService.getCheckoutOptions(storeSlug, request);
        return ResponseEntity.ok(ApiResponse.success(options));
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

    @GetMapping("/orders/{orderId}/shipping")
    public ResponseEntity<ApiResponse<OrderShippingStatusDto>> getOrderShipping(
            @PathVariable String storeSlug,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                orderShippingService.getPublicShipping(storeSlug, orderId)));
    }

    @GetMapping("/orders/{orderId}/delivery")
    public ResponseEntity<ApiResponse<DeliveryPublicDto>> getDelivery(
            @PathVariable String storeSlug,
            @PathVariable UUID orderId,
            @RequestParam String orderNumber,
            @RequestParam String email) {
        DeliveryPublicDto delivery = deliveryService.getPublic(
                storeSlug, orderId, orderNumber, email);
        return ResponseEntity.ok(ApiResponse.success(delivery));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
