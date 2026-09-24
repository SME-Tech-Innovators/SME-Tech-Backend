package sme.tech.innovators.sme.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sme.tech.innovators.sme.dto.request.*;
import sme.tech.innovators.sme.dto.response.*;
import sme.tech.innovators.sme.service.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/storefronts/{storeSlug}/orders")
@RequiredArgsConstructor
public class CustomerOrderController {
    private final CustomerOrderAccessService access;
    private final OrderCancellationRequestService cancellations;
    private final CheckoutService checkout;
    private final RateLimitService rateLimits;

    @PostMapping("/access-link")
    public ResponseEntity<ApiResponse<Map<String, String>>> accessLink(@PathVariable String storeSlug,
            @Valid @RequestBody OrderLookupRequest request, HttpServletRequest http) {
        rateLimits.checkAndIncrementOrderLookup(http.getRemoteAddr());
        access.sendAccessLink(storeSlug, request.getOrderNumber(), request.getEmail());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(ApiResponse.success(
                Map.of("message", "Your secure link has been requested. Check your inbox and spam folder.")));
    }

    @GetMapping("/{orderId}/customer-access")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<OrderConfirmationDto>> order(@PathVariable String storeSlug,
            @PathVariable UUID orderId, @RequestHeader(value = "X-Order-Access-Token", required = false) String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(
                checkout.toConfirmationDto(access.authorize(storeSlug, orderId, token))));
    }

    @GetMapping("/{orderId}/cancellation-request")
    public ResponseEntity<ApiResponse<OrderCancellationDto>> status(@PathVariable String storeSlug,
            @PathVariable UUID orderId, @RequestHeader(value = "X-Order-Access-Token", required = false) String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(
                cancellations.customerStatus(storeSlug, orderId, token)));
    }

    @PostMapping("/{orderId}/cancellation-request")
    public ResponseEntity<ApiResponse<OrderCancellationDto>> request(@PathVariable String storeSlug,
            @PathVariable UUID orderId, @RequestHeader(value = "X-Order-Access-Token", required = false) String token,
            @Valid @RequestBody RequestOrderCancellation request, HttpServletRequest http) {
        rateLimits.checkAndIncrementOrderLookup(http.getRemoteAddr());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(
                cancellations.request(storeSlug, orderId, token, request.reason())));
    }
}
